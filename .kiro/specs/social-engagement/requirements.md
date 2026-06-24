# Requirements Document

## Introduction

This feature adds three lightweight social mechanics to the Preamble Android app (Kotlin, Jetpack Compose, Room local database, Firebase Firestore backend), building directly on the already-implemented `collaborative-tasks` feature. It reuses that feature's defined terms (Collaborative_Task, Admin, Member, Assignee, Member_Status, Member_State, Optimistic_UI, Security_Rules) and its canonical document model at `/collaborativeTasks/{taskId}` (`schemaVersion == 2`). Existing collaborative-tasks behavior is unchanged; this document only adds new behavior.

The three mechanics, scoped as P0/P1 from the product roadmap, are:

1. **Kudos / reactions on shared-task progress.** A Member of a Collaborative_Task can give a lightweight, single reaction ("kudos", an emoji from a small fixed set) to acknowledge fellow members, especially when a member completes their slice. Each reactor has at most one reaction per task, which the reactor can toggle off or change. Reactions are visible to every Member. Giving kudos can trigger a push notification to the acknowledged member(s).

2. **Weekly friends leaderboard via Productivity_Points.** This revives the existing-but-unused `Friend.productivityPoints` field. Completing an assigned/shared Collaborative_Task awards a fixed, deterministic number of Productivity_Points to the completing user. A leaderboard ranks the signed-in user against that user's friends, scoped to a rolling weekly window (not a global rank). Points accrual is tamper-resistant: a user can only increment their own score, and only in bounded ways.

3. **Nudge a pending member.** A Member (including the Admin) can send a one-tap "nudge" to another Member whose own Member_Status is still `pending` on a shared task, delivering a push notification ("X nudged you about '<task>'"). Nudges are rate-limited to prevent spam.

This document defines WHAT the system must do. Data structures, exact rule expressions, scoring-storage placement, and push-delivery mechanics are addressed in the design phase.

## Known Technical Constraints and Design Flags

These are recorded here as inputs to the design phase; they are not acceptance criteria.

- **Reaction and points storage.** Reactions are expected to live on or alongside the canonical `/collaborativeTasks/{taskId}` document (for example, a `reactions` map keyed by user identifier, mirroring the `memberStates[uid]` structure) and/or a related per-user structure. The exact placement is a design decision.
- **"Edit only your own slice" guarantee.** The Security_Rules MUST preserve the existing collaborative-tasks idiom in which a Member may only write their own per-member slice (the deployed rules express this as `updatesOwnMemberStatusOnly`, using `request.resource.data.diff(resource.data).affectedKeys().hasOnly([...])` scoped to the requester's own key). Reactions and any per-member social state MUST follow the same idiom so a Member can only add, change, or remove their own reaction, and can only increment their own Productivity_Points.
- **Productivity_Points trust boundary.** The canonical location of a user's score and how it becomes visible to friends (the `Friend.productivityPoints` field today lives in the reciprocal friend record at `/users/{uid}/friends/{friendUid}`, which only the account owner can write under Requirement 17 of `collaborative-tasks`). Reconciling "a user can only increment their own score" with "friends can read my score" is a trust-boundary decision flagged for the design phase.
- **Push delivery requires a server-side trigger.** The app stores each user's FCM token at `users/{uid}.fcmToken` and `PreambleFcmService` renders incoming data-only messages, but there is no client-side path to send a push to another specific user. Delivering kudos and nudge notifications therefore requires a server-side trigger (for example, a Cloud Function on document writes). The need for this trigger is flagged for the design phase; these requirements state only that the notification SHALL be sent, not how it is delivered.
- **Weekly window time base.** Member completion timestamps are recorded in UTC by the collaborative-tasks feature; the Weekly_Window in this document is therefore defined in UTC for determinism.

## Glossary

- **Collaboration_System**: The existing umbrella Android-side system covering friends, invites, and collaborative tasks, as defined in the `collaborative-tasks` spec. Used here when no more specific component applies.
- **Collaborative_Task**, **Admin**, **Member**, **Assignee**, **Member_State**, **Member_Status**, **Optimistic_UI**, **Security_Rules**: As defined in the `collaborative-tasks` requirements; reused unchanged. Member_Status is one of `pending`, `accepted`, `completed`, `declined`, `left`, `removed`.
- **Reaction_Service**: The component responsible for adding, changing, removing, and displaying Reactions on Collaborative_Tasks.
- **Kudos**: The act of a Member giving a Reaction on a Collaborative_Task to acknowledge fellow Members.
- **Reaction**: A single record giving one Reactor's chosen Reaction_Emoji on one Collaborative_Task. A Reactor has at most one Reaction per Collaborative_Task.
- **Reactor**: A Member who has given a Reaction on a Collaborative_Task.
- **Reaction_Emoji**: A single emoji selected from the Reaction_Emoji_Set.
- **Reaction_Emoji_Set**: A fixed, predefined set of exactly six emoji available for Reactions: 👍, 🎉, 🔥, 👏, ❤️, 💪.
- **Leaderboard_Service**: The component responsible for awarding Productivity_Points and computing the Friends_Leaderboard.
- **Productivity_Points**: A non-negative integer score associated with a user, reusing the existing `Friend.productivityPoints` field, earned by completing assigned Collaborative_Tasks.
- **Scoring_Rule**: The deterministic rule that awards a fixed number of Productivity_Points (the Completion_Award) to a user the first time that user completes a given assigned Collaborative_Task.
- **Completion_Award**: The fixed number of Productivity_Points granted by the Scoring_Rule for one task completion, equal to 10.
- **Friends_Leaderboard**: An ordered ranking of the signed-in user together with that user's friends, ordered by Productivity_Points earned within the current Weekly_Window, descending.
- **Weekly_Window**: The rolling time window beginning at the most recent week boundary, defined as Monday 00:00:00 UTC, and ending at the present moment. Productivity_Points earned before the Weekly_Window start are excluded from the Friends_Leaderboard.
- **Nudge_Service**: The component responsible for sending nudges and enforcing the Nudge_Rate_Limit.
- **Nudge**: A one-tap action by which a Member prompts another Member, whose Member_Status on a shared Collaborative_Task is `pending`, to act on that task, resulting in a Push_Notification to the target Member.
- **Nudge_Target**: The Member who is the recipient of a Nudge and whose Member_Status on the shared Collaborative_Task is `pending`.
- **Nudge_Rate_Limit**: The constraint that a given Member SHALL send at most one Nudge to the same Nudge_Target for the same Collaborative_Task within any 60-minute rolling interval.
- **Push_Notification**: A Firebase Cloud Messaging message delivered to a target user's device through the user's stored FCM token and rendered by `PreambleFcmService`.
- **Notification_Service**: The component (server-side trigger, flagged for design) responsible for delivering a Push_Notification to a target user.

## Requirements

### Requirement 1: Give kudos with a reaction

**User Story:** As a Member of a shared task, I want to react to the task with a single emoji, so that I can acknowledge my fellow members' progress, especially when someone completes their part.

#### Acceptance Criteria

1. WHERE the signed-in user is a Member of a Collaborative_Task, THE Reaction_Service SHALL present a control to give a Reaction to that Collaborative_Task using a Reaction_Emoji selected from the Reaction_Emoji_Set.
2. WHEN a Member who has no existing Reaction on a Collaborative_Task gives a Reaction with a Reaction_Emoji from the Reaction_Emoji_Set, THE Reaction_Service SHALL record exactly one Reaction for that Member on that Collaborative_Task, storing the selected Reaction_Emoji, the Reactor's user identifier, and a creation timestamp in UTC.
3. IF a Member attempts to give a Reaction using an emoji that is not a member of the Reaction_Emoji_Set, THEN THE Reaction_Service SHALL reject the action and SHALL NOT record a Reaction.
4. IF a user who is not a Member of a Collaborative_Task attempts to give a Reaction to that Collaborative_Task, THEN THE Reaction_Service SHALL reject the action and SHALL NOT record a Reaction.

### Requirement 2: Change or remove the reactor's own reaction

**User Story:** As a Reactor, I want to change my emoji or take my reaction back, so that my single reaction always reflects what I currently mean.

#### Acceptance Criteria

1. THE Reaction_Service SHALL record at most one Reaction per Reactor per Collaborative_Task.
2. WHEN a Reactor who already has a Reaction on a Collaborative_Task gives a different Reaction_Emoji from the Reaction_Emoji_Set, THE Reaction_Service SHALL replace that Reactor's existing Reaction_Emoji with the newly selected Reaction_Emoji and SHALL NOT create an additional Reaction.
3. WHEN a Reactor selects the same Reaction_Emoji that is already recorded as that Reactor's Reaction on a Collaborative_Task, THE Reaction_Service SHALL remove that Reactor's Reaction from the Collaborative_Task.
4. WHEN a Reactor activates a remove control for that Reactor's Reaction, THE Reaction_Service SHALL remove that Reactor's Reaction from the Collaborative_Task.
5. WHEN a Reactor's Reaction is removed, THE Reaction_Service SHALL leave every other Member's Reaction on that Collaborative_Task unchanged.

### Requirement 3: Reactions are visible to all members

**User Story:** As a Member of a shared task, I want to see the reactions other members have given, so that I can see who acknowledged the task.

#### Acceptance Criteria

1. WHERE the signed-in user is a Member of a Collaborative_Task that has one or more Reactions, THE Reaction_Service SHALL display each Reaction's Reaction_Emoji together with the Reactor's display name.
2. WHEN a Reaction on a Collaborative_Task is added, changed, or removed by any Member, THE Reaction_Service SHALL update the displayed Reactions for every Member of that Collaborative_Task within 5 seconds of the change under normal network connectivity.
3. WHERE a Collaborative_Task has no Reactions, THE Reaction_Service SHALL display an empty-state indication that no reactions have been given.

### Requirement 4: Reaction authorization preserves the own-slice guarantee

**User Story:** As a user, I want reactions to be writable only by members and only for their own reaction, so that no one can forge or tamper with another member's reaction.

#### Acceptance Criteria

1. IF a request to write a Reaction on a Collaborative_Task is made by a user whose identifier is not present in that task's member list, THEN THE Security_Rules SHALL deny the write and SHALL leave the existing Collaborative_Task document unchanged.
2. IF a Member requests to add, change, or remove only that Member's own Reaction on a Collaborative_Task, THEN THE Security_Rules SHALL permit the write.
3. IF a Member requests a write that adds, changes, or removes any other Member's Reaction, THEN THE Security_Rules SHALL deny the write and SHALL leave the existing Reaction records unchanged.
4. IF a request to write a Reaction is made by a user who is not signed in, THEN THE Security_Rules SHALL deny the request.

### Requirement 5: Optimistic reaction feedback with revert on failure

**User Story:** As a Reactor, I want my reaction to appear instantly, so that giving kudos feels immediate.

#### Acceptance Criteria

1. WHEN a Member gives, changes, or removes a Reaction on a Collaborative_Task, THE Reaction_Service SHALL reflect the resulting Reaction state in the signed-in user's view within 200 milliseconds of the action and before the backend operation completes.
2. IF the backend operation for adding, changing, or removing a Reaction fails or is denied, THEN THE Reaction_Service SHALL restore the displayed Reaction state to the exact state held immediately before the action and SHALL display an error message indicating that the reaction could not be saved.
3. IF the backend operation for adding, changing, or removing a Reaction does not complete within 30 seconds, THEN THE Reaction_Service SHALL treat the operation as failed, SHALL restore the displayed Reaction state to the exact state held immediately before the action, and SHALL display an error message indicating that the reaction could not be saved.

### Requirement 6: Notify acknowledged members of kudos

**User Story:** As a Member who completed my part of a shared task, I want to be notified when a teammate gives kudos, so that I feel recognized.

#### Acceptance Criteria

1. WHEN a Member gives or changes a Reaction on a Collaborative_Task, THE Notification_Service SHALL send a Push_Notification to each other Member of that Collaborative_Task whose Member_Status is `completed`.
2. THE Push_Notification for a Kudos SHALL identify the Reactor's display name, the Reaction_Emoji given, and the title of the Collaborative_Task.
3. WHEN a Reactor removes that Reactor's Reaction from a Collaborative_Task, THE Notification_Service SHALL NOT send a Push_Notification for that removal.
4. IF no Member of the Collaborative_Task other than the Reactor has a Member_Status of `completed` at the time a Reaction is given or changed, THEN THE Notification_Service SHALL NOT send a Kudos Push_Notification.
5. IF delivery of a Kudos Push_Notification fails, THEN THE Reaction_Service SHALL retain the recorded Reaction and SHALL NOT revert the Reaction state.

### Requirement 7: Award productivity points on task completion

**User Story:** As a user, I want to earn productivity points when I complete tasks assigned to me, so that my effort is reflected in the leaderboard.

#### Acceptance Criteria

1. WHEN a Member's Member_Status on an assigned Collaborative_Task transitions to `completed` for the first time for that Member on that task, THE Leaderboard_Service SHALL increment that Member's Productivity_Points by the Completion_Award.
2. THE Leaderboard_Service SHALL award the Completion_Award for a given Member and a given Collaborative_Task at most once, such that a subsequent un-completion followed by re-completion of the same Collaborative_Task by the same Member SHALL NOT award additional Productivity_Points.
3. WHEN a Member's Member_Status on a Collaborative_Task transitions from `completed` to a non-completed status, THE Leaderboard_Service SHALL leave that Member's accumulated Productivity_Points unchanged.
4. THE Leaderboard_Service SHALL record, for each Productivity_Points award, the awarding timestamp in UTC, so that the award can be attributed to a Weekly_Window.
5. THE Leaderboard_Service SHALL apply the Scoring_Rule only to a Collaborative_Task in which the completing user is an Assignee or Member, and SHALL NOT award Productivity_Points for completing a non-collaborative task.

### Requirement 8: Tamper-resistant points accrual

**User Story:** As the app owner, I want points to be incrementable only by their own owner and only in bounded amounts, so that the leaderboard cannot be gamed.

#### Acceptance Criteria

1. IF a request would increase a user's Productivity_Points and the requesting user's authenticated identifier does not equal the identifier of the user whose Productivity_Points would increase, THEN THE Security_Rules SHALL deny the write.
2. IF a single write would increase a user's own Productivity_Points by an amount other than the Completion_Award, THEN THE Security_Rules SHALL deny the write.
3. IF a write would set a user's Productivity_Points to a value lower than the value recorded before the write, THEN THE Security_Rules SHALL deny the write.
4. IF a request to write any user's Productivity_Points is made by a user who is not signed in, THEN THE Security_Rules SHALL deny the request.

### Requirement 9: Weekly friends leaderboard

**User Story:** As a user, I want to see how my productivity points this week compare with my friends', so that I stay motivated through friendly competition.

#### Acceptance Criteria

1. WHERE the signed-in user has one or more friends, THE Leaderboard_Service SHALL present a Friends_Leaderboard listing the signed-in user and each of that user's friends, each entry showing the display name and the Productivity_Points earned within the current Weekly_Window.
2. THE Leaderboard_Service SHALL order the Friends_Leaderboard by Productivity_Points earned within the current Weekly_Window in descending order.
3. THE Friends_Leaderboard SHALL include only the signed-in user and that user's friends, and SHALL NOT include any user who is neither the signed-in user nor a friend of the signed-in user.
4. THE Leaderboard_Service SHALL exclude from each Friends_Leaderboard entry any Productivity_Points awarded before the start of the current Weekly_Window.
5. WHEN the current moment crosses a Weekly_Window boundary, THE Leaderboard_Service SHALL compute the Friends_Leaderboard against the new Weekly_Window, so that Productivity_Points earned in the prior window no longer contribute to the displayed ranking.
6. WHERE the signed-in user has no friends, THE Leaderboard_Service SHALL display an empty-state indication that the leaderboard requires friends.

### Requirement 10: Send a nudge to a pending member

**User Story:** As a Member or Admin of a shared task, I want to nudge a teammate who has not yet responded, so that the task can move forward.

#### Acceptance Criteria

1. WHERE the signed-in user is a Member of a Collaborative_Task and another Member of that task has a Member_Status of `pending`, THE Nudge_Service SHALL present a one-tap Nudge control targeting that pending Member.
2. WHEN a Member activates the Nudge control for a Nudge_Target, THE Nudge_Service SHALL send a Nudge that causes the Notification_Service to deliver a Push_Notification to the Nudge_Target.
3. THE Push_Notification for a Nudge SHALL identify the sending Member's display name and the title of the Collaborative_Task, in the form "<sender> nudged you about '<task>'".
4. WHEN a Member sends a Nudge, THE Nudge_Service SHALL reflect a nudged state for that Nudge_Target in the sending Member's view within 200 milliseconds of the action and before the backend operation completes.
5. IF the backend operation for sending a Nudge fails, is denied, or does not complete within 30 seconds, THEN THE Nudge_Service SHALL restore the nudged state to the value displayed immediately before the action and SHALL display an error message indicating that the nudge could not be sent.

### Requirement 11: Nudge eligibility guards

**User Story:** As a user, I want nudges restricted to members nudging pending teammates, so that nudges are meaningful and cannot be misused.

#### Acceptance Criteria

1. IF a user who is not a Member of a Collaborative_Task attempts to send a Nudge for that task, THEN THE Nudge_Service SHALL reject the action and SHALL NOT send a Nudge.
2. IF a Member attempts to send a Nudge to a Nudge_Target whose Member_Status on the Collaborative_Task is not `pending`, THEN THE Nudge_Service SHALL reject the action, SHALL NOT send a Nudge, and SHALL display a message indicating that only pending members can be nudged.
3. IF a Member attempts to send a Nudge to themselves, THEN THE Nudge_Service SHALL reject the action and SHALL NOT send a Nudge.
4. WHERE the signed-in user is the Admin of a Collaborative_Task, THE Nudge_Service SHALL permit the Admin to send a Nudge to any Member of that task whose Member_Status is `pending`.

### Requirement 12: Rate-limit nudges

**User Story:** As a Member who might receive nudges, I want nudges rate-limited, so that I am not spammed.

#### Acceptance Criteria

1. THE Nudge_Service SHALL enforce the Nudge_Rate_Limit, permitting a given Member to send at most one Nudge to the same Nudge_Target for the same Collaborative_Task within any 60-minute rolling interval.
2. IF a Member attempts to send a Nudge to a Nudge_Target for a Collaborative_Task within 60 minutes of that Member's previous Nudge to the same Nudge_Target for the same Collaborative_Task, THEN THE Nudge_Service SHALL reject the action, SHALL NOT send a Nudge, and SHALL display a message indicating that the member was nudged recently and can be nudged again later.
3. WHEN at least 60 minutes have elapsed since a Member's previous Nudge to a given Nudge_Target for a given Collaborative_Task, THE Nudge_Service SHALL permit that Member to send a new Nudge to the same Nudge_Target for the same Collaborative_Task.
4. THE Nudge_Service SHALL apply the Nudge_Rate_Limit independently for each combination of sending Member, Nudge_Target, and Collaborative_Task.
