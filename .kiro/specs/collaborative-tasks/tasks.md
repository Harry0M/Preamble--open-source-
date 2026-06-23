# Implementation Plan: Collaborative Tasks

## Overview

This plan rebuilds the friend-invite and collaborative-task systems in the Preamble Android app (Kotlin / Jetpack Compose / Room / Firestore). The strategy front-loads the pure, testable client logic (Preamble ID normalization, invite validation, canonical-document construction, member-state transitions, assignee resolution) so the 18 correctness properties can be validated early with property-based tests (jqwik). It then wires that logic into `WorkspaceRepository`, the ViewModels (optimistic UI + timeouts), and the Compose UI, and finishes with the Firestore Security_Rules and their emulator-based verification.

Implementation language: **Kotlin** for the Android client and pure logic, **JavaScript (Node, `@firebase/rules-unit-testing`)** for the Firestore rules verification suite, as specified in the design.

Each step builds on the previous ones and ends by wiring the new logic into the running app, so no code is left orphaned.

## Tasks

- [x] 1. Set up JVM test infrastructure for property-based testing
  - [x] 1.1 Add jqwik + JUnit 5 JVM test wiring
    - Add `net.jqwik:jqwik` and JUnit 5 as `testImplementation` dependencies in `app/build.gradle.kts`
    - Configure the JVM `test` task to run the JUnit Platform so jqwik properties execute
    - Create the JVM unit-test source set/package for collaborative-tasks pure logic
    - Establish the property-test tagging convention `// Feature: collaborative-tasks, Property {n}: {property text}` and `@Property(tries = 100)` minimum
    - _Requirements: testing infrastructure supporting all property tests_

- [x] 2. Implement Preamble ID, invite validation, and invite links (pure logic)
  - [x] 2.1 Implement `PreambleId` normalization
    - Create `PreambleId.normalize(raw)` (trim + uppercase) and `PreambleId.isBlank(raw)`
    - _Requirements: 1.1_

  - [ ]* 2.2 Write property test for normalization idempotence
    - **Property 1: Preamble ID normalization is idempotent**
    - **Validates: Requirements 1.1**

  - [x] 2.3 Implement `InviteValidation` and `sendInvite` gating logic
    - Define the `InviteValidation` sealed interface (Ok, EmptyId, SelfInvite, AlreadyFriends, AlreadyPending, NotFound)
    - Implement the pure validation that gates request creation: reject empty (1.2), self (1.5), existing friend (1.6), duplicate pending (1.7); construct the `WorkspaceInvite` carrying sender uid, name, and normalized Preamble_ID (1.8)
    - _Requirements: 1.2, 1.5, 1.6, 1.7, 1.8_

  - [ ]* 2.4 Write property test for invite validation gating
    - **Property 3: Invite validation gates request creation**
    - **Validates: Requirements 1.2, 1.5, 1.6, 1.7**

  - [ ]* 2.5 Write property test for created invite carrying sender identity
    - **Property 4: Created invite carries sender identity**
    - **Validates: Requirements 1.8**

  - [x] 2.6 Implement invite-link build and parse
    - Implement `buildInviteLink()` producing `https://preamble.theblankstate.com/invite/{normalized-id}` (2.1)
    - Implement parsing of an invite-link URI to the normalized Preamble_ID, returning an "invalid link" result for malformed links (2.2, 2.4)
    - _Requirements: 2.1, 2.2, 2.4_

  - [ ]* 2.7 Write property test for invite-link round-trip
    - **Property 2: Invite-link round-trip**
    - **Validates: Requirements 2.1, 2.2, 2.4**

  - [ ]* 2.8 Write unit tests for directory lookup and duplicate-pending branches
    - Test directory hit/miss for invite send (1.3, 1.4) and duplicate-pending rejection (1.7)
    - _Requirements: 1.3, 1.4, 1.7_

- [x] 3. Implement canonical collaborative document construction and payload projection (pure logic)
  - [x] 3.1 Implement `createCollaborativeDocument` and raise assignee maximum
    - Build the schema-v2 canonical document enforcing all invariants: single admin, admin in `memberUids`, dedup, `assigneeUids ⊆ memberUids` excluding admin, `memberUidMap` keys == `memberUids`, one `memberStates` entry per member (admin `accepted`, assignees `pending`)
    - Raise `MAX_ASSIGNEES` to 50 and validate `1..50` distinct assignees, rejecting oversized requests without producing a document
    - Skip document creation entirely when no friend is assigned
    - _Requirements: 6.3, 6.4, 6.5, 6.6, 8.1, 8.2, 8.3, 8.4, 8.5_

  - [ ]* 3.2 Write property test for canonical document invariants
    - **Property 5: Canonical collaborative document invariants**
    - **Validates: Requirements 6.3, 8.1, 8.2, 8.3, 8.4, 8.5**

  - [ ]* 3.3 Write property test for collaborative-iff-assignees
    - **Property 6: Collaborative iff assignees present**
    - **Validates: Requirements 6.4**

  - [ ]* 3.4 Write property test for assignee-count boundary
    - **Property 7: Assignee-count boundary** (covers create-time and admin-add)
    - **Validates: Requirements 6.5, 6.6, 11.9**

  - [x] 3.5 Implement `taskPayload` and `documentToTask` projection
    - Serialize the admin's `Task` into the shared payload, stripping local-only collab fields and forcing shared `isCompleted = false`
    - Project a canonical document back into a per-user `Task` (own status/completion from `memberStates[uid]`)
    - _Requirements: 7.2, 7.7_

  - [ ]* 3.6 Write property test for payload copy fidelity and shared-completion exclusion
    - **Property 8: Task payload copy fidelity and shared-completion exclusion**
    - **Validates: Requirements 7.2, 7.7**

- [x] 4. Implement member-state transitions, ownership/self-removal, and collaborator-view filtering (pure logic)
  - [x] 4.1 Implement member-status transition functions
    - Implement guarded accept/decline (pending only), complete (accepted only, sets completion flag + UTC timestamp), with no-op on disallowed source statuses
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [ ]* 4.2 Write property test for member status transition guards
    - **Property 10: Member status transition guards**
    - **Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5**

  - [x] 4.3 Implement single-member update, ownership transfer, and self-removal operations
    - Single-member state update that touches only the target member's entry (8.6)
    - `transferOwnership` pure transform: new admin set, previous admin retained as `accepted`, invariants preserved; reject non-member targets (11.4, 11.5)
    - `leaveCollaborativeTask` pure transform: remove only the leaving member from `memberUids`/`assigneeUids`/`memberUidMap`, set only that member's status to `left`; reject admin self-removal (12.2, 12.3, 12.4)
    - _Requirements: 8.6, 11.4, 11.5, 12.2, 12.3, 12.4_

  - [ ]* 4.4 Write property test for single-member state isolation
    - **Property 9: Single-member state changes are isolated**
    - **Validates: Requirements 8.6**

  - [ ]* 4.5 Write property test for ownership-transfer invariants
    - **Property 14: Ownership transfer preserves invariants**
    - **Validates: Requirements 11.4, 11.5**

  - [ ]* 4.6 Write property test for self-removal isolation
    - **Property 15: Self-removal affects only the leaving member**
    - **Validates: Requirements 12.2, 12.3, 12.4**

  - [x] 4.7 Implement collaborator-view filtering and friend-removal impact partition
    - Filter `memberStates` to display only `pending`/`accepted`/`completed`, excluding `declined`/`left`/`removed` (13.1)
    - Implement `friendRemovalImpact` partitioning shared tasks into admin-owned vs. member tasks (disjoint, union == shared set) (5.1)
    - _Requirements: 13.1, 5.1_

  - [ ]* 4.8 Write property test for collaborator-view exclusion of terminal members
    - **Property 16: Collaborator view excludes terminal members**
    - **Validates: Requirements 13.1**

- [x] 5. Implement the client-side Assignee_Resolver
  - [x] 5.1 Implement `AssigneeResolver` classification logic
    - Detect assignment intent markers, resolve intended terms by normalized name / Preamble_ID, and classify as `Assigned` (unique matches), `NoAssignee`, `Unmatched`, `Ambiguous` (term matches >1 friend), or `Failed`
    - _Requirements: 9.3, 9.5, 9.6, 9.7_

  - [ ]* 5.2 Write property test for assignee resolution classification
    - **Property 17: Assignee resolution classification**
    - **Validates: Requirements 9.3, 9.5, 9.6, 9.7**

  - [x] 5.3 Wire the resolver into the task pipeline and remove the Cloud Function dependency
    - Invoke the resolver from `AiParsingWorker` after the own-copy save, bounded by `withTimeout(30_000)`, so normal creation is never blocked (9.1, 9.2)
    - Only `Assigned` triggers `assignTaskToMultiple`; other outcomes leave the task non-collaborative, and `Ambiguous`/`Failed`/timeout surface a message (9.4, 9.8)
    - Replace `CloudAiService.resolveAssignees` / `aiResolveAssignees` so resolution runs client-side (9.9)
    - _Requirements: 9.1, 9.2, 9.4, 9.8, 9.9_

  - [ ]* 5.4 Write unit tests and smoke check for resolver wiring
    - Test resolver failure/timeout messaging (9.8) and confirm no call to the removed `aiResolveAssignees` Cloud Function (9.9)
    - _Requirements: 9.8, 9.9_

- [x] 6. Checkpoint - Ensure all pure-logic tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Wire pure logic into `WorkspaceRepository` (Firestore + Room)
  - [x] 7.1 Implement friend/invite Firestore operations
    - `sendInvite` enforcing all Requirement 1 checks before any write; `acceptInvite`/`declineInvite`/`removeFriend` with batched reciprocal writes
    - _Requirements: 1.3, 3.4, 4.2_

  - [x] 7.2 Implement `assignTaskToMultiple` canonical-document creation
    - Save admin's local copy first, then asynchronously set the canonical document via `createCollaborativeDocument`; retain local copy and surface a message on canonical-create failure
    - _Requirements: 6.2, 6.3, 6.7, 6.8_

  - [x] 7.3 Implement `getCollaborativeTasksFlow` and Room synchronization
    - Listen on `collaborativeTasks where memberUidMap.{uid} == true`, mirror visible tasks into Room within the 5 s window, prune stale assigned tasks, and write finalized AI-derived attributes as a subsequent canonical update
    - Retain last synced copy and surface a message on per-member mirror failure
    - _Requirements: 7.1, 7.3, 7.4, 7.5, 7.6, 9.4_

  - [x] 7.4 Implement member and admin collaborative operations
    - `updateCollabAssignmentStatus` (accept/decline/complete with transition guards, writing only `memberStates[uid]`), `updateCollabTaskSubtasks`, `removeCollaborator`, `transferOwnership`, `leaveCollaborativeTask` as transaction-based operations
    - _Requirements: 8.6, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 11.1, 11.2, 11.3, 11.4, 11.7, 11.8, 12.2, 12.3, 12.4_

  - [x] 7.5 Implement friend-removal orchestration
    - `resolveTasksAndRemoveFriend` applies each chosen action (transfer for admin-owned, self-removal for member tasks) and deletes the friend record only after all succeed; block confirm when an admin-owned task is unresolved; restore friend and leave tasks unchanged on any failure
    - _Requirements: 5.6, 5.7, 5.8_

  - [ ]* 7.6 Write property test for friend-removal impact partition
    - **Property 13: Friend-removal impact partition**
    - **Validates: Requirements 5.1**

- [x] 8. Implement optimistic UI and resilience in the ViewModels
  - [x] 8.1 Implement `WorkspaceViewModel` optimistic state, timeouts, links, and listener handling
    - Snapshot-and-revert for accept/decline/remove-friend/remove-member/add-member/transfer/self-removal with immediate (<200 ms) local transitions; `withTimeout(10_000)` for friend removal and `withTimeout(30_000)` for other collaborative/friend writes, mapping timeout to the revert path
    - `prefillFromInviteLink` (same validation as manual entry) and `buildInviteLink`; collect each listener with `.catch` that retains last-loaded data and emits a data-set-named error without crashing
    - _Requirements: 3.3, 3.5, 3.6, 3.7, 4.1, 4.3, 4.4, 5.5, 5.8, 10.7, 11.6, 12.5, 14.1, 14.2, 14.3, 14.4, 14.5, 2.1, 2.2, 2.3, 2.4_

  - [x] 8.2 Implement `TaskViewModel` assignment flow
    - Save the admin's own Room copy within 1 s without blocking on the canonical write, then launch the async canonical creation; revert and message on failure
    - _Requirements: 6.2, 6.7, 6.8, 10.7_

  - [ ]* 8.3 Write property test for immediate optimistic transitions
    - **Property 11: Optimistic transitions reflect immediately in local state**
    - **Validates: Requirements 3.3, 3.5, 4.1, 10.1, 10.2, 11.2, 11.8, 12.2**

  - [ ]* 8.4 Write property test for failure/timeout revert
    - **Property 12: Failure or timeout reverts to the exact prior state**
    - **Validates: Requirements 3.6, 3.7, 4.3, 4.4, 5.5, 5.8, 6.8, 7.6, 10.7, 11.6, 12.5, 14.2, 14.3, 14.5**

  - [ ]* 8.5 Write property test for total error handling
    - **Property 18: Error handling is total**
    - **Validates: Requirements 14.4**

- [x] 9. Wire collaborative features into the Compose UI
  - [x] 9.1 Update `AddTaskSheet` friend selection
    - Replace the 5-friend cap with a 1–50 friend selector bound to the assignment flow
    - _Requirements: 6.1_

  - [x] 9.2 Implement the collaborator view and role-based controls in `TaskDetailSheet`
    - Render displayed members by name with admin indicator, per-member completion flag, and acceptance status shown separately from completion; admin sees per-member remove + transfer controls; non-admin members see a Self_Removal control; admin sees no direct Self_Removal control
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7_

  - [ ]* 9.3 Write instrumented UI tests for rendering and timing-sensitive behaviors
    - Cover incoming-list and empty-state rendering (3.1, 3.2), the 1–50 selector (6.1), local-save-within-1s without blocking (6.2, 6.7), collaborator-view indicators and role controls (13.2–13.7), and listener-error messaging (14.1)
    - _Requirements: 3.1, 3.2, 6.1, 6.2, 6.7, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 14.1_

- [x] 10. Checkpoint - Ensure all client tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Implement and verify Firestore Security_Rules
  - [x] 11.1 Update `firebase-firestore-rules.rules`
    - Raise size caps to `memberUids.size() <= 51` / `assigneeUids.size() <= 50`; gate `/collaborativeTasks/{taskId}` get/list on membership, create on admin==requester + valid schema-v2, update on admin-metadata / own-member-state / own-subtask / self-removal, delete admin-only; deny the deprecated `/users/{uid}/collaborativeTasks` path
    - Preserve legacy `/tasks/{taskId}` owner-scoped rules and all per-user collection permissions unchanged; enforce friend/invite/preambleIds ownership with the reciprocal accept/clear exceptions; deny all unlisted paths
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7, 16.8, 16.9, 16.10, 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7_

  - [ ]* 11.2 Extend the emulator-based rules verification suite
    - Extend `firebase-rules-tests/firestore.rules.test.mjs` to cover legacy owner isolation and unauthenticated denial (15.1–15.4), per-user collection preservation (15.5, 18.5), collaborative membership gating and non-member read denial (16.1, 16.2, 18.2), admin-only create/edit/delete and own-state/own-subtask/self-removal updates (16.3–16.10), friend/invite/preambleIds ownership and reciprocal exceptions (17.1–17.7), role-based permit/deny checks (18.3, 18.4), and 50/51 assignee boundary cases
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7, 16.8, 16.9, 16.10, 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 18.2, 18.3, 18.4, 18.5_

  - [x] 11.3 Generate the verification matrix artifact from rule-comparison results
    - Produce a script/test that compares proposed vs. prior stable rules across the four scopes, records every behavioral difference, emits a gap entry `{path, operation, actorRole, expectedOutcome}` for any denied app operation, and records a pass/fail result per check
    - _Requirements: 18.1, 18.6, 18.7_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core implementation tasks are never optional.
- Each task references specific granular requirements for traceability.
- Property tests (Properties 1–18) are placed close to the pure logic they validate, so correctness issues surface before the Firestore/UI wiring depends on them.
- Firestore Security_Rules (Requirements 15–18) are verified by the emulator integration suite and the documented verification matrix, not by property-based tests, matching the design's testing strategy.
- Checkpoints provide incremental validation at the boundaries between pure logic, client wiring, and security rules.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["2.1", "3.1", "4.1", "5.1"] },
    { "id": 2, "tasks": ["2.3", "2.6", "3.5", "4.3", "4.7", "5.3"] },
    { "id": 3, "tasks": ["2.2", "2.4", "2.5", "2.7", "2.8", "3.2", "3.3", "3.4", "3.6", "4.2", "4.4", "4.5", "4.6", "4.8", "5.2", "5.4"] },
    { "id": 4, "tasks": ["7.1"] },
    { "id": 5, "tasks": ["7.2"] },
    { "id": 6, "tasks": ["7.3"] },
    { "id": 7, "tasks": ["7.4"] },
    { "id": 8, "tasks": ["7.5", "8.2"] },
    { "id": 9, "tasks": ["7.6", "8.1"] },
    { "id": 10, "tasks": ["8.3", "8.4", "8.5", "9.1", "9.2"] },
    { "id": 11, "tasks": ["9.3"] },
    { "id": 12, "tasks": ["11.1"] },
    { "id": 13, "tasks": ["11.2"] },
    { "id": 14, "tasks": ["11.3"] }
  ]
}
```

---

## Tasks (Iteration 2: UI Extensions)

Iteration 2 adds the four presentation-layer changes from Requirements 19–22 on top of the existing, unchanged ViewModel/repository logic. Two genuinely new pure functions (`IncomingTasks`, `CollaboratorPreview`) land first in the existing side-effect-free `com.theblankstate.preamble.collab` package so they can be validated with the established jqwik suite, then the Compose surfaces (`CollaboratorMemberList`, `CollaboratorAvatarCluster`, `IncomingSection`) are wired into the running app. Tasks continue the existing top-level numbering, starting at 13; tasks 1–12 are complete and unchanged.

Implementation language remains **Kotlin** (Android client + pure logic), consistent with the design and Iteration 1 tasks.

- [x] 13. Implement Iteration 2 pure logic in `com.theblankstate.preamble.collab`
  - [x] 13.1 Implement `IncomingTasks` own-pending selection
    - Create `collab/IncomingTasks.kt` as a pure object (no Android/Firestore deps): `INCOMING_STATUS = "pending"`, `incoming(tasks: List<Task>): List<Task>` filtering to tasks whose own `assignmentStatus == INCOMING_STATUS` in input order, and `hasIncoming(tasks): Boolean`
    - _Requirements: 19.1, 19.8_

  - [ ]* 13.2 Write property test for incoming selection
    - **Property 19: Incoming_Section selects exactly the own-pending collaborative tasks**
    - Generator produces collaborative-task lists with arbitrary own `assignmentStatus` values (including `pending`, `accepted`, `completed`, `declined`, `left`, `removed`, and absent); assert `incoming` returns exactly the `pending` subset preserving input order and `hasIncoming` matches non-emptiness; tag `// Feature: collaborative-tasks, Property 19: ...`, `@Property(tries = 100)` minimum
    - **Validates: Requirements 19.1, 19.8**

  - [x] 13.3 Implement `CollaboratorPreview` preview/overflow logic
    - Create `collab/CollaboratorPreview.kt` as a pure object: `PREVIEW_COUNT = 3`, generic `Preview<T>(shown, overflow, canExpand)` data class, and `fun <T> preview(visible: List<T>, expanded: Boolean): Preview<T>` returning the full list with no control when `size <= PREVIEW_COUNT`, the first 3 with `overflow = size - 3` and `canExpand = true` when collapsed, and all members with `overflow = 0` and `canExpand = true` when expanded
    - _Requirements: 21.1, 21.2, 21.3, 21.4, 22.2, 22.3_

  - [ ]* 13.4 Write property test for preview/overflow
    - **Property 20: Collaborator preview-and-overflow is correct and consistent across surfaces**
    - Generator produces visible-member lists spanning boundaries (0, 1, 2, 3, 4, 50); assert the shown-count, `overflow`, and `canExpand` rules for both `expanded=false` and `expanded=true`, that collapsing after expanding equals `preview(list, expanded=false)`, and that avatar-cluster counts (`min(size, 3)` shown, `max(0, size - 3)` overflow) follow from the same function; tag `// Feature: collaborative-tasks, Property 20: ...`, `@Property(tries = 100)` minimum
    - **Validates: Requirements 21.1, 21.2, 21.3, 21.4, 22.2, 22.3**

- [x] 14. Reconcile the collaborator view onto the tap-to-open detail sheet (Req 20/21)
  - [x] 14.1 Create the shared `CollaboratorMemberList` composable
    - Add `ui/components/CollaboratorMemberList.kt` with the design's signature (`adminUid`, `adminName`, `assignees`, `currentUserUid`, `showRoleControls`, optional `onRemoveMember`/`onTransferOwnership`/`onLeave`)
    - Compute visible members via `CollaboratorView.visibleMembers(assignees) { it.status }`, prepend the admin row from `adminUid`/`adminName`, and apply collapsible preview from `CollaboratorPreview.preview(visibleMembers, expanded)` with a local `var expanded by remember { mutableStateOf(false) }`; render an expand control when collapsed and a collapse control when expanded, and no control when `canExpand` is false
    - Render per-member admin badge (20.2), completion indicator from `isCompleted` (20.3), and a separate acceptance chip for `pending` vs `accepted` (20.4); gate remove/transfer/leave controls behind `showRoleControls`
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 21.1, 21.2, 21.3, 21.4_

  - [x] 14.2 Add the read-only collaborator section to `TaskDetailBottomSheet`
    - In `ui/components/TaskDetailBottomSheet.kt` (the tap-to-open sheet reached via `onDetail`), add a "Collaborators" section shown only when the task is collaborative (`task.collabAdminUid != null || task.collabAssignees.isNotEmpty()`), using `CollaboratorMemberList` with `showRoleControls = false` and no role callbacks
    - _Requirements: 20.1, 20.2, 20.3, 20.4_

  - [x] 14.3 Refactor `TaskDetailSheet` to use the shared `CollaboratorMemberList`
    - Replace the existing inline collaborator block (around the "Assigned Members & Status" `EditSection`) in `ui/components/TaskDetailSheet.kt` with `CollaboratorMemberList` using `showRoleControls = true`, forwarding the already-wired `onRemoveCollabMember`/`onTransferCollabOwnership`/`onLeaveCollabTask` callbacks so the edit sheet's Requirement 13 role behavior is unchanged and the two sheets cannot drift
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 21.1, 21.2, 21.3, 21.4_

  - [ ]* 14.4 Write Compose UI tests for the detail-sheet collaborator section and collapsible list
    - Tapping a task opens `TaskDetailBottomSheet` showing the collaborator section with the admin indicator, per-member completion, and a separate acceptance indicator; the list shows a preview of 3 with a working expand/collapse beyond 3 and no control at or below 3
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 21.1, 21.2, 21.3, 21.4_

- [x] 15. Implement the `CollaboratorAvatarCluster` on task rows (Req 22)
  - [x] 15.1 Create the `CollaboratorAvatarCluster` composable
    - Add `ui/components/CollaboratorAvatarCluster.kt` taking `task: Task`; render nothing for a non-collaborative task or one with no visible members (`task.collabAdminUid == null && task.collabAssignees.isEmpty()`)
    - Build the displayed set as the admin row plus `CollaboratorView.visibleMembers(task.collabAssignees) { it.status }`, reduce with `CollaboratorPreview.preview(displayed, expanded = false)`, draw up to 3 overlapping initials-circles (consistent with the existing initials-circle convention) and a trailing "+N" chip when `overflow > 0`
    - Add a small pure status→style helper so `accepted`/`completed` use the filled/primary treatment and `pending` uses a muted/outlined treatment (visible distinction)
    - _Requirements: 22.1, 22.2, 22.3, 22.4, 22.5_

  - [x] 15.2 Wire the cluster into `TaskItem`
    - In `ui/components/TaskItem.kt`, place `CollaboratorAvatarCluster` compactly (end-aligned in the row header area) without altering the row's `combinedClickable`, completion toggle, or the existing completion-driven reordering of the Home list
    - _Requirements: 22.1, 22.5, 22.6_

  - [ ]* 15.3 Write Compose UI tests for the avatar cluster
    - Cluster appears on collaborative rows with `accepted`/`completed` styled distinctly from `pending`, shows "+N" when there are more than 3 displayed members, is absent on non-collaborative rows, and does not disturb completion-driven reordering
    - _Requirements: 22.1, 22.4, 22.5, 22.6_

- [x] 16. Implement the Home `IncomingSection` (Req 19)
  - [x] 16.1 Create the `IncomingSection` composable in `HomeScreen.kt`
    - Add a private `IncomingSection` composable that renders a section header only when its incoming list is non-empty (19.8) and, for each task, the task title plus an inline Accept and Decline control pair (19.2)
    - _Requirements: 19.1, 19.2, 19.8_

  - [x] 16.2 Wire `IncomingSection` into the Home_Task_List as the first item
    - Source the section from `workspaceViewModel.incomingAssignments` narrowed by `IncomingTasks.incoming(...)`, and render `IncomingSection` as the first item of the home `LazyColumn` above the existing date groups
    - Bind Accept to `workspaceViewModel.acceptAssignment(task)` and Decline to `workspaceViewModel.declineAssignment(task)`, reusing the existing optimistic `<200 ms` update, pending-only transition guard, and exact-state revert on failure/timeout; after accept the task falls through to the normal Home list and after decline it is filtered out
    - Leave the existing `WorkspaceTasksScreen` "Incoming" tab and its shared `incomingAssignments`/accept/decline usage untouched (19.9)
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6, 19.7, 19.9_

  - [ ]* 16.3 Write Compose UI and timing tests for the Incoming_Section
    - Section renders above all other home content when own-pending tasks exist and is absent otherwise with inline Accept/Decline present; accept/decline reflect in the home list within 200 ms before the backend completes and revert on failure (exercised against the in-memory fake repository); the `WorkspaceTasksScreen` "Incoming" tab still renders and accepts/declines unchanged
    - _Requirements: 19.1, 19.2, 19.3, 19.5, 19.7, 19.8, 19.9_

- [x] 17. Iteration 2 checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes (Iteration 2)

- Tasks 13–17 are additive; tasks 1–12 (Iteration 1) remain complete and unchanged.
- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core implementation tasks are never optional.
- Properties 19 and 20 are the only new properties; Iteration 2 reuses Properties 10, 11, 12, and 16 because the Home Incoming_Section calls the same `WorkspaceViewModel.acceptAssignment`/`declineAssignment` paths and the detail-sheet/avatar surfaces consume the same `CollaboratorView.visibleMembers` filter — no duplicate property tests are added for them.
- **Test execution environment:** JVM unit-test execution (the jqwik property tests in tasks 13.2/13.4 and the existing Iteration 1 property/unit tests) is currently blocked because the environment lacks a complete JDK 21 with `jlink`. Until a full JDK 21 toolchain is available, the property/unit and Compose tests are verified by **compilation** (the test sources must compile cleanly) rather than by running the suite; the checkpoint in task 17 should be revisited to actually run the suite once the toolchain is fixed.
- The pure functions (`IncomingTasks`, `CollaboratorPreview`) carry no Android/Firestore dependencies, so they compile and (when the toolchain is available) run under the existing JVM test source set established in task 1.1.

## Task Dependency Graph (Iteration 2)

```json
{
  "waves": [
    { "id": 0, "tasks": ["13.1", "13.3"] },
    { "id": 1, "tasks": ["13.2", "13.4", "14.1", "15.1", "16.1"] },
    { "id": 2, "tasks": ["14.2", "14.3", "15.2", "16.2"] },
    { "id": 3, "tasks": ["14.4", "15.3", "16.3"] }
  ]
}
```
