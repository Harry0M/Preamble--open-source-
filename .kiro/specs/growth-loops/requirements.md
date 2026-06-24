# Requirements Document

## Introduction

This feature adds two growth-loop mechanics to the Preamble Android app (Kotlin, Jetpack Compose, Room local database, Firebase Firestore backend, TypeScript Cloud Functions). It builds directly on the already-implemented `collaborative-tasks` friend-invite system and reuses that feature's defined terms (Preamble_ID, Invite_Link, Friend_Request, Security_Rules) unchanged. Existing friend-invite, AI-credits, and stats/recap behavior is unchanged except where this document explicitly adds new behavior.

The two mechanics, scoped from the product roadmap, are:

1. **Rewarded two-sided referral invites.** Today a user can invite a friend by Preamble_ID or by an Invite_Link of the form `https://preamble.theblankstate.com/invite/{Preamble_ID}` (`WorkspaceRepository.sendInvite` / `acceptInvite`), but no reward is granted for inviting. This feature adds a referral reward: when a genuinely new user installs and signs up through a referrer's Invite_Link or Preamble_ID and the reciprocal friendship is established, BOTH the referrer and the new user receive a fixed AI-credit reward (the Referral_Reward). Granting credits is server-authoritative: the existing AI-credits economy stores balances at `users/{uid}/ai_credits` which is read-only to clients and written only by the Admin SDK (the `aiCreditsReward` / `aiCreditsBalance` Cloud Functions follow this pattern). The referral reward MUST be granted the same way — by a Cloud Function using the Admin SDK — exactly once per eligible referred signup, and MUST NOT be grantable or inflatable by any client. The reward MUST be resistant to farming through repeated add/remove of the same friend, self-referral, or referral of pre-existing accounts. The existing manual invite flow MUST continue to work unchanged when no referral attribution is present.

2. **Shareable moments.** This feature lets a user generate and share an image of (a) their weekly recap (`RecapScreen`), (b) a streak milestone, and (c) a perfect day, reusing existing recap/stats content (`StatsScreenV2`, `StatsCapsuleScreen`, `StatsRibbonScreen`, `RecapScreen`) and the milestone events the app already raises (`CelebrationEvent.StreakDay`, `CelebrationEvent.PerfectDay`). Each share produces an image rendered from existing content plus caption text that includes the sharer's Invite_Link, handed to the Android system share sheet. Sharing must never block the UI, and image-generation failures must be handled gracefully.

Both mechanics emit PostHog analytics through the existing `AnalyticsManager` so the invite funnel and share funnel can be measured.

This document defines WHAT the system must do. Data structures, the exact Cloud Function decomposition, attribution-token format, and the Compose-to-image capture mechanism are addressed in the design phase.

## Known Technical Constraints and Design Flags

These are recorded here as inputs to the design phase; they are not acceptance criteria.

- **Reward must be Admin-SDK / Cloud-Function enforced and idempotent.** AI-credit balances live at `users/{uid}/ai_credits/{doc}` and the deployed Security_Rules allow `read: if isOwner(uid)` with no client write (`firebase-firestore-rules.rules`). A client therefore cannot grant itself the Referral_Reward. The Referral_Reward MUST be granted by a server-side Cloud Function using the Admin SDK, mirroring the existing `aiCreditsReward` pattern (`FieldValue.increment` plus a persisted idempotency marker such as `ai_credits_first_bonus`). The at-most-once guarantee MUST be enforced server-side using a durable per-referral record, not a client flag.
- **Referral attribution must be captured at signup from the link / deep link.** The app already parses `https://preamble.theblankstate.com/invite/{Preamble_ID}` deep links in `MainActivity` into an `invite/{id}` target and routes them into the add-friend flow. Carrying the referrer's identity from that deep link (or Preamble_ID entry) through install and account creation, so that a new account can be attributed to exactly one referrer at signup time, is a design decision (for example, a pending-attribution record keyed by the new account, or a Play Install Referrer / deferred deep-link mechanism for users who did not yet have the app installed). The requirements state that attribution SHALL be captured and SHALL resolve to at most one referrer; the carrier mechanism is flagged for design.
- **Abuse / self-referral prevention is required.** The reward path MUST reject self-referral (referrer and referred user are the same account), referral of accounts that already existed before the attribution was created, and repeated reward attempts for the same referred account (including via repeated friend add/remove). The precise signals used to decide "genuinely new account" (account creation timestamp, first-seen marker, device/install signals) are a design decision; this document defines the eligibility outcome, not the detection implementation.
- **Image rendering from Compose needs a capture mechanism.** Producing a shareable image from existing recap/stats Composable content requires an off-screen or on-screen capture step (for example, rendering a Composable to a `Bitmap` via a graphics layer / `PixelCopy` / view capture, then writing a shareable file through a `FileProvider`). The choice of capture mechanism and the shareable's exact dimensions and branding are flagged for design; this document defines what each shareable must contain and that capture failures are handled gracefully.
- **Share delivery uses the Android system share sheet.** Sharing is performed with a standard `Intent.ACTION_SEND` (image + caption text) to the Android share sheet, consistent with the existing invite-share intent in `WorkspaceScreen`. Which target app the user picks is outside the app's control and is not constrained by these requirements.

## Glossary

- **Growth_System**: The overall Android-side and server-side system covering referral rewards and shareable moments introduced by this feature. Used as the umbrella system name when a more specific component is not required.
- **Preamble_ID**: As defined in `collaborative-tasks`: a short, normalized (uppercased, trimmed) public identifier mapping to exactly one user account.
- **Invite_Link**: As defined in `collaborative-tasks`: a shareable URL of the form `https://preamble.theblankstate.com/invite/{Preamble_ID}` that, when opened in the app, pre-fills a friend request to the link owner.
- **Friend_Request**: As defined in `collaborative-tasks`: a pending invitation record created by a sender and stored under the recipient's account until accepted or declined.
- **Referral_Service**: The Android-side component responsible for capturing referral attribution at signup, surfacing the referral call-to-action, and reporting referral funnel analytics.
- **Reward_Granter**: The server-side Cloud Function, running with the Admin SDK, that validates a referred signup and grants the Referral_Reward to both sides. It is the sole component permitted to write AI-credit balances for the Referral_Reward.
- **Referrer**: The existing Preamble user whose Invite_Link or Preamble_ID was used to bring a new user into the app, and to whom a Referred_Signup is attributed.
- **Referred_User**: The user of a newly created account that was attributed to a Referrer and that joined Preamble through that Referrer's Invite_Link or Preamble_ID.
- **Referral_Attribution**: A durable record, created at or before account creation, that links exactly one Referred_User account to exactly one Referrer and that carries the state of the referral (for example, pending, rewarded, rejected).
- **Referred_Signup**: The event of a Referred_User completing account creation while a Referral_Attribution to a Referrer is present.
- **Eligible_Referred_Signup**: A Referred_Signup that satisfies every eligibility condition: the Referred_User account is genuinely new (created as part of this referral and not a pre-existing account), the Referred_User is not the Referrer (no self-referral), the Referral_Attribution names exactly one Referrer, and no Referral_Reward has previously been granted for that Referred_User account.
- **Referral_Reward**: The fixed quantity of AI credits granted to one side of an Eligible_Referred_Signup. The Referral_Reward equals 50 AI credits for the Referrer and 50 AI credits for the Referred_User.
- **Friendship_Established**: The state in which a reciprocal friend relationship exists between the Referrer and the Referred_User, recorded under both accounts, as produced by the existing accept-invite flow.
- **AI_Credit_Balance**: A user's spendable AI-credit total stored at `users/{uid}/ai_credits`, read-only to clients and written only through the Admin SDK.
- **Referral_CTA**: The in-app call-to-action that invites the signed-in user to refer a friend, communicates that both sides receive credits, and exposes the user's Invite_Link for viewing, copying, and sharing.
- **Shareable_Service**: The Android-side component responsible for generating Shareable_Moment images and presenting them to the Android system share sheet.
- **Shareable_Moment**: An image generated from existing recap or stats content, together with caption text, that the user can share. Its three kinds are Weekly_Recap_Shareable, Streak_Milestone_Shareable, and Perfect_Day_Shareable.
- **Weekly_Recap_Shareable**: A Shareable_Moment rendered from the user's weekly recap content (`RecapScreen`).
- **Streak_Milestone_Shareable**: A Shareable_Moment rendered for a streak milestone, corresponding to the streak-day milestones the app already recognizes.
- **Perfect_Day_Shareable**: A Shareable_Moment rendered for a perfect day, in which the user completed all of a day's tasks.
- **Share_Caption**: The text accompanying a Shareable_Moment image, which includes the sharer's Invite_Link.
- **Share_Sheet**: The Android system share chooser presented via an `Intent.ACTION_SEND` carrying the generated image and the Share_Caption.
- **Analytics_Service**: The existing `AnalyticsManager` PostHog wrapper used to record funnel events.
- **Referral_Funnel**: The ordered sequence of analytics events for a referral: invite shared, invite opened, signup, friendship established, and reward granted.

## Requirements

### Requirement 1: Surface the referral call-to-action and invite link

**User Story:** As a user, I want a clear "invite a friend and you both get credits" prompt with my invite link, so that I am motivated to bring friends to Preamble.

#### Acceptance Criteria

1. WHERE the signed-in user has a Preamble_ID, THE Referral_Service SHALL present a Referral_CTA that states that both the inviting user and the invited user receive the Referral_Reward in AI credits when the invited user joins.
2. THE Referral_CTA SHALL display the signed-in user's Invite_Link in the defined Invite_Link form containing the user's own normalized Preamble_ID.
3. WHEN the signed-in user activates a copy control on the Referral_CTA, THE Referral_Service SHALL copy the user's Invite_Link to the device clipboard and SHALL display confirmation that the link was copied.
4. WHEN the signed-in user activates a share control on the Referral_CTA, THE Referral_Service SHALL present the Share_Sheet carrying text that contains the user's Invite_Link.
5. THE Referral_CTA SHALL state the Referral_Reward amount granted to each side.

### Requirement 2: Capture referral attribution at signup

**User Story:** As a new user who arrived through a friend's invite, I want my account to be linked to that friend, so that we both qualify for the referral reward.

#### Acceptance Criteria

1. WHEN the app is opened from an Invite_Link that carries a well-formed Preamble_ID before an account exists on the device, THE Referral_Service SHALL retain the referring Preamble_ID so that it remains available through account creation.
2. WHEN a new account completes account creation while a retained referring Preamble_ID is present, THE Referral_Service SHALL create exactly one Referral_Attribution linking the new account as Referred_User to the single Referrer identified by that Preamble_ID.
3. THE Referral_Service SHALL associate at most one Referrer with a given Referred_User account.
4. IF the retained referring Preamble_ID does not resolve to exactly one existing user account, THEN THE Referral_Service SHALL NOT create a Referral_Attribution and SHALL allow account creation to complete unchanged.
5. IF account creation completes with no retained referring Preamble_ID present, THEN THE Referral_Service SHALL NOT create a Referral_Attribution and SHALL allow account creation to complete unchanged.
6. IF the retained referring Preamble_ID resolves to the newly created account itself, THEN THE Referral_Service SHALL NOT create a Referral_Attribution.

### Requirement 3: Grant the two-sided referral reward server-side, exactly once

**User Story:** As the app owner, I want referral rewards granted only by the server and only once per genuine new referral, so that credits cannot be farmed or self-granted.

#### Acceptance Criteria

1. WHEN an Eligible_Referred_Signup has both a Referral_Attribution and Friendship_Established between the Referrer and the Referred_User, THE Reward_Granter SHALL grant the Referral_Reward to the Referrer's AI_Credit_Balance and the Referral_Reward to the Referred_User's AI_Credit_Balance.
2. THE Reward_Granter SHALL grant the Referral_Reward for a given Referred_User account at most once, such that repeated triggers for the same Referral_Attribution after a reward has been granted SHALL NOT grant additional AI credits to either side.
3. IF a referral does not satisfy every condition of an Eligible_Referred_Signup, THEN THE Reward_Granter SHALL NOT grant the Referral_Reward to either side and SHALL record the referral as not rewarded.
4. THE Reward_Granter SHALL write AI_Credit_Balance values only through the Admin SDK, and the Security_Rules SHALL deny any client write to `users/{uid}/ai_credits`.
5. WHEN the Reward_Granter grants the Referral_Reward, THE Reward_Granter SHALL update the Referral_Attribution state to indicate that the reward has been granted, in the same atomic operation that records the credit grant, so that the at-most-once outcome is durable across retries.
6. THE Reward_Granter SHALL increase each side's AI_Credit_Balance by exactly the Referral_Reward amount for one Eligible_Referred_Signup.

### Requirement 4: Reject self-referral, existing accounts, and repeated farming

**User Story:** As the app owner, I want the reward restricted to genuinely new referred users, so that the loop rewards real growth rather than abuse.

#### Acceptance Criteria

1. IF the Referrer and the Referred_User identify the same account, THEN THE Reward_Granter SHALL reject the referral and SHALL NOT grant the Referral_Reward to either side.
2. IF the account named as Referred_User existed before its Referral_Attribution was created, THEN THE Reward_Granter SHALL reject the referral and SHALL NOT grant the Referral_Reward to either side.
3. IF a Referral_Reward has already been granted for the Referred_User account, THEN THE Reward_Granter SHALL reject any further referral reward for that account and SHALL NOT grant additional AI credits, including when the Referrer and Referred_User repeatedly establish and dissolve their friendship.
4. WHEN the friendship between a rewarded Referrer and a rewarded Referred_User is dissolved and re-established, THE Reward_Granter SHALL NOT grant any additional Referral_Reward.
5. IF a single Referred_User account presents more than one candidate Referrer, THEN THE Reward_Granter SHALL grant the Referral_Reward for at most the single Referrer recorded in that account's Referral_Attribution.

### Requirement 5: Preserve the existing manual invite flow without attribution

**User Story:** As a user inviting an existing Preamble member, I want the normal friend-invite flow to keep working, so that adding friends is unaffected by the referral reward.

#### Acceptance Criteria

1. WHERE a Friend_Request is sent or accepted without any Referral_Attribution between the two users, THE Growth_System SHALL complete the existing invite, accept, and friend-creation behavior unchanged and SHALL NOT grant a Referral_Reward.
2. WHEN an existing user accepts a Friend_Request from another existing user, THE Growth_System SHALL establish the reciprocal friendship using the existing accept-invite behavior and SHALL NOT create a Referral_Attribution.
3. THE Growth_System SHALL NOT alter the validation rules of the existing invite flow, including the self-invite, already-friends, already-pending, and not-found rejections.

### Requirement 6: Track the referral funnel

**User Story:** As the app owner, I want the referral funnel measured, so that I can evaluate how well the growth loop performs.

#### Acceptance Criteria

1. WHEN the signed-in user shares or copies the Invite_Link from the Referral_CTA, THE Referral_Service SHALL record a referral-invite-shared event through the Analytics_Service.
2. WHEN the app is opened from an Invite_Link, THE Referral_Service SHALL record a referral-invite-opened event through the Analytics_Service.
3. WHEN a Referral_Attribution is created at signup, THE Referral_Service SHALL record a referral-signup event through the Analytics_Service.
4. WHEN Friendship_Established occurs for an attributed referral, THE Growth_System SHALL record a referral-friendship event through the Analytics_Service.
5. WHEN the Reward_Granter grants the Referral_Reward, THE Growth_System SHALL record a referral-reward-granted event through the Analytics_Service.

### Requirement 7: Generate a weekly recap shareable

**User Story:** As a user, I want to share an image of my weekly recap, so that I can show my progress and invite friends.

#### Acceptance Criteria

1. WHERE the signed-in user is viewing weekly recap content, THE Shareable_Service SHALL present a control to generate and share a Weekly_Recap_Shareable.
2. WHEN the user activates the share control for a Weekly_Recap_Shareable, THE Shareable_Service SHALL generate an image rendered from the user's weekly recap content.
3. THE Weekly_Recap_Shareable image SHALL depict the weekly recap summary content shown to the user for the recap period.
4. WHEN the Weekly_Recap_Shareable image has been generated, THE Shareable_Service SHALL present the Share_Sheet carrying the generated image and a Share_Caption that contains the sharer's Invite_Link.

### Requirement 8: Generate a streak milestone shareable

**User Story:** As a user who hit a streak milestone, I want to share it as an image, so that I can celebrate and bring friends in.

#### Acceptance Criteria

1. WHEN the app recognizes a streak milestone for the signed-in user, THE Shareable_Service SHALL offer a control to generate and share a Streak_Milestone_Shareable for that milestone.
2. WHEN the user activates the share control for a Streak_Milestone_Shareable, THE Shareable_Service SHALL generate an image that depicts the streak milestone, including the streak length in days.
3. WHEN the Streak_Milestone_Shareable image has been generated, THE Shareable_Service SHALL present the Share_Sheet carrying the generated image and a Share_Caption that contains the sharer's Invite_Link.

### Requirement 9: Generate a perfect day shareable

**User Story:** As a user who completed all of a day's tasks, I want to share my perfect day as an image, so that I can mark the achievement.

#### Acceptance Criteria

1. WHEN the app recognizes a perfect day for the signed-in user, THE Shareable_Service SHALL offer a control to generate and share a Perfect_Day_Shareable for that day.
2. WHEN the user activates the share control for a Perfect_Day_Shareable, THE Shareable_Service SHALL generate an image that depicts the perfect day, including the number of tasks completed that day.
3. WHEN the Perfect_Day_Shareable image has been generated, THE Shareable_Service SHALL present the Share_Sheet carrying the generated image and a Share_Caption that contains the sharer's Invite_Link.

### Requirement 10: Non-blocking sharing and graceful failure

**User Story:** As a user, I want sharing to stay smooth and never freeze the app, so that generating a shareable is painless even when something goes wrong.

#### Acceptance Criteria

1. WHILE a Shareable_Moment image is being generated, THE Shareable_Service SHALL keep the user interface responsive and SHALL NOT block the main thread.
2. WHEN the user requests a Shareable_Moment, THE Shareable_Service SHALL display a progress indication until the image is generated or generation fails.
3. IF generation of a Shareable_Moment image fails, THEN THE Shareable_Service SHALL display an error message indicating that the image could not be created and SHALL NOT present the Share_Sheet.
4. IF generation of a Shareable_Moment image does not complete within 10 seconds, THEN THE Shareable_Service SHALL treat the operation as failed, SHALL display an error message indicating that the image could not be created, and SHALL NOT present the Share_Sheet.
5. WHEN a Shareable_Moment is shared, THE Shareable_Service SHALL record a moment-shared event, identifying the Shareable_Moment kind, through the Analytics_Service.

### Requirement 11: Every shareable caption carries the invite link

**User Story:** As a user sharing a moment, I want my invite link included automatically, so that people who see it can join through me.

#### Acceptance Criteria

1. THE Share_Caption for every Shareable_Moment SHALL contain the sharer's Invite_Link in the defined Invite_Link form containing the sharer's normalized Preamble_ID.
2. WHERE the signed-in user has a Preamble_ID, THE Shareable_Service SHALL build the Share_Caption Invite_Link from that user's own normalized Preamble_ID.
3. IF the signed-in user has no resolvable Preamble_ID, THEN THE Shareable_Service SHALL still generate and share the Shareable_Moment image and SHALL omit the Invite_Link from the Share_Caption.
