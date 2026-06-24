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

---

## Tasks (Iteration 3: WS3 + WS4)

Iteration 3 implements the two WS3 collaborative-send correctness fixes (Requirements 23–24) and the three WS4 live-task-list visual upgrades (Requirements 25–27), exactly as specified in the "Design Document — Iteration 3" section. It follows the same bottom-up strategy as Iterations 1–2: the two genuinely new pure modules (`CollaborativeSend`, `AvatarSource`) land first in the side-effect-free `com.theblankstate.preamble.collab` package so the two new correctness properties (Properties 21 and 22) can be validated with the established jqwik suite, then the durable WorkManager send pipeline, the Room migration, the data plumbing for member photos, and the Compose surfaces are wired into the running app. Top-level numbering continues from Iteration 2, starting at 18; tasks 1–17 are complete and unchanged.

Implementation language remains **Kotlin** (Android client + pure logic), consistent with the design and Iterations 1–2.

- [x] 18. Implement WS3 collaborative-send pure logic and persistence foundation
  - [x] 18.1 Implement the `CollaborativeSend` pure module
    - Create `collab/CollaborativeSend.kt` as a pure object (no Android/Firestore deps): `SendStatus` enum (`PARSING`, `QUEUED`, `SENDING`, `SENT`, `SEND_FAILED`), `Connectivity` enum (`ONLINE`, `SLOW`, `OFFLINE`), the `Event` sealed interface (`Confirmed`, `ParseCompleted`, `ConnectivityOnline`, `ConnectivityLost`, `SendStarted`, `SendSucceeded`, `SendFailed`, `Retry`), `initial(connectivity, parsePending)` and the pure `next(current, event)` transition function
    - Encode: `SENT` absorbing and reachable only via `SendSucceeded`; `SEND_FAILED` reached only by `SendFailed` with retries exhausted and left only by `Retry` (→ `QUEUED`); `ParseCompleted` advances `PARSING` → `SENDING`; `ConnectivityLost` in flight → `QUEUED`
    - _Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.7, 24.1, 24.2, 24.4, 24.6, 24.7, 24.8_

  - [ ]* 18.2 Write property test for the Send_Status lifecycle
    - **Property 21: Send_Status lifecycle is correct and never reports an undelivered send as sent**
    - Generators produce arbitrary initial `Connectivity`/`parsePending` and arbitrary event sequences; assert `initial` mapping (parsing/queued/sending), that `SENT` is reachable only via `SendSucceeded` and is absorbing, that `SEND_FAILED` is reached only on exhausted retries and exits only via `Retry`, that `ParseCompleted` never strands a task in a terminal non-sent state, and that `ConnectivityLost` in flight returns to `queued`; tag `// Feature: collaborative-tasks, Property 21: ...`, `@Property(tries = 100)` minimum
    - **Validates: Requirements 23.1, 23.2, 23.3, 23.4, 23.5, 23.7, 24.1, 24.2, 24.4, 24.6, 24.7, 24.8**

  - [x] 18.3 Add the `collabSendStatus` column and Room migration 30 → 31
    - Add a nullable `collabSendStatus: String?` column to the `Task` Room entity (values `parsing`/`queued`/`sending`/`sent`/`send_failed`, `null` for non-collaborative tasks)
    - Add `MIGRATION_30_31` to `data/PreambleDatabase.kt` (`ALTER TABLE tasks ADD COLUMN collabSendStatus TEXT DEFAULT NULL`), register it, and bump the database version 30 → 31
    - **Device/environment-dependent:** the migration's forward-compatibility (open an existing v30 DB and migrate without data loss) should be verified on a device/emulator with a real installed database
    - _Requirements: 23.2, 24.2_

- [x] 19. Implement the durable collaborative-send pipeline and Send_Status surfacing
  - [x] 19.1 Create `CollaborativeSendWorker`
    - Add `ai/CollaborativeSendWorker.kt` as a `CoroutineWorker` taking `taskId`; read the current task from Room (so it picks up AI-refined or as-entered attributes)
    - Idempotent guards: task missing, no longer collaborative, or already `sent` → `Result.success()`
    - Build the assignee `Friend` list from `task.collabAssignees` (excluding admin) and call the existing `WorkspaceRepository.writeFinalizedCollaborativeAttributes(task, assignees)` (create-or-update canonical doc, preserving every member's `memberStates`)
    - Status writes via `CollaborativeSend.next(...)`: set `sending` before the attempt; on success set `sent` and `isSyncing = false`; on transient failure return `Result.retry()` (status stays `queued`); on terminal failure (`runAttemptCount >= MAX_SEND_ATTEMPTS`) set `send_failed`, retain the local copy unchanged, surface the "couldn't share with collaborators" message, and return `Result.failure()`
    - **Device/environment-dependent:** the durable-send behavior (queue survives process restart, delivers on reconnect, retry/backoff, terminal failure) benefits from device/emulator verification with WorkManager
    - _Requirements: 23.3, 23.4, 23.5, 23.6, 23.8, 24.4, 24.5, 24.6, 24.7_

  - [x] 19.2 Remove the inline collaborative-send from `AiParsingWorker`
    - In `ai/AiParsingWorker.kt`, remove the inline collaborative-send (the explicit-pending-collab branch that calls `writeFinalizedCollaborativeAttributes`) from both the happy/tool-calls path and the no-tool-calls early-return paths, so the parse phase ONLY refines and persists attributes and clears `isSyncing` for the parse phase
    - This is the change that closes the "parse returned nothing ⇒ never sent" bug; leave the natural-language `resolveAndAssignFriends` (voice/notification, Req 9) path untouched
    - _Requirements: 23.4, 23.7_

  - [x] 19.3 Wire the parse → send unique-work chain in `TaskViewModel.addTaskWithPendingAiParse`
    - Keep the own-copy `< 1 s` save (`isSyncing = true`); stamp the initial `collabSendStatus` from `CollaborativeSend.initial(connectivity, parsePending = true)` using a small `ConnectivityProbe` (Android `ConnectivityManager`) behind an interface so the pure machine stays testable
    - Replace the single `WorkManager.enqueue(parseRequest)` with `beginUniqueWork(sendWorkName(taskId), ExistingWorkPolicy.REPLACE, parseRequest).then(sendRequest).enqueue()`; both requests keep `NetworkType.CONNECTED`; leave the non-collaborative path as a single parse work with no send link
    - **Device/environment-dependent:** confirming the chain durably queues and runs the send when connectivity returns benefits from device/emulator verification
    - _Requirements: 23.1, 23.2, 24.1, 24.2, 24.3, 24.8_

  - [ ]* 19.4 Write unit tests for the send worker guards and terminal-failure path
    - Cover idempotent short-circuit (missing/not-collaborative/already-sent → success), transient failure → retry (status stays `queued`), and `runAttemptCount`-driven terminal failure → `send_failed` + retained local copy + message
    - _Requirements: 23.4, 24.6, 24.7_

  - [x] 19.5 Surface the Send_Status chip on the task row
    - In `ui/components/TaskItem.kt`, derive the Admin-visible status purely from `task.collabSendStatus` and render a small M3 Expressive status chip (`parsing`/`queued`/`sending`/`sent`/`send_failed`) consistent with the existing `isSyncing`/`syncFailed` indicators, without altering the row's click/completion behavior
    - _Requirements: 23.2, 23.5, 24.2, 24.4, 24.6_

- [x] 20. WS3 checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 21. Implement WS4 avatar-source pure logic
  - [x] 21.1 Implement the `AvatarSource` pure module
    - Create `collab/AvatarSource.kt` as a pure object: `Source` enum (`REAL_PHOTO`, `DEFAULT`), `select(hasRealPhoto, isInitialsPlaceholder, fetchFailed)` returning `REAL_PHOTO` iff a real photo is available, not a placeholder, and not fetch-failed (else `DEFAULT`), and `isGeneratedInitialsAvatar(url)` heuristic detecting blank/null and Google default-monogram URL patterns (e.g. `/a/default-user`, `/a-/` monogram variants, `=...-mo` monogram sizing suffix) while treating real `lh3.googleusercontent.com/a/...` photos as non-placeholder
    - _Requirements: 26.1, 26.2, 26.3, 26.4, 26.5_

  - [ ]* 21.2 Write property test for the avatar-source precedence
    - **Property 22: Member avatar source selection follows the real-photo-then-default precedence**
    - Generators enumerate every combination of `hasRealPhoto`, `isInitialsPlaceholder`, `fetchFailed`; assert `REAL_PHOTO` iff (`hasRealPhoto && !isInitialsPlaceholder && !fetchFailed`) and `DEFAULT` in every other case (no photo, placeholder, fetch failure, loading); tag `// Feature: collaborative-tasks, Property 22: ...`, `@Property(tries = 100)` minimum
    - **Validates: Requirements 26.1, 26.2, 26.3, 26.4, 26.5**

  - [ ]* 21.3 Write edge-case unit tests for `isGeneratedInitialsAvatar`
    - Test null/blank, Google default-user/monogram URLs (placeholder → true), and real `lh3.googleusercontent.com/a/...` photo URLs (→ false)
    - _Requirements: 26.3_

- [x] 22. Plumb member `photoUrl` data through the directory, friends, and canonical document (Req 26)
  - [x] 22.1 Capture and publish the Google `photoUrl` at sign-in
    - Add `photoUrl: String?` to `UserProfile`; in `auth/AuthManager` sign-in (`signInWithGoogle`) read `authResult.user?.photoUrl?.toString()` and store it in `UserProfile.photoUrl`; add `photoUrl` to the payloads written by `UserProfileStore.syncToFirestore` into `/preambleIds/{ID}` and `/users/{uid}`
    - **Device/environment-dependent:** confirming a real Google account `photoUrl` is captured at sign-in benefits from device/emulator verification with a real Google account
    - _Requirements: 26.1_

  - [x] 22.2 Add `photoUrl` to the `Friend` record and populate it on invite-accept
    - Add a nullable `photoUrl` to the `Friend` model; when a friend relationship is established (invite accept) in `WorkspaceRepository`, populate each reciprocal `Friend` record's `photoUrl` from the counterpart's public directory entry
    - _Requirements: 26.1_

  - [x] 22.3 Add `photoUrl` to `CollabAssigneeStatus` and write it on Collaborative_Send
    - Add a nullable `photoUrl` to `CollabAssigneeStatus` (serialized inside the existing `collabAssigneesJson` column — no Room migration needed); on the Collaborative_Send, write each member's `photoUrl` into the canonical doc (admin's own from `UserProfile.photoUrl`, assignees' from their `Friend` records) so every member can read photos from the shared document
    - _Requirements: 26.1_

- [x] 23. Implement the WS4 live task-list visual surfaces
  - [x] 23.1 Replace the circle with the Expressive_Member_Shape in `CollaboratorAvatarCluster`
    - In `ui/components/CollaboratorAvatarCluster.kt`, replace `CircleShape` in the avatar and overflow chip with a Material 3 Expressive morphing shape (`androidx.graphics.shapes` `RoundedPolygon`/`Morph` via a small `Shape` adapter, clipped with `Modifier.clip`), with a subtle alive morph/scale on appearance
    - Preserve the Req 22 behavior exactly: `CollaboratorView.visibleMembers` selection, `CollaboratorPreview.preview` overflow ("+N"), non-collaborative-row suppression, and the `avatarStatusStyle` accepted/completed-vs-pending fill/outline distinction
    - _Requirements: 25.1, 25.2, 25.3, 25.4, 25.5_

  - [x] 23.2 Implement the `MemberAvatar` composable with Default_Avatar fallback
    - Add a `MemberAvatar(photoUrl, name, style, shape)` composable that computes `AvatarSource.select(hasRealPhoto = !url.isNullOrBlank(), isInitialsPlaceholder = isGeneratedInitialsAvatar(url), fetchFailed)` and renders `REAL_PHOTO` → Coil `AsyncImage` clipped to the Expressive shape (with `onError` flipping local `fetchFailed`, and a Default_Avatar `placeholder` while loading) or `DEFAULT` → the bundled Default_Avatar inside the shape
    - Swap `CollaboratorAvatarCluster`'s `AvatarCircle(name, style)` for `MemberAvatar(...)`, keeping initials as the in-shape fallback content
    - _Requirements: 26.1, 26.2, 26.3, 26.4, 26.5_

  - [x] 23.3 Replace `IncomingSectionRow` with the `IncomingTaskCard`
    - In `HomeScreen.kt`, replace the minimal `IncomingSectionRow` with an `IncomingTaskCard(task, onAccept, onDecline)` that renders the task using the same metadata layout a normal task card uses (reuse/factor the `TaskItem` metadata block: title, deadline time formatted exactly as `TaskItem` does, tags, priority) so the two cannot drift
    - Controls: an inline Accept control with greater horizontal length (wide filled M3 button with `weight`) and a compact cross/`X` decline control, both Material 3 Expressive (alive shape/motion); wire Accept/Decline to the existing `workspaceViewModel.acceptAssignment(task)` / `declineAssignment(task)`, preserving the Req 19 placement, header-suppression, pending-only gating, optimistic `<200 ms`, and exact-state revert
    - _Requirements: 27.1, 27.2, 27.3, 27.4, 27.5, 27.6, 27.7, 27.8, 27.9_

  - [ ]* 23.4 Write Compose UI tests for the WS4 surfaces
    - Avatar cluster renders Expressive shapes preserving the 3-member preview, "+N" overflow, accepted/completed-vs-pending distinction, and non-collaborative suppression; `MemberAvatar` shows the Default_Avatar for placeholder/fetch-failed/loading and the photo only for a real URL; the Incoming_Task_Card shows full task metadata with an Accept control wider than the cross decline, and accept/decline reflect within 200 ms and revert on failure against the in-memory fake repository
    - _Requirements: 25.1, 25.3, 25.4, 26.2, 26.3, 27.2, 27.3, 27.4, 27.6, 27.7, 27.8_

- [x] 24. Iteration 3 final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes (Iteration 3)

- Tasks 18–24 are additive; tasks 1–17 (Iterations 1–2) remain complete and unchanged.
- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core implementation tasks are never optional.
- Properties 21 and 22 are the only new properties. The rest of Iteration 3 reuses existing properties per the design: canonical-document content/invariants for the deferred/queued send reuse Properties 5, 8, and 9; the cluster's displayed-member selection/overflow reuse Properties 16 and 20; and the Incoming_Task_Card's selection and optimistic accept/decline/revert reuse Properties 19, 10, 11, and 12 (the card calls the same `acceptAssignment`/`declineAssignment` paths).
- **Device/environment-dependent tasks:** the Room migration verification (18.3), the WorkManager durable-send behavior (19.1, and the chain in 19.3), and the Google-photo capture at sign-in (22.1) benefit from on-device/emulator verification beyond JVM unit tests.
- **Test execution environment:** as recorded for Iteration 2, JVM unit-test execution (jqwik) may be blocked until a full JDK 21 toolchain with `jlink` is available; until then the property/unit and Compose tests are verified by compilation and revisited at the checkpoints once the toolchain is fixed. The new pure modules (`CollaborativeSend`, `AvatarSource`) carry no Android/Firestore dependencies, so they compile and run under the existing JVM test source set established in task 1.1.

## Task Dependency Graph (Iteration 3)

```json
{
  "waves": [
    { "id": 0, "tasks": ["18.1", "21.1"] },
    { "id": 1, "tasks": ["18.2", "18.3", "21.2", "22.1", "22.2"] },
    { "id": 2, "tasks": ["19.1", "19.2", "19.5", "21.3", "23.1"] },
    { "id": 3, "tasks": ["19.3", "22.3", "23.3"] },
    { "id": 4, "tasks": ["19.4", "23.2"] },
    { "id": 5, "tasks": ["23.4"] }
  ]
}
```

---

## Tasks (Iteration 4: WS2 — Send to Circle + searchable recipient picker)

Iteration 4 implements the two WS2 changes from Requirements 28–31, exactly as specified in the "Design Document — Iteration 4: WS2 (Send to Circle + searchable recipient picker)" section: sending a task to one or more **Circles** under the members-as-assignees model, and replacing the inline friend dropdown + assignee-chip selector with a single searchable **Recipient_Picker** that lists Friends and Circles together. It follows the same bottom-up strategy as Iterations 1–3: the two genuinely new pure modules (`Recipient`, `RecipientResolution`) land first in the side-effect-free `com.theblankstate.preamble.collab` package so the one new correctness property (Property 23) can be validated with the established jqwik suite, then the `RecipientPicker` Compose surface is built, and finally the picker is wired into `AddTaskSheet` and `HomeScreen` over the **unchanged** Iteration 1–3 assignment path (`assignTaskToMultiple` / AI-parse-then-send chain / offline `CollaborativeSendWorker` queue). Top-level numbering continues from Iteration 3, starting at 25; tasks 1–24 are complete and unchanged.

Implementation language remains **Kotlin** (Android client + pure logic), consistent with the design and Iterations 1–3. The new pure modules carry no Android/Firestore dependencies, so they live in the existing JVM test source set established in task 1.1 and reuse the property-test tagging convention `// Feature: collaborative-tasks, Property {n}: {property text}` with `@Property(tries = 100)` minimum. No schema or entity changes, and no `circleId` is added to the Collaborative_Task (Req 28.9 snapshot semantics).

- [x] 25. Implement WS2 Circle-send pure logic in `com.theblankstate.preamble.collab`
  - [x] 25.1 Implement the `Recipient` model + `Searchable` adapter
    - Create `collab/Recipient.kt` as pure, framework-free types (no Android/Firestore deps): `FriendRef(uid, name, preambleId, photoUrl)` and `CircleRef(id, name, memberUids)`
    - Define the `Recipient` sealed interface with `FriendRecipient(friend: FriendRef)` and `CircleRecipient(circle: CircleRef)`, each exposing a stable selection `key` (`"f:<uid>"` / `"c:<id>"`)
    - Implement `Recipient.asSearchable(): SocialSearch.Searchable` so the existing case-insensitive `SocialSearch.filter` matches a Friend on display name AND Preamble_ID and a Circle on Circle_Name (Circle exposes `preambleId = ""`, `displayName = circleName`); add the thin `SearchableRecipient` wrapper used to feed `SocialSearch.filter`/`PageWindow.visible` directly
    - _Requirements: 30.1, 30.3_

  - [x] 25.2 Implement `RecipientResolution` (dedupe, sender-exclusion, post-expansion cap)
    - Create `collab/RecipientResolution.kt` as a pure object: `MAX_ASSIGNEES = 50` (= `CollaborativeDocument.MAX_ASSIGNEES`), the `Resolved(assigneeUids: List<String>, withinLimit: Boolean)` data class with a `size` accessor, and `resolve(selectedFriendUids, selectedCircleMemberUids, senderUid): Resolved`
    - Union the selected friend uids with every selected Circle's member uids using a `LinkedHashSet` for insertion-ordered dedupe (friends first in selection order, then circle members in selection order, each uid exactly once), remove the `senderUid` after the union, and classify `withinLimit = uids.size <= MAX_ASSIGNEES` measured **after** expansion + dedupe; an all-sender-only selection yields the empty set
    - _Requirements: 28.2, 28.3, 28.4, 28.5, 29.1, 29.4_

  - [ ]* 25.3 Write property test for Circle-send resolution
    - **Property 23: Circle_Send resolution dedupes, excludes the sender, and caps after expansion**
    - Generators produce arbitrary friend-uid lists, lists of Circle member-uid lists (including empty circles, sender-only circles, and circles overlapping each other and the friends), and a sender uid (sometimes present among the inputs, sometimes not), spanning the size boundaries that straddle 50 after expansion + dedupe (e.g. 49, 50, 51); assert no duplicates in the result, that the result equals `(selectedFriendUids ∪ allCircleMemberUids) \ {senderUid}` as a set, that the sender is always absent, that an all-sender-only selection yields the empty set, and that `withinLimit == (size <= 50)`; tag `// Feature: collaborative-tasks, Property 23: ...`, `@Property(tries = 100)` minimum
    - **Validates: Requirements 28.2, 28.3, 28.4, 28.5, 28.7, 29.1, 29.4**

- [x] 26. Build the searchable `RecipientPicker` modal bottom sheet (Req 30, 31)
  - [x] 26.1 Create `ui/components/RecipientPicker.kt`
    - Add the Material 3 Expressive `ModalBottomSheet` composable with the design's signature (`friends: List<Friend>`, `circles: List<Circle>`, `senderUid`, `initiallySelectedKeys: Set<String>`, `onConfirm: (List<Recipient>) -> Unit`, `onDismiss`); follow the Social_Hub_Design_Language (Cardfolio-style vibrant cards, alive morphing shapes/motion)
    - Build `recipients = friends.map { FriendRecipient(it.toRef()) } + circles.map { CircleRecipient(it.toRef()) }`; render each row with the name, a Friend-vs-Circle affordance (Circle rows show `Circle.memberCount`), and a selected indicator (30.1, 30.6)
    - Search: `filtered = SocialSearch.filter(query, searchableRecipients)` over the **full** recipient set independent of the paging window (30.4); blank/whitespace query ⇒ full set (30.5); paging: `visible = PageWindow.visible(filtered, pageCount)` with `PageWindow.shouldLoadMore(...)` growing `pageCount` per page as the user scrolls (30.2)
    - Selection: a session-scoped `Set<Recipient.key>` seeded from `initiallySelectedKeys`, keyed (not index-based) so it survives filtering/windowing; preserved across search-query changes (31.6) and across reopen (31.5)
    - Live count: derive selected friends + selected circles' member uid lists and call `RecipientResolution.resolve(...)`; display the **Selected_Count** and the live **Resolved_Assignee_Set** size (`resolved.size`) (29.3, 30.6); when `!resolved.withinLimit`, show an "exceeds maximum assignees" indication and **disable the confirm control** (29.4)
    - Empty/no-match states: empty `recipients` ⇒ "no friends or Circles to send to" empty-state with no selectable rows (31.1, 31.2); a non-empty query with no matches ⇒ a no-match indication with no rows, restored to the full set when cleared (31.3, 31.4)
    - Confirm: invoke `onConfirm(selectedRecipients)` and close the sheet (30.7)
    - _Requirements: 30.1, 30.2, 30.3, 30.4, 30.5, 30.6, 30.7, 29.3, 29.4, 31.1, 31.2, 31.3, 31.4, 31.5, 31.6_

  - [ ]* 26.2 Write Compose UI tests for the Recipient_Picker
    - Empty-state when there are no Friends and no Circles with no selectable rows (31.1); all available Recipients listed otherwise (31.2); a no-match query shows the no-match indication with no rows and clearing it restores the full set (31.3, 31.4); selection is preserved across a search-query change and across reopen (31.5, 31.6); the live Resolved_Assignee_Set size is shown and the confirm control is disabled when the resolved size exceeds 50 (29.3, 29.4)
    - _Requirements: 29.3, 29.4, 31.1, 31.2, 31.3, 31.4, 31.5, 31.6_

- [x] 27. Wire the Recipient_Picker into the task-create flow over the unchanged assignment path
  - [x] 27.1 Replace the inline selector in `AddTaskSheet` with the `RecipientPicker`
    - In `ui/components/AddTaskSheet.kt`, remove the `Group`-icon `DropdownMenu` of friend `Checkbox` items and the assignee `InputChip` row; add a single recipient control that opens `RecipientPicker`, making it the sole recipient-selection control (30.9)
    - Add a `circles: List<Circle>` parameter alongside the existing `friends: List<Friend>`; replace `var selectedFriends` with a session-scoped, keyed `var selectedRecipients by remember { mutableStateOf<List<Recipient>>(emptyList()) }` that persists across reopen (31.5), and pass its keys as the picker's `initiallySelectedKeys`
    - Show a compact confirmed-selection summary that distinguishes selected Friends from selected Circles and shows the live resolved assignee count (30.8)
    - On task confirm, materialize the Resolved_Assignee_Set as `List<Friend>`: selected `FriendRecipient`s contribute their `Friend` directly; selected `CircleRecipient`s contribute, for each resolved member uid, a `Friend(uid, name, photoUrl)` built from that Circle's `members` (`CircleMember.uid`/`name`), all filtered to the `RecipientResolution.resolve(...)` uid set so dedupe + sender-exclusion apply once; pass the materialized list to the **unchanged** `onAddTask(...)` / `onAddTaskPendingParse(...)` `assignedToFriends` parameter (an empty set takes the existing non-collaborative branch, 28.7)
    - Leave `TaskViewModel.addTask` / `addTaskWithPendingAiParse` and `WorkspaceRepository.assignTaskToMultiple` untouched so the canonical-document write, rich content, per-member lifecycle, AI-parse-then-send chain, and offline queue are preserved, and no `circleId` is stored (28.6, 28.8, 28.9)
    - _Requirements: 28.1, 28.6, 28.7, 28.8, 28.9, 30.8, 30.9_

  - [x] 27.2 Source Circles and wire them through `HomeScreen` into `AddTaskSheet`
    - In `HomeScreen.kt`, obtain a `CircleViewModel` (`viewModel()`), collect `circles` from `CircleViewModel.circles` (reusing `shared-circles`' `CircleRepository.getCirclesFlow()`), and pass `circles` into `AddTaskSheet` alongside the existing `friends` from `WorkspaceViewModel.friends`
    - Supply the sender uid from the existing `FirebaseAuth.getInstance().currentUser?.uid` so the picker and resolution exclude the sender
    - _Requirements: 28.1, 28.2, 28.5, 30.1_

  - [ ]* 27.3 Write Compose UI / integration test for end-to-end Circle send
    - From the create sheet, selecting one or more Circles (and optionally individual Friends), confirming the picker, and confirming the task produces a `List<Friend>` equal to the deduped, sender-excluded Resolved_Assignee_Set that flows into the unchanged `assignedToFriends` path (collaborative task created with the sender as Admin+Member and each resolved uid as a `pending` Member); a sender-only Circle selection produces an empty set and a normal non-collaborative task (28.7); the canonical document stores no `circleId` (28.9)
    - _Requirements: 28.2, 28.3, 28.4, 28.5, 28.6, 28.7, 28.9_

- [x] 28. Iteration 4 checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes (Iteration 4)

- Tasks 25–28 are additive; tasks 1–24 (Iterations 1–3) remain complete and unchanged.
- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core implementation tasks are never optional.
- Property 23 is the only new property. The rest of Iteration 4 reuses existing properties per the design: a Circle-sent task's canonical-document construction and the collaborative-iff-assignees rule reuse Properties 5, 6, and 7 (the resolver only produces the `List<Friend>` fed into the unchanged `assignTaskToMultiple` path); the preserved rich content / per-member lifecycle / send lifecycle reuse Properties 8, 9, and 21; and the picker's search + paging reuse the existing `social-hub-redesign` `SocialSearch`/`PageWindow` properties. The `Recipient → Searchable` adapter and the snapshot-at-send (no `circleId`) behavior are verified by example/UI tests rather than a property.
- **Test execution environment:** as recorded for Iterations 2–3, JVM unit-test execution (jqwik) may be blocked until a full JDK 21 toolchain with `jlink` is available; until then the property/unit and Compose tests are verified by compilation and revisited at the checkpoint once the toolchain is fixed. The new pure modules (`Recipient`, `RecipientResolution`) carry no Android/Firestore dependencies, so they compile and run under the existing JVM test source set established in task 1.1.

## Task Dependency Graph (Iteration 4)

```json
{
  "waves": [
    { "id": 0, "tasks": ["25.1", "25.2"] },
    { "id": 1, "tasks": ["25.3", "26.1"] },
    { "id": 2, "tasks": ["26.2", "27.1"] },
    { "id": 3, "tasks": ["27.2"] },
    { "id": 4, "tasks": ["27.3"] }
  ]
}
```
