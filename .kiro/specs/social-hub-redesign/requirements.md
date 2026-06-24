# Requirements Document

## Introduction

This feature redesigns the social surface of the Preamble Android app (Kotlin, Jetpack Compose, Material 3, Firebase Firestore backend). The social surface today is `WorkspaceScreen` (titled "Friends"): a single vertical `LazyColumn` that stacks a hero ID card, a `ReferralCta`, pending invites, a `LeaderboardSection`, and the full friends list into one scroll. This redesign reworks that surface into a cohesive, engaging Social_Hub built from the latest Material 3 Expressive components and expressive ("alive") shape and motion, while preserving the existing visual language (the Cardfolio-style vibrant stacked cards, the lime hero ID card, and the existing `RandomBackgrounds` PNG packs).

The redesign builds on three already-shipped features and reuses their defined terms and behavior unchanged except where this document adds new behavior:

- **collaborative-tasks** — the friend invite system (`WorkspaceRepository`, `WorkspaceViewModel`, `WorkspaceScreen`), including Preamble_ID, Invite_Link, and Friend_Request.
- **social-engagement** — the weekly Friends_Leaderboard and kudos.
- **growth-loops** — referral invites and the `ReferralCta`, including the two-sided 50-credit Referral_Reward.
- **shared-circles** — Circles (named shared friend groups, each with one shared task list).

The redesign addresses these goals: (1) a beautiful, "alive", full social surface that handles its loading, empty, and error edge cases gracefully; (2) a layout that scales gracefully when the Friends_Leaderboard and the friends list each grow to roughly a thousand rows, using search, incremental paged loading, and clear navigation between the two areas; (3) Circles that are discoverable and self-explanatory on the social surface; (4) persistent, visible feedback for sent invites — the same representation used for incoming invites — plus a Requests_List the user is taken to after sending; (5) a beautiful, on-theme invite-entry experience for both link and Preamble_ID entry; (6) a deep-link fix so that opening an `invite/{id}` link lands on the social surface rather than the collaborative-tasks workspace; and (7) a development-mode gate that disables the referral AI-credit reward while keeping the invite/friendship flow working.

This is an enhancement pass on an already-implemented feature. Several behaviors defined here (Requirements 4, 5, 6, and 7) were implemented in a previous pass but are reported to still misbehave in the actual build — the invite-sent feedback still surfaces only a transient toast, and the `invite/{id}` deep link still opens the collaborative-tasks workspace instead of the social surface. The corresponding requirements are sharpened below to state the observable end-state precisely, and they MUST be re-verified against the actual implementation (`WorkspaceScreen`, `WorkspaceViewModel`, `WorkspaceRepository`, and the deep-link handling in `MainActivity`/`PreambleApp`) and the built, deployed app rather than treated as satisfied because code exists.

This document defines WHAT the system must do. Specific Compose components, layout widgets, navigation wiring, and styling are deferred to the design phase, except where the user has explicitly required a constraint (use of Material 3 Expressive components and expressive shape/motion, and reuse of the existing PNG packs and card design language).

## Out of Scope

The following are handled by separate specs and are NOT part of this feature: collaborative-task creation, plan-my-day, and notification mechanics. This document does not alter the underlying friend-invite, leaderboard, kudos, Circle, or reward-eligibility logic beyond the changes explicitly stated here.

## Known Technical Constraints and Design Flags

These are recorded as inputs to the design phase; they are not acceptance criteria.

- **Deep-link routing (re-verify in build).** `MainActivity` parses `https://preamble.theblankstate.com/invite/{id}` into an `invite/{id}` target. The `invite/` branch in `PreambleApp`'s deep-link `LaunchedEffect` has since been changed to set `showFriendsScreen = true` and pass `initialInviteId`, which is intended to open the social surface (`WorkspaceScreen`) rather than the collaborative-tasks workspace (`selectedTab = 4`, `WorkspaceTasksScreen`). The user nonetheless reports the link still lands on the workspace in the built app, so Requirement 7 MUST be re-verified end-to-end against the running build (including cold start and the `showFriendsScreen`/`initialInviteId` plumbing into `WorkspaceScreen`), not merely confirmed in source.
- **Invite-sent feedback (re-verify in build).** `WorkspaceViewModel.sendInvite` writes an optimistic outgoing mirror, sets a transient `WorkspaceUiState.Success("Invite sent")` toast, and emits a one-shot `navigateToRequests` event that `WorkspaceScreen` collects to open the `RequestsList`. The user reports that in the built app only the small toast appears and the navigation/persistent representation is not observed. Requirements 4 and 5 MUST be re-verified against the running build; the transient toast must not be the sole confirmation.
- **Sent-invite data.** The collaborative-tasks invite model stores a Friend_Request under the recipient's account. Surfacing an Outgoing_Invite to the sender (Requirement 4) is implemented via a mirror subcollection at `/users/{senderUid}/outgoingInvites/{targetUid}`; its Firestore security rule was added but flagged as needing deploy, which is a candidate cause of the reported misbehavior and must be confirmed deployed.
- **Referral reward gate (Requirement 8).** The Referral_Reward is granted server-side by the `Reward_Granter` Cloud Function (growth-loops Requirement 3) using the Admin SDK. Disabling the reward reversibly is therefore primarily a server-side configuration/flag decision; the client `ReferralCta` reward-statement copy must also be reconciled with the disabled reward. The exact gating mechanism (Cloud Function feature flag, remote config, or equivalent) is flagged for design. The requirement defines the disabled outcome and reversibility, not the mechanism.
- **Search and paging at scale (Requirement 2 and Requirement 9).** The Friends_Leaderboard and Friends_List can each reach roughly a thousand rows. Incremental rendering alone (a `LazyColumn`) bounds composition size but does not bound how much data is loaded or make a target row reachable without long scrolling. The search and paged-loading behavior is therefore stated explicitly; the data-source paging/query approach and the search index location (client-side over loaded pages vs. server-side query) are flagged for design.
- **Material 3 Expressive availability.** The user explicitly requires the latest Material 3 Expressive components and expressive shapes/motion. The specific component and API versions, and any shape-morphing APIs used, are a design decision.

## Glossary

Terms marked "reused" carry the same meaning as in the referenced shipped spec and are not redefined here.

- **Social_Hub**: The redesigned social surface of the app (replacing the current single-scroll `WorkspaceScreen` titled "Friends"), presenting the signed-in user's identity, invites, Friends_Leaderboard, friends list, Circles entry, and referral call-to-action.
- **Social_Hub_Design_Language**: The established visual style of the current social surface, comprising the Cardfolio-style vibrant stacked cards, the lime hero ID card, rounded expressive card shapes, and the `RandomBackgrounds` PNG image packs.
- **Expressive_Component**: A component drawn from the latest Material 3 Expressive component set, used to build the Social_Hub.
- **Expressive_Motion_And_Shape**: Material 3 expressive motion and expressive ("alive") shape treatments, including shape morphing where applicable, used to make the Social_Hub feel animated and alive.
- **Friends_Leaderboard**: Reused from social-engagement — the ordered weekly ranking of the signed-in user and that user's friends by Productivity_Points earned within the current weekly window.
- **Friends_List**: The list of the signed-in user's established friends shown on the Social_Hub.
- **Section_Organizer**: The Social_Hub mechanism that organizes the Friends_Leaderboard and the Friends_List into distinct areas so neither dominates the other and scrolling stays manageable at large list sizes, providing a persistent control to navigate between the two areas.
- **Large_Friend_Set**: A signed-in user's friend collection whose size is large enough (on the order of 1000 friends) that rendering the Friends_Leaderboard and Friends_List in a single undivided scroll degrades usability.
- **Social_Search**: The Social_Hub mechanism that filters the currently-viewed area (the Friends_List or the Friends_Leaderboard) to the entries matching a user-entered query, so a user with a Large_Friend_Set can locate a person without scrolling.
- **Paged_Loading**: The incremental loading of the Friends_List and Friends_Leaderboard in successive fixed-size pages as the user scrolls, so that the full collection is not loaded at once.
- **Loading_State**: The Social_Hub presentation shown while the identity, invites, Friends_Leaderboard, or Friends_List data has not yet loaded.
- **Error_State**: The Social_Hub presentation shown when loading the Friends_Leaderboard or Friends_List fails, offering the user a way to retry.
- **Circle**: Reused from shared-circles — a named shared space owned by one Circle_Admin and joined by Circle_Members, containing one shared task list.
- **Circles_Entry**: The representation on the Social_Hub through which the signed-in user understands what Circles are and navigates to view and create Circles.
- **Preamble_ID**: Reused from collaborative-tasks — a short, normalized (uppercased, trimmed) public identifier mapping to exactly one user account.
- **Invite_Link**: Reused from collaborative-tasks/growth-loops — a shareable URL of the form `https://preamble.theblankstate.com/invite/{Preamble_ID}` that, when opened in the app, pre-fills a friend request to the link owner.
- **Friend_Request**: Reused from collaborative-tasks — a pending invitation record created by a sender and stored under the recipient's account until accepted or declined.
- **Incoming_Invite**: A Friend_Request for which the signed-in user is the recipient, shown on the Social_Hub with accept and decline controls.
- **Outgoing_Invite**: A Friend_Request that the signed-in user has sent to another user and that has not yet been accepted or declined.
- **Invite_Entry_Experience**: The on-theme input surface through which the signed-in user enters a friend's Preamble_ID, or opens an invite by link, to send a Friend_Request.
- **Requests_List**: The organized Social_Hub area that lists the signed-in user's Outgoing_Invites and Incoming_Invites.
- **Referral_CTA**: Reused from growth-loops — the in-app call-to-action inviting the user to refer a friend and exposing the user's Invite_Link for copying and sharing.
- **Referral_Reward**: Reused from growth-loops — the fixed two-sided 50-AI-credit reward granted on an eligible referred signup.
- **Reward_Granter**: Reused from growth-loops — the server-side Cloud Function that grants the Referral_Reward through the Admin SDK.
- **Development_Mode**: The current pre-launch state of the app in which the Referral_Reward is intentionally disabled.
- **Deep_Link_Router**: The component that resolves an opened `invite/{id}` deep link to an in-app destination.
- **Bottom_System_Inset**: The combined bottom region occupied by the app's bottom navigation bar and the device's system insets (navigation gesture area / software navigation bar) at the bottom of the Social_Hub, behind which list content must not be hidden.

## Requirements

### Requirement 1: Cohesive, alive Social Hub redesign

**User Story:** As a user, I want the social surface to feel beautiful, full, and alive, so that connecting with friends on Preamble feels engaging and delightful.

#### Acceptance Criteria

1. THE Social_Hub SHALL present the signed-in user's identity, the Friends_Leaderboard, the Friends_List, the Circles_Entry, the Referral_CTA, and the user's invites as a single cohesive surface.
2. THE Social_Hub SHALL be built using Expressive_Component elements from the latest Material 3 Expressive component set.
3. THE Social_Hub SHALL apply Expressive_Motion_And_Shape to its primary surfaces, including animated or morphing shape treatments, so that the surface presents motion rather than only static elements.
4. THE Social_Hub SHALL preserve the Social_Hub_Design_Language, retaining the vibrant stacked-card styling, the lime hero ID card, and the `RandomBackgrounds` PNG image packs.
5. THE Social_Hub SHALL display the signed-in user's Preamble_ID and current productivity score on the hero ID card.
6. WHILE the Social_Hub data has not yet loaded, THE Social_Hub SHALL present a Loading_State built from Expressive_Component elements rather than a blank surface.
7. IF loading the Friends_Leaderboard or the Friends_List fails, THEN THE Social_Hub SHALL present an Error_State that describes the failure and offers a control to retry loading.

### Requirement 2: Scalable organization of leaderboard and friends list

**User Story:** As a user with many friends, I want the leaderboard and the friends list organized so neither overwhelms the other, so that I can use the social surface without an endless single scroll.

#### Acceptance Criteria

1. THE Section_Organizer SHALL present the Friends_Leaderboard and the Friends_List as distinct, separately navigable areas rather than as one continuous undivided scroll.
2. WHERE the signed-in user has a Large_Friend_Set, THE Section_Organizer SHALL keep both the Friends_Leaderboard and the Friends_List reachable without requiring the user to scroll through the entire Friends_List to reach the Friends_Leaderboard.
3. WHILE the signed-in user views the Friends_List, THE Social_Hub SHALL render the Friends_List incrementally so that the number of friend entries simultaneously held in the composition does not grow proportionally to the total size of the Friends_List.
4. WHERE the signed-in user has no friends, THE Social_Hub SHALL display an empty-state indication that the user has no friends yet and SHALL present a control to add a friend.
5. WHEN the signed-in user navigates between the Friends_Leaderboard area and the Friends_List area, THE Social_Hub SHALL preserve each area's scroll position independently of the other.
6. THE Section_Organizer SHALL present a persistent, labeled navigation control that names the Friends_Leaderboard area and the Friends_List area and indicates which area is currently active.
7. WHILE the Friends_List is larger than one page, THE Social_Hub SHALL load Friends_List entries through Paged_Loading, fetching the next page as the user scrolls toward the end of the loaded entries rather than loading the entire Friends_List at once.
8. WHILE the Friends_Leaderboard is larger than one page, THE Social_Hub SHALL load Friends_Leaderboard entries through Paged_Loading, fetching the next page as the user scrolls toward the end of the loaded entries rather than loading the entire Friends_Leaderboard at once.

### Requirement 3: Circles are discoverable and clearly understood

**User Story:** As a user, I want to immediately understand that Circles let me create a shared friend group, so that I can discover and use Circles from the social surface.

#### Acceptance Criteria

1. THE Social_Hub SHALL present a Circles_Entry that is visible without the user activating any additional menu.
2. THE Circles_Entry SHALL communicate that a Circle is a shared friend group, using descriptive text that conveys grouping friends together rather than only an unlabeled icon.
3. WHEN the signed-in user activates the Circles_Entry, THE Social_Hub SHALL navigate to the Circles surface where the user can view existing Circles and create a new Circle.
4. THE Circles_Entry SHALL be styled consistently with the Social_Hub_Design_Language and SHALL incorporate imagery from the `RandomBackgrounds` PNG image packs.
5. WHERE the signed-in user belongs to no Circles, THE Circles_Entry SHALL still convey what a Circle is and SHALL present the option to create one.

### Requirement 4: Persistent, visible representation of sent invites

**User Story:** As a user who sent an invite, I want a clearly visible record that the invite was sent, so that I am not left wondering whether it worked after a brief toast disappears.

#### Acceptance Criteria

1. WHEN the signed-in user sends a Friend_Request to another user, THE Social_Hub SHALL create a persistent Outgoing_Invite representation that remains visible until the Friend_Request is accepted, declined, or withdrawn.
2. THE Social_Hub SHALL represent an Outgoing_Invite with a level of visual prominence and detail comparable to an Incoming_Invite, displaying the recipient's Preamble_ID and a status indicating the invite is awaiting a response.
3. THE Social_Hub SHALL visually distinguish an Outgoing_Invite from an Incoming_Invite so that the user can tell sent invites apart from received invites.
4. WHEN an Outgoing_Invite is accepted by its recipient, THE Social_Hub SHALL stop displaying that Outgoing_Invite and SHALL reflect the new friend in the Friends_List.
5. WHEN a Friend_Request is sent successfully, THE Social_Hub SHALL confirm the send through the persistent Outgoing_Invite representation in the Requests_List, and SHALL NOT rely on a transient toast as the only confirmation that the invite was sent.

### Requirement 5: Navigate to the requests list after sending an invite

**User Story:** As a user, I want to be taken to a list of my sent invites right after sending one, so that I can see it was sent instead of relying on a toast.

#### Acceptance Criteria

1. WHEN the signed-in user successfully sends a Friend_Request, THE Social_Hub SHALL automatically navigate the user to the Requests_List without requiring any further action.
2. WHEN the Social_Hub displays the Requests_List following a successful send, THE Requests_List SHALL include the just-sent Outgoing_Invite.
3. THE Requests_List SHALL present the signed-in user's Outgoing_Invites and Incoming_Invites in an organized arrangement that groups outgoing invites separately from incoming invites.
4. IF sending a Friend_Request fails, THEN THE Social_Hub SHALL display an error message indicating that the invite could not be sent and SHALL NOT create an Outgoing_Invite for the failed request.
5. WHERE the signed-in user has no Outgoing_Invites and no Incoming_Invites, THE Requests_List SHALL display an empty-state indication that there are no pending requests.
6. THE Social_Hub SHALL present a control that opens the Requests_List on demand at any time, indicating the number of pending Outgoing_Invites and Incoming_Invites.

### Requirement 6: Beautiful, on-theme invite-entry experience

**User Story:** As a user adding a friend, I want a beautiful on-theme way to enter a Preamble ID or use a link, so that inviting feels like part of the experience rather than a plain form.

#### Acceptance Criteria

1. WHERE the signed-in user chooses to add a friend by Preamble_ID, THE Invite_Entry_Experience SHALL present an on-theme entry surface styled consistently with the Social_Hub_Design_Language, using Expressive_Component elements and the `RandomBackgrounds` PNG image packs in place of a plain default text box.
2. WHERE the signed-in user opens the app from an Invite_Link, THE Invite_Entry_Experience SHALL present the same on-theme entry surface with the linked Preamble_ID pre-filled, in place of a plain default text box.
3. WHEN the signed-in user enters characters into the Invite_Entry_Experience, THE Invite_Entry_Experience SHALL normalize the entered Preamble_ID to uppercase as the user types.
4. WHILE the entered Preamble_ID is blank, THE Invite_Entry_Experience SHALL keep the send control disabled.
5. THE Invite_Entry_Experience SHALL apply Expressive_Motion_And_Shape so that the entry surface presents motion consistent with the rest of the Social_Hub.
6. THE Invite_Entry_Experience SHALL present the same on-theme entry surface for both manual Preamble_ID entry and Invite_Link entry, so that neither path shows a plain default text box.

### Requirement 7: Invite-link deep link lands on the social surface

**User Story:** As a user who taps an invite link, I want to land on the friends social surface, so that I can immediately act on the invite.

#### Acceptance Criteria

1. WHEN the app is opened from an `invite/{id}` deep link, THE Deep_Link_Router SHALL navigate to the Social_Hub and SHALL NOT open the collaborative-tasks workspace surface.
2. WHEN the Social_Hub is opened from an `invite/{id}` deep link, THE Social_Hub SHALL present the Invite_Entry_Experience with the Preamble_ID from the deep link pre-filled.
3. WHEN the deep-linked invite has been presented to the user, THE Social_Hub SHALL consume the deep-link target so that re-rendering the Social_Hub does not re-present the same invite.
4. IF the `invite/{id}` deep link carries a Preamble_ID that does not resolve to exactly one user account, THEN THE Social_Hub SHALL still open and SHALL display a message indicating that the invited identifier could not be found.
5. WHEN the app is launched from a cold start by an `invite/{id}` deep link, THE Deep_Link_Router SHALL land on the Social_Hub with the Preamble_ID pre-filled, without the user navigating manually.

### Requirement 8: Disable referral reward in development mode

**User Story:** As the app owner, I want the referral AI-credit reward turned off during development, so that no credits are granted while the invite flow keeps working.

#### Acceptance Criteria

1. WHILE the app is in Development_Mode, THE Reward_Granter SHALL NOT grant the Referral_Reward to either the referrer or the referred user for any referred signup.
2. WHILE the app is in Development_Mode, THE Referral_CTA SHALL NOT state that the inviting user and the invited user receive AI credits.
3. WHILE the Referral_Reward is disabled, THE Social_Hub SHALL continue to send Friend_Requests, accept Friend_Requests, and establish reciprocal friendships using the existing invite and accept-invite behavior.
4. THE Referral_Reward disablement SHALL be reversible through configuration without removing the referral attribution and invite behavior, so that the Referral_Reward can be re-enabled at a later time.
5. WHILE the Referral_Reward is disabled, THE Reward_Granter SHALL NOT modify any user's AI-credit balance on account of a referral.

### Requirement 9: Search across friends and leaderboard

**User Story:** As a user with many friends, I want to search the friends list and the leaderboard, so that I can find a specific person without scrolling through a thousand rows.

#### Acceptance Criteria

1. THE Social_Hub SHALL present a Social_Search control within both the Friends_List area and the Friends_Leaderboard area.
2. WHEN the signed-in user enters a query into the Social_Search control, THE Social_Search SHALL filter the currently-viewed area to the entries whose Preamble_ID or display name match the query.
3. THE Social_Search SHALL match queries case-insensitively against the Preamble_ID and display name.
4. WHEN the signed-in user clears the Social_Search query, THE Social_Hub SHALL restore the unfiltered Friends_List or Friends_Leaderboard for the currently-viewed area.
5. WHERE a Social_Search query matches no entries in the currently-viewed area, THE Social_Hub SHALL display an empty-state indication that no matching entries were found.
6. WHEN the signed-in user has a Large_Friend_Set, THE Social_Search SHALL search across the full Friends_List rather than only the entries already loaded through Paged_Loading.

### Requirement 10: Social Hub is fully scrollable with no content hidden behind the bottom navigation

**User Story:** As a user viewing the social surface, I want every element and every friend to be reachable by scrolling, so that I never lose access to content hidden below the screen and never mistake an off-screen friend for a missing one.

#### Acceptance Criteria

1. THE Social_Hub SHALL make the Friends_List pane scrollable so that every Friends_List entry, including the last entry, is reachable by scrolling.
2. THE Social_Hub SHALL make the Friends_Leaderboard pane scrollable so that every Friends_Leaderboard entry, including the last entry, is reachable by scrolling.
3. THE Social_Hub SHALL apply bottom content padding equal to or greater than the Bottom_System_Inset to the Friends_List pane and the Friends_Leaderboard pane so that no list entry remains hidden behind the Bottom_System_Inset when the pane is scrolled to its end.
4. WHERE the signed-in user has friends, THE Friends_List SHALL render every friend so that each friend is reachable by scrolling the Friends_List pane to its end.
5. THE Social_Hub SHALL allocate vertical space between the fixed header content and the Section_Organizer panes so that each pane receives a usable scrollable height independent of how tall the fixed header content is sized.
6. WHILE the Friends_List pane is scrolled to its end, THE Social_Hub SHALL keep the final Friends_List entry fully visible above the Bottom_System_Inset.
