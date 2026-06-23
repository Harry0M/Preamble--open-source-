# Requirements Document

## Introduction

This feature rebuilds the friend-invite system and the collaborative-task system in the Preamble Android app (Kotlin, Jetpack Compose, Room local database, Firebase Firestore backend). An earlier implementation exists but is unreliable: it lacks responsive UI feedback, throws runtime exceptions, and produces Firestore `permission-denied` errors during normal use.

The goal is a fast, reliable, and secure collaborative task experience where users add friends by Preamble ID or invite link, assign tasks to one or more friends, and manage shared tasks with clear admin/member roles. All friend and task actions must provide optimistic UI feedback. A copy of each assigned task must propagate to every assignee. Task assignment from voice and notification input must be resolved by a dedicated AI phase that runs separately from normal task creation, without interrupting it.

This app has live production users on the Google Play Store. The Firestore security rules MUST be verified and, where necessary, redesigned so that existing (non-collaborative) functionality continues to work unchanged, while the collaborative features are protected against unauthorized access. Two rule sets exist today: the new rules (currently deployed, producing permission errors) and the older stable rules (working, but with an overly-permissive task-read flaw). Both are inputs to the security verification work in this feature.

This document defines WHAT the system must do. Technical design (data structures, function decomposition, exact rule expressions) is addressed in the design phase.

## Glossary

- **Collaboration_System**: The overall Android-side system covering friends, invites, and collaborative tasks. Acts as the umbrella system name when a more specific component is not required.
- **Friend_Service**: The component responsible for friend relationships (add, list, remove) and the local friend list state.
- **Invite_Service**: The component responsible for sending, receiving, accepting, and declining friend invitations.
- **Preamble_ID**: A short, normalized (uppercased, trimmed) public identifier that maps to exactly one user account, stored in the public `preambleIds` directory.
- **Invite_Link**: A shareable URL of the form `https://preamble.theblankstate.com/invite/{Preamble_ID}` that, when opened in the app, pre-fills a friend request to the link owner.
- **Friend_Request**: A pending invitation record created by a sender and stored under the recipient's account until accepted or declined.
- **Collaborative_Task**: A task shared between two or more users, stored as a single canonical Firestore document at `/collaborativeTasks/{taskId}` with `schemaVersion == 2`.
- **Admin**: The single user who owns a Collaborative_Task and can edit its details, add or remove members, and transfer ownership. Identified by `adminUid`.
- **Member**: Any user who is part of a Collaborative_Task, including the Admin. Identified within `memberUids`.
- **Assignee**: A Member, other than the Admin, to whom the task is assigned. Identified within `assigneeUids`.
- **Member_State**: The per-member record (`memberStates[uid]`) tracking that member's `status`, `role`, completion flag, and timestamps.
- **Member_Status**: One of `pending`, `accepted`, `completed`, `declined`, `left`, `removed`.
- **Task_Sync_Engine**: The component that mirrors the canonical Collaborative_Task document into each member's local Room database and reflects local member changes back to Firestore.
- **Task_Create_Sheet**: The bottom-sheet UI used to manually create a task, which includes an option to assign the task to friends.
- **Assignee_Resolver**: The dedicated AI phase that detects intended assignees from natural-language task input (voice or notification) and maps them to friends, running separately from task creation.
- **Optimistic_UI**: A UI pattern where the result of a user action is reflected in the interface immediately, before the backend confirms it, and is reverted only if the backend operation fails.
- **Security_Rules**: The Firestore security rules governing read/write access to all collections.
- **Legacy_Task**: A non-collaborative task stored at the root `/tasks/{taskId}` collection, used by the currently published Play Store app.
- **Live_User**: An existing production user whose app version may predate this feature and who may use only Legacy_Task functionality.
- **Transfer_Ownership**: The operation by which an Admin assigns the Admin role of a Collaborative_Task to another Member.
- **Self_Removal**: The operation by which a non-admin Member removes themselves from a Collaborative_Task.

## Requirements

### Requirement 1: Discover and invite friends by Preamble ID

**User Story:** As a user, I want to send a friend request using another person's Preamble ID, so that I can connect with people I know.

#### Acceptance Criteria

1. WHEN a user submits a Preamble_ID to add a friend, THE Invite_Service SHALL normalize the Preamble_ID by removing all leading and trailing whitespace and converting all alphabetic characters to uppercase before performing the lookup.
2. IF the normalized Preamble_ID is empty or contains only whitespace, THEN THE Invite_Service SHALL reject the request, SHALL display a message indicating that a Preamble_ID is required, and SHALL NOT perform a directory lookup or create a Friend_Request.
3. WHEN a normalized Preamble_ID resolves to exactly one existing user account in the public Preamble_ID directory, THE Invite_Service SHALL create exactly one Friend_Request stored under the target user's account.
4. IF a submitted Preamble_ID does not resolve to any user account in the public Preamble_ID directory, THEN THE Invite_Service SHALL display a message indicating that no user exists with that Preamble_ID and SHALL NOT create a Friend_Request.
5. IF a user submits their own Preamble_ID, THEN THE Invite_Service SHALL reject the request and SHALL display a message indicating that a user cannot invite themselves.
6. IF a user submits a Preamble_ID belonging to an existing friend, THEN THE Invite_Service SHALL reject the request and SHALL display a message indicating that the two users are already friends.
7. IF a pending Friend_Request from the user to the same target user already exists, THEN THE Invite_Service SHALL reject the request, SHALL display a message indicating that a request to that user is already pending, and SHALL NOT create a duplicate Friend_Request.
8. THE Friend_Request SHALL include the sender's user identifier, the sender's display name, and the sender's Preamble_ID.

### Requirement 2: Invite via shareable link

**User Story:** As a user, I want to share an invite link, so that a friend can open the app and request to connect with me without typing my Preamble_ID.

#### Acceptance Criteria

1. WHEN a user chooses to share their invite, THE Friend_Service SHALL produce an Invite_Link of the defined Invite_Link form that contains the user's own normalized (trimmed and uppercased) Preamble_ID.
2. WHEN the app is opened from an Invite_Link that carries a well-formed Preamble_ID, THE Invite_Service SHALL pre-fill the add-friend flow with the normalized Preamble_ID carried by the Invite_Link.
3. WHEN the add-friend flow is pre-filled from an Invite_Link, THE Collaboration_System SHALL apply the same validation rules defined in Requirement 1 before sending a Friend_Request.
4. IF the app is opened from an Invite_Link that does not carry a well-formed Preamble_ID, THEN THE Invite_Service SHALL NOT pre-fill the add-friend flow and SHALL display a message indicating that the invite link is invalid.

### Requirement 3: Accept or decline incoming friend requests with optimistic UI

**User Story:** As a user, I want incoming friend requests to update instantly when I accept or decline them, so that the app feels responsive.

#### Acceptance Criteria

1. WHILE a user is signed in, THE Invite_Service SHALL display the list of incoming Friend_Requests addressed to that user, where each displayed entry shows the sender's display name and Preamble_ID.
2. WHEN no incoming Friend_Requests are addressed to the signed-in user, THE Invite_Service SHALL display an empty-state indication that there are no pending incoming requests.
3. WHEN a user accepts a Friend_Request, THE Invite_Service SHALL remove the request from the displayed incoming list and add the sender to the displayed friend list within 200 milliseconds of the user action and before the backend operation completes.
4. WHEN a user accepts a Friend_Request, THE Friend_Service SHALL establish a reciprocal friend relationship recorded under both the accepting user's account and the sender's account.
5. WHEN a user declines a Friend_Request, THE Invite_Service SHALL remove the request from the displayed incoming list within 200 milliseconds of the user action and before the backend operation completes.
6. IF the backend operation for accepting a Friend_Request fails, THEN THE Invite_Service SHALL restore the incoming-request list and friend list to the exact state held before the accept action and SHALL display an error message indicating that the request could not be accepted.
7. IF the backend operation for declining a Friend_Request fails, THEN THE Invite_Service SHALL restore the incoming-request list to the exact state held before the decline action and SHALL display an error message indicating that the request could not be declined.

### Requirement 4: Remove a friend with optimistic UI

**User Story:** As a user, I want removing a friend to reflect immediately in my friend list, so that I get instant feedback.

#### Acceptance Criteria

1. WHEN a user removes a friend who shares no Collaborative_Task with the user, THE Friend_Service SHALL remove the friend from the displayed friend list within 200 milliseconds of the removal action and before the backend operation completes.
2. WHEN a friend is removed, THE Friend_Service SHALL delete the friend relationship record under the removing user's account and the reciprocal friend relationship record under the removed friend's account.
3. IF the backend operation for removing a friend fails, THEN THE Friend_Service SHALL restore the friend to its previous position in the displayed friend list and SHALL display an error message indicating that the removal failed and the friend was not removed.
4. IF the backend operation for removing a friend does not complete within 10 seconds, THEN THE Friend_Service SHALL treat the operation as failed, SHALL restore the friend to its previous position in the displayed friend list, and SHALL display an error message indicating that the removal could not be completed.

### Requirement 5: Warn and resolve shared tasks when removing a friend

**User Story:** As a user, I want to be warned when I remove a friend with whom I still share tasks, so that I can decide what happens to those shared tasks before the friendship ends.

#### Acceptance Criteria

1. WHEN a user initiates removal of a friend, THE Collaboration_System SHALL, before deleting any friend relationship record, determine the set of Collaborative_Tasks shared with that friend, partitioned into tasks the user administers as Admin and tasks in which the user is a non-admin Member.
2. IF the friend being removed shares one or more Collaborative_Tasks with the user, THEN THE Collaboration_System SHALL, before deleting any friend relationship record, display a warning that identifies each affected shared task by its title and by the user's role (Admin or Member) in that task.
3. WHERE the user is the Admin of one or more affected shared tasks, THE Collaboration_System SHALL offer the user the option to Transfer_Ownership (as defined in Requirement 11) of each such task before any friend relationship record is deleted.
4. WHERE the user is a non-admin Member of one or more affected shared tasks, THE Collaboration_System SHALL offer the user the option to perform Self_Removal (as defined in Requirement 12) from each such task before any friend relationship record is deleted.
5. IF the user dismisses or cancels the warning, THEN THE Collaboration_System SHALL abort the removal, retain the friend relationship record, and leave every affected shared task unchanged.
6. IF the user confirms removal while any admin-owned affected shared task still has no completed Transfer_Ownership, THEN THE Collaboration_System SHALL block the removal, retain the friend relationship record, leave every affected shared task unchanged, and display an error message identifying each unresolved admin-owned task by title.
7. WHEN the user confirms resolution and every affected shared task has a chosen action (Transfer_Ownership for admin-owned tasks or Self_Removal for member tasks), THE Collaboration_System SHALL apply each chosen action to its affected task and SHALL delete the friend relationship record only after all chosen actions have succeeded.
8. IF applying any chosen action to an affected shared task fails during friend removal, THEN THE Collaboration_System SHALL retain the friend relationship record, restore the friend to the displayed friend list, leave every affected shared task in its pre-attempt state, and display an error message indicating that the removal could not be completed.

### Requirement 6: Assign a task to friends from the task-create sheet

**User Story:** As a user, I want to assign a task to one or more friends while creating it, so that I can delegate or collaborate from the start.

#### Acceptance Criteria

1. THE Task_Create_Sheet SHALL provide an option to assign the task being created to between 1 and 50 friends selected from the user's existing friend list.
2. WHEN a user confirms creation of a task with one or more assigned friends, THE Collaboration_System SHALL first save the task to the user's own local database as the Admin's copy within 1 second.
3. WHEN the user's own copy of an assigned task is saved, THE Collaboration_System SHALL asynchronously create a single canonical Collaborative_Task document that records the creator as both Admin and Member, records each assigned friend as a Member with an initial Member_Status of `pending`, and includes a copy of the task content.
4. WHERE no friend is assigned during task creation, THE Collaboration_System SHALL create a normal non-collaborative task and SHALL NOT create a Collaborative_Task document.
5. THE Collaboration_System SHALL limit the number of Assignees on a single Collaborative_Task to a maximum of 50.
6. IF an assignment request specifies more than 50 Assignees, THEN THE Collaboration_System SHALL reject the request, SHALL NOT create a Collaborative_Task document, and SHALL display an error message indicating that the maximum number of assignees has been exceeded.
7. WHILE a task is being created with assigned friends, THE Collaboration_System SHALL complete the user's own local copy within 1 second and SHALL NOT block or delay that local-save flow while the canonical Collaborative_Task document is created asynchronously.
8. IF creation of the canonical Collaborative_Task document fails after the user's local copy has been saved, THEN THE Collaboration_System SHALL retain the user's local copy and SHALL display an error indication that the collaborative assignment could not be completed.

### Requirement 7: Propagate an exact copy of the task to each assignee

**User Story:** As an assignee, I want to receive an exact copy of a task assigned to me, so that I see the same details the admin entered.

#### Acceptance Criteria

1. WHEN a Collaborative_Task is created, THE Task_Sync_Engine SHALL make the task content available to every assigned Member through the canonical Collaborative_Task document within 5 seconds of the document write completing under normal network connectivity.
2. THE Collaborative_Task copy SHALL include the task title, description, tags, priority, deadline time, recurrence settings, and subtasks, and the value of each of these fields in the copy SHALL equal the value of the corresponding field in the Admin's copy at the time the task is confirmed and saved.
3. WHERE task attributes are produced asynchronously by AI parsing after initial entry, THE Collaboration_System SHALL include the finalized attributes in the Collaborative_Task copy at the time the task is confirmed and saved.
4. IF AI parsing of task attributes has not finalized at the time the task is confirmed and saved, THEN THE Collaboration_System SHALL include the attributes as entered by the Admin in the Collaborative_Task copy and SHALL include the finalized AI-derived attributes in a subsequent update to the canonical Collaborative_Task document once parsing completes.
5. WHEN the canonical Collaborative_Task document changes, THE Task_Sync_Engine SHALL update each affected Member's local copy to reflect the change within 5 seconds of receiving the change under normal network connectivity.
6. IF updating a Member's local copy from a change to the canonical Collaborative_Task document fails, THEN THE Task_Sync_Engine SHALL retain that Member's last successfully synced local copy and SHALL display a message indicating that the shared task could not be updated.
7. THE Collaborative_Task copy SHALL NOT carry the Admin's per-user completion state as the shared completion state, and per-member completion SHALL be tracked individually per Member.

### Requirement 8: Collaborative task data model

**User Story:** As a user collaborating on tasks, I want each shared task to track its members, admin, and completion progress, so that everyone's participation is represented.

#### Acceptance Criteria

1. THE Collaborative_Task SHALL record exactly one Admin identified by a single user identifier.
2. THE Collaborative_Task SHALL record the complete list of Member identifiers, the list SHALL include the Admin's identifier, SHALL contain no duplicate identifiers, and SHALL contain at most one more identifier than the Assignee maximum defined in Requirement 6 (the Assignee maximum plus the Admin).
3. THE Collaborative_Task SHALL record the list of Assignee identifiers, where every Assignee identifier is also present in the Member list, the Admin's identifier is not present in the Assignee list, and the Assignee list contains no duplicate identifiers.
4. THE Collaborative_Task SHALL record exactly one Member_State for each identifier in the Member list and SHALL NOT record a Member_State for any identifier absent from the Member list, where each Member_State includes a Member_Status whose value is exactly one of `pending`, `accepted`, `completed`, `declined`, `left`, or `removed`, and a completion flag whose value is exactly one of boolean true or boolean false.
5. THE Collaborative_Task SHALL represent each Member as completed when that Member's completion flag equals true and as not completed when that Member's completion flag equals false, through the per-member completion flag in that Member's Member_State.
6. WHEN a single Member's completion state changes, THE Collaboration_System SHALL update only that Member's Member_State and SHALL leave every other Member's Member_Status and completion flag byte-for-byte identical to their values immediately before the change.

### Requirement 9: Resolve assignees from natural language as a separate AI phase

**User Story:** As a user creating a task by voice or from a notification, I want the app to detect who I want to assign the task to and assign it to that friend, so that I can delegate hands-free.

#### Acceptance Criteria

1. WHEN a task is created from voice input or notification input, THE Collaboration_System SHALL complete the normal task-creation flow and save the user's own copy without waiting for assignee resolution.
2. AFTER the user's own copy of a voice-created or notification-created task is saved, THE Assignee_Resolver SHALL analyze the natural-language input to detect intended assignees in a separate phase that completes within 30 seconds of the user's own copy being saved under normal network connectivity.
3. WHEN the Assignee_Resolver detects one or more intended assignees that each uniquely match exactly one existing friend, THE Collaboration_System SHALL assign the task to each matched friend by creating or updating the Collaborative_Task.
4. WHEN the Collaboration_System assigns a voice-created or notification-created task to one or more matched friends, THE Collaboration_System SHALL update the displayed copy of that task to reflect its collaborative status, including the assigned Members, within 5 seconds of the assignment completing under normal network connectivity.
5. IF the Assignee_Resolver detects no intended assignee, THEN THE Collaboration_System SHALL leave the task as a normal non-collaborative task.
6. IF the Assignee_Resolver detects an intended assignee that does not match any existing friend, THEN THE Collaboration_System SHALL leave the task as a normal non-collaborative task and SHALL NOT create a Collaborative_Task.
7. IF the Assignee_Resolver detects an intended assignee whose name matches more than one existing friend, THEN THE Collaboration_System SHALL leave the task as a normal non-collaborative task, SHALL NOT create a Collaborative_Task, and SHALL display a message indicating that the intended assignee was ambiguous.
8. IF the Assignee_Resolver fails or does not complete within 30 seconds, THEN THE Collaboration_System SHALL retain the user's already-saved own copy unchanged, SHALL leave the task as a normal non-collaborative task, and SHALL display a message indicating that assignee resolution could not be completed.
9. THE Assignee_Resolver SHALL run client-side or through a dedicated path that does not depend on the removed `aiResolveAssignees` Cloud Function.

### Requirement 10: Member acceptance and completion

**User Story:** As an assignee, I want to accept, decline, and complete tasks assigned to me, so that I control my participation and progress.

#### Acceptance Criteria

1. WHEN an Assignee accepts an assigned Collaborative_Task for which that Assignee's Member_Status is `pending`, THE Collaboration_System SHALL set that Assignee's Member_Status to `accepted` and SHALL reflect the acceptance in the Assignee's task list within 200 ms and before the backend operation completes.
2. WHEN an Assignee declines an assigned Collaborative_Task for which that Assignee's Member_Status is `pending`, THE Collaboration_System SHALL set that Assignee's Member_Status to `declined` and SHALL remove the task from the Assignee's incoming list within 200 ms and before the backend operation completes.
3. IF an Assignee attempts to accept or decline an assigned Collaborative_Task for which that Assignee's Member_Status is not `pending`, THEN THE Collaboration_System SHALL reject the operation, SHALL leave that Assignee's Member_Status unchanged, and SHALL display an error message indicating the task is not in a pending state.
4. WHEN a Member whose Member_Status is `accepted` marks an assigned Collaborative_Task as completed, THE Collaboration_System SHALL set that Member's Member_Status to `completed`, set that Member's completion flag to true, and record a completion timestamp in UTC.
5. IF a Member whose Member_Status is not `accepted` attempts to mark an assigned Collaborative_Task as completed, THEN THE Collaboration_System SHALL reject the operation, SHALL leave that Member's Member_Status and completion flag unchanged, and SHALL display an error message indicating the task must be accepted before completion.
6. WHEN a Member updates the shared subtasks of a Collaborative_Task, THE Collaboration_System SHALL persist the updated subtasks to the canonical Collaborative_Task document.
7. IF a backend operation for accepting, declining, completing, or updating the subtasks of an assigned Collaborative_Task fails, THEN THE Collaboration_System SHALL restore the prior local state for that task and SHALL display an error message indicating the operation failed.

### Requirement 11: Admin management of members

**User Story:** As an admin, I want to add members, remove members, and transfer ownership of a task I own, so that I can manage who collaborates on it.

#### Acceptance Criteria

1. WHERE the signed-in user is the Admin of a Collaborative_Task, THE Collaboration_System SHALL allow the Admin to remove any Member other than the Admin from the task.
2. WHEN an Admin removes a Member, THE Collaboration_System SHALL remove that Member from the Member list and Assignee list, set that Member's Member_Status to `removed`, and reflect the removal in the UI within 200 ms and before the backend operation completes.
3. WHERE the signed-in user is the Admin of a Collaborative_Task, THE Collaboration_System SHALL allow the Admin to Transfer_Ownership to any Member of that task other than the current Admin.
4. WHEN an Admin transfers ownership, THE Collaboration_System SHALL set the chosen Member as the new Admin, SHALL retain the previous Admin as a Member with Member_Status `accepted`, and SHALL reflect the new Admin in the UI within 200 ms and before the backend operation completes.
5. IF an Admin attempts to Transfer_Ownership to a user who is not a Member of the task, THEN THE Collaboration_System SHALL reject the operation, SHALL leave the Admin, Member, and Assignee records unchanged, and SHALL display an error message.
6. IF a backend operation for adding a Member, removing a Member, or transferring ownership fails, THEN THE Collaboration_System SHALL restore the prior local state and SHALL display an error message.
7. WHERE the signed-in user is the Admin of a Collaborative_Task, THE Collaboration_System SHALL allow the Admin to add an existing friend to the task as a Member with an initial Member_Status of `pending`.
8. WHEN an Admin adds a Member, THE Collaboration_System SHALL reflect the added Member in the UI within 200 ms and before the backend operation completes.
9. IF an Admin attempts to add a Member that would cause the number of Assignees to exceed the maximum defined in Requirement 6, THEN THE Collaboration_System SHALL reject the operation, SHALL leave the Member and Assignee records unchanged, and SHALL display an error message indicating that the maximum number of assignees has been exceeded.

### Requirement 12: Member self-removal

**User Story:** As a non-admin member, I want to remove myself from a shared task, so that I can stop participating without needing the admin.

#### Acceptance Criteria

1. WHERE the signed-in user is a non-admin Member of a Collaborative_Task, THE Collaboration_System SHALL present a Self_Removal control for that task and SHALL require the user to confirm the Self_Removal before it is performed.
2. WHEN a non-admin Member confirms Self_Removal, THE Collaboration_System SHALL remove that Member from the Member list and Assignee list, set that Member's Member_Status to `left`, and immediately remove the task from the user's local task list before the backend operation completes.
3. WHEN a non-admin Member confirms Self_Removal, THE Task_Sync_Engine SHALL apply the same removal to the canonical Collaborative_Task document so that the user is removed from the Member list, the Assignee list, and the member map, and that user's Member_Status is set to `left`.
4. IF the signed-in user is the Admin of a Collaborative_Task, THEN THE Collaboration_System SHALL NOT present a direct Self_Removal control, SHALL reject any Self_Removal request, and SHALL display a message indicating that the Admin must Transfer_Ownership before leaving the task.
5. IF a backend operation for Self_Removal fails, THEN THE Collaboration_System SHALL restore the task to the user's local task list, restore that Member's prior Member_Status and membership in the Member list and Assignee list, and SHALL display an error message indicating that Self_Removal did not complete.

### Requirement 13: Collaborator view in the task detail sheet

**User Story:** As a user viewing a shared task, I want to see who is collaborating on it, so that I know who else is involved and their progress.

#### Acceptance Criteria

1. WHERE a task being viewed is a Collaborative_Task, THE Task_Create_Sheet detail view SHALL display, identified by display name, the list of Members whose Member_Status is one of `pending`, `accepted`, or `completed`, and SHALL exclude Members whose Member_Status is `declined`, `left`, or `removed`.
2. WHERE a task being viewed is a Collaborative_Task, THE Task_Create_Sheet detail view SHALL indicate, among the displayed Members, which Member is the Admin.
3. WHERE a task being viewed is a Collaborative_Task, THE Task_Create_Sheet detail view SHALL indicate, for each displayed Member, whether that Member's per-member completion flag is completed or not completed.
4. WHERE a task being viewed is a Collaborative_Task, THE Task_Create_Sheet detail view SHALL indicate, for each displayed Member, whether that Member's acceptance status is `pending` or `accepted`, presented separately from the Member's completion indication.
5. WHERE a task being viewed is a Collaborative_Task AND the signed-in user is the Admin, THE Task_Create_Sheet detail view SHALL present, for each displayed non-admin Member, a control to remove that Member and a control to Transfer_Ownership to that Member, regardless of how many Members remain on the task.
6. WHERE a task being viewed is a Collaborative_Task AND the signed-in user is a non-admin Member, THE Task_Create_Sheet detail view SHALL present a control to perform Self_Removal.
7. WHERE a task being viewed is a Collaborative_Task AND the signed-in user is the Admin, THE Task_Create_Sheet detail view SHALL require the Admin to Transfer_Ownership before leaving the task and SHALL NOT present a direct Self_Removal control to the Admin.

### Requirement 14: Resilient handling of backend errors

**User Story:** As a user, I want the app to handle backend failures gracefully, so that it does not crash or get stuck when a Firestore operation is denied or fails.

#### Acceptance Criteria

1. IF a Firestore listener for friends, invites, or Collaborative_Tasks reports an error, THEN THE Collaboration_System SHALL log the error, SHALL retain any data for that data set that was loaded before the error, and SHALL display an error message identifying which data set (friends, invites, or Collaborative_Tasks) could not be loaded, without terminating the app.
2. IF a Firestore write operation is denied by Security_Rules or otherwise fails, THEN THE Collaboration_System SHALL revert any associated Optimistic_UI change to the value displayed immediately before the action and SHALL display an error message indicating that the change could not be saved.
3. WHEN any collaborative or friend operation fails, including a Firestore listener error, THE Collaboration_System SHALL leave the affected local records in a state that matches the last backend-confirmed state and SHALL NOT alter records unaffected by the failed operation.
4. IF a Firestore operation is denied or fails, THEN THE Collaboration_System SHALL handle the resulting error and SHALL NOT propagate an unhandled exception that terminates the app.
5. IF a Firestore write operation does not complete within 30 seconds, THEN THE Collaboration_System SHALL treat the operation as failed, SHALL revert any associated Optimistic_UI change to the value displayed immediately before the action, and SHALL display an error message indicating that the change could not be saved.

### Requirement 15: Backward-compatible security rules for legacy task functionality

**User Story:** As a live production user on an older app version, I want my existing tasks and data to keep working, so that an update to the rules does not break my app.

#### Acceptance Criteria

1. WHERE the requesting user is authenticated, THE Security_Rules SHALL allow that user to read, create, update, and delete a Legacy_Task at `/tasks/{taskId}` only when the document's stored owner identifier equals the requesting user's authenticated user identifier.
2. IF a request to read, update, or delete a Legacy_Task at `/tasks/{taskId}` is made by a user whose authenticated user identifier does not equal the document's stored owner identifier, THEN THE Security_Rules SHALL deny the operation.
3. IF a request to access a Legacy_Task at `/tasks/{taskId}` is made by an unauthenticated requester, THEN THE Security_Rules SHALL deny the operation.
4. WHEN a Legacy_Task is created or updated at `/tasks/{taskId}`, THE Security_Rules SHALL require that the resulting document's stored owner identifier equals the requesting user's authenticated user identifier, and SHALL deny the write otherwise.
5. THE Security_Rules SHALL preserve, for each currently supported per-user collection (user profile, tag overrides, assigned-tasks status flags, AI chat, AI memory, AI credits, broadcasts, and problem reports), the same set of read and write operations that the currently published app version is permitted to perform, so that no operation permitted under the prior stable rules is denied under the proposed rules.
6. THE Security_Rules SHALL deny all reads and writes to any path not explicitly permitted by the defined rules.
7. WHEN the Security_Rules are evaluated against every read and write operation performed by the currently published app version, THE verification SHALL confirm that no such operation permitted under the prior stable rules is denied by the proposed rules, and SHALL record any operation that is newly denied.

### Requirement 16: Security rules for collaborative tasks

**User Story:** As a user, I want shared tasks to be accessible only to their members, so that my collaborative data stays private and protected.

#### Acceptance Criteria

1. IF a user requests to read a Collaborative_Task and that user's identifier is present in the task's member list (`memberUids`), THEN THE Security_Rules SHALL permit the read.
2. IF a user requests to read a Collaborative_Task and that user's identifier is not present in the task's member list (`memberUids`), THEN THE Security_Rules SHALL deny the read and SHALL NOT return any task content.
3. IF a user requests to create a Collaborative_Task and the request records the creating user as both the Admin (`adminUid`) and a Member (present in `memberUids`), THEN THE Security_Rules SHALL permit the creation; otherwise THE Security_Rules SHALL deny the creation.
4. IF a user other than the Admin requests to edit shared task details, add a Member, remove a Member, or transfer ownership, THEN THE Security_Rules SHALL deny the write and SHALL leave the existing Collaborative_Task document unchanged.
5. IF a non-admin Member requests to update only that member's own Member_State (`memberStates[uid]` where `uid` equals the requesting user), THEN THE Security_Rules SHALL permit the update.
6. IF a non-admin Member requests to change another member's Member_State, THEN THE Security_Rules SHALL deny the write and SHALL leave the existing Member_State records unchanged.
7. IF a non-admin Member requests a Self_Removal that removes only that member's identifier from the member list, the Assignee list, and the member map and sets only that member's Member_Status to `left`, THEN THE Security_Rules SHALL permit the write; IF the request alters any other member's records, THEN THE Security_Rules SHALL deny the write.
8. IF a user other than the Admin requests to delete a Collaborative_Task, THEN THE Security_Rules SHALL deny the deletion and SHALL leave the Collaborative_Task document unchanged.
9. IF any user requests a write to the deprecated per-user collaborative task copy path under `/users/{uid}/collaborativeTasks`, THEN THE Security_Rules SHALL deny the write.
10. IF a create or update request for a Collaborative_Task does not satisfy all required schema conditions, namely `schemaVersion == 2`, a member map whose keys are exactly equal to the set of identifiers in the member list, and a Member_Status for every member that is one of `pending`, `accepted`, `completed`, `declined`, `left`, or `removed`, THEN THE Security_Rules SHALL deny the write and SHALL leave any existing Collaborative_Task document unchanged.

### Requirement 17: Friend and invite security rules

**User Story:** As a user, I want my friend list and invitations to be writable only through valid, authorized operations, so that no one can tamper with my relationships.

#### Acceptance Criteria

1. THE Security_Rules SHALL allow a signed-in user to read, create, update, and delete friend records and invite records stored under that same user's own account, and SHALL deny any read or write to friend records and invite records under another user's account except for the reciprocal operations defined in criteria 2 through 4.
2. THE Security_Rules SHALL allow a signed-in user to create a Friend_Request under the target user's account only when the Friend_Request's recorded sender identifier equals the requesting user's authenticated identifier, and SHALL deny creation otherwise.
3. THE Security_Rules SHALL allow a signed-in user to create a friend record under another user's account only as part of accepting an invitation, and only when the created friend record represents the reciprocal relationship between the requesting user and that other user, and SHALL deny such creation otherwise.
4. THE Security_Rules SHALL allow a signed-in user to delete a friend record under another user's account only when that friend record's identifier matches the requesting user's own identifier, so that friend removal can clear the requesting user's side of the relationship, and SHALL deny deletion of any other friend record under another user's account.
5. THE Security_Rules SHALL allow any signed-in user to read the public Preamble_ID directory.
6. THE Security_Rules SHALL allow a signed-in user to create, update, or delete a Preamble_ID directory entry only when that entry maps to the requesting user's own identifier, and SHALL deny these operations on any directory entry that maps to another user's identifier.
7. IF a request to read or write a friend record, invite record, Friend_Request, or Preamble_ID directory entry is made by a user who is not signed in, THEN THE Security_Rules SHALL deny the request.

### Requirement 18: Verification of security-rule safety and consistency

**User Story:** As the app owner, I want the security rules verified for safety and backward compatibility, so that I can deploy them with confidence that live users are not affected and the new features are protected.

#### Acceptance Criteria

1. THE verification SHALL compare the currently deployed Security_Rules against the prior stable Security_Rules for each of the following path-and-operation scopes — Legacy_Task paths, Collaborative_Task paths, the per-user collections defined in Requirement 15, and the friend, invite, and preambleIds paths defined in Requirement 17 — and SHALL document, for every read and write operation in each scope, every behavioral difference affecting live or collaborative users.
2. THE verification SHALL confirm that the proposed Security_Rules deny a read of any task to any actor who is neither the task owner nor a Member of that task, thereby confirming the prior overly-permissive behavior in which any authenticated user could read any task is absent.
3. WHEN an actor holds a role required by Requirements 6 through 13 for an operation — Admin, Member, or Assignee as defined for that operation — THE verification SHALL confirm that the proposed Security_Rules permit that collaborative read or write operation for that actor.
4. WHEN an actor does not hold any role required by Requirements 6 through 13 for an operation — that is, the actor is neither Admin, Member, nor Assignee as required for that operation — THE verification SHALL confirm that the proposed Security_Rules deny that collaborative read or write operation for that actor.
5. THE verification SHALL confirm that the proposed Security_Rules preserve the prior stable read and write permissions for Legacy_Task paths and for the per-user collections defined in Requirement 15, such that any actor authorized under the prior stable rules remains authorized under the proposed rules for those paths.
6. IF the verification identifies an operation that the app performs but the proposed Security_Rules deny, THEN THE verification SHALL record a gap entry containing the path, the operation, the actor role, and the expected outcome, so that either the rules or the app behavior can be corrected before deployment.
7. WHEN the verification completes each check defined in criteria 1 through 6, THE verification SHALL record a documented pass or fail result for that check.
---

## Requirements (Iteration 2: UI Extensions)

The following requirements extend the implemented feature with four user-interface improvements. They reuse the components, defined terms, and behaviors established in Requirements 1–18 (optimistic UI, Member_Status transition guards, the collaborator view, and the canonical Collaborative_Task document) and add the terms below. Existing requirements are unchanged.

### Additional Glossary (Iteration 2)

- **Home_Task_List**: The primary task list shown on the app's home screen, displaying the signed-in user's tasks.
- **Incoming_Section**: A grouping positioned at the top of the Home_Task_List that contains Collaborative_Tasks for which the signed-in user's own Member_Status is `pending`.
- **Task_Detail_Sheet**: The bottom-sheet UI that opens when a user taps a task to view its details, distinct from the edit sheet used to modify a task.
- **Avatar_Cluster**: A compact visual grouping of Member avatar or initials indicators displayed on a task row.
- **Member_Preview_Count**: The maximum number of Members displayed in the collapsed preview state of the collaborator list, equal to 3.

### Requirement 19: Incoming shared-task requests in the home task list

**User Story:** As a user, I want pending shared-task assignments addressed to me to appear at the top of my home task list with inline accept and decline controls, so that I can respond to them without leaving the home screen.

#### Acceptance Criteria

1. WHERE the signed-in user has one or more Collaborative_Tasks for which that user's own Member_Status is `pending`, THE Home_Task_List SHALL display those Collaborative_Tasks grouped under a dedicated Incoming_Section whose section header is positioned at the top of the Home_Task_List, above all other tasks.
2. THE Home_Task_List SHALL present, for each Collaborative_Task displayed in the Incoming_Section, an inline Accept control and an inline Decline control.
3. WHEN a user activates the Accept control for a Collaborative_Task in the Incoming_Section for which the user's own Member_Status is `pending`, THE Collaboration_System SHALL set that user's Member_Status to `accepted` and SHALL reflect the acceptance in the Home_Task_List within 200 milliseconds of the action and before the backend operation completes.
4. WHEN a user accepts a Collaborative_Task from the Incoming_Section, THE Home_Task_List SHALL remove that Collaborative_Task from the Incoming_Section and SHALL display it as a normal task in the Home_Task_List.
5. WHEN a user activates the Decline control for a Collaborative_Task in the Incoming_Section for which the user's own Member_Status is `pending`, THE Collaboration_System SHALL set that user's Member_Status to `declined` and SHALL remove that Collaborative_Task from the Incoming_Section and from the Home_Task_List within 200 milliseconds of the action and before the backend operation completes.
6. IF a user attempts to accept or decline a Collaborative_Task from the Incoming_Section for which the user's own Member_Status is not `pending`, THEN THE Collaboration_System SHALL reject the operation, SHALL leave that user's Member_Status unchanged, and SHALL display an error message indicating the task is not in a pending state.
7. IF the backend operation for accepting or declining a Collaborative_Task from the Incoming_Section fails, THEN THE Collaboration_System SHALL restore the Home_Task_List and the Incoming_Section to the exact state held before the action and SHALL display an error message indicating the operation failed.
8. WHERE the signed-in user has no Collaborative_Task for which that user's own Member_Status is `pending`, THE Home_Task_List SHALL NOT display the Incoming_Section header.
9. THE Collaboration_System SHALL continue to display the existing workspace incoming shared-task list and SHALL preserve its accept and decline behavior unchanged.

### Requirement 20: Collaborator status visible in the tap-to-open detail sheet

**User Story:** As a user, I want to see collaborator acceptance and completion status when I tap a shared task to view its details, so that I can see who is involved without opening the edit sheet.

#### Acceptance Criteria

1. WHERE a task opened in the Task_Detail_Sheet is a Collaborative_Task, THE Task_Detail_Sheet SHALL display, identified by display name, the Members whose Member_Status is one of `pending`, `accepted`, or `completed`, and SHALL exclude Members whose Member_Status is `declined`, `left`, or `removed`.
2. WHERE a task opened in the Task_Detail_Sheet is a Collaborative_Task, THE Task_Detail_Sheet SHALL indicate, among the displayed Members, which Member is the Admin.
3. WHERE a task opened in the Task_Detail_Sheet is a Collaborative_Task, THE Task_Detail_Sheet SHALL indicate, for each displayed Member, whether that Member's per-member completion flag is completed or not completed.
4. WHERE a task opened in the Task_Detail_Sheet is a Collaborative_Task, THE Task_Detail_Sheet SHALL indicate, for each displayed Member, whether that Member's acceptance status is `pending` or `accepted`, presented separately from the Member's completion indication.

> Design-phase note (not an acceptance criterion): Requirement 13 specifies the collaborator view for the "Task_Create_Sheet detail view". The design phase MUST verify whether the tap-to-open Task_Detail_Sheet is a different composable than the surface where the Requirement 13 collaborator view currently renders, and reconcile the two so the collaborator status is shown in the tap-to-open detail sheet.

### Requirement 21: Scalable collaborator list in the detail sheet

**User Story:** As a user viewing a shared task with many collaborators, I want the collaborator list to be collapsible, so that the detail sheet stays usable and does not overflow when the task has many members.

#### Acceptance Criteria

1. WHERE a Collaborative_Task opened in the Task_Detail_Sheet has more displayed Members than the Member_Preview_Count, THE Task_Detail_Sheet SHALL present the collaborator list in a collapsible form whose default state shows at most the Member_Preview_Count of displayed Members together with a control to expand the full list.
2. WHEN a user activates the expand control on the collaborator list, THE Task_Detail_Sheet SHALL display every displayed Member of the Collaborative_Task whose Member_Status is one of `pending`, `accepted`, or `completed`.
3. WHEN a user activates the collapse control on the collaborator list, THE Task_Detail_Sheet SHALL return the collaborator list to its default state showing at most the Member_Preview_Count of displayed Members.
4. WHERE a Collaborative_Task opened in the Task_Detail_Sheet has a number of displayed Members at or below the Member_Preview_Count, THE Task_Detail_Sheet SHALL display the full collaborator list without an expand control.

### Requirement 22: Member avatar cluster on task rows

**User Story:** As a user scanning my task list, I want shared tasks to show a small avatar cluster of their members, so that I can tell at a glance which tasks are collaborative and who is involved.

#### Acceptance Criteria

1. WHERE a task row displayed in the Home_Task_List represents a Collaborative_Task that has one or more Members, THE Home_Task_List SHALL display on that task row a compact Avatar_Cluster.
2. THE Avatar_Cluster SHALL display individual avatar or initials indicators for at most 3 Members whose Member_Status is one of `pending`, `accepted`, or `completed`.
3. WHERE a Collaborative_Task has more displayed Members than the 3 shown individually, THE Avatar_Cluster SHALL display a "+N" overflow indicator where N equals the count of displayed Members not shown individually.
4. THE Avatar_Cluster SHALL visually distinguish Members whose Member_Status is `accepted` or `completed` from Members whose Member_Status is `pending`.
5. WHERE a task row in the Home_Task_List represents a non-collaborative task, THE Home_Task_List SHALL NOT display an Avatar_Cluster on that task row.
6. THE Home_Task_List SHALL preserve the existing behavior whereby a Member completing a Collaborative_Task moves that task upward in the Home_Task_List, unchanged by the presence of the Avatar_Cluster.
