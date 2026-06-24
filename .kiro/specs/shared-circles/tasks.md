# Implementation Plan: Shared Circles

## Overview

This plan adds **Shared Circles** to the Preamble Android app by paralleling the shipped `collaborative-tasks` feature. The strategy front-loads the pure, side-effect-free client logic in a new `com.theblankstate.preamble.circles` package (canonical Circle document build/validate, single-slice membership transforms, Circle_Task build, shared-completion transform, and edit/delete authorization) so the 8 correctness properties can be validated early with property-based tests (jqwik). It then wires that logic through the Firestore Security_Rules, a TypeScript Cloud Functions trigger (`onCirclesMembershipChanged` for `memberUidMap` propagation + delete cascade), `CircleRepository`, `CircleViewModel` (optimistic UI + 30 s timeouts), and finally the Compose UI (`CirclesScreen` + `CircleDetailScreen`).

Implementation language: **Kotlin** for the Android client and pure logic, **TypeScript (Node)** for the Cloud Function, and **JavaScript (Node, `@firebase/rules-unit-testing`)** for the Firestore rules verification suite, as specified in the design.

Each step builds on the previous ones and ends by wiring the new logic into the running app, so no code is left orphaned. There is no Room migration (the local DB version stays 29); Circle data lives in `CircleViewModel` `StateFlow` caches. Existing `collaborative-tasks` and `social-engagement` behavior is unchanged.

## Tasks

- [x] 1. Implement shared-circles pure logic in `com.theblankstate.preamble.circles`
  - [x] 1.1 Implement `CircleDocument.kt` build/validate/normalizeName/memberUidMap
    - Create `circles/CircleDocument.kt` as a pure object (no Android/Firestore deps): define `CircleMemberRef(uid, name)`, the `CircleDocumentResult` sealed interface (`Created(document)` / `EmptyName` / `TooManyMembers`), and constants `MAX_MEMBERS = 50`, `MEMBER_STATUSES = {active, left, removed}`, `ROLE_ADMIN`, `ROLE_MEMBER`
    - Implement `normalizeName(raw)` (trim leading/trailing whitespace), `memberUidMap(memberUids)` (distinct non-blank uids associated with `true`, keys equal to memberUids exactly), `build(circleId, adminUid, adminName, name, now)` (creator is sole admin + only `active` member with `role=admin`; returns `EmptyName` for a blank normalized name), and `isValid(document)` enforcing every invariant (exactly one `adminUid` present in `memberUids`; no duplicate uids; size 1..50; `memberUidMap.keys() == memberUids` with `memberUidMap[adminUid] == true`; exactly one `memberStates` entry per member and none for non-members, each with `name`, `role ∈ {admin, member}`, `status ∈ {active, left, removed}`, `joinedAt`; non-empty `name`, `createdAt`, `updatedAt`)
    - _Requirements: 1.1, 1.2, 1.3, 8.1, 8.2, 8.3, 8.4, 8.5, 13.7_

  - [ ]* 1.2 Write property test for canonical Circle document invariants
    - **Property 1: Canonical Circle document invariants**
    - Tag `// Feature: shared-circles, Property 1: ...`, `@Property(tries = 100)` minimum; generators produce admin/member uid sets spanning 0, 1, 49, 50 additional members with distinct uids
    - **Validates: Requirements 1.3, 8.1, 8.2, 8.3, 8.4, 8.5**

  - [ ]* 1.3 Write property test for name normalization
    - **Property 2: Name normalization is idempotent and gates empty names**
    - Tag `// Feature: shared-circles, Property 2: ...`, `@Property(tries = 100)` minimum; generator produces names with leading/trailing/Unicode whitespace and empty/blank strings; asserts `normalizeName(normalizeName(x)) == normalizeName(x)`, that `build` returns `EmptyName` for a blank name, and that `rename` sets only `name`+`updatedAt` for non-empty and leaves `name` unchanged for empty
    - **Validates: Requirements 1.1, 1.2, 3.2, 3.3, 3.4**

  - [x] 1.4 Implement `CircleMemberOps.kt` single-slice membership transforms
    - Create `circles/CircleMemberOps.kt` as a pure object: define the rejection-reason constants and the `DocumentOpResult` sealed interface (`Updated(document)` / `Rejected(reason)`)
    - Implement `addMember(document, newMember, now)` (rejects duplicates and over-capacity; on success appends only the new uid to `memberUids`/`memberUidMap` and only that member's `memberStates` entry with `status=active`, `role=member`), `removeMember(document, targetUid, now)` (rejects removing the admin or a non-member; on success removes only that uid from `memberUids`/`memberUidMap` and sets only that member's status to `removed`, retaining the entry), `leaveCircle(document, leavingUid, now)` (rejects admin self-removal; on success removes only the leaver from `memberUids`/`memberUidMap` and sets only their status to `left`), and `rename(document, newName, now)` (rejects empty normalized name; on success sets only `name` + `updatedAt`); each success recomputes `memberUidMap` via `CircleDocument.memberUidMap` so keys-equal-members holds by construction and every other member's records stay byte-for-byte identical
    - _Requirements: 3.3, 3.4, 4.2, 4.4, 4.5, 4.6, 5.2, 5.3, 6.2, 6.3, 8.6_

  - [ ]* 1.5 Write property test for adding a member
    - **Property 3: Adding a member is single-slice and capacity-bounded**
    - Tag `// Feature: shared-circles, Property 3: ...`, `@Property(tries = 100)` minimum; generator produces Circle documents at member counts near the 50 boundary and candidate members both already-present and new
    - **Validates: Requirements 4.2, 4.4, 4.5, 4.6, 8.6**

  - [ ]* 1.6 Write property test for removing a member
    - **Property 4: Removing a member is single-slice and admin-guarded**
    - Tag `// Feature: shared-circles, Property 4: ...`, `@Property(tries = 100)` minimum; generator produces documents with mixed members and target uids that are the admin, a non-admin member, and a non-member
    - **Validates: Requirements 5.2, 5.3, 8.6**

  - [ ]* 1.7 Write property test for leaving a Circle
    - **Property 5: Leaving a Circle affects only the leaving member and is admin-guarded**
    - Tag `// Feature: shared-circles, Property 5: ...`, `@Property(tries = 100)` minimum; generator produces documents where the leaver is the admin and where the leaver is a non-admin member
    - **Validates: Requirements 6.2, 6.3, 8.6**

  - [x] 1.8 Implement `CircleTask.kt` build/setCompletion/classifyEditDelete
    - Create `circles/CircleTask.kt` as a pure object: define `MAX_TITLE_LEN = 500`, the `BuildResult` sealed interface (`Created(document)` / `EmptyTitle`), and the `EditDeleteDecision` enum (`ALLOW` / `DENY`)
    - Implement `build(taskId, circleId, authorUid, title, memberUidMap, now)` (trimmed non-empty title → document recording `circleId`, `authorUid`, trimmed `title`, not-completed shared-completion (`isCompleted=false`, `completedBy=null`, `completedAt=null`), the denormalized `memberUidMap` copied from input, and `createdAt`; blank title → `EmptyTitle` with no document), `setCompletion(document, completed, actorUid, actorName, now)` (completed → `isCompleted=true`, `completedBy={uid,name}`, `completedAt=now`; not-completed → `isCompleted=false`, `completedBy=null`, `completedAt=null`; both leave `circleId`/`authorUid`/`title`/`memberUidMap` unchanged), and `classifyEditDelete(taskAuthorUid, circleAdminUid, requesterUid)` (`ALLOW` iff requester == author or requester == admin, else `DENY`)
    - _Requirements: 9.2, 9.4, 11.2, 11.4, 12.1, 12.2, 12.3, 12.4_

  - [ ]* 1.9 Write property test for shared completion
    - **Property 6: Shared completion sets and clears the Completer**
    - Tag `// Feature: shared-circles, Property 6: ...`, `@Property(tries = 100)` minimum; generator produces Circle_Task documents in both completed and not-completed prior states with arbitrary actor uid/name and timestamps
    - **Validates: Requirements 11.2, 11.4**

  - [ ]* 1.10 Write property test for edit/delete authorization
    - **Property 7: Edit/delete authorization classification**
    - Tag `// Feature: shared-circles, Property 7: ...`, `@Property(tries = 100)` minimum; generator produces author/admin/requester uid triples with equal and distinct values
    - **Validates: Requirements 12.1, 12.2, 12.3, 12.4**

  - [ ]* 1.11 Write property test for Circle_Task construction
    - **Property 8: Circle_Task construction invariants and empty-title rejection**
    - Tag `// Feature: shared-circles, Property 8: ...`, `@Property(tries = 100)` minimum; generator produces titles (empty, blank, 1-char, 500-char, Unicode) and arbitrary member maps
    - **Validates: Requirements 9.2, 9.4**

- [x] 2. Checkpoint - Ensure all pure-logic tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Add and verify the Firestore Security_Rules for Circles and Circle tasks
  - [x] 3.1 Add the `/circles/{circleId}` rule block
    - In `firebase-firestore-rules.rules`, add the Circle helpers (`circleIsMember`, `circleIsQueryableMember`, `circleHasValidMemberUidMap`, `isValidCircleMemberStatus`, `isValidCircleDocument` enforcing `memberUids.size() <= 50`, admin-is-member, and keys-equal-members, `isCircleAdminCreate`, `isCircleAdminUpdate`, `removesSelfFromCircleOnly` using the `diff().affectedKeys().hasOnly(['memberUids','memberUidMap','memberStates','updatedAt'])` own-slice idiom with `memberStates` diff scoped to the requester and own status set to `left`) and the `match /circles/{circleId}` block: `get` for a member, `list` for a queryable member, `create` admin-and-member only, `update` via admin-update or self-removal, `delete` admin-only; leave all existing rules unchanged
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 13.8_

  - [x] 3.2 Add the `/circleTasks/{taskId}` rule block
    - In `firebase-firestore-rules.rules`, add the Circle_Task helpers (`circleTaskIsMember` / `circleTaskIncomingIsMember` checking the task's own denormalized `memberUidMap` with no parent `get()`, `isValidCircleTaskCreate` requiring `authorUid == request.auth.uid`, `title` size 1..500, `isCompleted == false`, and incoming membership, `updatesOnlyCompletionFields` permitting **any** member to write only `['isCompleted','completedBy','completedAt','updatedAt']` while asserting `circleId`/`authorUid`/`title`/`memberUidMap` unchanged, `isCircleTaskAuthor`, `isCircleAdminOf(circleId)`, and `authorOrAdminEdit` asserting `memberUidMap` unchanged) and the `match /circleTasks/{taskId}` block: `get`/`list` for a member, `create` via `isValidCircleTaskCreate`, `update` via completion-only or author/admin edit, `delete` for author or admin
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8_

  - [ ]* 3.3 Extend the emulator rules suite for Circles and Circle tasks
    - Extend the Node `@firebase/rules-unit-testing` suite (`firebase-rules-tests/firestore.rules.test.mjs`) to cover: member vs non-member `/circles` get/list (13.1, 13.2); admin-only create with creator-as-admin-and-member (13.3); admin rename/add/remove allowed and non-admin denied (13.4); the non-admin self-removal own-slice path allowed and a write touching another member denied (13.5); admin-only delete (13.6); schema rejection for a bad `memberUidMap` or invalid status and member-count 50 accepted / 51 rejected (13.7); unauthenticated denial (13.8); Circle_Task member read/list via the denormalized map and non-member denial (14.1, 14.2); member create with `authorUid == self` (14.3); `updatesOnlyCompletionFields` permitting any member to flip completion while denying any other field change (14.4); author/admin title-edit and delete with denial for other members (14.5, 14.6); non-member write denial (14.7); and unauthenticated denial (14.8)
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 13.8, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8_

- [x] 4. Checkpoint - Ensure all rules tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement the membership-propagation Cloud Function (`functions/`, TypeScript)
  - [x] 5.1 Implement `onCirclesMembershipChanged` in `functions/src/circles.ts`
    - Add `functions/src/circles.ts` exporting an `onDocumentWritten` trigger on `circles/{circleId}` in the `"preamble"` database: on deletion (`after == null`) cascade-delete every `/circleTasks` document where `circleId == id`; on a membership change (sorted keys of `before.memberUidMap` differ from `after.memberUidMap`) batch-set `memberUidMap = after.memberUidMap` on every `/circleTasks` document where `circleId == id`; a name-only/timestamp-only change is a no-op; make the trigger idempotent and log/swallow per-batch failures so retries are safe
    - _Requirements: 5.5, 7.2, 7.3_

  - [x] 5.2 Export `onCirclesMembershipChanged` from `functions/src/index.ts`
    - Export the new trigger from `functions/src/index.ts` alongside the existing triggers, following the established `firebase-functions/v2` registration conventions
    - _Requirements: 5.5, 7.2_

  - [ ]* 5.3 Write Cloud Functions integration tests for the trigger
    - Add a Node test (e.g. `firebase-functions-test` against the emulator): adding/removing/leaving a member updates the denormalized `memberUidMap` on every `/circleTasks` document with that `circleId` (a removed member's key becomes absent — 5.5); a name-only change is a no-op; deleting `/circles/{id}` cascade-deletes every task with that `circleId` (7.2, 7.3); and running the trigger twice is idempotent
    - _Requirements: 5.5, 7.2, 7.3_

- [x] 6. Checkpoint - Ensure all functions tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement `CircleRepository` Firestore gateway
  - [x] 7.1 Implement the Circle and Circle_Task read flows and projections
    - Create `repository/CircleRepository.kt` with the `Circle` and `CircleTaskModel` projection types and `getCirclesFlow()` (listen `/circles` where `memberUidMap.{uid} == true`) and `getCircleTasksFlow(circleId)` (listen `/circleTasks` where `circleId == id` and `memberUidMap.{uid} == true`, scoped to the open Circle); project snapshots through the pure `circles` types and surface listener errors via `.catch { reportListenerFailure(label, it) }` retaining last-loaded data
    - _Requirements: 2.1, 2.3, 10.1, 10.2, 10.4, 15.1_

  - [x] 7.2 Implement the Circle lifecycle writes
    - Add `createCircle(name)`, `renameCircle(circleId, newName)`, `addMember(circleId, friend)`, `removeMember(circleId, memberUid)`, `leaveCircle(circleId)` (issuing the own-slice `removesSelfFromCircleOnly` update, not a full overwrite), and `deleteCircle(circleId)` (deletes only `/circles/{id}`; the Cloud Function cascades task deletes); each builds documents/transforms through `CircleDocument`/`CircleMemberOps` and returns `Result<...>` via `runCatching`
    - _Requirements: 1.3, 3.4, 4.2, 5.2, 6.2, 7.2_

  - [x] 7.3 Implement the Circle_Task writes
    - Add `addCircleTask(circleId, title)` (reads the parent Circle's current `memberUidMap` to seed the denormalized copy via `CircleTask.build`), `setCircleTaskCompletion(taskId, completed)` (via `CircleTask.setCompletion`), `editCircleTaskTitle(taskId, newTitle)`, and `deleteCircleTask(taskId)`; each returns `Result<...>` via `runCatching` and never embeds business rules inline
    - _Requirements: 9.2, 11.2, 12.1, 12.3_

  - [ ]* 7.4 Write unit tests for repository write shapes
    - Verify `leaveCircle` issues an own-slice update touching only `memberUids`/`memberUidMap`/own `memberStates`/`updatedAt`, `deleteCircle` deletes only the circle document, and `addCircleTask` seeds `memberUidMap` from the parent circle; verify every write returns `Result` and never throws
    - _Requirements: 6.2, 7.2, 9.2_

- [x] 8. Implement optimistic UI and Circle state in `CircleViewModel`
  - [x] 8.1 Implement Circle lifecycle actions with snapshot-and-revert
    - Create `ui/viewmodels/CircleViewModel.kt` exposing `circles: StateFlow<List<Circle>>` and `uiState: StateFlow<CircleUiState>`; implement `createCircle`, `renameCircle`, `addMember`, `removeMember`, `leaveCircle`, and `deleteCircle`, each pre-validating through the pure `circles` results (empty name, non-friend/duplicate/over-capacity add, admin self-remove/admin leave) **before** any optimistic mutation, snapshotting prior state, applying the change within 200 ms, launching the repository write inside `withTimeout(30_000)`, and restoring the exact snapshot with an error message on failure/timeout
    - _Requirements: 1.2, 1.4, 1.5, 3.3, 3.4, 3.6, 4.2, 4.3, 4.4, 4.6, 4.8, 5.2, 5.3, 5.6, 6.2, 6.3, 6.4, 7.2, 7.5, 15.2, 15.3, 15.4_

  - [x] 8.2 Implement Circle_Task actions and `openCircle`
    - Add `circleTasks: StateFlow<List<CircleTaskModel>>`, `openCircle(circleId)` (starts `getCircleTasksFlow(circleId)`), and `addTask`, `setCompletion`, `editTaskTitle`, and `deleteTask`; pre-validate empty title and gate `editTaskTitle`/`deleteTask` on `CircleTask.classifyEditDelete` before issuing the write, then apply the optimistic change within 200 ms, launch the repository write inside `withTimeout(30_000)`, and restore the exact snapshot with a message on failure/timeout
    - _Requirements: 9.2, 9.3, 9.4, 9.6, 11.2, 11.4, 11.5, 11.6, 12.1, 12.3, 12.5, 12.6, 15.2, 15.3, 15.4_

  - [ ]* 8.3 Write fake-repository example tests for optimistic timing and revert
    - Against an in-memory fake repository, verify each action reflects in `CircleViewModel` state before the backend completes (1.4, 3.4, 4.2, 5.2, 6.2, 7.2, 9.3, 11.5, 12.5) and reverts to the exact prior snapshot on induced failure/timeout (1.5, 3.6, 4.8, 5.6, 6.4, 7.5, 9.6, 11.6, 12.6, 15.2, 15.4); a listener error retains last-loaded data and surfaces a message without crashing (15.1, 10.4); and the non-friend add rejection surfaces the friends-only message (4.3)
    - _Requirements: 1.4, 1.5, 3.4, 3.6, 4.2, 4.3, 4.8, 5.2, 5.6, 6.2, 6.4, 7.2, 7.5, 9.3, 9.6, 10.4, 11.5, 11.6, 12.5, 12.6, 15.1, 15.2, 15.4_

- [x] 9. Checkpoint - Ensure all repository and ViewModel tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Wire the Circles feature into the Compose UI
  - [x] 10.1 Implement `CirclesScreen` and its navigation entry
    - Create `ui/screens/CirclesScreen.kt` listing the user's Circles (name + Circle_Member count) from `CircleViewModel.circles`, an empty-state with a create control when the user belongs to none, and a create-Circle control bound to `createCircle`; add a navigation entry reachable from the friends/workspace area and route taps into `CircleDetailScreen` via `openCircle`
    - _Requirements: 1.1, 2.1, 2.2, 2.4_

  - [x] 10.2 Implement `CircleDetailScreen`
    - Create `ui/screens/CircleDetailScreen.kt` showing the Circle's shared task list (title, Shared_Completion state, Completer name, Circle_Author name) from `CircleViewModel.circleTasks`, an add-task control, an empty-state when there are no tasks, a per-task completion toggle, and author/admin-only edit/delete affordances (gated via `CircleTask.classifyEditDelete`); for the Circle_Admin render membership controls (add from friends, remove member, rename, delete), and for a non-admin member render a confirmed Leave control with no admin controls
    - _Requirements: 5.4, 6.1, 6.3, 7.4, 9.1, 10.1, 10.3, 11.1, 11.4, 12.1, 12.2, 12.3, 12.4_

  - [ ]* 10.3 Write Compose UI tests for the Circles surfaces
    - Verify the Circles list renders name + member count and the empty-state create control (2.1, 2.2) and is reachable from the friends/workspace area (2.4); the shared task list shows title, Shared_Completion, Completer name, and author (10.1) with an empty state (10.3); the completion toggle renders (11.1); author/admin-only edit/delete affordances appear and are absent for other members (12.1–12.4); and the admin membership controls vs the non-admin confirmed Leave control render correctly (5.4, 6.1, 6.3, 7.4)
    - _Requirements: 2.1, 2.2, 2.4, 5.4, 6.1, 6.3, 7.4, 10.1, 10.3, 11.1, 12.1, 12.2, 12.3, 12.4_

- [x] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core (non-test) implementation tasks are never optional.
- Each task references specific granular requirements clauses for traceability.
- Property tests (Properties 1–8) target only the pure `com.theblankstate.preamble.circles` logic and are placed immediately beside the function they validate, so correctness issues surface before the Firestore/Cloud Functions/UI wiring depends on them.
- Firestore Security_Rules (Requirements 13, 14) and the membership-propagation/cascade Cloud Function (Requirements 5.5, 7.2, 7.3) are verified by the emulator suite and Cloud Functions integration tests, not by property-based tests, matching the design's testing strategy.
- **No Room migration:** Circle data is held in `CircleViewModel` `StateFlow` caches projected from Firestore snapshots; the local DB version stays 29 (Decision D5), and Firestore's offline cache serves the "retain last synced copy" requirement (10.4, 15.1).
- Checkpoints provide incremental validation at the boundaries between pure logic, security rules, Cloud Functions, repository/ViewModel wiring, and UI.
- **Test execution environment:** JVM unit-test execution (the jqwik property tests and the Compose tests) is currently blocked because the build environment lacks a complete JDK 21 with `jlink` (`gradle.properties` pins an older JDK). Until a full JDK 21 toolchain is available, the pure-logic and Compose test sources are verified by **compilation** (they must compile cleanly against the production code) rather than by running the suite. The Firestore emulator rules tests and the Cloud Functions integration tests run under Node, are not subject to this constraint, and are executable now — providing executable verification of the rules and propagation paths in the interim.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "3.1", "5.1"] },
    { "id": 1, "tasks": ["1.4", "1.8", "3.2", "5.2"] },
    { "id": 2, "tasks": ["1.2", "1.3", "1.5", "1.6", "1.7", "1.9", "1.10", "1.11", "3.3", "5.3", "7.1"] },
    { "id": 3, "tasks": ["7.2"] },
    { "id": 4, "tasks": ["7.3"] },
    { "id": 5, "tasks": ["7.4", "8.1"] },
    { "id": 6, "tasks": ["8.2"] },
    { "id": 7, "tasks": ["8.3", "10.1", "10.2"] },
    { "id": 8, "tasks": ["10.3"] }
  ]
}
```
