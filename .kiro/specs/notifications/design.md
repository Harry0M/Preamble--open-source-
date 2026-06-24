# Design Document

## Overview

WS7 adds dedicated push notifications for social and collaborative events to the Preamble Android app. The work is almost entirely **server-side**: because a client cannot push to another user's device, every new event-driven notification is produced by a Cloud Function (Firestore trigger) running the Firebase Admin SDK against the named **`"preamble"`** database, following the conventions already established by `functions/src/kudos.ts` (`onCollaborativeTaskReaction`), `functions/src/nudge.ts` (`sendNudge`), and `functions/src/referrals.ts` (`onReferralFriendship`).

The client side changes are deliberately small and additive: `PreambleFcmService.createChannels` gains a set of **Social_Channels**, and the `channelType → channelId` switch in `showNotification` is extended to route the new categories. The existing data-only FCM rendering pipeline, the deep-link → `MainActivity` routing, and the `users/{uid}.fcmToken` persistence are reused unchanged except for two small additive deep-link branches (`preamble://social`, `preamble://task/{id}`).

The feature covers five events:

| Event | Trigger source | Recipients | Channel |
| --- | --- | --- | --- |
| Invite accepted (Req 1) | friend-doc creation under the inviter | the Inviter only | `preamble_social_invites` |
| Task assigned (Req 3) | `/collaborativeTasks/{id}` **document create** | assignees (members − admin) | `preamble_social_collab` |
| Task changed (Req 4) | `/collaborativeTasks/{id}` **update**, `task` content diff | members − admin actor | `preamble_social_collab` |
| Member completed slice (Req 5) | `/collaborativeTasks/{id}` **update**, `memberStates` status diff | the Admin only (− completing member) | `preamble_social_collab` |
| Re-categorize kudos / nudge (Req 6.7) | existing functions (copy change only) | unchanged | `preamble_social_kudos` |

Invite-sent confirmation (Req 2) is handled **client-side** as a local confirmation, not a push (see Decision D2).

Referral/reward language is explicitly out of scope; all bodies are plain.

## Research and Grounding

Key facts confirmed by reading the actual codebase (these drive the design):

- **Data-only FCM contract** — `PreambleFcmService.onMessageReceived` reads `data["title"]`, `data["body"]`, `data["deepLink"]`, `data["url"]`, `data["channelType"]` (default `"broadcast"`), plus campaign keys. `showNotification` currently maps `channelType == "promo"` → `CHANNEL_PROMO` and **everything else → `CHANNEL_BROADCAST`**. This is the only mapping point that must change.
- **Existing channels** — only `preamble_broadcasts` ("Updates & Announcements", `IMPORTANCE_DEFAULT`) and `preamble_promos` ("Offers & Tips", `IMPORTANCE_LOW`) are registered in `createChannels`.
- **Existing send pattern** (`kudos.ts`, `nudge.ts`) — read `users/{uid}.fcmToken` from `getFirestore("preamble")`, skip when it is missing/empty, `getMessaging().send({ token, data: {...}, android: { priority: "high" } })`, and wrap each per-recipient send in `try/catch` that logs and swallows. Both currently send `channelType: "broadcast"`.
- **Friendship creation** — `WorkspaceRepository.acceptInvite` writes, in one batch, both `users/{accepterUid}/friends/{senderUid}` and `users/{senderUid}/friends/{accepterUid}`, deletes the accepter's incoming invite, but leaves the sender's mirror `users/{senderUid}/outgoingInvites/{accepterUid}`. The **Inviter is `invite.senderUid`**; the friend doc created under the inviter carries the **accepter's** `name` and `preambleId`. `onReferralFriendship` already triggers on `onDocumentCreated("users/{ownerUid}/friends/{friendUid}")` on `"preamble"` — the exact trigger point reused here.
- **Canonical collaborative doc** (`CollaborativeDocument.build`, `/collaborativeTasks/{taskId}`, `schemaVersion: 2`): fields `adminUid`, `adminName`, `memberUids` (admin first, then assignees), `assigneeUids`, `memberUidMap`, `memberStates` (per-uid `{ uid, name, role, status, isCompleted, completedTimestamp, photoUrl, assignedTimestamp }`), `task` (payload incl. `title`), `createdAt`, `updatedAt`, and `reactions` (added by the kudos feature). Member_Status ∈ `pending | accepted | completed | declined | left | removed`. The admin starts `accepted`; assignees start `pending`. **Assignees never include the admin** (`normalizeAssignees` drops it).
- **Assignment manifests as a document create** — `assignTaskToMultiple` does `db.collection("collaborativeTasks").document(id).set(document)`. A late create can also occur via `finalizeCollaborativeTaskDetails` when AI refinement completes before the doc existed.
- **Change manifests as an update** — `updateCollaborativeTask` / `finalizeCollaborativeTaskDetails` write `task` + `updatedAt` (+ recomputed `memberUidMap`). Both are admin-only (`require(task.collabAdminUid == uid)`).
- **Completion manifests as an update** — `updateCollabAssignmentStatus` / the complete path writes only `memberStates.{uid}.status` (+ `isCompleted`, `completedTimestamp`, `updatedAt`). Reverting completion sets status back to `accepted` and `completedTimestamp = null`.
- **Deep-link routing** (`MainActivity.parseDeepLink` + the consumption `when`) — `preamble://host/path` → `"host/path"`. `invite/{id}` opens the Friends overlay (`showFriendsScreen = true`) with `initialInviteId` prefilled (social-hub-redesign Task 8). There is **no** branch for a plain Social_Hub link and **no** branch for `task/...` yet — both are added here (Decision D6).
- **Functions conventions** — `index.ts` exports each function; triggers declare `{ database: "preamble", document: "..." }`; Node 20; `firebase-functions` v2. No test runner is configured yet (see Testing Strategy).

## Architecture

Four new Notification_Services are added under `functions/src/`, plus one pure, dependency-free helper module that holds the diff/recipient logic so it can be property-tested without Firestore.

```
functions/src/
  social-notifications.ts   NEW  onInviteAccepted, onCollaborativeTaskCreated,
                                 onCollaborativeTaskUpdated  (3 trigger exports)
  notifications/
    logic.ts                NEW  PURE: classifyTaskUpdate(), selectRecipients(),
                                 buildPayload()  (no firebase imports)
  kudos.ts                  EDIT channelType "broadcast" -> "social_kudos"
  nudge.ts                  EDIT channelType "broadcast" -> "social_kudos"
  index.ts                  EDIT export the 3 new triggers
```

```mermaid
flowchart TD
    subgraph Client["Android client (existing, +small edits)"]
      A1[acceptInvite batch] -->|creates users/.../friends/...| FS[(preamble DB)]
      A2[assignTaskToMultiple .set] -->|creates collaborativeTasks/id| FS
      A3[updateCollaborativeTask] -->|updates task payload| FS
      A4[updateCollabAssignmentStatus] -->|updates memberStates.status| FS
    end

    subgraph Functions["Notification_Services (Admin SDK, db=preamble)"]
      T1[onInviteAccepted\nonDocumentCreated\nusers/ownerUid/friends/friendUid]
      T2[onCollaborativeTaskCreated\nonDocumentCreated\ncollaborativeTasks/taskId]
      T3[onCollaborativeTaskUpdated\nonDocumentUpdated\ncollaborativeTasks/taskId]
      L[[notifications/logic.ts\nPURE diff + recipient + payload]]
      LED[(notificationEvents/eventId\nidempotency ledger)]
    end

    FS --> T1 & T2 & T3
    T3 --> L
    T2 --> L
    T1 & T2 & T3 -->|claim eventId| LED
    T1 & T2 & T3 -->|read users/uid.fcmToken| FS
    T1 & T2 & T3 -->|getMessaging().send data-only| FCM[(FCM)]
    FCM --> Dev[PreambleFcmService.onMessageReceived\nchannelType -> channelId\ndeepLink -> MainActivity]
```

### Why these trigger shapes

- **`onInviteAccepted`** reuses the proven `onReferralFriendship` trigger point (`onDocumentCreated` on `users/{ownerUid}/friends/{friendUid}`). The acceptance batch creates two friend docs, so the trigger fires twice. We disambiguate the Inviter from the Actor by checking which owner holds the matching **outgoing-invite mirror**: the Inviter is the `ownerUid` for whom `users/{ownerUid}/outgoingInvites/{friendUid}` exists. Only that firing notifies; the reciprocal firing finds no mirror and returns. This gives exactly one push to the Inviter and never to the Actor (anti-self-notify, Req 7.1).
- **Assignment = `onDocumentCreated`** on `collaborativeTasks/{taskId}` (Decision D1). A brand-new canonical doc *is* the assignment; its `assigneeUids` are precisely the new assignees and exclude the admin by construction. This is cleaner and cheaper than detecting "new memberUids" inside an update trigger, and it also catches the late-create path from `finalizeCollaborativeTaskDetails`.
- **Change and completion share one `onDocumentUpdated`** trigger (mirrors `onCollaborativeTaskReaction`, which already listens on the same document). A single update trigger reads `before`/`after` once and the pure `classifyTaskUpdate` decides whether the write was a **content change**, one-or-more **completions**, both, or neither. Sharing one trigger avoids double-reading the document and keeps all `memberStates`/`task` diffing in one place.

## Components and Interfaces

### `notifications/logic.ts` (pure, no firebase imports)

```ts
export type Status =
  | "pending" | "accepted" | "completed" | "declined" | "left" | "removed";

export interface MemberState { status?: Status; name?: string; completedTimestamp?: number; }
export interface TaskDoc {
  adminUid?: string;
  memberUids?: string[];
  assigneeUids?: string[];
  memberStates?: Record<string, MemberState>;
  task?: Record<string, unknown>;        // payload incl. title
  createdAt?: number;
  updatedAt?: number;
}

/** Member-relevant task fields whose change constitutes a Req-4 "change". */
export const MEMBER_RELEVANT_TASK_KEYS = [
  "title", "description", "dueDate", "deadlineTime", "date",
  "priority", "subtasks", "tags", "isEvent", "eventIcon", "eventColor",
] as const;

export interface TaskUpdateClassification {
  contentChanged: boolean;          // Req 4.1 / 4.5
  completedMemberUids: string[];    // members who crossed into "completed" this write (Req 5.1)
}

/** Pure before/after diff — the heart of Req 8.1/8.2. No I/O. */
export function classifyTaskUpdate(before: TaskDoc, after: TaskDoc): TaskUpdateClassification;

export type EventKind =
  | { kind: "assigned" }
  | { kind: "changed"; actorUid: string | null }
  | { kind: "completed"; completingUid: string };

/** Pure recipient selection — the heart of Req 7 (anti-self-notify + targeting). */
export function selectRecipients(doc: TaskDoc, event: EventKind): string[];

export type PayloadType = "invite_accepted" | "task_assigned" | "task_changed" | "task_completed";

export interface NotificationPayload {
  title: string; body: string; deepLink: string; type: PayloadType; channelType: string;
  [k: string]: string;            // FCM data values are all strings
}

/** Pure payload builder (plain copy, no reward language — Req 1.6/2.4). */
export function buildPayload(input: { type: PayloadType; taskId?: string; title?: string;
  actorName?: string; }): NotificationPayload;
```

`classifyTaskUpdate` rules:
- `contentChanged` = `true` iff the JSON of any `MEMBER_RELEVANT_TASK_KEYS` field differs between `before.task` and `after.task`. Member-state-only writes (status flips, the idempotency marker, `memberUidMap` recompute) leave `task` untouched ⇒ `false` (Req 4.5).
- `completedMemberUids` = every uid where `before.memberStates[uid].status !== "completed"` and `after.memberStates[uid].status === "completed"`. A status that was already `completed` (e.g. a marker write, or an unrelated field change) yields nothing (Req 8.2, 8.3).

`selectRecipients` rules (Req 7 + per-event targeting):
- `assigned` → `after.assigneeUids` (members − admin, by construction), with terminal-status members dropped.
- `changed` → `memberUids − adminUid − {actorUid}` with terminal-status members (`declined`/`left`/`removed`) dropped. The change actor is the admin, so this reduces to the non-terminal assignees.
- `completed` → `{ adminUid }` minus the `completingUid`. If the admin is the completer, the set is empty ⇒ no push (Req 5.2, 7.3).

### `social-notifications.ts` (Admin-SDK triggers)

```ts
export const onInviteAccepted = onDocumentCreated(
  { database: "preamble", document: "users/{ownerUid}/friends/{friendUid}" }, handler);

export const onCollaborativeTaskCreated = onDocumentCreated(
  { database: "preamble", document: "collaborativeTasks/{taskId}" }, handler);

export const onCollaborativeTaskUpdated = onDocumentUpdated(
  { database: "preamble", document: "collaborativeTasks/{taskId}" }, handler);
```

Each handler: classify (pure) → select recipients (pure) → claim `event.id` in the idempotency ledger → for each recipient read `users/{uid}.fcmToken`, skip if absent, `getMessaging().send(...)`, `try/catch` log-and-swallow per recipient (identical structure to `kudos.ts`).

A shared local helper centralizes the send + token lookup + swallow:

```ts
async function sendToRecipients(db, recipientUids: string[], payload: NotificationPayload,
  ctx: Record<string, unknown>): Promise<void>;
```

### Idempotency ledger — `claimEvent`

```ts
/** Returns true the FIRST time an eventId is seen; false on retries (Req 8.4). */
async function claimEvent(db, eventId: string): Promise<boolean> {
  const ref = db.collection("notificationEvents").doc(eventId);
  try {
    await ref.create({ at: Date.now() });   // create() fails if it already exists
    return true;
  } catch { return false; }                 // ALREADY_EXISTS -> retry, skip
}
```

`event.id` (the CloudEvent id) is stable across Cloud Functions retries of the *same* delivery, so `create()` succeeds once and fails on retries — covering Req 8.3 ("same transition") and Req 8.4 ("retried for an already-processed event") without per-document state. A genuine re-completion is a *different* write with a *different* `event.id`, so it is correctly allowed to notify again. `/notificationEvents` is written only through the Admin SDK.

### Client: `PreambleFcmService` changes

`createChannels` registers three Social_Channels in addition to the two existing ones:

```kotlin
private const val CHANNEL_SOCIAL_INVITES = "preamble_social_invites"
private const val CHANNEL_SOCIAL_COLLAB  = "preamble_social_collab"
private const val CHANNEL_SOCIAL_KUDOS   = "preamble_social_kudos"
```

| channelId | User-visible name | Importance | Carries |
| --- | --- | --- | --- |
| `preamble_social_invites` | Invites & Friends | `IMPORTANCE_DEFAULT` | invite-accepted |
| `preamble_social_collab` | Shared Tasks | `IMPORTANCE_DEFAULT` | assignment, change, completion |
| `preamble_social_kudos` | Kudos & Nudges | `IMPORTANCE_DEFAULT` | reactions (kudos), nudges |

The `channelType → channelId` mapping in `showNotification` is replaced with a `when`:

```kotlin
val channelId = when (channelType) {
    "promo"          -> CHANNEL_PROMO
    "social_invites" -> CHANNEL_SOCIAL_INVITES
    "social_collab"  -> CHANNEL_SOCIAL_COLLAB
    "social_kudos"   -> CHANNEL_SOCIAL_KUDOS
    else             -> CHANNEL_BROADCAST     // backward-compatible default
}
```

The priority line is updated so only `"promo"` is low-priority; the social channels use `PRIORITY_DEFAULT` (already the else branch). Unknown/legacy `channelType` values still fall through to `CHANNEL_BROADCAST`, so existing admin broadcasts are unaffected (Req 6 backward compatibility).

### Client: deep-link branches (`MainActivity`, additive)

Two branches are added to the deep-link consumption `when` (Decision D6):

```kotlin
deepLinkTarget == "social" -> { showFriendsScreen = true }          // Social_Hub (Req 1.3, 6.4)
deepLinkTarget.startsWith("task/") -> { selectedTab = 4 }            // Shared Tasks tab (Req 5.4, 6.5)
```

`parseDeepLink` already turns `preamble://social` into `"social"` and `preamble://task/{id}` into `"task/{id}"`; no parser change is required.

## Data Models

No schema changes to `collaborativeTasks`, `users`, friend docs, or invites. The design **prefers diffing over new persistent state** (Req 8 guidance):

- Event detection uses only the existing `before`/`after` snapshots (`task`, `memberStates`, `assigneeUids`, `adminUid`).
- Inviter disambiguation uses the existing `outgoingInvites` mirror that `acceptInvite` already leaves in place.
- The **only** new persisted state is the small idempotency ledger collection:

  **`/notificationEvents/{eventId}`** — `{ at: number }`. Admin-SDK-only. Purpose: at-most-once delivery across function retries.

  **Security_Rules implication:** add a deny-all rule for `/notificationEvents/{doc}` (mirroring the existing `/nudges` rule) so no client can read or forge ledger entries. A TTL policy on the `at` field (e.g. 7 days) is recommended to keep the collection bounded; expiry only re-opens the (already-delivered) event to a duplicate in the practically-impossible case of a retry arriving after TTL.

No per-member `completionNotified` flag is added — the `event.id` ledger makes it unnecessary and avoids touching the client-writable `memberStates` map (which would carry a rules/trust concern).

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

The trigger wiring, FCM delivery, and channel registration are integration/side-effect concerns (tested with the emulator and by compilation). The genuinely property-bearing logic is the **pure** `notifications/logic.ts` module (`classifyTaskUpdate`, `selectRecipients`), which is a set of pure functions over generated documents — an ideal PBT target. The following properties are written against that module.

### Property 1: An actor is never a recipient of their own event

*For any* collaborative task document and any event whose actor is identifiable (a `changed` event with `actorUid`, or a `completed` event with `completingUid`), the recipient set returned by `selectRecipients` SHALL NOT contain that actor.

**Validates: Requirements 7.1, 5.2, 3.2**

### Property 2: Recipients are always a subset of the document's members and never include the admin for member-facing events

*For any* collaborative task document, the recipients of an `assigned` or `changed` event SHALL all be members of the document (in `memberUids`) and SHALL NOT include `adminUid`; and the recipients of a `completed` event SHALL be either empty or exactly `{ adminUid }`.

**Validates: Requirements 3.1, 3.2, 4.1, 5.1, 7.2**

### Property 3: Completion is detected only on a genuine crossing into `completed`

*For any* pair of before/after documents, `classifyTaskUpdate(before, after).completedMemberUids` SHALL contain a uid if and only if that member's status was not `completed` in `before` and is `completed` in `after`; in particular, when `before == after` (a replayed/no-op write) the set SHALL be empty.

**Validates: Requirements 8.1, 8.2, 8.3, 5.1**

### Property 4: Member-state-only writes never register as a content change

*For any* pair of before/after documents that are identical across all `MEMBER_RELEVANT_TASK_KEYS` of `task`, `classifyTaskUpdate(before, after).contentChanged` SHALL be `false`, regardless of differences in `memberStates`, `memberUidMap`, or `updatedAt`.

**Validates: Requirements 4.5, 8.1, 8.2**

### Property 5: When there is no eligible recipient, no one is selected

*For any* `completed` event whose completing member is the admin, and *for any* `assigned`/`changed` event on a document whose only non-terminal member is the actor/admin, `selectRecipients` SHALL return an empty set.

**Validates: Requirements 7.3, 5.2**

## Error Handling

Mirrors the established `kudos.ts` / `referrals.ts` discipline — **log and swallow, never block the data operation** (Req 9):

- **Per-recipient isolation.** Each `getMessaging().send` is wrapped in its own `try/catch` that logs `{ function, taskId/ownerUid, recipientUid, error }` and continues to the next recipient (Req 9.1, 9.3).
- **Missing token.** A recipient whose `users/{uid}.fcmToken` is absent or empty is skipped silently before any send is attempted (Req 1.5, 3.6, 4.6, 5.6, 9.2).
- **No rollback.** Triggers run *after* the data write has already committed; a handler throwing or returning early cannot undo the friendship/assignment/change/completion. Handlers additionally never re-throw, so the function reports success and Cloud Functions does not schedule punitive retries for delivery failures (Req 9.4).
- **Defensive parsing.** All map/array reads go through `asStringMap`-style guards (as in `kudos.ts`); a malformed document causes a no-op return, not a crash.
- **Ledger failure.** If `claimEvent`'s `create()` fails for a reason other than ALREADY_EXISTS (e.g. transient), the handler treats it as "not claimed" and proceeds to send; the worst case is a rare duplicate, never a blocked operation.

## Testing Strategy

### Dual approach

- **Property-based tests** cover the universal behaviors of the pure `notifications/logic.ts` module (Properties 1–5).
- **Integration tests** (Firebase emulator) cover trigger wiring, recipient delivery, dedup, anti-self-notify, and missing-token handling — behaviors that depend on Firestore/FCM and do not vary meaningfully across 100 random inputs.
- **Compilation + focused unit tests** cover the Kotlin client channel/mapping changes.

### Property-based tests (functions side)

A property-testing library is added to `functions/` (e.g. **fast-check** with a Jest or Vitest runner — no runner exists yet, so one is introduced; do not hand-roll PBT). Each property test:
- runs a **minimum of 100 iterations**,
- generates random `TaskDoc`s (random admin, member sets, statuses, task payloads) and random events,
- is tagged: `// Feature: notifications, Property N: <text>`.

These tests require no emulator because `logic.ts` has no firebase imports.

### Integration tests (Firebase emulator)

Representative cases (1–3 each, not property-based — they exercise external service wiring):

1. **Invite accepted** — seed an `outgoingInvites` mirror + both friend docs; assert exactly one push to the Inviter, none to the Actor; assert deepLink `preamble://social` and `channelType: "social_invites"`.
2. **Assignment** — create a canonical doc with 2 assignees; assert each assignee (not the admin) gets `type: "task_assigned"`, `channelType: "social_collab"`, deepLink `preamble://task/{id}`.
3. **Change** — update `task.title`; assert members − admin notified; assert a `memberStates`-only update (status flip) sends **no** change push (Req 4.5).
4. **Completion** — flip an assignee to `completed`; assert the admin is notified once and the completer is not; flip a second time / replay the same event id and assert **no** duplicate (Req 8.3/8.4); admin completing own slice sends nothing (Req 5.2/7.3).
5. **Missing token** — recipient without `fcmToken` is skipped while other recipients still receive (Req 9.2/9.3).
6. **Re-categorization** — kudos/nudge sends now carry `channelType: "social_kudos"`.

### Client tests (Kotlin)

- **Channel registration & mapping** are primarily verified **by compilation** of `PreambleFcmService`. The JVM unit-test path in this repo requires **JDK 21 with `jlink`** (the constraint noted across sibling specs); where that toolchain is available, a small JVM unit test asserts the `channelType → channelId` `when` returns the expected channel for each of `promo`, `social_invites`, `social_collab`, `social_kudos`, and an unknown value (→ `CHANNEL_BROADCAST`). Because the mapping is a trivial pure `when`, compilation plus this single example test is sufficient; it is not a PBT candidate.
- **Deep-link branches** — a unit test (where the JVM toolchain permits) asserts `"social"` opens the Friends overlay and `"task/{id}"` selects the Shared Tasks tab.

### What is intentionally NOT property-tested

- Trigger registration / database routing (`{ database: "preamble" }`) — emulator integration concern.
- FCM delivery and token lookup — external service; mocked or emulated, 1–3 examples.
- Channel registration — Android configuration; verified by compilation.
- `buildPayload` exact copy strings — example-based assertions in the integration tests.

## Design Decisions and Rationale

- **D1 — Assignment as `onDocumentCreated`, not "new memberUids in an update".** The canonical doc is created whole by `assignTaskToMultiple` (and the late-create finalize path), so a create *is* the assignment and `assigneeUids` are exactly the new assignees. This is simpler, cheaper, and avoids reconstructing "what's new" from an update diff.
- **D2 — Invite-sent confirmation is client-local (Req 2), not a push.** The Inviter is the signed-in user performing the action; `sendInvite` already returns `Result` success/failure synchronously, so a local snackbar/confirmation that names the recipient (Req 2.2) and reports failure (Req 2.3) is correct and avoids a pointless self-push. Glossary point 10 leaves this open; this is the chosen resolution.
- **D3 — One shared update trigger for change + completion.** Matches the existing `onCollaborativeTaskReaction` listener on the same document and lets a single `before/after` read drive both classifications.
- **D4 — `event.id` ledger over per-member completion flags.** Satisfies Req 8.3/8.4 generically for all events, keeps `memberStates` (client-writable) untouched, and naturally permits genuine re-completions (new write ⇒ new event id).
- **D5 — Inviter vs Actor via the `outgoingInvites` mirror.** Avoids any new field; the mirror that `acceptInvite` already leaves behind is an unambiguous, owner-scoped signal of who sent the invite, and makes the duplicate friend-doc firing self-deduplicating.
- **D6 — New `preamble://social` deep link + `task/{id}` branch.** A plain Social_Hub link is needed because the existing `invite/{id}` link requires a specific id; `task/{id}` has no consumer branch today even though kudos/nudge already emit it, so adding it also fixes routing for those existing notifications (Req 6.5).
- **D7 — Change-vs-assignment double-push guard.** The AI-refinement `finalizeCollaborativeTaskDetails` write can alter `task` content shortly after creation. Because that update *does* change member-relevant content, `classifyTaskUpdate` would classify it as a change. To avoid an assignment-immediately-followed-by-change pair, the update handler suppresses a `changed` push when the write occurs within a short grace window of `createdAt` (treating early content writes as part of assignment finalization). This is a heuristic; if a deterministic signal is preferred, the client finalize write can set a transient `aiFinalizing`/version marker the trigger keys on. Recorded as an open design choice for review.
