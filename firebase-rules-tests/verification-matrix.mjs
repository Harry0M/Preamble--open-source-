// Security-rule verification matrix generator (Requirement 18.1, 18.6, 18.7).
//
// Compares the PROPOSED Firestore rules (working-tree `firebase-firestore-rules.rules`)
// against the PRIOR STABLE rules (`prior-stable.rules`, extracted from the last commit)
// across the four scopes named in the design:
//   1. Legacy_Task paths            (/tasks, /tagOverrides)
//   2. Collaborative_Task paths     (/collaborativeTasks, deprecated /users/{uid}/collaborativeTasks)
//   3. Per-user collections (Req 15)(/users/{uid}, assignedTasks, ai_chat, ai_memory, ai_credits)
//   4. friend / invite / preambleIds (Req 17)
//
// For every read/write operation in each scope it:
//   - evaluates the outcome (allow/deny) under BOTH rule sets,
//   - records every behavioral difference affecting live or collaborative users (18.1),
//   - emits a gap entry {path, operation, actorRole, expectedOutcome} for any operation
//     the app performs but the PROPOSED rules deny (18.6),
//   - records a documented pass/fail result per check (18.7),
// and writes the matrix artifact to the spec folder (markdown + JSON).
//
// Run with the Firestore emulator, e.g.:
//   npm run verify-matrix

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where,
} from "firebase/firestore";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.join(__dirname, "..");
const proposedRules = fs.readFileSync(path.join(repoRoot, "firebase-firestore-rules.rules"), "utf8");
const priorRules = fs.readFileSync(path.join(__dirname, "prior-stable.rules"), "utf8");

const specDir = path.join(repoRoot, ".kiro", "specs", "collaborative-tasks");

const now = 1_782_044_800_000; // 2026-06-22T00:00:00Z

// ---------------------------------------------------------------------------
// Two isolated test environments — one per rule set — backed by the same emulator.
// Different projectIds keep their data stores separate.
// ---------------------------------------------------------------------------
const priorEnv = await initializeTestEnvironment({
  projectId: "demo-prior-stable",
  firestore: { rules: priorRules },
});
const proposedEnv = await initializeTestEnvironment({
  projectId: "demo-proposed",
  firestore: { rules: proposedRules },
});

// ---------------------------------------------------------------------------
// Fixture builders
// ---------------------------------------------------------------------------
function legacyTask(uid, title = "Task") {
  return { uid, title, createdDate: "2026-06-22", createdTimestamp: now, updatedTimestamp: now };
}

function tagOverride(uid) {
  return { uid, tags: "work" };
}

function friend(uid, name) {
  return { uid, name, preambleId: name.toUpperCase(), addedAt: now, productivityPoints: 0 };
}

function invite(senderUid, targetUid, senderName = "Alice") {
  return {
    id: senderUid,
    senderUid,
    targetUid,
    senderName,
    senderPreambleId: senderName.toUpperCase(),
    timestamp: now,
  };
}

function memberState(uid, name, role, status) {
  return {
    uid,
    name,
    role,
    status,
    isCompleted: status === "completed",
    completedTimestamp: status === "completed" ? now : null,
    assignedTimestamp: now,
  };
}

function collaborativeTask(overrides = {}) {
  return {
    schemaVersion: 2,
    taskId: "shared-1",
    adminUid: "alice",
    adminName: "Alice",
    memberUids: ["alice", "bob"],
    assigneeUids: ["bob"],
    memberUidMap: { alice: true, bob: true },
    memberStates: {
      alice: memberState("alice", "Alice", "admin", "accepted"),
      bob: memberState("bob", "Bob", "member", "pending"),
    },
    task: {
      title: "Shared task",
      createdDate: "2026-06-22",
      createdTimestamp: now,
      updatedTimestamp: now,
      subtasksJson: "[]",
    },
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

// Deprecated per-user collaborative task shape (prior schema).
function legacyCollabTask(assignedByUid, assignedToUid) {
  return {
    assignedByUid,
    assignedToUid,
    title: "Old shared task",
    createdTimestamp: now,
    updatedTimestamp: now,
  };
}

// ---------------------------------------------------------------------------
// Operation catalogue. Each entry is one "check": a single read/write that the
// matrix evaluates under both rule sets.
//
//   scope          one of the four named scopes
//   path           the rule path being exercised
//   operation      get | list | create | update | delete
//   actorRole      human-readable role of the actor for this operation
//   actorUid       uid to authenticate as, or null for unauthenticated
//   appPerforms    true if the published/new app actually issues this operation
//   expected       the documented expected outcome under the PROPOSED rules
//   seed(db)       seeds prerequisite data (executed with rules disabled)
//   run(db)        performs the operation as `actorRole` (executed under auth)
// ---------------------------------------------------------------------------
const ops = [
  // ---- Scope 1: Legacy_Task paths ----------------------------------------
  {
    scope: "Legacy_Task", path: "/tasks/{taskId}", operation: "get", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "tasks/alice-task"), legacyTask("alice")),
    run: (db) => getDoc(doc(db, "tasks/alice-task")),
  },
  {
    scope: "Legacy_Task", path: "/tasks/{taskId}", operation: "get", actorRole: "non-owner (other authed user)",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "tasks/alice-task"), legacyTask("alice")),
    run: (db) => getDoc(doc(db, "tasks/alice-task")),
  },
  {
    scope: "Legacy_Task", path: "/tasks", operation: "list", actorRole: "owner querying own uid",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "tasks/alice-task"), legacyTask("alice")),
    run: (db) => getDocs(query(collection(db, "tasks"), where("uid", "==", "alice"))),
  },
  {
    scope: "Legacy_Task", path: "/tasks/{taskId}", operation: "create", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "tasks/alice-new"), legacyTask("alice", "New")),
  },
  {
    scope: "Legacy_Task", path: "/tasks/{taskId}", operation: "create", actorRole: "user writing another uid",
    actorUid: "alice", appPerforms: false, expected: "deny",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "tasks/bad"), legacyTask("bob", "Bad")),
  },
  {
    scope: "Legacy_Task", path: "/tasks/{taskId}", operation: "delete", actorRole: "non-owner (other authed user)",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "tasks/alice-task"), legacyTask("alice")),
    run: (db) => deleteDoc(doc(db, "tasks/alice-task")),
  },
  {
    scope: "Legacy_Task", path: "/tasks/{taskId}", operation: "get", actorRole: "unauthenticated",
    actorUid: null, appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "tasks/alice-task"), legacyTask("alice")),
    run: (db) => getDoc(doc(db, "tasks/alice-task")),
  },
  {
    scope: "Legacy_Task", path: "/tagOverrides/{id}", operation: "get", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "tagOverrides/alice-tag"), tagOverride("alice")),
    run: (db) => getDoc(doc(db, "tagOverrides/alice-tag")),
  },
  {
    scope: "Legacy_Task", path: "/tagOverrides/{id}", operation: "get", actorRole: "non-owner (other authed user)",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "tagOverrides/alice-tag"), tagOverride("alice")),
    run: (db) => getDoc(doc(db, "tagOverrides/alice-tag")),
  },

  // ---- Scope 2: Per-user collections (Requirement 15) --------------------
  {
    scope: "Per-user", path: "/users/{uid}", operation: "get", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "users/alice"), { name: "Alice" }),
    run: (db) => getDoc(doc(db, "users/alice")),
  },
  {
    scope: "Per-user", path: "/users/{uid}", operation: "get", actorRole: "non-owner",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "users/alice"), { name: "Alice" }),
    run: (db) => getDoc(doc(db, "users/alice")),
  },
  {
    scope: "Per-user", path: "/users/{uid}/assignedTasks/{id}", operation: "get", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "users/alice/assignedTasks/t1"), { completed: false, dismissed: false }),
    run: (db) => getDoc(doc(db, "users/alice/assignedTasks/t1")),
  },
  {
    scope: "Per-user", path: "/users/{uid}/assignedTasks/{id}", operation: "update", actorRole: "owner (valid flags)",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "users/alice/assignedTasks/t1"), { completed: false, dismissed: false }),
    run: (db) => setDoc(doc(db, "users/alice/assignedTasks/t1"), { completed: true, dismissed: false }),
  },
  {
    scope: "Per-user", path: "/users/{uid}/ai_chat/{id}", operation: "create", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "users/alice/ai_chat/c1"), { title: "Chat" }),
  },
  {
    scope: "Per-user", path: "/users/{uid}/ai_memory/{id}", operation: "create", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "users/alice/ai_memory/m1"), { fact: "x" }),
  },
  {
    scope: "Per-user", path: "/users/{uid}/ai_credits/{doc}", operation: "get", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "users/alice/ai_credits/balance"), { credits: 10 }),
    run: (db) => getDoc(doc(db, "users/alice/ai_credits/balance")),
  },
  {
    scope: "Per-user", path: "/users/{uid}/ai_credits/{doc}", operation: "update", actorRole: "owner (client write)",
    actorUid: "alice", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "users/alice/ai_credits/balance"), { credits: 10 }),
    run: (db) => setDoc(doc(db, "users/alice/ai_credits/balance"), { credits: 999 }),
  },

  // ---- Scope 3: Collaborative_Task paths ---------------------------------
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "get", actorRole: "Member",
    actorUid: "bob", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => getDoc(doc(db, "collaborativeTasks/shared-1")),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "get", actorRole: "non-Member",
    actorUid: "charlie", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => getDoc(doc(db, "collaborativeTasks/shared-1")),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks", operation: "list", actorRole: "Member (own memberUidMap)",
    actorUid: "bob", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => getDocs(query(collection(db, "collaborativeTasks"), where("memberUidMap.bob", "==", true))),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks", operation: "list", actorRole: "non-Member querying another's map",
    actorUid: "charlie", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => getDocs(query(collection(db, "collaborativeTasks"), where("memberUidMap.bob", "==", true))),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "create", actorRole: "Admin (valid schema-v2)",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "create", actorRole: "non-Admin (adminUid != requester)",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "update", actorRole: "Admin (metadata)",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => updateDoc(doc(db, "collaborativeTasks/shared-1"), { adminName: "Alice II", updatedAt: now + 1 }),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "update", actorRole: "Member (own member-state)",
    actorUid: "bob", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => updateDoc(doc(db, "collaborativeTasks/shared-1"), {
      "memberStates.bob.status": "accepted",
      "memberStates.bob.isCompleted": false,
      "memberStates.bob.completedTimestamp": null,
      updatedAt: now + 1,
    }),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "update", actorRole: "Member editing another member's state",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => updateDoc(doc(db, "collaborativeTasks/shared-1"), {
      "memberStates.alice.status": "declined",
      updatedAt: now + 1,
    }),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "update", actorRole: "Member (own subtask state)",
    actorUid: "bob", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => updateDoc(doc(db, "collaborativeTasks/shared-1"), {
      "task.subtasksJson": "[{\"id\":\"s1\",\"isCompleted\":true}]",
      "task.updatedTimestamp": now + 2,
      "memberStates.bob.status": "accepted",
      "memberStates.bob.isCompleted": false,
      "memberStates.bob.completedTimestamp": null,
      updatedAt: now + 2,
    }),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "update", actorRole: "Member (self-removal)",
    actorUid: "bob", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => updateDoc(doc(db, "collaborativeTasks/shared-1"), {
      memberUids: ["alice"],
      assigneeUids: [],
      memberUidMap: { alice: true },
      "memberStates.bob.status": "left",
      updatedAt: now + 3,
    }),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "update", actorRole: "Admin attempting self-removal",
    actorUid: "alice", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => updateDoc(doc(db, "collaborativeTasks/shared-1"), {
      memberUids: ["bob"],
      assigneeUids: ["bob"],
      memberUidMap: { bob: true },
      "memberStates.alice.status": "left",
      updatedAt: now + 3,
    }),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "delete", actorRole: "Admin",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => deleteDoc(doc(db, "collaborativeTasks/shared-1")),
  },
  {
    scope: "Collaborative_Task", path: "/collaborativeTasks/{taskId}", operation: "delete", actorRole: "non-Admin Member",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "collaborativeTasks/shared-1"), collaborativeTask()),
    run: (db) => deleteDoc(doc(db, "collaborativeTasks/shared-1")),
  },
  {
    scope: "Collaborative_Task", path: "/users/{uid}/collaborativeTasks/{taskId} (deprecated)", operation: "get", actorRole: "owner",
    actorUid: "alice", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "users/alice/collaborativeTasks/old-1"), legacyCollabTask("bob", "alice")),
    run: (db) => getDoc(doc(db, "users/alice/collaborativeTasks/old-1")),
  },
  {
    scope: "Collaborative_Task", path: "/users/{uid}/collaborativeTasks/{taskId} (deprecated)", operation: "create", actorRole: "assigner",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "users/alice/collaborativeTasks/old-2"), legacyCollabTask("bob", "alice")),
  },

  // ---- Scope 4: friend / invite / preambleIds (Requirement 17) -----------
  {
    scope: "Friend/Invite/PreambleIds", path: "/preambleIds/{id}", operation: "get", actorRole: "any signed-in user",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "preambleIds/BOB123"), { uid: "bob" }),
    run: (db) => getDoc(doc(db, "preambleIds/BOB123")),
  },
  {
    scope: "Friend/Invite/PreambleIds", path: "/preambleIds/{id}", operation: "create", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "preambleIds/ALICE1"), { uid: "alice" }),
  },
  {
    scope: "Friend/Invite/PreambleIds", path: "/preambleIds/{id}", operation: "get", actorRole: "unauthenticated",
    actorUid: null, appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "preambleIds/BOB123"), { uid: "bob" }),
    run: (db) => getDoc(doc(db, "preambleIds/BOB123")),
  },
  {
    scope: "Friend/Invite/PreambleIds", path: "/users/{uid}/friends/{friendId}", operation: "get", actorRole: "owner",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "users/alice/friends/bob"), friend("bob", "Bob")),
    run: (db) => getDoc(doc(db, "users/alice/friends/bob")),
  },
  {
    scope: "Friend/Invite/PreambleIds", path: "/users/{uid}/invites/{inviteId}", operation: "create", actorRole: "sender (valid invite)",
    actorUid: "alice", appPerforms: true, expected: "allow",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "users/bob/invites/alice"), invite("alice", "bob")),
  },
  {
    scope: "Friend/Invite/PreambleIds", path: "/users/{uid}/invites/{inviteId}", operation: "create", actorRole: "sender forging another senderUid",
    actorUid: "alice", appPerforms: false, expected: "deny",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "users/bob/invites/alice"), { ...invite("alice", "bob"), senderUid: "charlie" }),
  },
  {
    scope: "Friend/Invite/PreambleIds", path: "/users/{uid}/invites/{inviteId}", operation: "update", actorRole: "recipient",
    actorUid: "bob", appPerforms: false, expected: "deny",
    seed: (db) => setDoc(doc(db, "users/bob/invites/alice"), invite("alice", "bob")),
    run: (db) => updateDoc(doc(db, "users/bob/invites/alice"), { senderName: "Tampered" }),
  },
  {
    scope: "Friend/Invite/PreambleIds", path: "/users/{uid}/invites/{inviteId}", operation: "delete", actorRole: "recipient (decline)",
    actorUid: "bob", appPerforms: true, expected: "allow",
    seed: (db) => setDoc(doc(db, "users/bob/invites/alice"), invite("alice", "bob")),
    run: (db) => deleteDoc(doc(db, "users/bob/invites/alice")),
  },
  {
    scope: "Friend/Invite/PreambleIds", path: "/users/{uid}/friends/{friendId}", operation: "create", actorRole: "stranger writing own record without reciprocal/ invite",
    actorUid: "charlie", appPerforms: false, expected: "deny",
    seed: () => Promise.resolve(),
    run: (db) => setDoc(doc(db, "users/alice/friends/charlie"), friend("charlie", "Charlie")),
  },
];

// ---------------------------------------------------------------------------
// Evaluation
// ---------------------------------------------------------------------------
function actorDb(env, actorUid) {
  return actorUid ? env.authenticatedContext(actorUid).firestore() : env.unauthenticatedContext().firestore();
}

async function evaluateInEnv(env, op) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await op.seed(ctx.firestore());
  });
  const db = actorDb(env, op.actorUid);
  try {
    await op.run(db);
    return "allow";
  } catch {
    return "deny";
  }
}

const checks = [];
for (const op of ops) {
  const prior = await evaluateInEnv(priorEnv, op);
  const proposed = await evaluateInEnv(proposedEnv, op);
  const difference = prior !== proposed;
  const pass = proposed === op.expected;
  const gap = op.appPerforms && proposed === "deny";
  checks.push({ op, prior, proposed, difference, pass, gap });
}

await priorEnv.cleanup();
await proposedEnv.cleanup();

// ---------------------------------------------------------------------------
// Reporting
// ---------------------------------------------------------------------------
const differences = checks.filter((c) => c.difference);
const gaps = checks.filter((c) => c.gap);
const failures = checks.filter((c) => !c.pass);
const passCount = checks.length - failures.length;

const generatedAt = new Date().toISOString();
const scopes = ["Legacy_Task", "Per-user", "Collaborative_Task", "Friend/Invite/PreambleIds"];

function cell(v) {
  return v === "allow" ? "allow" : "deny";
}

let md = "";
md += "# Security Rules Verification Matrix\n\n";
md += "_Generated by `firebase-rules-tests/verification-matrix.mjs` (Requirement 18.1, 18.6, 18.7)._\n\n";
md += `- Generated at: ${generatedAt}\n`;
md += "- Proposed rules: `firebase-firestore-rules.rules` (working tree)\n";
md += "- Prior stable rules: `firebase-rules-tests/prior-stable.rules` (extracted from last commit)\n";
md += `- Checks run: **${checks.length}** — passed: **${passCount}**, failed: **${failures.length}**\n`;
md += `- Behavioral differences vs. prior stable: **${differences.length}**\n`;
md += `- Gap entries (app performs but proposed denies): **${gaps.length}**\n\n`;

md += "## Summary (Requirement 18.7 — pass/fail per check)\n\n";
for (const scope of scopes) {
  const scoped = checks.filter((c) => c.op.scope === scope);
  if (scoped.length === 0) continue;
  md += `### Scope: ${scope}\n\n`;
  md += "| Path | Operation | Actor role | Prior | Proposed | Expected | Diff | Result |\n";
  md += "| --- | --- | --- | --- | --- | --- | --- | --- |\n";
  for (const c of scoped) {
    md += `| \`${c.op.path}\` | ${c.op.operation} | ${c.op.actorRole} | ${cell(c.prior)} | ${cell(c.proposed)} | ${c.op.expected} | ${c.difference ? "yes" : "—"} | ${c.pass ? "PASS" : "FAIL"} |\n`;
  }
  md += "\n";
}

md += "## Behavioral differences vs. prior stable rules (Requirement 18.1)\n\n";
if (differences.length === 0) {
  md += "_No behavioral differences detected._\n\n";
} else {
  md += "| Scope | Path | Operation | Actor role | Prior | Proposed | Notes |\n";
  md += "| --- | --- | --- | --- | --- | --- | --- |\n";
  for (const c of differences) {
    const note = c.prior === "allow" && c.proposed === "deny"
      ? "Tightened — previously permitted, now denied"
      : "New capability — previously denied, now permitted";
    md += `| ${c.op.scope} | \`${c.op.path}\` | ${c.op.operation} | ${c.op.actorRole} | ${cell(c.prior)} | ${cell(c.proposed)} | ${note} |\n`;
  }
  md += "\n";
}

md += "## Gap entries (Requirement 18.6)\n\n";
md += "Operations the app performs that the proposed rules deny. Each entry is `{path, operation, actorRole, expectedOutcome}`.\n\n";
if (gaps.length === 0) {
  md += "_No gaps — every operation the app performs is permitted by the proposed rules._\n\n";
} else {
  md += "| path | operation | actorRole | expectedOutcome |\n";
  md += "| --- | --- | --- | --- |\n";
  for (const c of gaps) {
    md += `| \`${c.op.path}\` | ${c.op.operation} | ${c.op.actorRole} | ${c.op.expected} |\n`;
  }
  md += "\n";
}

md += "## Check failures\n\n";
if (failures.length === 0) {
  md += "_All checks produced their expected proposed-rule outcome._\n";
} else {
  md += "| Scope | Path | Operation | Actor role | Proposed | Expected |\n";
  md += "| --- | --- | --- | --- | --- | --- |\n";
  for (const c of failures) {
    md += `| ${c.op.scope} | \`${c.op.path}\` | ${c.op.operation} | ${c.op.actorRole} | ${cell(c.proposed)} | ${c.op.expected} |\n`;
  }
  md += "\n";
}

const jsonArtifact = {
  generatedAt,
  proposedRules: "firebase-firestore-rules.rules",
  priorStableRules: "firebase-rules-tests/prior-stable.rules",
  totals: {
    checks: checks.length,
    passed: passCount,
    failed: failures.length,
    behavioralDifferences: differences.length,
    gaps: gaps.length,
  },
  checks: checks.map((c) => ({
    scope: c.op.scope,
    path: c.op.path,
    operation: c.op.operation,
    actorRole: c.op.actorRole,
    appPerforms: c.op.appPerforms,
    priorOutcome: c.prior,
    proposedOutcome: c.proposed,
    expectedOutcome: c.op.expected,
    behavioralDifference: c.difference,
    result: c.pass ? "pass" : "fail",
  })),
  gapEntries: gaps.map((c) => ({
    path: c.op.path,
    operation: c.op.operation,
    actorRole: c.op.actorRole,
    expectedOutcome: c.op.expected,
  })),
};

fs.mkdirSync(specDir, { recursive: true });
fs.writeFileSync(path.join(specDir, "verification-matrix.md"), md, "utf8");
fs.writeFileSync(path.join(specDir, "verification-matrix.json"), JSON.stringify(jsonArtifact, null, 2), "utf8");

console.log(`Verification matrix: ${checks.length} checks, ${passCount} passed, ${failures.length} failed, ` +
  `${differences.length} behavioral differences, ${gaps.length} gaps.`);
console.log(`Artifact written to ${path.join(specDir, "verification-matrix.md")}`);

if (failures.length > 0) {
  console.error("FAIL: one or more checks did not match the expected proposed-rule outcome.");
  process.exitCode = 1;
}
