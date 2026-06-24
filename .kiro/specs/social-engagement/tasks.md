# Implementation Plan: Social Engagement

## Overview

This plan layers three social mechanics — kudos/reactions, a weekly friends leaderboard, and nudges — onto the shipped `collaborative-tasks` feature. The strategy front-loads the pure, side-effect-free client logic in `com.theblankstate.preamble.collab` (reaction toggle/validation, reaction projection, weekly-window bucketing, points award + leaderboard ranking, nudge eligibility, nudge rate limit) so the 7 correctness properties can be validated early with property-based tests (jqwik). It then wires that logic through Room (the single `Migration(29, 30)`), `WorkspaceRepository`, `WorkspaceViewModel` (optimistic UI + timeouts), the Firestore Security_Rules, a TypeScript Cloud Functions layer (callable `sendNudge` + `onCollaborativeTaskReaction` trigger), and finally the Compose UI.

Implementation language: **Kotlin** for the Android client and pure logic, **TypeScript (Node)** for the Cloud Functions, and **JavaScript (Node, `@firebase/rules-unit-testing`)** for the Firestore rules verification suite, as specified in the design.

Each step builds on the previous ones and ends by wiring the new logic into the running app, so no code is left orphaned. Existing collaborative-tasks behavior is unchanged.

## Tasks

- [x] 1. Implement social-engagement pure logic in `com.theblankstate.preamble.collab`
  - [x] 1.1 Implement `Reactions.kt` toggle/change/remove + validation
    - Create `collab/Reactions.kt` as a pure object (no Android/Firestore deps): define `EMOJI_SET` as the six fixed emoji in order, `isValidEmoji`, the `ReactionResult` sealed interface (`Updated(reactions, effect)` / `Rejected(reactions, reason)`) and `Effect` enum (`ADDED`/`CHANGED`/`REMOVED`)
    - Implement `apply(reactions, reactorUid, emoji, targetUid, now)` toggle semantics (no entry + valid emoji → ADD with UTC `createdAt`; existing different emoji → CHANGE in place; same emoji → REMOVE; out-of-set emoji → Rejected with unchanged map) and `remove(reactions, reactorUid)` (own-entry-only, no-op when absent), always keeping at most one entry per reactor and leaving every other reactor's entry byte-for-byte identical
    - _Requirements: 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ]* 1.2 Write property test for reaction semantics
    - **Property 1: Reaction toggle/change/remove semantics, single-entry, validation, and isolation**
    - Tag `// Feature: social-engagement, Property 1: ...`, `@Property(tries = 100)` minimum; generators produce reactions maps with 0..N reactors and in-set/out-of-set/non-emoji strings
    - **Validates: Requirements 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5**

  - [x] 1.3 Extend `TaskProjection.kt` with reaction projection
    - Add the `TaskReaction(reactorUid, reactorName, emoji, targetUid?, createdAt)` type and extend `documentToTask` to project the document's `reactions` map into a new `reactionsJson` field, resolving each reactor's display name from `memberStates[reactorUid].name`; a document with no reactions projects to a `null`/empty `reactionsJson`
    - Ensure the admin's own `reactionsJson` is never written back into the shared `task` payload (reactions live at the document top level, not inside `task`)
    - _Requirements: 3.1, 3.3_

  - [ ]* 1.4 Write property test for reaction projection fidelity
    - **Property 2: Reaction projection fidelity**
    - Tag `// Feature: social-engagement, Property 2: ...`, `@Property(tries = 100)` minimum; generator produces `reactions` + `memberStates` maps with mixed statuses and missing names
    - **Validates: Requirements 3.1**

  - [x] 1.5 Implement `WeeklyWindow.kt` UTC bucketing
    - Create `collab/WeeklyWindow.kt` as a pure object: `windowStart(utcMillis)` (most recent Monday 00:00:00 UTC at or before the instant), `weekKey(utcMillis)` (stable ISO-8601 week key in UTC, equal keys iff same window), and `isInCurrentWindow(awardMillis, nowMillis)`
    - _Requirements: 7.4, 9.5_

  - [ ]* 1.6 Write property test for weekly-window bucketing
    - **Property 3: Weekly-window bucketing is deterministic at the Monday-00:00-UTC boundary**
    - Tag `// Feature: social-engagement, Property 3: ...`, `@Property(tries = 100)` minimum; generators cluster timestamps around Monday-00:00-UTC boundaries including year/ISO-week rollovers
    - **Validates: Requirements 7.4, 9.5**

  - [x] 1.7 Implement `Leaderboard.kt` award + ranking
    - Create `collab/Leaderboard.kt` as a pure object: `COMPLETION_AWARD = 10`, the `ScoreDoc(uid, totalPoints, weeklyPoints, awardedTasks)` and `Entry(uid, name, weeklyPoints)` types
    - Implement `award(doc, taskId, now)` (no-op when `taskId ∈ awardedTasks`; otherwise `totalPoints += 10`, `weeklyPoints[weekKey(now)] += 10`, `awardedTasks += taskId`; `totalPoints` always equals 10 × distinct awarded tasks and never decreases) and `ranking(selfUid, friendUids, scores, names, now)` (entries are exactly self + friend uids, scored by `weeklyPoints[weekKey(now)]` defaulting to 0, ordered descending)
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 8.2, 8.3, 9.1, 9.2, 9.3, 9.4_

  - [ ]* 1.8 Write property test for points award
    - **Property 4: Points award is idempotent, monotonic, and bucketed by exactly the Completion_Award**
    - Tag `// Feature: social-engagement, Property 4: ...`, `@Property(tries = 100)` minimum; generator produces `ScoreDoc`s with overlapping/disjoint `awardedTasks` and varied `weeklyPoints` keys
    - **Validates: Requirements 7.1, 7.2, 7.3, 7.4, 8.2, 8.3**

  - [ ]* 1.9 Write property test for leaderboard ranking
    - **Property 5: Friends_Leaderboard membership, ordering, and window exclusion**
    - Tag `// Feature: social-engagement, Property 5: ...`, `@Property(tries = 100)` minimum; generator produces self/friend uid sets with and without overlap and non-friend decoy uids
    - **Validates: Requirements 9.1, 9.2, 9.3, 9.4**

  - [x] 1.10 Implement `NudgeEligibility.kt`
    - Create `collab/NudgeEligibility.kt` as a pure object: `classify(senderUid, targetUid, memberUids, targetStatus)` returning `SenderNotMember`, `SelfNudge`, `TargetNotPending`, or `Eligible` (eligible only when sender is a member, target differs from sender, and target status is `pending`, so an admin — being a member — may nudge any pending member)
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

  - [ ]* 1.11 Write property test for nudge eligibility
    - **Property 6: Nudge eligibility classification**
    - Tag `// Feature: social-engagement, Property 6: ...`, `@Property(tries = 100)` minimum
    - **Validates: Requirements 11.1, 11.2, 11.3, 11.4**

  - [x] 1.12 Implement `NudgeRateLimit.kt`
    - Create `collab/NudgeRateLimit.kt` as a pure object: `WINDOW_MILLIS = 60 min`, `canSend(lastSentAt, now)` (true when never sent or ≥ 60 min elapsed), `cooldownRemaining(lastSentAt, now)` (0 when allowed, else millis until 60 min elapse); the result depends only on the single supplied `(sender, target, task)` last-sent timestamp, giving per-triple independence
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

  - [ ]* 1.13 Write property test for nudge rate limit
    - **Property 7: Nudge rate limit over a 60-minute rolling window**
    - Tag `// Feature: social-engagement, Property 7: ...`, `@Property(tries = 100)` minimum; generator produces `lastSentAt` values spanning just-inside and just-outside the 60-minute window
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4**

- [x] 2. Checkpoint - Ensure all pure-logic tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Add the Room reaction column and migration
  - [x] 3.1 Add `Task.reactionsJson` + `Migration(29, 30)` and register it
    - Add the nullable `reactionsJson: String?` column to the `Task` entity, bump the Room DB version 29 → 30, define `MIGRATION_29_30` (`ALTER TABLE tasks ADD COLUMN reactionsJson TEXT`), and register it in the database builder's `addMigrations(...)` list
    - _Requirements: 3.1, 3.2_

- [x] 4. Wire reaction, points, leaderboard, and nudge operations into `WorkspaceRepository`
  - [x] 4.1 Implement `updateMyReaction` transaction
    - Add `updateMyReaction(taskId, emoji?): Result<Unit>` mirroring `updateCollabAssignmentStatus`: client-side guard `emoji == null || Reactions.isValidEmoji(emoji)`, transaction reads the snapshot, computes via `Reactions.apply`/`Reactions.remove`, and writes only `reactions.{uid}` (or `FieldValue.delete()` for removal) plus `updatedAt`
    - _Requirements: 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 4.2 Implement `awardCompletionPoints` wired into the accepted→completed path
    - Add `awardCompletionPoints(taskId)` as a transaction on `/leaderboard/{uid}` that reads the current `ScoreDoc` (empty on first award), applies `Leaderboard.award(doc, taskId, now)`, and commits `totalPoints`/`weeklyPoints`/`awardedTasks`/`updatedAt` only when the doc changes; invoke it from the collaborative `accepted → completed` transition in `updateCollabAssignmentStatus` after the completion commits, as best-effort (a failure is logged/surfaced but never rolls back completion)
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 4.3 Implement leaderboard reads
    - Add a read path that loads `/leaderboard/{uid}` for self (snapshot listener) and bounded multi-get/`whereIn` batches for friend uids, exposed as a flow of `Map<String, Leaderboard.ScoreDoc>` for the ViewModel to rank; `.catch` retains last-loaded scores and emits a message
    - _Requirements: 9.1, 9.4_

  - [x] 4.4 Implement the `sendNudge` callable client
    - Add `sendNudge(taskId, targetUid): Result<Unit>` invoking the Firebase Functions `sendNudge` callable inside the caller's coroutine, mapping a `failed-precondition` error (with reason) to `Result.failure` so the ViewModel can revert and message
    - _Requirements: 10.2, 10.5, 11.2, 12.2_

  - [ ]* 4.5 Write unit tests for the award call site
    - Verify `awardCompletionPoints` is invoked only on the collaborative `accepted → completed` transition and never for a non-collaborative task, and that a retried award after a transient failure cannot double-count (idempotent on `awardedTasks`)
    - _Requirements: 7.5, 7.2_

- [x] 5. Implement optimistic UI and leaderboard state in `WorkspaceViewModel`
  - [x] 5.1 Implement optimistic `updateMyReaction`
    - Add `updateMyReaction(task, emoji)` that snapshots the task's projected reactions, applies `Reactions.apply` to local state within 200 ms, launches `repository.updateMyReaction` inside `withTimeout(30_000)`, and restores the exact snapshot with an error message on failure/timeout
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 5.2 Implement optimistic `nudge`
    - Add `nudge(task, targetUid)` that gates the control via `NudgeRateLimit.canSend`/`cooldownRemaining` against the last local nudge time for that triple, reflects an optimistic nudged state within 200 ms, launches `repository.sendNudge` inside `withTimeout(30_000)`, and reverts the nudged state with the appropriate message (`only pending members can be nudged`, `nudged recently, try again later`) on failure/timeout
    - _Requirements: 10.4, 10.5, 11.2, 12.2_

  - [x] 5.3 Expose the leaderboard `StateFlow`
    - Add `leaderboard: StateFlow<List<Leaderboard.Entry>>` computed from the repository leaderboard reads plus the signed-in user's friend uids/names via `Leaderboard.ranking(selfUid, friendUids, scores, names, now)` with `now = System.currentTimeMillis()`, recomputing against the current week key so a window crossing changes the ranking without any write
    - _Requirements: 9.1, 9.2, 9.3, 9.5, 9.6_

- [x] 6. Checkpoint - Ensure all client wiring tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement and verify the Firestore Security_Rules
  - [x] 7.1 Add `updatesOwnReactionOnly()` + emoji validation to the `/collaborativeTasks` update allow list
    - In `firebase-firestore-rules.rules`, add `isValidReactionEmoji`, `isValidOwnReaction`, and `updatesOwnReactionOnly()` (mirroring the deployed `updatesOwnMemberStatusOnly()` idiom: `diff().affectedKeys().hasOnly(['reactions','updatedAt'])` plus the own-key `reactions` diff scoped to `request.auth.uid`, allowing own-key removal or a valid six-set emoji), gate it on `isCollabMember()`, and add `|| updatesOwnReactionOnly()` to the `/collaborativeTasks/{taskId}` `update` allow rule
    - _Requirements: 1.4, 4.1, 4.2, 4.3, 4.4_

  - [x] 7.2 Add the `/leaderboard/{uid}` friend-readable, owner-only, +10 monotonic rules
    - Add `isValidPointsAward()` (exactly `before + 10`, non-decreasing, `affectedKeys().hasOnly(['totalPoints','weeklyPoints','awardedTasks','updatedAt'])`) and the `match /leaderboard/{uid}` block: read for owner or a friend present in the owner's `friends` subcollection; create owner-only at exactly 10; update owner-only via `isValidPointsAward()`; delete denied
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 9.1, 9.3_

  - [x] 7.3 Add the `/nudges/{nudgeId}` deny-all rule
    - Add `match /nudges/{nudgeId} { allow read, write: if false; }` so the collection is reachable only through the Admin-SDK `sendNudge` Cloud Function
    - _Requirements: 12.1_

  - [ ]* 7.4 Extend the emulator rules suite and verification matrix
    - Extend `firebase-rules-tests/firestore.rules.test.mjs` and `verification-matrix.mjs` to cover: a member adding/changing/removing only their own `reactions.{uid}` (allow); editing another member's reaction key, a non-member reaction write, an out-of-set emoji, keys beyond `['reactions','updatedAt']`, and unauthenticated writes (deny); owner and friend reads of `/leaderboard/{uid}` (allow), non-friend read, +10 vs other-delta increments, decreases, non-owner writes, and unauthenticated writes (deny); any client read/write to `/nudges/**` (deny)
    - _Requirements: 1.4, 4.1, 4.2, 4.3, 4.4, 8.1, 8.2, 8.3, 8.4_

- [x] 8. Checkpoint - Ensure all rules tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Implement the Cloud Functions push layer (`functions/`, TypeScript)
  - [x] 9.1 Implement the `sendNudge` callable
    - Add `functions/src/nudge.ts`: authenticate the caller (deny when absent), read `/collaborativeTasks/{taskId}` and verify membership (Req 11.1) and not-self (Req 11.3), verify `memberStates[targetUid].status == "pending"` (Req 11.2/11.4), read `/nudges/{taskId}_{senderUid}_{targetUid}` and enforce the 60-minute window server-side (Req 12), then write `lastSentAt = now` and send a data-only FCM message to `users/{targetUid}.fcmToken` with the rendered body `"<sender> nudged you about '<task>'"`; return `ok` or a `failed-precondition` with a reason string
    - _Requirements: 10.2, 10.3, 11.1, 11.2, 11.3, 11.4, 12.1, 12.2, 12.3, 12.4_

  - [x] 9.2 Implement the `onCollaborativeTaskReaction` Firestore onUpdate trigger
    - Add `functions/src/kudos.ts`: on `/collaborativeTasks/{taskId}` updates, diff `before.reactions` vs `after.reactions`; when the effect is `ADDED`/`CHANGED` (never `REMOVED`), read `after.memberStates`, select every member other than the reactor whose `status == "completed"`, and send each a data-only FCM push carrying the reactor's display name, the emoji, and the task title; removals and the no-completed-members case send nothing, and a per-recipient FCM failure is logged and swallowed without affecting the stored reaction
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 9.3 Export both functions from `functions/src/index.ts`
    - Export `sendNudge` and `onCollaborativeTaskReaction` from `functions/src/index.ts` alongside the existing triggers, using the existing data-only FCM rendering conventions (`title`/`body`/`deepLink`/`type`)
    - _Requirements: 6.1, 10.2_

  - [ ]* 9.4 Write Cloud Functions integration tests
    - Add a Node test layer (e.g. `firebase-functions-test` against the emulator): for `onCollaborativeTaskReaction`, an added/changed reaction pushes to every other `completed` member and to none when there are no completed members, a removed reaction sends nothing, and the payload carries reactor name + emoji + task title; for `sendNudge`, an eligible call sends FCM and writes `lastSentAt` while non-member/non-pending/self/within-60-min calls return `failed-precondition` and send nothing, and a call after 60 minutes succeeds
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 10.2, 10.3_

- [x] 10. Checkpoint - Ensure all functions tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Wire the social features into the Compose UI
  - [x] 11.1 Add the six-emoji reaction control + reactor summary/empty-state
    - In `CollaboratorMemberList`/`TaskDetailBottomSheet`, render the six `Reactions.EMOJI_SET` emoji control for members, highlight the signed-in user's current reaction (tapping it removes, tapping another changes), add a reaction-summary row showing each reactor's display name + emoji from `task.reactionsJson`, and show an empty-state when there are no reactions; bind taps to `WorkspaceViewModel.updateMyReaction`
    - _Requirements: 1.1, 3.1, 3.2, 3.3_

  - [x] 11.2 Add the nudge control for pending targets
    - In `CollaboratorMemberList`/`TaskDetailBottomSheet`, show a one-tap nudge control only for members whose target `Member_Status` is `pending`, bound to `WorkspaceViewModel.nudge`, reflecting the optimistic nudged/cooldown state and the rate-limit/precondition messages
    - _Requirements: 10.1, 10.4, 11.2, 12.2_

  - [x] 11.3 Add the `LeaderboardSection` to `WorkspaceScreen`
    - Add a `LeaderboardSection` composable on `WorkspaceScreen.kt` sourced from `WorkspaceViewModel.leaderboard`, listing self + friends by current-window points descending and showing the no-friends empty-state; the legacy `friend.productivityPoints` display is superseded by the current-window figure
    - _Requirements: 9.1, 9.2, 9.6_

  - [ ]* 11.4 Write Compose UI tests for reactions
    - The six-emoji control renders for members (1.1), the current user's reaction is highlighted, the reactor name + emoji summary renders (3.1) and the empty-state shows when there are none (3.3); optimistic apply reflects within 200 ms and reverts on a forced fake-repo failure/timeout (5.1, 5.2, 5.3)
    - _Requirements: 1.1, 3.1, 3.3, 5.1, 5.2, 5.3_

  - [ ]* 11.5 Write Compose UI tests for nudge
    - The one-tap control appears only for pending targets (10.1), shows an optimistic nudged state within 200 ms (10.4), and reflects cooldown/rejection messages on rate-limit or precondition failure (10.5, 11.2, 12.2) against the in-memory fake repository
    - _Requirements: 10.1, 10.4, 10.5, 11.2, 12.2_

  - [ ]* 11.6 Write Compose UI tests for the leaderboard
    - `LeaderboardSection` lists self + friends by current-window points descending (9.1, 9.2) and shows the no-friends empty-state (9.6)
    - _Requirements: 9.1, 9.2, 9.6_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core (non-test) implementation tasks are never optional.
- Each task references specific granular requirements clauses for traceability.
- Property tests (Properties 1–7) target only the pure `com.theblankstate.preamble.collab` logic and are placed immediately beside the function they validate, so correctness issues surface before the Firestore/Cloud Functions/UI wiring depends on them.
- Firestore Security_Rules (Requirements 1.4, 4.1–4.4, 8.1–8.4) and FCM/Cloud Function delivery (Requirements 6.1–6.5, 10.2, 10.3) are verified by the emulator suite and Cloud Functions integration tests, not by property-based tests, matching the design's testing strategy.
- Checkpoints provide incremental validation at the boundaries between pure logic, client wiring, security rules, and Cloud Functions.
- **Test execution environment:** JVM unit-test execution (the jqwik property tests and the Compose tests) is currently blocked because the build environment lacks a complete JDK 21 with `jlink` (`gradle.properties` pins JDK 17). Until a full JDK 21 toolchain is available, the pure-logic and Compose test sources are verified by **compilation** (they must compile cleanly) rather than by running the suite. The Firestore emulator rules tests and the Cloud Functions integration tests run under Node, are not subject to this constraint, and are executable now — providing executable verification of the rules and push paths in the interim.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.3", "1.5", "1.7", "1.10", "1.12", "3.1", "7.1", "9.1", "9.2"] },
    { "id": 1, "tasks": ["1.2", "1.4", "1.6", "1.8", "1.9", "1.11", "1.13", "4.1", "7.2", "9.3"] },
    { "id": 2, "tasks": ["4.2", "7.3", "9.4"] },
    { "id": 3, "tasks": ["4.3", "7.4"] },
    { "id": 4, "tasks": ["4.4", "4.5"] },
    { "id": 5, "tasks": ["5.1"] },
    { "id": 6, "tasks": ["5.2"] },
    { "id": 7, "tasks": ["5.3"] },
    { "id": 8, "tasks": ["11.1", "11.3"] },
    { "id": 9, "tasks": ["11.2"] },
    { "id": 10, "tasks": ["11.4", "11.5", "11.6"] }
  ]
}
```


## Tasks — Iteration 2: WS6 reaction visibility latency (bug fix)

- [x] 13. Carry `reactionsJson` (and `collabSendStatus`) through the remote→local collaborative merge
  - In `ui/viewmodels/WorkspaceViewModel.kt`, add `reactionsJson = remote.reactionsJson` to the
    `local.copy(...)` in `mergeRemoteCollaboration(local, remote)` so a reaction-only change to the
    canonical `/collaborativeTasks/{taskId}` document is written into the local Room mirror by
    `synchronizeCollaborativeTasksToRoom` → `taskDao.insertTask(merged)`, making other members'
    reactions appear promptly (within Req 3.2's 5 s) instead of only after an unrelated mutation.
    Also add `collabSendStatus = remote.collabSendStatus` to the same copy so the durable-send
    status is not reverted by an unrelated remote change. Do not change reaction semantics, the
    optimistic self-reaction path, or any other field-merge behavior.
  - Verify with `./gradlew :app:compileDebugKotlin` (JVM tests blocked by the JDK 21/jlink
    constraint — compile-only verification).
  - _Requirements: 13.1, 13.2, 13.3, 13.4_
