# Requirements Document

## Introduction

This feature (WS7) introduces dedicated push notifications for social and collaborative events in the Preamble Android app, extending the notification system beyond today's admin-broadcast and promotional pushes. Users will receive meaningful, category-specific notifications when friends accept their invites, when collaborative tasks are assigned/changed/completed, and (for already-existing events) when teammates react or nudge. Each category of notification is delivered on a dedicated Android notification channel so users can independently control categories through the operating system, and every notification deep-links to the relevant screen.

Because a client cannot push directly to another user's device, every new event-driven push is produced by a server-side Cloud Function (Firestore trigger or callable) using the Firebase Admin SDK, following the same data-only FCM conventions already established by the existing `sendNudge` callable and `onCollaborativeTaskReaction` trigger. This spec reuses the existing FCM rendering pipeline (`PreambleFcmService`), the existing deep-link routing, and the existing collaborative-task and invite data models; it does not duplicate that infrastructure.

Referral/reward language is explicitly out of scope: rewards are disabled in Development_Mode, and no notification copy will promise credits, rewards, or referral bonuses.

## Glossary

- **Notification_System**: The end-to-end capability that produces and renders Preamble push notifications, comprising the server-side trigger/callable Cloud Functions and the client-side `PreambleFcmService`.
- **Notification_Service**: A server-side Cloud Function (Firestore trigger or callable, TypeScript, Admin SDK, database "preamble") that detects an event and sends a Push_Notification.
- **Push_Notification**: A data-only Firebase Cloud Messaging (FCM) message carrying the keys `title`, `body`, `deepLink`, `type`, and `channelType`, rendered on-device by `PreambleFcmService`.
- **FCM_Token**: A device's Firebase Cloud Messaging registration token, stored at `users/{uid}.fcmToken`, used as the delivery address for a Push_Notification.
- **Notification_Channel**: An Android `NotificationChannel` (API 26+) created in `PreambleFcmService.createChannels`, identifying a user-controllable notification category. Existing channels are `preamble_broadcasts` ("Updates & Announcements") and `preamble_promos` ("Offers & Tips").
- **Social_Channel**: A Notification_Channel dedicated to social/collaborative notifications, separate from the admin broadcast and promo channels.
- **Deep_Link**: A `preamble://...` URI carried on a Push_Notification (for example `preamble://task/{id}`, `preamble://home`, and the Social_Hub link) that routes the user to a relevant in-app screen when the notification is opened.
- **Collaborative_Task**: The canonical shared-task document at `/collaborativeTasks/{taskId}`, containing `task` (payload including `title`), `memberUids`, `memberStates`, and the admin uid.
- **Admin**: The owner/creator of a Collaborative_Task who assigns it to others and may change it.
- **Member**: A user listed in a Collaborative_Task's `memberUids` (including the Admin) with an entry in `memberStates`.
- **Member_Status**: The status of a Member in `memberStates[uid].status`, one of `pending`, `accepted`, `completed`, `declined`, `left`, or `removed`.
- **Invite**: A friend invitation; outgoing invites are mirrored under the sender's `/users/{uid}/...` records (the `outgoingInvites` mirror), and acceptance creates reciprocal friend records at `users/{ownerUid}/friends/{friendUid}`.
- **Inviter**: The signed-in user who sent an Invite.
- **Actor**: The user whose action triggered an event (for example the Member who completed a slice, or the friend who accepted an Invite).
- **Recipient**: A user selected to receive a Push_Notification for a given event.
- **Social_Hub**: The Workspace/Social Hub screen reached via its Deep_Link, listing friends, invites, and leaderboard.
- **Development_Mode**: The current environment configuration in which referral rewards are disabled.

## Known Technical Constraints and Design Flags

These constraints are recorded to inform design and to bound the requirements below. Items marked "design decision" are deliberately left to the design phase.

1. **Server-side trigger requirement**: A client cannot send a Push_Notification to another user's device. Every new event-driven notification MUST originate from a Notification_Service (Cloud Function) using the Admin SDK, following the existing conventions in `functions/src/nudge.ts` (callable) and `functions/src/kudos.ts` (`onDocumentUpdated` trigger): data-only FCM with `title`, `body`, `deepLink`, `type`, and `channelType`.
2. **Existing rendering pipeline**: `PreambleFcmService.onMessageReceived` renders data-only messages and `createChannels` registers channels. Today only `preamble_broadcasts` and `preamble_promos` exist; new Social_Channels must be added here. The exact channel set, ids, names, and importance levels are a design decision.
3. **Existing events are in scope only for channel categorization**: Reaction (kudos) pushes (`onCollaborativeTaskReaction`, `type: "kudos"`) and nudge pushes (`sendNudge`, `type: "nudge"`) ALREADY EXIST and MUST NOT be re-implemented. They are in scope only insofar as they should be re-categorized onto an appropriate Social_Channel for consistency.
4. **Idempotency / no-duplicate pushes**: Firestore triggers may fire on every document write and may be retried. A Notification_Service MUST detect the specific state transition that constitutes the event (diffing before/after) so that unrelated writes or repeated writes do not produce duplicate Push_Notifications for the same event.
5. **Recipient targeting (anti-self-notify)**: A Notification_Service MUST NOT notify the Actor of their own action, and MUST send only to the Recipients relevant to the event.
6. **Per-user notification settings / OS channel toggles**: Delivery respects the operating system's per-channel enablement; a user who disables a Social_Channel at the OS level will not see notifications for that category, and the Notification_System MUST NOT attempt to override that choice.
7. **Graceful failure**: A failed Push_Notification (missing FCM_Token, delivery error) MUST be logged and swallowed and MUST NOT roll back or block the underlying data operation (invite acceptance, task assignment/change/completion).
8. **No reward language**: Notification copy MUST be plain and clear and MUST NOT promise rewards, credits, or referral bonuses, consistent with Development_Mode.
9. **Invite-accept detection pattern**: The reciprocal-friendship creation at `users/{ownerUid}/friends/{friendUid}` is an available trigger point (see the existing `onReferralFriendship` pattern in `functions/src/referrals.ts`), reusable to detect invite acceptance WITHOUT adding any reward.
10. **Invite-sent confirmation transport**: Whether the "your invite was sent" confirmation is a local client confirmation or a Push_Notification is a design decision; the requirement states only that the Inviter is informed.

## Requirements

### Requirement 1: Notify the Inviter when an Invite is accepted

**User Story:** As an Inviter, I want to be notified when a friend accepts my invite, so that I know my friend has joined my network.

#### Acceptance Criteria

1. WHEN a reciprocal friendship between the Inviter and the accepting friend is established, THE Notification_Service SHALL send a Push_Notification to the Inviter's FCM_Token.
2. THE Notification_Service SHALL compose the accepted-invite Push_Notification body to state that the accepting friend accepted the Inviter's invite, using the accepting friend's display name.
3. THE Notification_Service SHALL set the accepted-invite Push_Notification's Deep_Link to the Social_Hub Deep_Link.
4. THE Notification_Service SHALL set the accepted-invite Push_Notification's `channelType` to the value designating the Invites/Friends Social_Channel.
5. IF the Inviter has no stored FCM_Token, THEN THE Notification_Service SHALL skip delivery and complete without error.
6. THE accepted-invite Push_Notification body SHALL NOT reference rewards, credits, or referral bonuses.

### Requirement 2: Inform the Inviter that an Invite was sent

**User Story:** As an Inviter, I want confirmation that my invite was sent, so that I know the action succeeded.

#### Acceptance Criteria

1. WHEN the signed-in user sends an Invite, THE Notification_System SHALL inform the Inviter that the Invite was sent.
2. THE invite-sent confirmation SHALL identify the recipient of the Invite.
3. IF sending the Invite fails, THEN THE Notification_System SHALL inform the Inviter that the Invite was not sent.
4. THE invite-sent confirmation SHALL NOT reference rewards, credits, or referral bonuses.

### Requirement 3: Notify assignees when a Collaborative_Task is assigned

**User Story:** As a Member, I want to be notified when an Admin sends me a new shared task, so that I know I have been assigned collaborative work.

#### Acceptance Criteria

1. WHEN an Admin assigns a Collaborative_Task to one or more assignees, THE Notification_Service SHALL send a Push_Notification to each assignee's FCM_Token.
2. THE Notification_Service SHALL exclude the Admin from the assignment Recipients when the Admin is also a Member.
3. THE Notification_Service SHALL compose the assignment Push_Notification body to state that the assignee received a new shared task, using the Collaborative_Task title.
4. THE Notification_Service SHALL set the assignment Push_Notification's Deep_Link to `preamble://task/{taskId}` for the assigned Collaborative_Task.
5. THE Notification_Service SHALL set the assignment Push_Notification's `channelType` to the value designating the Collaboration Social_Channel.
6. IF an assignee has no stored FCM_Token, THEN THE Notification_Service SHALL skip that assignee and continue sending to the remaining assignees.

### Requirement 4: Notify Members when a Collaborative_Task is changed

**User Story:** As a Member, I want to be notified when an Admin changes a shared task I belong to, so that I can act on the change.

#### Acceptance Criteria

1. WHEN an Admin changes a Collaborative_Task, THE Notification_Service SHALL send a Push_Notification to each Member of the Collaborative_Task other than the Admin who made the change.
2. THE Notification_Service SHALL compose the change Push_Notification body to state that the shared task was changed, using the Collaborative_Task title.
3. THE Notification_Service SHALL set the change Push_Notification's Deep_Link to `preamble://task/{taskId}` for the changed Collaborative_Task.
4. THE Notification_Service SHALL set the change Push_Notification's `channelType` to the value designating the Collaboration Social_Channel.
5. WHERE a write to the Collaborative_Task does not alter task content relevant to Members, THE Notification_Service SHALL send no change Push_Notification.
6. IF a Member has no stored FCM_Token, THEN THE Notification_Service SHALL skip that Member and continue sending to the remaining Members.

### Requirement 5: Notify the Admin when a Member completes their slice

**User Story:** As an Admin, I want to be notified when a Member completes their part of a shared task, so that I can track collaborative progress.

#### Acceptance Criteria

1. WHEN a Member's Member_Status transitions to `completed`, THE Notification_Service SHALL send a Push_Notification to the Admin's FCM_Token.
2. THE Notification_Service SHALL exclude the completing Member from the completion Recipients.
3. THE Notification_Service SHALL compose the completion Push_Notification body to state that the Member completed their part, using the completing Member's display name and the Collaborative_Task title.
4. THE Notification_Service SHALL set the completion Push_Notification's Deep_Link to `preamble://task/{taskId}` for the Collaborative_Task.
5. THE Notification_Service SHALL set the completion Push_Notification's `channelType` to the value designating the Collaboration Social_Channel.
6. IF the Admin has no stored FCM_Token, THEN THE Notification_Service SHALL skip delivery and complete without error.

### Requirement 6: Dedicated Notification_Channels and routing

**User Story:** As a user, I want social and collaborative notifications on their own channels that route me to the right screen, so that I can control categories independently and act on each notification.

#### Acceptance Criteria

1. THE Notification_System SHALL deliver social and collaborative Push_Notifications on Social_Channels that are separate from the `preamble_broadcasts` and `preamble_promos` channels.
2. THE Notification_System SHALL register each Social_Channel in `PreambleFcmService.createChannels` so that each Social_Channel is independently controllable through the operating system.
3. WHILE a Social_Channel is disabled at the operating-system level, THE Notification_System SHALL allow the operating system to suppress that channel's Push_Notifications without overriding the user's choice.
4. WHEN a user opens an invite-related Push_Notification, THE Notification_System SHALL route the user to the Social_Hub via the Push_Notification's Deep_Link.
5. WHEN a user opens a Collaborative_Task-related Push_Notification, THE Notification_System SHALL route the user to the corresponding Collaborative_Task via the `preamble://task/{taskId}` Deep_Link.
6. THE Notification_System SHALL render each social and collaborative Push_Notification with a distinct title and body that identify the event.
7. THE Notification_System SHALL assign existing reaction (kudos) and nudge Push_Notifications to a Social_Channel rather than the `preamble_broadcasts` channel.

### Requirement 7: Recipient targeting and anti-self-notification

**User Story:** As a user, I want to receive only notifications that are relevant to me and never a notification about my own action, so that notifications stay meaningful.

#### Acceptance Criteria

1. THE Notification_Service SHALL exclude the Actor from the Recipients of a Push_Notification generated by that Actor's action.
2. THE Notification_Service SHALL send each event's Push_Notification only to the Recipients defined for that event type.
3. WHERE an event has no eligible Recipient, THE Notification_Service SHALL send no Push_Notification.

### Requirement 8: Idempotency and duplicate suppression

**User Story:** As a user, I want to receive a single notification per event, so that I am not spammed by repeated or duplicate pushes.

#### Acceptance Criteria

1. WHEN a Notification_Service processes a Collaborative_Task write, THE Notification_Service SHALL determine the event by comparing the document's before-state and after-state.
2. IF a Collaborative_Task write does not represent the specific transition for an event type, THEN THE Notification_Service SHALL send no Push_Notification for that event type.
3. WHEN the same Member_Status transition has already produced a completion Push_Notification, THE Notification_Service SHALL NOT send a duplicate completion Push_Notification for that transition.
4. WHEN a Notification_Service is retried for an already-processed event, THE Notification_Service SHALL avoid sending a duplicate Push_Notification for that event.

### Requirement 9: Graceful failure isolation

**User Story:** As a user, I want my invite and task actions to succeed even if a notification cannot be delivered, so that notification problems never block my work.

#### Acceptance Criteria

1. IF sending a Push_Notification fails, THEN THE Notification_Service SHALL log the failure and complete the underlying data operation successfully.
2. IF a Recipient has no stored FCM_Token, THEN THE Notification_Service SHALL skip that Recipient without raising an error.
3. WHEN delivery to one Recipient fails during a multi-Recipient send, THE Notification_Service SHALL continue attempting delivery to the remaining Recipients.
4. THE Notification_Service SHALL NOT roll back or block an invite acceptance, task assignment, task change, or task completion because of a Push_Notification delivery failure.
