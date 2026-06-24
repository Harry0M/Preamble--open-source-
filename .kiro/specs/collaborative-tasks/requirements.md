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

---

## Requirements (Iteration 3: Collaborative correctness & live task-list visuals)

The following requirements extend the implemented feature with two correctness fixes for collaborative task creation (WS3) and three visual upgrades for the collaborative task list (WS4). They reuse the components, defined terms, and behaviors established in Requirements 1–22 — notably the canonical Collaborative_Task document, the asynchronous own-copy-first save flow (Requirements 6.2, 6.7, 7.3, 7.4), the AI-parsing-then-finalize path, optimistic UI, the Incoming_Section (Requirement 19), and the member preview/overflow arithmetic behind the Avatar_Cluster (Requirement 22). Existing requirements are unchanged except where a criterion below explicitly supersedes a named earlier criterion.

All visual surfaces introduced or modified here MUST follow the Social_Hub_Design_Language already used across the app: Cardfolio-style vibrant cards, the latest Material 3 Expressive components, and live "alive" morphing shapes with expressive motion, so the collaborative task list feels consistent with the Social Hub theme.

### Additional Glossary (Iteration 3)

- **AI_Parse_Phase**: The background phase (the existing `AiParsingWorker`) that refines a task's attributes — title, date, deadline time, tags, priority, recurrence, description, and subtasks — after the task's own local copy is saved, routed through the Mistral model in Cloud Functions when the user is signed in.
- **Collaborative_Send**: The act of finalizing a Collaborative_Task by creating its canonical Collaborative_Task document and making it available to every Assignee. A collaborative task is "sent" only once its canonical document exists and carries the finalized task content.
- **Send_Status**: The user-visible status of a collaborative task's Collaborative_Send, exactly one of `parsing`, `queued`, `sending`, `sent`, or `send_failed`.
- **Connectivity**: The device's network reachability state at a given moment, classified as `online` (a backend write can complete within the Requirement 14.5 write timeout), `slow` (reachable but a write does not complete within that timeout), or `offline` (no reachable network).
- **Collaborative_Send_Queue**: The durable mechanism that retains a pending Collaborative_Send across connectivity loss, app backgrounding, and process restarts until the send is delivered or determined to be undeliverable.
- **Member_Avatar**: The per-member visual indicator displayed for a Collaborative_Task member on a task row and in the Avatar_Cluster.
- **Expressive_Member_Shape**: A live, "alive" Material 3 Expressive shape (a morphing/rounded-polygon shape consistent with the Social_Hub_Design_Language) used as the container of a Member_Avatar, replacing the plain circle previously used.
- **Google_Profile_Image**: A Member's real Google account profile photograph, obtained from the Google Sign-In account `photoUrl`.
- **Generated_Initials_Avatar**: An auto-generated placeholder avatar that is not a real photograph of the Member, including an initials or single-letter image returned by Google in place of a real photo.
- **Default_Avatar**: The app's own bundled fallback Member_Avatar image, used when no real Google_Profile_Image is available for a Member.
- **Incoming_Task_Card**: The Home_Task_List representation of a pending incoming Collaborative_Task assignment, rendered as a normal task card carrying full task metadata together with inline accept and decline controls.

### Requirement 23: Defer collaborative send until AI parsing completes

**User Story:** As a user who shares a task with friends while the AI is still parsing it, I want the app to wait and send the fully-parsed task, so that my assignees always receive the complete task and I never end up with a saved task that was silently never sent.

#### Acceptance Criteria

1. WHEN a user confirms creation of a Collaborative_Task while the AI_Parse_Phase for that task has not completed, THE Collaboration_System SHALL save the Admin's own local copy within 1 second and SHALL defer the Collaborative_Send until the AI_Parse_Phase completes.
2. WHILE a confirmed Collaborative_Task is awaiting completion of its AI_Parse_Phase before the Collaborative_Send, THE Collaboration_System SHALL display that task to the Admin with a Send_Status of `parsing`.
3. WHEN the AI_Parse_Phase for a confirmed Collaborative_Task completes with refined attributes, THE Collaboration_System SHALL perform the Collaborative_Send using the finalized attributes, so that every Assignee receives the fully-parsed task content as defined in Requirement 7.2.
4. IF the AI_Parse_Phase for a confirmed Collaborative_Task ends without producing refined attributes, including the cases where parsing returns no result or fails without producing attributes, THEN THE Collaboration_System SHALL perform the Collaborative_Send using the attributes as entered by the Admin so that the task is still sent to every Assignee.
5. WHEN the Collaborative_Send for a confirmed Collaborative_Task completes successfully, THE Collaboration_System SHALL display that task to the Admin with a Send_Status of `sent`.
6. WHEN a Collaborative_Task is finalized by the Collaborative_Send after its AI_Parse_Phase completes, THE Collaboration_System SHALL record the Admin as both Admin and Member and SHALL record each assigned friend as a Member with an initial Member_Status of `pending`, consistent with Requirement 6.3.
7. THE Collaboration_System SHALL ensure that, for every confirmed Collaborative_Task, the Collaborative_Send occurs after the AI_Parse_Phase completes under `online` Connectivity, such that no confirmed Collaborative_Task remains saved in the Admin's local list without its canonical Collaborative_Task document having been created.
8. WHERE a Collaborative_Task's AI_Parse_Phase finalizes refined attributes after the Collaborative_Send has already created the canonical Collaborative_Task document, THE Collaboration_System SHALL write the finalized attributes as a subsequent update to that canonical document while preserving every Member's existing Member_State, consistent with Requirements 7.4 and 8.6.

### Requirement 24: Reliable collaborative delivery over offline and slow connectivity

**User Story:** As a user creating a shared task while my connection is off or slow, I want the assignment to be queued and delivered when my connection returns, so that collaborative tasks work the same offline as they do online instead of silently failing.

#### Acceptance Criteria

1. WHEN a user confirms creation of a Collaborative_Task while Connectivity is `offline` or `slow`, THE Collaboration_System SHALL save the Admin's own local copy within 1 second and SHALL enqueue the Collaborative_Send in the Collaborative_Send_Queue.
2. WHILE a Collaborative_Send is held in the Collaborative_Send_Queue and has not yet been delivered, THE Collaboration_System SHALL display that task to the Admin with a Send_Status of `queued`.
3. THE Collaborative_Send_Queue SHALL retain each pending Collaborative_Send across connectivity loss, app backgrounding, and app process restart until that Collaborative_Send is delivered or is determined to be undeliverable.
4. WHEN Connectivity returns to `online` while a Collaborative_Send is held in the Collaborative_Send_Queue, THE Collaboration_System SHALL retry the Collaborative_Send and SHALL display that task to the Admin with a Send_Status of `sending` for the duration of the in-progress attempt.
5. WHEN a queued Collaborative_Send is delivered successfully, THE Collaboration_System SHALL make the task available to every Assignee through the canonical Collaborative_Task document and SHALL display that task to the Admin with a Send_Status of `sent`.
6. IF a queued Collaborative_Send remains undelivered after the Collaborative_Send_Queue exhausts its retry attempts, THEN THE Collaboration_System SHALL retain the Admin's local copy, SHALL display that task to the Admin with a Send_Status of `send_failed`, and SHALL display an error message indicating that the task could not be shared with the collaborators.
7. WHILE Connectivity is `offline` or `slow`, THE Collaboration_System SHALL NOT discard a pending Collaborative_Send and SHALL NOT report that Collaborative_Send as delivered until its canonical Collaborative_Task document write has completed.
8. WHERE a Collaborative_Task created offline also has a pending AI_Parse_Phase, THE Collaboration_System SHALL order the Collaborative_Send after the AI_Parse_Phase as defined in Requirement 23 and SHALL still deliver the task once both the AI_Parse_Phase has completed and Connectivity has returned to `online`.

### Requirement 25: Live Material member shapes on task rows

**User Story:** As a user scanning my task list, I want member avatars to appear as lively Material shapes instead of plain circles, so that the collaborative task list feels alive and consistent with the Social Hub theme.

#### Acceptance Criteria

1. WHERE a task row in the Home_Task_List displays a Member_Avatar for a Collaborative_Task Member, THE Home_Task_List SHALL render that Member_Avatar inside an Expressive_Member_Shape rather than a plain circular shape.
2. THE Expressive_Member_Shape SHALL be a Material 3 Expressive shape consistent with the Social_Hub_Design_Language used elsewhere in the app.
3. THE Avatar_Cluster SHALL preserve the behavior defined in Requirement 22, displaying individual Member_Avatars for at most 3 Members whose Member_Status is one of `pending`, `accepted`, or `completed` and a "+N" overflow indicator for the remaining displayed Members, with the Member_Avatars now rendered as Expressive_Member_Shapes.
4. THE Avatar_Cluster SHALL continue to visually distinguish Members whose Member_Status is `accepted` or `completed` from Members whose Member_Status is `pending`, consistent with Requirement 22.4, using the Expressive_Member_Shape treatment.
5. This requirement supersedes the plain-circle presentation of the Member_Avatar; the displayed-member selection, overflow count, and non-collaborative-row suppression defined in Requirement 22 remain unchanged.

### Requirement 26: Member profile images with default fallback

**User Story:** As a user, I want each member's real Google profile photo shown in their avatar shape when it is available, and a clean default image otherwise, so that I recognize collaborators without seeing low-quality placeholder images.

#### Acceptance Criteria

1. WHERE a Member has an available Google_Profile_Image that is a real photograph, THE Home_Task_List SHALL display that Google_Profile_Image inside the Member's Expressive_Member_Shape.
2. IF a Member's Google_Profile_Image cannot be fetched, THEN THE Home_Task_List SHALL display the Default_Avatar inside that Member's Expressive_Member_Shape.
3. IF the image available for a Member is a Generated_Initials_Avatar rather than a real photograph, THEN THE Home_Task_List SHALL display the Default_Avatar inside that Member's Expressive_Member_Shape instead of the Generated_Initials_Avatar.
4. THE Home_Task_List SHALL select a Member_Avatar image using the following precedence in order: first a real Google_Profile_Image when available, otherwise the Default_Avatar.
5. WHILE a Member's Google_Profile_Image is being fetched, THE Home_Task_List SHALL display the Default_Avatar until the Google_Profile_Image is available.

> Design-phase note (not an acceptance criterion): The design phase MUST confirm whether the Google Sign-In account `photoUrl` is captured and stored for Collaborative_Task Members (the current build derives row avatars from a generated source rather than the Google `photoUrl`), and MUST define how a Generated_Initials_Avatar / placeholder is detected so the Default_Avatar fallback in criteria 2–3 can be applied reliably.

### Requirement 27: Incoming task request rendered as a normal task card

**User Story:** As a user, I want an incoming shared-task request to look like a normal task card at the top of my list with clear accept and decline controls, so that I can read its full details and respond without a separate oversized banner.

#### Acceptance Criteria

1. WHERE the signed-in user has one or more Collaborative_Tasks for which that user's own Member_Status is `pending`, THE Home_Task_List SHALL render each such task as an Incoming_Task_Card positioned at the top of the Home_Task_List, below the Home_Task_List progress indicators and above all other tasks.
2. THE Incoming_Task_Card SHALL display the task's metadata in the same form a normal task card uses, including the task title, the deadline time shown the same way a normal task card shows it, and the task's tags and priority when present.
3. THE Incoming_Task_Card SHALL present an inline Accept control and an inline decline control, where the decline control is presented as a cross (close) affordance.
4. THE Accept control SHALL have a greater horizontal length than the decline control on the Incoming_Task_Card.
5. THE Accept control and the decline control SHALL be rendered as live Material 3 Expressive controls consistent with the Social_Hub_Design_Language.
6. WHEN a user activates the Accept control on an Incoming_Task_Card for which the user's own Member_Status is `pending`, THE Collaboration_System SHALL set that user's Member_Status to `accepted` and SHALL reflect the acceptance in the Home_Task_List within 200 milliseconds of the action and before the backend operation completes, consistent with Requirement 19.3.
7. WHEN a user activates the decline control on an Incoming_Task_Card for which the user's own Member_Status is `pending`, THE Collaboration_System SHALL set that user's Member_Status to `declined` and SHALL remove that task from the Home_Task_List within 200 milliseconds of the action and before the backend operation completes, consistent with Requirement 19.5.
8. IF the backend operation for accepting or declining an Incoming_Task_Card fails, THEN THE Collaboration_System SHALL restore the Home_Task_List to the exact state held before the action and SHALL display an error message indicating the operation failed, consistent with Requirement 19.7.
9. This requirement supersedes the minimal title-and-button presentation of the Incoming_Section row defined in Requirement 19.2; the Incoming_Section placement, header-suppression, pending-only gating, and optimistic accept/decline behavior defined in Requirement 19 otherwise remain unchanged.
---

## Requirements (Iteration 4: WS2 — Send to Circle + searchable picker)

The following requirements extend task creation so a user can send a task to one or more of their Circles from the task-create sheet, the same way tasks are assigned to friends today, and replace the current inline friend dropdown/chip selector with a single searchable recipient picker that lists both friends and Circles and scales to thousands of entries. They reuse the components, defined terms, and behaviors established in Requirements 1–27 — notably the canonical Collaborative_Task document, the `assignTaskToMultiple` assignment path used by Requirement 6, the 50-Assignee maximum (Requirements 6.1, 6.5, 6.6), the asynchronous own-copy-first save and AI-parse-then-send flow (Requirements 6.2, 6.7, 7, 23, 24), and the per-member acceptance/completion lifecycle (Requirements 8, 10). They also reuse the **Circle** terminology from the `shared-circles` feature. Existing requirements are unchanged except where a criterion below explicitly supersedes a named earlier criterion.

All visual surfaces introduced or modified here MUST follow the Social_Hub_Design_Language already defined for Iteration 3: Cardfolio-style vibrant cards, the latest Material 3 Expressive components, and live "alive" morphing shapes with expressive motion, so the recipient picker feels consistent with the Social Hub theme.

### Chosen "send to a Circle" model (recorded for the design phase)

`shared-circles` exposes two distinct stored shapes: the canonical Circle document at `/circles/{circleId}` (with `adminUid`, `memberUids`, and a `members` list of Circle_Members) and a **separate, deliberately simple** shared list of Circle_Tasks at `/circleTasks` that carry only a title, a single global Shared_Completion flag, and a Circle_Author — with no rich attributes (no description, tags, priority, deadline, recurrence, or subtasks) and no per-member acceptance lifecycle. The `shared-circles` "add a task to the shared list" flow (its Requirement 9) lives inside the Circle, not in the task-create sheet, and `shared-circles` has **no** mechanism for sending a Collaborative_Task to a Circle.

The task-create sheet produces a **rich** task (title, description, tags, priority, deadline, recurrence, subtasks) whose collaborative form is a per-member Collaborative_Task. Therefore this feature defines **"send to a Circle" as the members-as-assignees model**, not the post-to-circle-list model:

- Sending a task to a Circle resolves that Circle's **current Circle_Members** (every member other than the sending user) into Assignees on a single canonical Collaborative_Task, exactly as selecting those people individually as friends would, and reuses the existing `assignTaskToMultiple` assignment path.
- This preserves the rich task content (Requirement 7.2), the per-member acceptance/completion lifecycle (Requirements 8, 10), the AI-parse-then-send and offline-queue behavior (Requirements 23, 24), and the 50-Assignee maximum (Requirements 6.5, 6.6).
- The post-to-circle-list model is **rejected** for this flow because it would discard all rich attributes, replace per-member completion with global Shared_Completion, and target the in-Circle shared list rather than the user's task list — none of which matches "the same way tasks are assigned to friends today."
- Because Assignees are resolved from the Circle's membership **at send time**, the resolved membership is a **snapshot**: the created Collaborative_Task does not record the Circle's identifier and does not stay synchronized to later Circle membership changes. Circle_Members added or removed after the task is created do not change the task's Assignees; subsequent membership of the task is managed only through the existing admin controls (Requirement 11).

### Additional Glossary (Iteration 4)

Terms marked "reused (shared-circles)" carry the same meaning as in the `shared-circles` requirements.

- **Circle**: Reused (shared-circles) — a named shared space owned by one Circle_Admin and joined by one or more Circle_Members, identified by a Circle identifier and a Circle_Name.
- **Circle_Member**: Reused (shared-circles) — a user who belongs to a Circle, including the Circle_Admin, identified within the Circle's `memberUids`.
- **Circle_Admin**: Reused (shared-circles) — the single owning user of a Circle.
- **Circle_Name**: Reused (shared-circles) — the human-readable display name of a Circle.
- **Recipient**: A selectable target in the Recipient_Picker, exactly one of a Friend (from the user's friend list) or a Circle (from the user's Circle list).
- **Recipient_Picker**: The searchable Material 3 Expressive modal bottom sheet, launched from the Task_Create_Sheet, that lists the user's Friends and Circles together for multi-selection, replacing the previous inline friend dropdown and assignee chips.
- **Recipient_Search**: The case-insensitive text search within the Recipient_Picker, matched against a Friend's display name and Preamble_ID and against a Circle's Circle_Name, evaluated over the full Recipient set independently of how many entries are currently rendered. Reuses the Social_Search semantics from the `social-hub-redesign` feature.
- **Recipient_Paging**: The incremental, lazy rendering of Recipient entries that loads additional entries as the user scrolls, reusing the paging approach (PageWindow) from the `social-hub-redesign` feature, so the Recipient_Picker remains responsive with thousands of entries.
- **Selected_Recipients**: The set of Recipients the user has currently selected in the Recipient_Picker, before confirmation.
- **Selected_Count**: The displayed count of currently Selected_Recipients in the Recipient_Picker.
- **Resolved_Assignee_Set**: The deduplicated set of Assignee identifiers produced by expanding every selected Circle into its current Circle_Members and taking the union with the individually selected Friends, then excluding the sending user's own identifier; the size of this set is the number of Assignees the confirmed task will carry.
- **Circle_Send**: The act of sending a task to a Circle under the members-as-assignees model, by adding the Circle's current Circle_Members (other than the sending user) to the Resolved_Assignee_Set at send time.

### Requirement 28: Send a task to one or more Circles from the task-create sheet

**User Story:** As a user, I want to send a task to one or more of my Circles while creating it, so that I can assign work to a whole group as easily as I assign it to individual friends.

#### Acceptance Criteria

1. THE Task_Create_Sheet SHALL allow the user to select one or more of the user's Circles as recipients of the task being created, alongside individually selected Friends, through the Recipient_Picker defined in Requirement 30.
2. WHEN the user confirms creation of a task with one or more selected Circles, THE Collaboration_System SHALL perform a Circle_Send by resolving each selected Circle to its current Circle_Members other than the sending user and adding those Circle_Members to the Resolved_Assignee_Set.
3. WHEN the user confirms creation of a task with both one or more selected Circles and one or more individually selected Friends, THE Collaboration_System SHALL combine the expanded Circle_Members and the selected Friends into a single Resolved_Assignee_Set in which each Assignee identifier appears exactly once.
4. WHEN a Circle_Member resolved from a selected Circle has the same identifier as an individually selected Friend or as a Circle_Member resolved from another selected Circle, THE Collaboration_System SHALL include that identifier exactly once in the Resolved_Assignee_Set.
5. WHEN building the Resolved_Assignee_Set, THE Collaboration_System SHALL exclude the sending user's own identifier from the Assignees, so that the sending user is recorded only as the Admin and Member and not as an Assignee, consistent with Requirements 8.2 and 8.3.
6. WHEN the user confirms creation of a task whose Resolved_Assignee_Set contains one or more Assignees, THE Collaboration_System SHALL create a single canonical Collaborative_Task through the same assignment path used for friend assignment in Requirement 6.3, recording the sending user as both Admin and Member and recording each identifier in the Resolved_Assignee_Set as a Member with an initial Member_Status of `pending`.
7. WHERE the Resolved_Assignee_Set is empty after a Circle_Send, including the case where every selected Circle contains only the sending user, THE Collaboration_System SHALL create a normal non-collaborative task and SHALL NOT create a Collaborative_Task document, consistent with Requirement 6.4.
8. THE Collaboration_System SHALL preserve, for a task sent to one or more Circles, the rich task content defined in Requirement 7.2, the per-member acceptance and completion lifecycle defined in Requirements 8 and 10, and the AI-parse-then-send and offline-queue behavior defined in Requirements 23 and 24.
9. WHEN a Collaborative_Task is created from a Circle_Send, THE Collaboration_System SHALL record the resolved Assignees as a snapshot taken at the time of the send and SHALL NOT alter that task's Assignees in response to a later change to the membership of any Circle used in the send.

### Requirement 29: Enforce the assignee maximum after Circle expansion

**User Story:** As a user sending a task to large Circles, I want a clear limit and message when the combined recipients exceed the allowed number of assignees, so that I understand why the task cannot be sent as selected.

#### Acceptance Criteria

1. THE Collaboration_System SHALL limit the size of the Resolved_Assignee_Set for a single task to the Assignee maximum of 50 defined in Requirements 6.1 and 6.5, measured after Circle expansion and deduplication.
2. IF the Resolved_Assignee_Set for a confirmed task contains more than 50 Assignees, THEN THE Collaboration_System SHALL reject the creation, SHALL NOT create a Collaborative_Task document, and SHALL display an error message indicating that the combined recipients exceed the maximum number of assignees.
3. WHEN the Recipient_Picker is open, THE Recipient_Picker SHALL display the current size of the Resolved_Assignee_Set that the present selection would produce, so that the user can see the combined assignee count before confirming.
4. IF the present selection in the Recipient_Picker would produce a Resolved_Assignee_Set larger than 50, THEN THE Recipient_Picker SHALL indicate that the assignee maximum is exceeded and SHALL prevent confirmation of that selection.

### Requirement 30: Searchable recipient picker for friends and Circles

**User Story:** As a user with many friends and Circles, I want a searchable picker that lists both, so that I can quickly find and select the people and groups I want to send a task to.

#### Acceptance Criteria

1. WHEN the user opens the recipient selection control in the Task_Create_Sheet, THE Task_Create_Sheet SHALL present the Recipient_Picker as a Material 3 Expressive modal bottom sheet that lists both the user's Friends and the user's Circles as selectable Recipients.
2. THE Recipient_Picker SHALL render its Recipient entries using Recipient_Paging, loading additional entries incrementally as the user scrolls, so that a Recipient set of at least several thousand entries is displayed without rendering every entry at once.
3. THE Recipient_Picker SHALL provide a Recipient_Search input that filters the displayed Recipients using Recipient_Search, matching a Friend against the Friend's display name and Preamble_ID and matching a Circle against the Circle_Name, compared case-insensitively.
4. THE Recipient_Picker SHALL evaluate Recipient_Search over the full Recipient set rather than only over the entries currently loaded by Recipient_Paging, so that a matching Recipient is found regardless of whether it has already been scrolled into view.
5. WHEN the Recipient_Search input is empty or contains only whitespace, THE Recipient_Picker SHALL display the full Recipient set, subject only to Recipient_Paging.
6. THE Recipient_Picker SHALL allow the user to select and deselect multiple Recipients, SHALL visually indicate which Recipients are currently selected, and SHALL display the Selected_Count of currently Selected_Recipients.
7. THE Recipient_Picker SHALL provide a confirm control that, when activated, applies the current Selected_Recipients as the task's recipients and closes the Recipient_Picker.
8. WHEN the user activates the confirm control, THE Task_Create_Sheet SHALL reflect the confirmed Selected_Recipients, distinguishing selected Friends from selected Circles, in the task being created.
9. THE Task_Create_Sheet SHALL use the Recipient_Picker as the sole control for selecting task recipients, superseding the previous inline friend dropdown menu and inline assignee chips for recipient selection.

### Requirement 31: Recipient picker edge cases

**User Story:** As a user, I want the recipient picker to behave clearly when I have no contacts, when nothing matches my search, and when I reopen it, so that selecting recipients is predictable.

#### Acceptance Criteria

1. WHERE the user has no Friends and no Circles, THE Recipient_Picker SHALL display an empty-state indication that there are no friends or Circles to send the task to and SHALL NOT present any selectable Recipient entries.
2. WHERE the user has at least one Friend or at least one Circle, THE Recipient_Picker SHALL present those available Recipients for selection.
3. IF a Recipient_Search query matches no Friend and no Circle, THEN THE Recipient_Picker SHALL display a no-match indication and SHALL display no Recipient entries while that query is active.
4. WHEN the user clears a Recipient_Search query that produced a no-match indication, THE Recipient_Picker SHALL restore the full Recipient set, subject only to Recipient_Paging.
5. WHEN the user reopens the Recipient_Picker after a previous selection within the same task-creation session that has not been cleared, THE Recipient_Picker SHALL display the previously Selected_Recipients as still selected.
6. WHEN the user changes the Recipient_Search query, THE Recipient_Picker SHALL preserve the current Selected_Recipients across the change, so that selecting a Recipient, searching again, and selecting another Recipient accumulates both selections.
