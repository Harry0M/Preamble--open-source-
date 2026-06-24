# Implementation Plan: Notifications (WS7)

## Overview

This plan delivers dedicated push notifications for social and collaborative events. The work is almost entirely **server-side** (Cloud Functions, TypeScript, Admin SDK against the named `"preamble"` database) with deliberately small, additive **Android client** edits.

The strategy front-loads the pure, dependency-free `functions/src/notifications/logic.ts` module (`classifyTaskUpdate`, `selectRecipients`, `buildPayload`, `MEMBER_RELEVANT_TASK_KEYS`) so the five correctness properties can be validated early with property-based tests (fast-check). It then layers the three Firestore triggers (`onInviteAccepted`, `onCollaborativeTaskCreated`, `onCollaborativeTaskUpdated`) and their shared `sendToRecipients` helper + `claimEvent` idempotency ledger on top of that pure core, re-categorizes the existing kudos/nudge pushes onto a social channel, exports the new triggers, adds the Security_Rules deny-all for the ledger, and finally makes the additive client changes (three social `NotificationChannel`s + `channelType → channelId` `when`, two deep-link branches, and the client-local invite-sent confirmation). Each step builds on the previous ones and ends by wiring the new code into the running system, so no code is left orphaned.

Implementation language: **TypeScript (Node 20, `firebase-functions` v2)** for the Cloud Functions and the pure logic, and **Kotlin** for the Android client edits, exactly as specified in the design. The existing data-only FCM rendering pipeline, deep-link routing, and `users/{uid}.fcmToken` persistence are reused unchanged except for the noted additive edits.

## Tasks

- [x] 1. Implement the pure notification logic in `functions/src/notifications/logic.ts`
  - [x] 1.1 Implement `logic.ts` types, `MEMBER_RELEVANT_TASK_KEYS`, `classifyTaskUpdate`, `selectRecipients`, and `buildPayload`
    - Create `functions/src/notifications/logic.ts` as a pure module with **no firebase imports**: define `Status`, `MemberState`, `TaskDoc`, the `MEMBER_RELEVANT_TASK_KEYS` tuple (`title`, `description`, `dueDate`, `deadlineTime`, `date`, `priority`, `subtasks`, `tags`, `isEvent`, `eventIcon`, `eventColor`), `TaskUpdateClassification`, `EventKind`, `PayloadType`, and `NotificationPayload`
    - Implement `classifyTaskUpdate(before, after)`: `contentChanged` is `true` iff the JSON of any `MEMBER_RELEVANT_TASK_KEYS` field differs between `before.task` and `after.task` (member-state-only writes leave `task` untouched ⇒ `false`); `completedMemberUids` lists every uid whose `memberStates[uid].status` was not `"completed"` in `before` and is `"completed"` in `after` (an already-`completed` status yields nothing)
    - Implement `selectRecipients(doc, event)`: `assigned` → `after.assigneeUids` (members − admin, terminal-status members dropped); `changed` → `memberUids − adminUid − {actorUid}` with `declined`/`left`/`removed` dropped; `completed` → `{ adminUid }` minus `completingUid` (empty when the admin is the completer)
    - Implement `buildPayload({ type, taskId?, title?, actorName? })`: plain copy only (no reward/credit/referral language), correct `deepLink` per type (`invite_accepted` → the Social_Hub link `preamble://social`; task types → `preamble://task/{taskId}`), `channelType` (`social_invites` for invite, `social_collab` for task types), and stringified FCM `data` values
    - _Requirements: 1.2, 1.3, 1.6, 2.4, 3.2, 3.3, 3.4, 3.5, 4.2, 4.3, 4.4, 4.5, 5.2, 5.3, 5.4, 5.5, 7.1, 7.2, 7.3, 8.1, 8.2, 8.3_

  - [ ]* 1.2 Set up the functions test runner and write the property test for anti-self-notification
    - Introduce a property-testing setup in `functions/` since none exists: add **fast-check** plus a runner (Vitest or Jest) to `functions/package.json` with an npm test script and config; do not hand-roll PBT (this runner setup belongs here, not in task 1.1)
    - **Property 1: An actor is never a recipient of their own event** — generate random `TaskDoc`s and `changed`/`completed` events and assert the actor (`actorUid`/`completingUid`) is never in `selectRecipients`
    - Tag `// Feature: notifications, Property 1: An actor is never a recipient of their own event`, minimum 100 iterations
    - **Validates: Requirements 7.1, 5.2, 3.2**

  - [ ]* 1.3 Write the property test for recipient membership and admin exclusion
    - **Property 2: Recipients are a subset of members and never the admin for member-facing events** — assert `assigned`/`changed` recipients are all in `memberUids` and never `adminUid`, and `completed` recipients are either empty or exactly `{ adminUid }`
    - Tag `// Feature: notifications, Property 2: Recipients are always a subset of members and never include the admin for member-facing events`, minimum 100 iterations
    - **Validates: Requirements 3.1, 3.2, 4.1, 5.1, 7.2**

  - [ ]* 1.4 Write the property test for completion crossing detection
    - **Property 3: Completion is detected only on a genuine crossing into `completed`** — assert `completedMemberUids` contains a uid iff its status was not `completed` before and is `completed` after, and that `before == after` yields an empty set
    - Tag `// Feature: notifications, Property 3: Completion is detected only on a genuine crossing into completed`, minimum 100 iterations
    - **Validates: Requirements 8.1, 8.2, 8.3, 5.1**

  - [ ]* 1.5 Write the property test for member-state-only writes
    - **Property 4: Member-state-only writes never register as a content change** — for before/after pairs identical across all `MEMBER_RELEVANT_TASK_KEYS`, assert `contentChanged` is `false` regardless of `memberStates`/`memberUidMap`/`updatedAt` differences
    - Tag `// Feature: notifications, Property 4: Member-state-only writes never register as a content change`, minimum 100 iterations
    - **Validates: Requirements 4.5, 8.1, 8.2**

  - [ ]* 1.6 Write the property test for the no-eligible-recipient case
    - **Property 5: When there is no eligible recipient, no one is selected** — assert a `completed` event whose completer is the admin, and `assigned`/`changed` events whose only non-terminal member is the actor/admin, return an empty set
    - Tag `// Feature: notifications, Property 5: When there is no eligible recipient, no one is selected`, minimum 100 iterations
    - **Validates: Requirements 7.3, 5.2**

- [x] 2. Checkpoint - Ensure all pure-logic tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Implement the notification trigger services in `functions/src/social-notifications.ts`
  - [x] 3.1 Implement the `claimEvent` idempotency ledger and the shared `sendToRecipients` helper
    - Create `functions/src/social-notifications.ts` and add `claimEvent(db, eventId)`: `db.collection("notificationEvents").doc(eventId).create({ at: Date.now() })`, returning `true` on first create and `false` on `ALREADY_EXISTS` (so retries of the same CloudEvent delivery skip), treating any other failure as "not claimed" and proceeding
    - Add `async function sendToRecipients(db, recipientUids, payload, ctx)`: for each recipient read `users/{uid}.fcmToken` from `getFirestore("preamble")`, skip silently when absent/empty, `getMessaging().send({ token, data: payload, android: { priority: "high" } })`, each wrapped in its own `try/catch` that logs `{ function, recipientUid, error }` and continues — identical discipline to `kudos.ts` (log-and-swallow, never block, never re-throw)
    - _Requirements: 8.3, 8.4, 9.1, 9.2, 9.3_

  - [x] 3.2 Implement the `onInviteAccepted` trigger
    - In `social-notifications.ts`, add `onInviteAccepted = onDocumentCreated({ database: "preamble", document: "users/{ownerUid}/friends/{friendUid}" }, handler)`: disambiguate Inviter vs Actor by checking that `users/{ownerUid}/outgoingInvites/{friendUid}` exists (only the Inviter's firing notifies; the reciprocal firing finds no mirror and returns), `claimEvent(event.id)`, build the payload via `buildPayload({ type: "invite_accepted", actorName: <accepter name> })`, and `sendToRecipients([inviterUid], …)`
    - Skip cleanly when the Inviter has no `fcmToken` (Req 1.5) and never notify the Actor (Req 7.1)
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 7.1, 9.4_

  - [x] 3.3 Implement the `onCollaborativeTaskCreated` (assignment) trigger
    - In `social-notifications.ts`, add `onCollaborativeTaskCreated = onDocumentCreated({ database: "preamble", document: "collaborativeTasks/{taskId}" }, handler)`: a brand-new canonical doc *is* the assignment; `claimEvent(event.id)`, compute recipients via `selectRecipients(doc, { kind: "assigned" })` (assignees exclude the admin by construction), build via `buildPayload({ type: "task_assigned", taskId, title })`, and `sendToRecipients(...)` skipping assignees without a token while continuing to the rest
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 7.2_

  - [x] 3.4 Implement the `onCollaborativeTaskUpdated` (change + completion) trigger with the D7 grace-window guard
    - In `social-notifications.ts`, add `onCollaborativeTaskUpdated = onDocumentUpdated({ database: "preamble", document: "collaborativeTasks/{taskId}" }, handler)`: read `before`/`after` once, call `classifyTaskUpdate(before, after)`, then `claimEvent(event.id)`
    - For a content change (`contentChanged`), send `task_changed` to `selectRecipients(after, { kind: "changed", actorUid: adminUid })`; **apply the D7 AI-finalize grace-window guard** — suppress the `changed` push when the write occurs within a short grace window of `after.createdAt` so an `aiFinalizing` early content write (e.g. `finalizeCollaborativeTaskDetails`) is treated as assignment finalization, not a separate change
    - For each uid in `completedMemberUids`, send `task_completed` to `selectRecipients(after, { kind: "completed", completingUid: uid })` (the Admin only, never the completer)
    - Member-state-only writes leave `task` untouched ⇒ no change push (Req 4.5); each send goes through `sendToRecipients`, skipping tokenless members and continuing
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 7.1, 8.1, 8.2, 8.3_

  - [ ]* 3.5 Write Cloud Functions integration tests (Firebase emulator)
    - Add a Node integration layer (`firebase-functions-test`/emulator): **invite accepted** — seed the `outgoingInvites` mirror + both friend docs, assert exactly one push to the Inviter, none to the Actor, deepLink `preamble://social`, `channelType: "social_invites"`; **assignment** — two assignees each get `type: "task_assigned"`, `channelType: "social_collab"`, deepLink `preamble://task/{id}`, admin excluded; **change** — a `task.title` update notifies members − admin while a `memberStates`-only write sends no change push (Req 4.5); **completion** — flipping an assignee to `completed` notifies the admin once, the completer not, a replayed `event.id` sends no duplicate (Req 8.3/8.4), and an admin completing their own slice sends nothing; **missing token** — a tokenless recipient is skipped while others still receive
    - _Requirements: 1.1, 1.5, 3.1, 3.6, 4.5, 5.1, 5.2, 7.3, 8.3, 8.4, 9.2, 9.3_

- [x] 4. Re-categorize the existing kudos and nudge pushes onto the social channel
  - [x] 4.1 Change the kudos push `channelType` to `social_kudos`
    - In `functions/src/kudos.ts`, change the data-only FCM `channelType` from `"broadcast"` to `"social_kudos"` (copy/logic otherwise unchanged); do not re-implement the existing `onCollaborativeTaskReaction` behavior
    - _Requirements: 6.7_

  - [x] 4.2 Change the nudge push `channelType` to `social_kudos`
    - In `functions/src/nudge.ts`, change the data-only FCM `channelType` from `"broadcast"` to `"social_kudos"` (copy/logic otherwise unchanged); do not re-implement the existing `sendNudge` behavior
    - _Requirements: 6.7_

- [x] 5. Export the new triggers from the functions entry point
  - [x] 5.1 Export the three triggers from `functions/src/index.ts`
    - Export `onInviteAccepted`, `onCollaborativeTaskCreated`, and `onCollaborativeTaskUpdated` from `functions/src/index.ts` alongside the existing triggers, following the existing export conventions so all three are deployed
    - _Requirements: 1.1, 3.1, 4.1, 5.1_

- [x] 6. Checkpoint - Ensure the functions build passes
  - Run `npm run build` in `functions/` and ensure it succeeds; ensure all tests pass, ask the user if questions arise.

- [x] 7. Add the Security_Rules deny-all for the idempotency ledger
  - [x] 7.1 Add a deny-all rule for `/notificationEvents/{doc}`
    - In the Firestore rules file (mirroring the existing `/nudges` rule), add `match /notificationEvents/{doc} { allow read, write: if false; }` so the ledger is reachable only through the Admin SDK and no client can read or forge entries
    - Add a comment noting the recommended TTL policy on the `at` field (e.g. 7 days) to keep the collection bounded
    - _Requirements: 8.4_

- [x] 8. Register the social channels and channel mapping in `PreambleFcmService.kt`
  - [x] 8.1 Add the three social channels and replace the `channelType → channelId` mapping
    - In `PreambleFcmService.kt`, add the constants `CHANNEL_SOCIAL_INVITES = "preamble_social_invites"`, `CHANNEL_SOCIAL_COLLAB = "preamble_social_collab"`, `CHANNEL_SOCIAL_KUDOS = "preamble_social_kudos"` and register all three in `createChannels` with user-visible names "Invites & Friends", "Shared Tasks", and "Kudos & Nudges", all `IMPORTANCE_DEFAULT`, alongside the existing two channels
    - Replace the `channelType → channelId` if/else in `showNotification` with a `when` mapping `"promo" → CHANNEL_PROMO`, `"social_invites" → CHANNEL_SOCIAL_INVITES`, `"social_collab" → CHANNEL_SOCIAL_COLLAB`, `"social_kudos" → CHANNEL_SOCIAL_KUDOS`, and `else → CHANNEL_BROADCAST` (backward-compatible default for unknown/legacy values)
    - Fix the priority line so only `"promo"` is low-priority and the social channels use `PRIORITY_DEFAULT`
    - _Requirements: 6.1, 6.2, 6.3, 6.6, 6.7_

  - [ ]* 8.2 Write a JVM unit test for the channel mapping (where the JDK 21/jlink toolchain permits)
    - Assert the `channelType → channelId` `when` returns the expected channel for each of `promo`, `social_invites`, `social_collab`, `social_kudos`, and an unknown value (→ `CHANNEL_BROADCAST`); a single example test plus compilation is sufficient (not a PBT candidate)
    - _Requirements: 6.1, 6.7_

- [x] 9. Add the deep-link routing branches in `MainActivity.kt`
  - [x] 9.1 Add the `preamble://social` and `preamble://task/{id}` consumption branches
    - In `MainActivity.kt`, add two additive branches to the deep-link consumption `when` (no `parseDeepLink` change needed): `deepLinkTarget == "social"` opens the Friends overlay (`showFriendsScreen = true`) for the Social_Hub, and `deepLinkTarget.startsWith("task/")` selects the Shared Tasks tab — the latter also fixes routing for the existing kudos/nudge notifications that already emit `task/{id}`
    - _Requirements: 1.3, 5.4, 6.4, 6.5_

  - [ ]* 9.2 Write a JVM unit test for the deep-link branches (where the JDK 21/jlink toolchain permits)
    - Assert `"social"` opens the Friends overlay and `"task/{id}"` selects the Shared Tasks tab
    - _Requirements: 6.4, 6.5_

- [x] 10. Add the client-local invite-sent confirmation (Req 2, Decision D2)
  - [x] 10.1 Extend the existing invite-send feedback to name the recipient and report failure
    - In `WorkspaceViewModel.sendInvite`, extend the existing `onSuccess` feedback (currently the generic `WorkspaceUiState.Success("Invite sent")`) to a client-local confirmation that **names the recipient** of the invite (Req 2.1, 2.2), and ensure the `onFailure` path informs the Inviter the invite was not sent (Req 2.3); keep the copy plain with no reward/credit/referral language (Req 2.4)
    - This is a client-local confirmation (snackbar), **not** a push (Decision D2); extend the existing social-hub-redesign invite-sent feedback rather than duplicating it, and surface the message through `WorkspaceScreen.kt`'s existing snackbar host
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [x] 11. Final checkpoint - Ensure the Kotlin client compiles and all tests pass
  - Run `./gradlew :app:compileDebugKotlin` to verify the Kotlin client; ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core (non-test) implementation tasks are never optional.
- Each task references specific granular requirements clauses for traceability.
- Property tests (Properties 1–5) target only the pure `functions/src/notifications/logic.ts` module (`classifyTaskUpdate`, `selectRecipients`) and are placed immediately beside it, so correctness issues surface before the trigger/FCM/client wiring depends on them. They run under Node via fast-check with a Vitest/Jest runner introduced in task 1.2 (no runner exists today), a minimum of 100 iterations each, and require no emulator because `logic.ts` has no firebase imports.
- Trigger wiring, FCM delivery, dedup, anti-self-notify, and missing-token handling are verified by the Firebase emulator integration tests (task 3.5), not by property-based tests, matching the design's testing strategy.
- TypeScript is verified by `npm run build` in `functions/`; the Kotlin client is verified by `./gradlew :app:compileDebugKotlin`.
- **Test execution environment:** JVM unit-test execution (the Kotlin example tests in tasks 8.2/9.2) is currently blocked because the build environment lacks a complete JDK 21 with `jlink`. Until that toolchain is available, the Kotlin client changes are verified by **compilation** (`./gradlew :app:compileDebugKotlin`) rather than by running the JVM suite. The Node property tests (task 1.2–1.6) and the Cloud Functions emulator integration tests (task 3.5) are not subject to this constraint and are executable now.
- Decision D7 (the AI-finalize grace-window guard) is implemented as part of task 3.4 to avoid an assignment-immediately-followed-by-change double push.
- Checkpoints provide incremental validation at the boundaries between pure logic, triggers/build, security rules, and the client.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "4.1", "4.2", "7.1", "8.1", "9.1", "10.1"] },
    { "id": 1, "tasks": ["1.2", "3.1", "8.2", "9.2"] },
    { "id": 2, "tasks": ["1.3", "1.4", "1.5", "1.6", "3.2"] },
    { "id": 3, "tasks": ["3.3"] },
    { "id": 4, "tasks": ["3.4"] },
    { "id": 5, "tasks": ["5.1"] },
    { "id": 6, "tasks": ["3.5"] }
  ]
}
```
