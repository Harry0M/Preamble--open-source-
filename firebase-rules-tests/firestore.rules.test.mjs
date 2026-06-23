import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
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
  writeBatch,
} from "firebase/firestore";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rules = fs.readFileSync(path.join(__dirname, "..", "firebase-firestore-rules.rules"), "utf8");

const testEnv = await initializeTestEnvironment({
  projectId: "demo-preamble",
  firestore: {
    rules,
  },
});

const now = 1_782_044_800_000; // 2026-06-22T00:00:00Z

function authed(uid) {
  return testEnv.authenticatedContext(uid).firestore();
}

function legacyTask(uid, title = "Task") {
  return {
    uid,
    title,
    createdDate: "2026-06-22",
    createdTimestamp: now,
    updatedTimestamp: now,
  };
}

function friend(uid, name) {
  return {
    uid,
    name,
    preambleId: name.toUpperCase(),
    addedAt: now,
    productivityPoints: 0,
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
    memberUidMap: {
      alice: true,
      bob: true,
    },
    memberStates: {
      alice: {
        uid: "alice",
        name: "Alice",
        role: "admin",
        status: "accepted",
        isCompleted: false,
        completedTimestamp: null,
        assignedTimestamp: now,
      },
      bob: {
        uid: "bob",
        name: "Bob",
        role: "member",
        status: "pending",
        isCompleted: false,
        completedTimestamp: null,
        assignedTimestamp: now,
      },
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

async function clear() {
  await testEnv.clearFirestore();
}

try {
  await clear();

  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "tasks/alice-task"), legacyTask("alice", "Alice task"));
    await setDoc(doc(db, "tasks/bob-task"), legacyTask("bob", "Bob task"));
    await setDoc(doc(db, "tagOverrides/alice-tag"), { uid: "alice", tags: "work" });
    await setDoc(doc(db, "tagOverrides/bob-tag"), { uid: "bob", tags: "private" });
  });

  const aliceDb = authed("alice");
  const bobDb = authed("bob");
  const charlieDb = authed("charlie");

  await assertSucceeds(getDoc(doc(aliceDb, "tasks/alice-task")));
  await assertFails(getDoc(doc(aliceDb, "tasks/bob-task")));
  await assertFails(deleteDoc(doc(aliceDb, "tasks/bob-task")));
  await assertSucceeds(setDoc(doc(aliceDb, "tasks/alice-new"), legacyTask("alice", "New task")));
  await assertFails(setDoc(doc(aliceDb, "tasks/alice-for-bob"), legacyTask("bob", "Bad task")));

  const aliceTasksQuery = query(collection(aliceDb, "tasks"), where("uid", "==", "alice"));
  const aliceTasks = await assertSucceeds(getDocs(aliceTasksQuery));
  assert(aliceTasks.docs.length >= 1);

  await assertSucceeds(getDoc(doc(aliceDb, "tagOverrides/alice-tag")));
  await assertFails(getDoc(doc(aliceDb, "tagOverrides/bob-tag")));
  await assertFails(setDoc(doc(aliceDb, "tagOverrides/bad"), { uid: "bob", tags: "x" }));

  await clear();

  await assertSucceeds(setDoc(doc(aliceDb, "users/bob/invites/alice"), {
    id: "alice",
    senderUid: "alice",
    targetUid: "bob",
    senderName: "Alice",
    senderPreambleId: "ALICE",
    timestamp: now,
  }));

  await assertFails(setDoc(doc(charlieDb, "users/alice/friends/charlie"), friend("charlie", "Charlie")));

  const acceptBatch = writeBatch(bobDb);
  acceptBatch.set(doc(bobDb, "users/bob/friends/alice"), friend("alice", "Alice"));
  acceptBatch.set(doc(bobDb, "users/alice/friends/bob"), friend("bob", "Bob"));
  acceptBatch.delete(doc(bobDb, "users/bob/invites/alice"));
  await assertSucceeds(acceptBatch.commit());

  await clear();

  await assertSucceeds(setDoc(doc(aliceDb, "collaborativeTasks/shared-1"), collaborativeTask()));
  await assertSucceeds(getDoc(doc(bobDb, "collaborativeTasks/shared-1")));
  await assertFails(getDoc(doc(charlieDb, "collaborativeTasks/shared-1")));

  const bobCollaborativeTasksQuery = query(
    collection(bobDb, "collaborativeTasks"),
    where("memberUidMap.bob", "==", true),
  );
  const bobCollaborativeTasks = await assertSucceeds(getDocs(bobCollaborativeTasksQuery));
  assert.equal(bobCollaborativeTasks.docs.length, 1);

  const unsafeCollaborativeTasksQuery = query(
    collection(charlieDb, "collaborativeTasks"),
    where("memberUidMap.bob", "==", true),
  );
  await assertFails(getDocs(unsafeCollaborativeTasksQuery));

  const charlieCollaborativeTasksQuery = query(
    collection(charlieDb, "collaborativeTasks"),
    where("memberUidMap.charlie", "==", true),
  );
  const charlieCollaborativeTasks = await assertSucceeds(getDocs(charlieCollaborativeTasksQuery));
  assert.equal(charlieCollaborativeTasks.docs.length, 0);

  await assertSucceeds(updateDoc(doc(bobDb, "collaborativeTasks/shared-1"), {
    "memberStates.bob.status": "accepted",
    "memberStates.bob.isCompleted": false,
    "memberStates.bob.completedTimestamp": null,
    updatedAt: now + 1,
  }));

  await assertFails(updateDoc(doc(bobDb, "collaborativeTasks/shared-1"), {
    "task.title": "Bob should not edit title",
    updatedAt: now + 2,
  }));

  await assertSucceeds(updateDoc(doc(aliceDb, "collaborativeTasks/shared-1"), {
    task: {
      title: "Fix with AI",
      createdDate: "2026-06-22",
      createdTimestamp: now,
      updatedTimestamp: now + 2,
      subtasksJson: "[]",
      isCompleted: false,
      completedTimestamp: null,
      completedDate: null,
      isSyncing: false,
      syncFailed: false,
    },
    updatedAt: now + 2,
  }));

  await assertSucceeds(updateDoc(doc(bobDb, "collaborativeTasks/shared-1"), {
    "task.subtasksJson": "[{\"id\":\"s1\",\"title\":\"One\",\"isCompleted\":true}]",
    "task.updatedTimestamp": now + 3,
    "memberStates.bob.status": "completed",
    "memberStates.bob.isCompleted": true,
    "memberStates.bob.completedTimestamp": now + 3,
    updatedAt: now + 3,
  }));

  await assertSucceeds(updateDoc(doc(aliceDb, "collaborativeTasks/shared-1"), {
    adminUid: "bob",
    adminName: "Bob",
    memberUids: ["alice", "bob"],
    assigneeUids: ["alice"],
    memberUidMap: {
      alice: true,
      bob: true,
    },
    "memberStates.bob.role": "admin",
    "memberStates.bob.status": "accepted",
    "memberStates.alice.role": "member",
    updatedAt: now + 4,
  }));

  await assertFails(deleteDoc(doc(aliceDb, "collaborativeTasks/shared-1")));
  await assertSucceeds(deleteDoc(doc(bobDb, "collaborativeTasks/shared-1")));

  console.log("Firestore rules tests passed");
} finally {
  await testEnv.cleanup();
}
