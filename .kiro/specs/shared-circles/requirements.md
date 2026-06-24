# Requirements Document

## Introduction

This feature adds **Shared Circles** to the Preamble Android app (Kotlin, Jetpack Compose, Room local database, Firebase Firestore backend). A Circle is a named shared space — for example "Home", "Gym buddies", or "Trip" — that several friends belong to and that contains a single shared list of tasks. Every Member of a Circle sees the shared list in real time, any Member can add tasks to it, and a completed task is done for the whole Circle. This is the family / roommate / couples primitive: when anyone checks off "buy milk", it is checked off for everyone.

The feature builds directly on the already-implemented `collaborative-tasks` feature and deliberately **parallels** its canonical-document + membership + security model. It reuses that feature's defined terms (Member, Admin, `memberUidMap`, `memberUids`, `memberStates`, Optimistic_UI, Security_Rules, Preamble_ID, friends) and mirrors its idioms: a single canonical Firestore document per shared entity, membership-gated reads, admin-gated membership changes, the own-slice write guarantee, and the self-removal idiom. Existing `collaborative-tasks` and `social-engagement` behavior is unchanged; this document only adds new behavior.

The most important **departure** from `collaborative-tasks` is completion semantics. In `collaborative-tasks`, completion is **per-member** (each Member completes their own slice). In a Shared Circle, a household list needs the opposite: completion is **global / shared**. When any Member completes a Circle_Task, the task is completed for the whole Circle, attributed to the Member who completed it. This deliberate difference is called out in the design-flags section.

This document defines WHAT the system must do. Data structures (subcollection vs. top-level collection for Circle_Tasks, exact rule expressions, Room mirroring, navigation placement) are addressed in the design phase.

## Glossary

Terms marked "reused" carry the same meaning as in the `collaborative-tasks` requirements and are not redefined here.

- **Circle**: A named shared space owned by one Circle_Admin and joined by one or more Circle_Members, containing a single shared list of Circle_Tasks. Stored as a single canonical Firestore document at `/circles/{circleId}`.
- **Circle_Name**: A human-readable, non-empty display name for a Circle (for example "Home"), set at creation and editable by the Circle_Admin.
- **Circle_Admin**: The single user who owns a Circle and can rename it, add Circle_Members, remove Circle_Members, and delete the Circle. Identified by `adminUid`. (Parallels **Admin**, reused.)
- **Circle_Member**: Any user who belongs to a Circle, including the Circle_Admin. Identified within `memberUids`. (Parallels **Member**, reused.)
- **Circle_Task**: A task belonging to one Circle's shared list, visible to and completable by every Circle_Member of that Circle, identified by `circleId`.
- **Shared_Completion**: The completion state of a Circle_Task that is global to the Circle — a single completion flag for the whole Circle rather than a per-member flag — together with attribution of the Circle_Member who completed it. Contrast with the per-member completion of a `collaborative-tasks` Collaborative_Task.
- **Completer**: The Circle_Member whose action set a Circle_Task to completed, recorded as attribution on the Circle_Task.
- **Circle_Author**: The Circle_Member who created a given Circle_Task, identified by `authorUid` on that Circle_Task.
- **Max_Circle_Members**: The maximum number of Circle_Members permitted in a single Circle, equal to 50 (including the Circle_Admin).
- **Circle_Service**: The component responsible for Circle lifecycle (create, rename, add member, remove member, delete) and the local Circle list state. (Parallels Friend_Service / the admin-management portion of the Collaboration_System.)
- **Circle_Task_Service**: The component responsible for the shared task list within a Circle (add, edit, delete, complete) and its real-time synchronization. (Parallels Task_Sync_Engine.)
- **Circles_Screen**: The Compose surface that lists the signed-in user's Circles and provides entry to create a Circle and to open a Circle.
- **Circle_Detail_Screen**: The Compose surface that shows one Circle's shared task list and, for the Circle_Admin, its membership-management controls.
- **memberUidMap**: Reused — the map field on the canonical document, keyed by Circle_Member identifier with boolean `true` values, whose keys equal `memberUids` exactly, used for queryability and Security_Rules membership checks.
- **memberStates**: Reused — the per-member record map on the canonical document, keyed by Circle_Member identifier, holding each Circle_Member's display name, role, and join timestamp.
- **Member_Status (circle)**: The membership status of a Circle_Member, one of `active`, `left`, or `removed`. (Narrower than the `collaborative-tasks` Member_Status because Circle membership has no per-member accept/decline/complete lifecycle.)
- **Optimistic_UI**: Reused — a UI pattern where the result of a user action is reflected immediately, before the backend confirms it, and reverted only if the backend operation fails.
- **Security_Rules**: Reused — the Firestore security rules governing read/write access to all collections.
- **Preamble_ID**, **friends**: Reused — friends are the existing reciprocal friend relationships from `collaborative-tasks` Requirement 17; Circle_Members are added only from the signed-in user's friends.

## Requirements

### Requirement 1: Create a Circle

**User Story:** As a user, I want to create a named Circle, so that I have a shared space where my friends and I keep one shared task list.

#### Acceptance Criteria

1. WHEN a user submits a request to create a Circle with a Circle_Name, THE Circle_Service SHALL normalize the Circle_Name by removing all leading and trailing whitespace.
2. IF the normalized Circle_Name is empty, THEN THE Circle_Service SHALL reject the request, SHALL display a message indicating that a Circle name is required, and SHALL NOT create a Circle.
3. WHEN a user creates a Circle with a non-empty normalized Circle_Name, THE Circle_Service SHALL create exactly one canonical Circle document that records the creating user as the Circle_Admin and as a Circle_Member with Member_Status `active`, records the Circle_Name, and records a creation timestamp in UTC.
4. WHEN a Circle is created, THE Circle_Service SHALL reflect the new Circle in the creating user's displayed Circle list within 200 milliseconds of the action and before the backend operation completes.
5. IF the backend operation for creating a Circle fails or does not complete within 30 seconds, THEN THE Circle_Service SHALL treat the operation as failed, SHALL remove the optimistically displayed Circle from the displayed Circle list, and SHALL display an error message indicating that the Circle could not be created.

### Requirement 2: View the list of Circles

**User Story:** As a user, I want to see all the Circles I belong to, so that I can choose one to open.

#### Acceptance Criteria

1. WHILE a user is signed in, THE Circles_Screen SHALL display the list of Circles in which that user's identifier is present in the Circle's member list (`memberUids`), where each displayed entry shows the Circle_Name and the count of Circle_Members.
2. WHEN no Circle includes the signed-in user as a Circle_Member, THE Circles_Screen SHALL display an empty-state indication that the user belongs to no Circles and SHALL present a control to create a Circle.
3. WHEN a Circle's membership or Circle_Name changes by the action of any Circle_Member, THE Circle_Service SHALL update the displayed Circle list for every Circle_Member of that Circle within 5 seconds of the change under normal network connectivity.
4. THE Circles_Screen SHALL be reachable from the friends or workspace area of the app.

### Requirement 3: Rename a Circle

**User Story:** As a Circle admin, I want to rename my Circle, so that its name stays meaningful.

#### Acceptance Criteria

1. WHERE the signed-in user is the Circle_Admin of a Circle, THE Circle_Service SHALL present a control to rename that Circle.
2. WHEN a Circle_Admin submits a new Circle_Name, THE Circle_Service SHALL normalize the new Circle_Name by removing all leading and trailing whitespace.
3. IF the normalized new Circle_Name is empty, THEN THE Circle_Service SHALL reject the rename, SHALL leave the existing Circle_Name unchanged, and SHALL display a message indicating that a Circle name is required.
4. WHEN a Circle_Admin renames a Circle with a non-empty normalized Circle_Name, THE Circle_Service SHALL set the Circle_Name to the new value and SHALL reflect the new Circle_Name in the Circle_Admin's view within 200 milliseconds of the action and before the backend operation completes.
5. IF a Circle_Member who is not the Circle_Admin requests to rename a Circle, THEN THE Circle_Service SHALL reject the request and SHALL leave the existing Circle_Name unchanged.
6. IF the backend operation for renaming a Circle fails or does not complete within 30 seconds, THEN THE Circle_Service SHALL restore the displayed Circle_Name to the value held immediately before the action and SHALL display an error message indicating that the rename could not be saved.

### Requirement 4: Add members to a Circle

**User Story:** As a Circle admin, I want to add my friends to a Circle, so that they share its task list.

#### Acceptance Criteria

1. WHERE the signed-in user is the Circle_Admin of a Circle, THE Circle_Service SHALL allow the Circle_Admin to add a Circle_Member selected from the Circle_Admin's existing friend list.
2. WHEN a Circle_Admin adds a friend to a Circle, THE Circle_Service SHALL add that friend's identifier to the member list (`memberUids`) and the member map (`memberUidMap`), SHALL record a Member_State for that Circle_Member with Member_Status `active`, and SHALL reflect the added Circle_Member in the Circle_Admin's view within 200 milliseconds of the action and before the backend operation completes.
3. IF a Circle_Admin attempts to add a user who is not a friend of the Circle_Admin, THEN THE Circle_Service SHALL reject the request, SHALL leave the member list unchanged, and SHALL display an error message indicating that only friends can be added to a Circle.
4. IF a Circle_Admin attempts to add a user who is already a Circle_Member of the Circle, THEN THE Circle_Service SHALL reject the request and SHALL leave the member list unchanged.
5. THE Circle_Service SHALL limit the number of Circle_Members in a single Circle to Max_Circle_Members.
6. IF adding a Circle_Member would cause the number of Circle_Members to exceed Max_Circle_Members, THEN THE Circle_Service SHALL reject the request, SHALL leave the member list unchanged, and SHALL display an error message indicating that the maximum number of Circle members has been reached.
7. IF a Circle_Member who is not the Circle_Admin requests to add a Circle_Member, THEN THE Circle_Service SHALL reject the request and SHALL leave the member list unchanged.
8. IF the backend operation for adding a Circle_Member fails or does not complete within 30 seconds, THEN THE Circle_Service SHALL restore the displayed member list to the state held immediately before the action and SHALL display an error message indicating that the member could not be added.

### Requirement 5: Remove members from a Circle

**User Story:** As a Circle admin, I want to remove a member from a Circle, so that I control who shares the list.

#### Acceptance Criteria

1. WHERE the signed-in user is the Circle_Admin of a Circle, THE Circle_Service SHALL allow the Circle_Admin to remove any Circle_Member other than the Circle_Admin.
2. WHEN a Circle_Admin removes a Circle_Member, THE Circle_Service SHALL remove that Circle_Member's identifier from the member list (`memberUids`) and the member map (`memberUidMap`), SHALL set that Circle_Member's Member_Status to `removed`, and SHALL reflect the removal in the Circle_Admin's view within 200 milliseconds of the action and before the backend operation completes.
3. IF a Circle_Admin attempts to remove the Circle_Admin's own identifier through the member-removal control, THEN THE Circle_Service SHALL reject the request and SHALL display a message indicating that the Circle_Admin must delete the Circle instead of removing themselves.
4. IF a Circle_Member who is not the Circle_Admin requests to remove another Circle_Member, THEN THE Circle_Service SHALL reject the request and SHALL leave the member list unchanged.
5. WHEN a Circle_Member is removed from a Circle, THE Circle_Service SHALL stop displaying that Circle and its Circle_Tasks to the removed user within 5 seconds of the removal under normal network connectivity.
6. IF the backend operation for removing a Circle_Member fails or does not complete within 30 seconds, THEN THE Circle_Service SHALL restore the displayed member list to the state held immediately before the action and SHALL display an error message indicating that the member could not be removed.

### Requirement 6: Leave a Circle

**User Story:** As a non-admin member, I want to leave a Circle, so that I can stop sharing its task list without needing the admin.

#### Acceptance Criteria

1. WHERE the signed-in user is a Circle_Member of a Circle and is not the Circle_Admin, THE Circle_Service SHALL present a control to leave that Circle and SHALL require the user to confirm leaving before it is performed.
2. WHEN a non-admin Circle_Member confirms leaving a Circle, THE Circle_Service SHALL remove only that user's identifier from the member list (`memberUids`) and the member map (`memberUidMap`), SHALL set only that user's Member_Status to `left`, SHALL leave every other Circle_Member's records unchanged, and SHALL remove the Circle from that user's displayed Circle list within 200 milliseconds of the action and before the backend operation completes.
3. IF the signed-in user is the Circle_Admin of a Circle, THEN THE Circle_Service SHALL NOT present a leave control, SHALL reject any leave request, and SHALL display a message indicating that the Circle_Admin must delete the Circle to leave it.
4. IF the backend operation for leaving a Circle fails or does not complete within 30 seconds, THEN THE Circle_Service SHALL restore the Circle to the user's displayed Circle list, restore that user's prior membership in the member list and member map, and SHALL display an error message indicating that leaving the Circle did not complete.

### Requirement 7: Delete a Circle

**User Story:** As a Circle admin, I want to delete a Circle, so that I can remove a shared space that is no longer needed.

#### Acceptance Criteria

1. WHERE the signed-in user is the Circle_Admin of a Circle, THE Circle_Service SHALL present a control to delete that Circle and SHALL require the Circle_Admin to confirm deletion before it is performed.
2. WHEN a Circle_Admin confirms deletion of a Circle, THE Circle_Service SHALL delete the canonical Circle document together with every Circle_Task belonging to that Circle and SHALL remove the Circle from the Circle_Admin's displayed Circle list within 200 milliseconds of the action and before the backend operation completes.
3. WHEN a Circle is deleted, THE Circle_Service SHALL stop displaying that Circle and its Circle_Tasks to every former Circle_Member within 5 seconds of the deletion under normal network connectivity.
4. IF a Circle_Member who is not the Circle_Admin requests to delete a Circle, THEN THE Circle_Service SHALL reject the request and SHALL leave the Circle unchanged.
5. IF the backend operation for deleting a Circle fails or does not complete within 30 seconds, THEN THE Circle_Service SHALL restore the Circle to the Circle_Admin's displayed Circle list and SHALL display an error message indicating that the Circle could not be deleted.

### Requirement 8: Canonical Circle data model

**User Story:** As a user collaborating in a Circle, I want each Circle to track its members and admin consistently, so that membership and access are unambiguous.

#### Acceptance Criteria

1. THE Circle SHALL record exactly one Circle_Admin identified by a single user identifier (`adminUid`).
2. THE Circle SHALL record the complete list of Circle_Member identifiers (`memberUids`), the list SHALL include the Circle_Admin's identifier, SHALL contain no duplicate identifiers, and SHALL contain at most Max_Circle_Members identifiers.
3. THE Circle SHALL record a member map (`memberUidMap`) whose keys are exactly equal to the set of identifiers in the member list (`memberUids`).
4. THE Circle SHALL record exactly one Member_State for each identifier in the member list and SHALL NOT record a Member_State for any identifier absent from the member list, where each Member_State includes the Circle_Member's display name and a Member_Status whose value is exactly one of `active`, `left`, or `removed`.
5. THE Circle SHALL record a non-empty Circle_Name, a creation timestamp in UTC, and a last-updated timestamp in UTC.
6. WHEN a single Circle_Member is added, removed, or leaves, THE Circle_Service SHALL update only that Circle_Member's entry in the member list, member map, and Member_State map and SHALL leave every other Circle_Member's entries unchanged.

### Requirement 9: Add a task to a Circle's shared list

**User Story:** As a Circle member, I want to add a task to a Circle's shared list, so that everyone in the Circle sees it.

#### Acceptance Criteria

1. WHERE the signed-in user is a Circle_Member of a Circle, THE Circle_Task_Service SHALL present a control to add a Circle_Task to that Circle's shared task list.
2. WHEN a Circle_Member adds a Circle_Task with a non-empty title, THE Circle_Task_Service SHALL create exactly one Circle_Task that records the Circle's identifier (`circleId`), records the adding Circle_Member as the Circle_Author (`authorUid`), records the title, records a not-completed Shared_Completion state, and records a creation timestamp in UTC.
3. WHEN a Circle_Member adds a Circle_Task, THE Circle_Task_Service SHALL reflect the new Circle_Task in the adding Circle_Member's view of the shared list within 200 milliseconds of the action and before the backend operation completes.
4. IF a Circle_Member attempts to add a Circle_Task with an empty title, THEN THE Circle_Task_Service SHALL reject the request, SHALL NOT create a Circle_Task, and SHALL display a message indicating that a task title is required.
5. IF a user who is not a Circle_Member of the Circle attempts to add a Circle_Task to that Circle, THEN THE Circle_Task_Service SHALL reject the request and SHALL NOT create a Circle_Task.
6. IF the backend operation for adding a Circle_Task fails or does not complete within 30 seconds, THEN THE Circle_Task_Service SHALL remove the optimistically displayed Circle_Task from the shared list and SHALL display an error message indicating that the task could not be added.

### Requirement 10: See the shared task list in real time

**User Story:** As a Circle member, I want to see the Circle's shared task list update in real time, so that I always see the same list as everyone else in the Circle.

#### Acceptance Criteria

1. WHERE the signed-in user is a Circle_Member of a Circle, THE Circle_Detail_Screen SHALL display every Circle_Task belonging to that Circle, each showing the title, the Shared_Completion state, and the Circle_Author's display name.
2. WHEN any Circle_Member adds, edits, completes, un-completes, or deletes a Circle_Task in a Circle, THE Circle_Task_Service SHALL update the displayed shared task list for every Circle_Member of that Circle within 5 seconds of the change under normal network connectivity.
3. WHERE a Circle has no Circle_Tasks, THE Circle_Detail_Screen SHALL display an empty-state indication that the Circle's shared list has no tasks.
4. IF updating a Circle_Member's local copy of the shared task list from a backend change fails, THEN THE Circle_Task_Service SHALL retain that Circle_Member's last successfully synced copy and SHALL display a message indicating that the shared list could not be updated.

### Requirement 11: Shared completion of a Circle task

**User Story:** As a Circle member, I want completing a shared task to mark it done for the whole Circle and show who did it, so that nobody repeats work that is already finished.

#### Acceptance Criteria

1. WHERE the signed-in user is a Circle_Member of a Circle, THE Circle_Task_Service SHALL allow that Circle_Member to set any Circle_Task in that Circle to completed.
2. WHEN a Circle_Member sets a Circle_Task to completed, THE Circle_Task_Service SHALL set the Circle_Task's Shared_Completion state to completed for the whole Circle, SHALL record the acting Circle_Member as the Completer, and SHALL record a completion timestamp in UTC.
3. WHEN a Circle_Task's Shared_Completion state is set to completed, THE Circle_Task_Service SHALL present that Circle_Task as completed to every Circle_Member of the Circle within 5 seconds of the change under normal network connectivity, together with the Completer's display name.
4. WHERE the signed-in user is a Circle_Member of a Circle, THE Circle_Task_Service SHALL allow that Circle_Member to set a completed Circle_Task back to not completed, and SHALL clear the recorded Completer when the Circle_Task is set back to not completed.
5. WHEN a Circle_Member changes the Shared_Completion state of a Circle_Task, THE Circle_Task_Service SHALL reflect the changed state in the acting Circle_Member's view within 200 milliseconds of the action and before the backend operation completes.
6. IF the backend operation for changing the Shared_Completion state of a Circle_Task fails or does not complete within 30 seconds, THEN THE Circle_Task_Service SHALL restore the displayed Shared_Completion state and Completer to the values held immediately before the action and SHALL display an error message indicating that the change could not be saved.

### Requirement 12: Edit and delete a Circle task

**User Story:** As a Circle member, I want clear rules for who can change or remove a shared task, so that the list stays trustworthy.

#### Acceptance Criteria

1. WHERE the signed-in user is the Circle_Author of a Circle_Task or the Circle_Admin of the Circle, THE Circle_Task_Service SHALL allow that user to edit the Circle_Task's title.
2. IF a Circle_Member who is neither the Circle_Author of a Circle_Task nor the Circle_Admin of the Circle attempts to edit that Circle_Task's title, THEN THE Circle_Task_Service SHALL reject the request and SHALL leave the Circle_Task unchanged.
3. WHERE the signed-in user is the Circle_Author of a Circle_Task or the Circle_Admin of the Circle, THE Circle_Task_Service SHALL allow that user to delete the Circle_Task.
4. IF a Circle_Member who is neither the Circle_Author of a Circle_Task nor the Circle_Admin of the Circle attempts to delete that Circle_Task, THEN THE Circle_Task_Service SHALL reject the request and SHALL leave the Circle_Task unchanged.
5. WHEN a user authorized to edit or delete a Circle_Task performs that action, THE Circle_Task_Service SHALL reflect the change in the acting user's view within 200 milliseconds of the action and before the backend operation completes.
6. IF the backend operation for editing or deleting a Circle_Task fails or does not complete within 30 seconds, THEN THE Circle_Task_Service SHALL restore the displayed Circle_Task to the state held immediately before the action and SHALL display an error message indicating that the change could not be saved.

### Requirement 13: Security rules for Circles

**User Story:** As a user, I want my Circles to be accessible only to their members, so that my shared spaces stay private.

#### Acceptance Criteria

1. IF a user requests to read a Circle and that user's identifier is present in the Circle's member list (`memberUids`), THEN THE Security_Rules SHALL permit the read.
2. IF a user requests to read a Circle and that user's identifier is not present in the Circle's member list (`memberUids`), THEN THE Security_Rules SHALL deny the read and SHALL NOT return any Circle content.
3. IF a user requests to create a Circle and the request records the creating user as both the Circle_Admin (`adminUid`) and a Circle_Member (present in `memberUids`), THEN THE Security_Rules SHALL permit the creation; otherwise THE Security_Rules SHALL deny the creation.
4. IF a user other than the Circle_Admin requests to rename the Circle, add a Circle_Member, or remove a Circle_Member, THEN THE Security_Rules SHALL deny the write and SHALL leave the existing Circle document unchanged.
5. IF a non-admin Circle_Member requests a write that removes only that member's own identifier from the member list, the member map, and that member's own Member_State sets only that member's Member_Status to `left`, THEN THE Security_Rules SHALL permit the write; IF the request alters any other Circle_Member's records, THEN THE Security_Rules SHALL deny the write.
6. IF a user other than the Circle_Admin requests to delete a Circle, THEN THE Security_Rules SHALL deny the deletion and SHALL leave the Circle document unchanged.
7. IF a create or update request for a Circle does not satisfy all required schema conditions, namely a member map whose keys are exactly equal to the set of identifiers in the member list and a Member_Status for every Circle_Member that is one of `active`, `left`, or `removed`, THEN THE Security_Rules SHALL deny the write and SHALL leave any existing Circle document unchanged.
8. IF a request to read or write a Circle is made by a user who is not signed in, THEN THE Security_Rules SHALL deny the request.

### Requirement 14: Security rules for Circle tasks

**User Story:** As a user, I want a Circle's shared tasks to be readable and writable only by that Circle's members, so that only members can see and change the shared list.

#### Acceptance Criteria

1. IF a user requests to read a Circle_Task and that user is a Circle_Member of the Circle the Circle_Task belongs to, THEN THE Security_Rules SHALL permit the read.
2. IF a user requests to read a Circle_Task and that user is not a Circle_Member of the Circle the Circle_Task belongs to, THEN THE Security_Rules SHALL deny the read and SHALL NOT return any Circle_Task content.
3. IF a Circle_Member of a Circle requests to create a Circle_Task that records the Circle's identifier and records the requesting user as the Circle_Author, THEN THE Security_Rules SHALL permit the creation; otherwise THE Security_Rules SHALL deny the creation.
4. IF a Circle_Member of a Circle requests to change the Shared_Completion state of a Circle_Task in that Circle, THEN THE Security_Rules SHALL permit the write.
5. IF a user who is the Circle_Author of a Circle_Task or the Circle_Admin of the Circle requests to edit or delete that Circle_Task, THEN THE Security_Rules SHALL permit the write.
6. IF a Circle_Member who is neither the Circle_Author of a Circle_Task nor the Circle_Admin of the Circle requests to edit the title of or delete that Circle_Task, THEN THE Security_Rules SHALL deny the write and SHALL leave the existing Circle_Task unchanged.
7. IF a user who is not a Circle_Member of the Circle requests any write to a Circle_Task in that Circle, THEN THE Security_Rules SHALL deny the write and SHALL leave any existing Circle_Task unchanged.
8. IF a request to read or write a Circle_Task is made by a user who is not signed in, THEN THE Security_Rules SHALL deny the request.

### Requirement 15: Resilient handling of backend errors

**User Story:** As a user, I want the Circles feature to handle backend failures gracefully, so that it does not crash or get stuck when a Firestore operation is denied or fails.

#### Acceptance Criteria

1. IF a Firestore listener for Circles or Circle_Tasks reports an error, THEN THE Circle_Service SHALL log the error, SHALL retain any data for that data set that was loaded before the error, and SHALL display an error message identifying which data set (Circles or Circle tasks) could not be loaded, without terminating the app.
2. IF a Firestore write operation for any Circle or Circle_Task action is denied by Security_Rules or otherwise fails, THEN THE Circle_Service SHALL revert the associated Optimistic_UI change to the value displayed immediately before the action and SHALL display an error message indicating that the change could not be saved.
3. IF a Firestore operation is denied or fails, THEN THE Circle_Service SHALL handle the resulting error and SHALL NOT propagate an unhandled exception that terminates the app.
4. WHEN any Circle or Circle_Task operation fails, THE Circle_Service SHALL leave the affected local records in a state that matches the last backend-confirmed state and SHALL NOT alter records unaffected by the failed operation.

## Known Technical Constraints and Design Flags

These are recorded here as inputs to the design phase; they are not acceptance criteria.

- **Canonical `/circles` model parallels `/collaborativeTasks`.** The canonical Circle document at `/circles/{circleId}` deliberately mirrors the `/collaborativeTasks/{taskId}` shape — `adminUid`, `memberUids`, `memberUidMap`, `memberStates` (name + status per member), `name`, and UTC timestamps — so the existing own-slice / membership-gated / admin-gated security-rule idioms (`memberUidMap.{uid} == true` reads, admin-only metadata writes, the self-removal `affectedKeys().hasOnly([...])` idiom) apply with minimal new rule machinery. The design phase should confirm the field names align with the deployed rules' helper functions.

- **Circle_Task storage placement is undecided.** Circle_Tasks likely live either in a subcollection `/circles/{circleId}/tasks/{taskId}` (natural ownership, read rule can defer to the parent Circle's membership) or as a top-level `/circleTasks/{taskId}` collection carrying `circleId` plus a denormalized `memberUidMap` (so list queries and Security_Rules can check membership without a parent lookup, matching the `collaborativeTasks` query idiom). The trade-off (subcollection get-parent reads in rules vs. denormalized membership that must be kept in sync on every Circle membership change) is a design decision. This is the analog of how `collaborative-tasks` denormalized `memberUidMap` onto each task document for queryability.

- **Shared (global) completion is a deliberate departure from per-member completion.** `collaborative-tasks` tracks completion per Member in `memberStates[uid].isCompleted`. Circle_Tasks use a single Shared_Completion flag for the whole Circle with Completer attribution (Requirement 11). This is intentional for household/roommate lists and is flagged so the design does not accidentally reuse the per-member completion model. A consequence is that the Circle_Task security rule must permit *any* Circle_Member to write the completion field (Requirement 14.4), which is broader than the `collaborative-tasks` own-slice write and should be scoped carefully (for example, permit any member to write only the completion/Completer fields while restricting title edits and deletes to the author or admin).

- **Room mirroring and main-list visibility is a design decision.** Whether Circle_Tasks are mirrored into the existing Room `tasks` table (and therefore appear in the main Home_Task_List alongside personal and collaborative tasks) or are kept in a separate local representation scoped to the Circle_Detail_Screen is undecided. Mixing global Shared_Completion tasks into a list that otherwise uses per-user completion risks UI and data-model confusion; the design phase should decide whether Circle_Tasks surface in the main task list at all, and if so how their shared completion is represented there.

- **Max_Circle_Members.** The maximum is set to 50 Circle_Members including the Circle_Admin, matching the `collaborative-tasks` member cap (50 assignees + admin). The design phase should align the Security_Rules size cap (`memberUids.size() <= 50`) with this value.

- **Adding members is restricted to friends.** Requirement 4 requires added Circle_Members to be drawn from the Circle_Admin's existing friends (the reciprocal friend records from `collaborative-tasks` Requirement 17). Enforcing "is a friend" inside Security_Rules would require cross-document reads of the admin's friend list; the design phase should decide whether friendship is enforced client-side only, in rules, or both, consistent with how `collaborative-tasks` handled assignee membership.

- **Push notifications are out of scope.** Unlike `social-engagement`, this feature does not specify push notifications for Circle activity. Notifying members of new shared tasks or completions would require the server-side trigger flagged in `social-engagement` and is deferred.
