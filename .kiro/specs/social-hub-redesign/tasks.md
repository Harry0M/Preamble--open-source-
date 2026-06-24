# Implementation Plan: Social Hub Redesign

## Overview

This plan evolves the existing `WorkspaceScreen` (the "Friends" overlay) in place into the Social_Hub. The existing screen, its layout, the Cardfolio-style stacked cards, and the lime hero ID card are GOOD and are preserved — all UI tasks extend/enhance the existing composables and layer in Material 3 Expressive "alive" material and the new capabilities rather than rebuilding the screen.

Work is sequenced bottom-up: the input-dependent decision logic is built first as pure, JVM-testable modules in `com.theblankstate.preamble.collab` (verified with jqwik property tests), then the repository/ViewModel data plumbing, then the in-place UI enhancements, the deep-link routing fix, and finally the server-side referral reward gate. Each step builds on the previous and is wired into the existing surface as it lands, so no orphaned code is left behind.

Property tests follow the repository's existing jqwik conventions (see `TestInfrastructureTest.kt`): they live in `app/src/test/java/com/theblankstate/preamble/collab`, run a minimum of 100 iterations (`@Property(tries = 100)` or higher), and each is tagged with a single-line comment of the form `// Feature: social-hub-redesign, Property {n}: {property text}`.

## Tasks

- [x] 1. Add pure decision modules and the OutgoingInvite data model
  - [x] 1.1 Create the `OutgoingInvite` data model and `OutgoingInviteReconciler`
    - Add `OutgoingInvite` data class (`targetUid`, `targetPreambleId`, `timestamp`) in `com.theblankstate.preamble.collab`
    - Implement `OutgoingInviteReconciler.visibleOutgoing(mirrored, friendUids)` returning mirrored invites whose target uid is not in the friend set, with no duplication or fabrication
    - _Requirements: 4.1, 4.4, 5.2_

  - [ ]* 1.2 Write property test for outgoing-invite reconciliation
    - **Property 3: Outgoing-invite reconciliation persists unresolved invites and drops accepted ones**
    - Generate random mirrored-invite lists and friend-uid sets; assert output equals mirrored-minus-friends with no loss/duplication/fabrication
    - **Validates: Requirements 4.1, 4.4, 5.2**

  - [x] 1.3 Implement `RequestsListOrganizer`
    - Add `RequestsListOrganizer` with `Sections(outgoing, incoming)` (plus `isEmpty`) and `organize(outgoing, incoming)` partitioning invites into outgoing/incoming groups with no loss, duplication, or cross-grouping
    - _Requirements: 5.3, 5.5_

  - [ ]* 1.4 Write property test for requests-list grouping
    - **Property 5: Requests_List grouping partitions invites without loss or duplication**
    - Generate random outgoing and incoming invite lists; assert `organize` partitions them exactly
    - **Validates: Requirements 5.3**

  - [x] 1.5 Implement `DeepLinkInviteState`
    - Add `DeepLinkInviteState` with `toPresent(pending)` and `consume(pending)` such that after consume the value to present is null and consuming again stays null (idempotent)
    - _Requirements: 7.3_

  - [ ]* 1.6 Write property test for deep-link consumption
    - **Property 6: Deep-link invite presentation is consumed at most once**
    - Generate arbitrary pending ids; assert `toPresent` after `consume` is null and idempotent
    - **Validates: Requirements 7.3**

  - [ ]* 1.7 Write property tests for Preamble_ID input handling (reusing existing `PreambleId.normalize`)
    - **Property 1: Preamble_ID input normalization is uppercase, trimmed, and idempotent** — generate arbitrary strings; assert `PreambleId.normalize` output is trimmed, has no lowercase, and `normalize(normalize(x)) == normalize(x)`. **Validates: Requirements 6.3**
    - **Property 2: The send control is enabled exactly when the entered id is non-blank** — generate whitespace-only and mixed strings; assert send-enabled ⇔ normalized id is non-blank. **Validates: Requirements 6.4**

- [x] 2. Checkpoint - Ensure all tests pass
  - Ensure all pure-module tests pass, ask the user if questions arise.

- [x] 3. Extend `WorkspaceRepository` for the outgoing-invite mirror
  - [x] 3.1 Add the outgoing-invite mirror read/write to `WorkspaceRepository`
    - Add `getOutgoingInvitesFlow(): Flow<List<OutgoingInvite>>` over `/users/{senderUid}/outgoingInvites`, following the existing snapshot-listener pattern (error closes the flow, last value retained)
    - Extend `sendInvite(...)` so the successful write becomes a single Firestore batch writing BOTH the recipient's incoming Friend_Request and the sender mirror at `/users/{senderUid}/outgoingInvites/{targetUid}`, preserving all existing validation gates so a failed send creates neither doc
    - Add `withdrawInvite(targetUid): Result<Unit>` deleting both docs
    - _Requirements: 4.1, 5.4_

  - [ ]* 3.2 Write unit tests for the repository batch and withdraw behavior
    - Verify both docs are written in one batch on success and neither on failure; verify withdraw deletes both docs
    - _Requirements: 4.1, 5.4_

- [x] 4. Extend `WorkspaceViewModel` to orchestrate outgoing invites and deep-link presentation
  - [x] 4.1 Wire outgoing invites, requests sections, and send/withdraw orchestration
    - Add `outgoingInvites: StateFlow<List<OutgoingInvite>>` derived from `getOutgoingInvitesFlow()` reconciled with `_friends` via `OutgoingInviteReconciler.visibleOutgoing`
    - Add `requestsSections: StateFlow<RequestsListOrganizer.Sections>` combining `outgoingInvites` and existing `invites`
    - Extend `sendInvite` to optimistically add the mirror and emit a one-shot "navigate to Requests_List" event on success; on failure surface `WorkspaceUiState.Error` and revert the optimistic entry so the outgoing set is unchanged
    - Add `withdrawInvite(targetUid)`
    - _Requirements: 4.4, 5.1, 5.2, 5.4_

  - [x] 4.2 Route deep-link invite presentation through `DeepLinkInviteState`
    - Hold pending deep-link id state and expose the value to present via `DeepLinkInviteState.toPresent`; make `onInviteConsumed` call `consume` so re-render is idempotent
    - Surface a not-found message (reusing the existing `resolvePreambleId` null path / `InviteValidation.NotFound`) when the id does not resolve to exactly one account
    - _Requirements: 7.2, 7.3, 7.4_

  - [ ]* 4.3 Write property test for failed-send invariance
    - **Property 4: A failed send leaves the outgoing-invite set unchanged**
    - Generate a prior outgoing set; apply a simulated failed send; assert the resulting outgoing set equals the prior set
    - **Validates: Requirements 5.4**

  - [ ]* 4.4 Write unit tests for send/navigation and not-found handling
    - Verify a successful send emits the navigate-to-Requests_List event (5.1); verify an unresolvable deep-link id still opens with a not-found message (7.4)
    - _Requirements: 5.1, 7.4_

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all repository and ViewModel tests pass, ask the user if questions arise.

- [x] 6. Enhance the existing `WorkspaceScreen` (Social_Hub) in place
  - [x] 6.1 Extend the `WorkspaceScreen` signature and preserved composition with Social_Hub wiring
    - Add the additive parameters (`onOpenCircles`, deep-link consumption) to the existing `WorkspaceScreen`; keep all existing parameters and the established layout/composition intact
    - Keep the surface a full-screen overlay opened from Home and the deep link; do NOT create a new screen
    - _Requirements: 1.1_

  - [x] 6.2 Enrich the existing lime hero ID card in place
    - Extend the existing hero ID card composable to keep showing the signed-in user's Preamble_ID and current productivity score, retaining the lime styling and `RandomBackgrounds` PNG, and layer in an expressive morphing shape treatment (Expressive_Motion_And_Shape)
    - _Requirements: 1.4, 1.5, 1.3_

  - [x] 6.3 Add the always-visible `CirclesEntryCard` to the existing composition
    - Add a styled card (consistent with the existing design language, may reuse `RandomBackgrounds`) carrying descriptive text that a Circle is a shared friend group, visible without opening any menu, conveying the meaning and a "create a Circle" option even with no Circles, and invoking `onOpenCircles` to route to the existing `CirclesScreen`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [x] 6.4 Add the `SectionOrganizer` layered onto the existing list composition
    - Add `SocialSection` enum and a `SectionOrganizer` composable using a Material 3 Expressive segmented/tab control that switches between the Leaderboard pane and the Friends_List pane
    - Give each pane its own `LazyListState` so scroll positions are preserved independently; keep the established card styling within each pane
    - Render the Friends_List pane as a `LazyColumn` so only visible rows are composed regardless of total size; show a no-friends empty-state with an "add a friend" control
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 6.5 Adopt Material 3 Expressive components and expressive motion across the enhanced surface
    - Replace/wrap the surface's primary controls and cards with Expressive_Component elements and apply Expressive_Motion_And_Shape (animated/morphing shapes) while preserving the existing Cardfolio stacked-card layout, lime hero card, and `RandomBackgrounds` packs
    - _Requirements: 1.2, 1.3, 1.4_

  - [ ]* 6.6 Write unit/UI tests for the enhanced Social_Hub composition
    - Verify the hero card renders Preamble_ID and productivity score (1.5); the surface presents all named sub-surfaces (1.1); Circles_Entry is visible with descriptive text and create option including the no-circles case and invokes `onOpenCircles` (3.1, 3.2, 3.3, 3.5); the Section_Organizer exposes two distinct selectable areas reachable with a large list, retains independent scroll positions, and shows the no-friends empty-state with an add control (2.1, 2.2, 2.4, 2.5)
    - _Requirements: 1.1, 1.5, 2.1, 2.2, 2.4, 2.5, 3.1, 3.2, 3.3, 3.5_

  - [ ]* 6.7 Add visual/snapshot review coverage for preserved design language and expressive treatment
    - Snapshot the enhanced surface to confirm preserved Cardfolio styling, lime hero card, and `RandomBackgrounds` reuse alongside Material 3 Expressive components and expressive motion/shape
    - _Requirements: 1.2, 1.3, 1.4, 3.4_

- [x] 7. Add the `InviteEntrySheet` (Invite_Entry_Experience) and the `RequestsList`
  - [x] 7.1 Implement the `InviteEntrySheet` replacing the plain add-friend dialog
    - Add an on-theme entry surface (Expressive_Component + `RandomBackgrounds` + expressive motion) styled consistently with the existing surface, in place of the plain add-friend dialog
    - Normalize entry to uppercase as the user types via `PreambleId.normalize`; support a pre-filled id from a link/deep link; keep the send control disabled while the normalized id is blank
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [x] 7.2 Implement the `RequestsList` and wire post-send navigation
    - Render Outgoing_Invites and Incoming_Invites in grouped sections using `RequestsListOrganizer`, each Outgoing_Invite showing recipient Preamble_ID and an "awaiting response" status with prominence comparable to and visually distinguished from incoming invites; show an empty-state when there are no invites
    - Navigate the user to the `RequestsList` on a successful send (consuming the ViewModel one-shot event), ensuring the just-sent invite is present
    - _Requirements: 4.2, 4.3, 5.1, 5.2, 5.3, 5.5_

  - [ ]* 7.3 Write unit tests for the invite-entry sheet and requests list
    - Verify the sheet pre-fills from a link/deep link (6.2); the outgoing invite renders recipient Preamble_ID + awaiting-response status (4.2); the Requests_List empty-state shows when there are no invites (5.5)
    - _Requirements: 4.2, 5.5, 6.2_

- [x] 8. Fix the invite deep-link routing to land on the Social_Hub
  - [x] 8.1 Route `invite/{id}` to the enhanced friends overlay
    - Change the `invite/` branch in `PreambleApp`'s deep-link `LaunchedEffect` to open the enhanced friends overlay (`showFriendsScreen = true`) instead of selecting the collaborative-tasks tab (`selectedTab = 4`), retaining the existing `initialInviteId` plumbing into `WorkspaceScreen`
    - _Requirements: 7.1, 7.2_

  - [ ]* 8.2 Write unit test for deep-link routing
    - Verify `invite/{id}` maps to the Social_Hub overlay rather than `selectedTab = 4`
    - _Requirements: 7.1_

- [x] 9. Checkpoint - Ensure all tests pass
  - Ensure all client UI and routing tests pass, ask the user if questions arise.

- [x] 10. Implement the server-side referral reward gate
  - [x] 10.1 Add the `REFERRAL_REWARDS_ENABLED` flag and `gatedReferralDecision`
    - Add `REFERRAL_REWARDS_ENABLED` to `functions/src/config.ts` (sourced from the `REFERRAL_REWARDS_ENABLED` env var, defaulting to disabled in Development_Mode)
    - Add the pure `gatedReferralDecision(input, rewardsEnabled)` in `functions/src/referrals.ts` returning `RejectRewardsDisabled` when disabled and otherwise delegating to the existing `classifyReferralEligibility`
    - _Requirements: 8.1, 8.4, 8.5_

  - [x] 10.2 Wire the gate into `onReferralFriendship`
    - Call `gatedReferralDecision(..., REFERRAL_REWARDS_ENABLED)` so the credit-increment transaction never runs while disabled, leaving attribution, friendship establishment, and funnel logging untouched (reversible via configuration)
    - _Requirements: 8.1, 8.3, 8.4, 8.5_

  - [ ]* 10.3 Write property test for the referral reward gate (TypeScript functions test suite)
    - **Property 7: The referral reward gate grants nothing while rewards are disabled**
    - Generate arbitrary `ReferralInput`; assert `gatedReferralDecision(input, rewardsEnabled = false)` is never `Eligible`
    - **Validates: Requirements 8.1, 8.5**

  - [ ]* 10.4 Write integration test for the disabled-reward invite/accept flow
    - With the reward disabled, verify sending and accepting an invite still establishes the reciprocal friendship and modifies no AI-credit balances (1–2 representative cases against the functions emulator/mocks)
    - _Requirements: 8.3_

- [x] 11. Reconcile the Referral CTA copy with the disabled reward
  - [x] 11.1 Update `ReferralCta` reward-statement copy
    - Change the reused `ReferralCta` copy so it no longer states that both sides receive AI credits while the reward is disabled, keeping it wired into the enhanced Social_Hub
    - _Requirements: 8.2_

  - [ ]* 11.2 Write unit test for CTA copy gating
    - Verify the CTA omits the credits statement when the reward is disabled and that flipping the flag re-enables the eligibility path
    - _Requirements: 8.2, 8.4_

- [x] 12. Final checkpoint - Ensure all tests pass
  - Ensure all client and server tests pass, ask the user if questions arise.

## Enhancement Pass (expanded requirements/design)

> Tasks 1–12 above are shipped and remain checked. The tasks below cover ONLY the delta
> introduced by the expanded requirements (Req 1.6, 1.7, 2.6, 2.7, 2.8, 3.4, 5.6, 6.6, 9.1–9.6)
> and the expanded design (Social_Search, PageWindow, enriched Leaderboard.Entry,
> Loading/Error/empty states, persistent labeled section control, on-demand requests count,
> paged loading for both panes, and the deploy/build verification of the already-shipped
> invite-send + deep-link behavior). Sequencing stays bottom-up: new pure modules first
> (jqwik-verified), then the data/ViewModel layer, then the in-place UI enhancements, and
> finally the deploy/build re-verification of Req 4, 5, and 7. Property tests follow the
> existing conventions: under `app/src/test/java/com/theblankstate/preamble/collab`, minimum
> 100 iterations, tagged `// Feature: social-hub-redesign, Property {n}: ...`.

- [x] 13. Add the new pure decision modules for search and paging
  - [x] 13.1 Implement `SocialSearch` (filtering over the full in-memory list)
    - Add `SocialSearch` in `com.theblankstate.preamble.collab` with the `Searchable` interface (`preambleId`, `displayName`) and `filter(query, entries)` returning the order-preserving sublist of entries whose Preamble_ID or display name contains the query, compared case-insensitively
    - A blank query returns the input list unchanged; the result never fabricates or duplicates entries; filtering operates on the full list independent of any paging window
    - _Requirements: 9.2, 9.3, 9.4, 9.6_

  - [ ]* 13.2 Write property test for `SocialSearch.filter`
    - **Property 8: Social_Search returns exactly the case-insensitively matching entries, over the full list**
    - Generate searchable entries with varied Preamble_ID/display-name casing and queries (substrings, mismatched case, blank); assert membership equivalence (kept iff case-insensitive match on preambleId or displayName), sublist/order preservation, and blank-query identity; use large generated lists to exercise the full-list guarantee
    - **Validates: Requirements 9.2, 9.3, 9.4, 9.6**

  - [x] 13.3 Implement `PageWindow` (client-side render windowing)
    - Add `PageWindow` in `com.theblankstate.preamble.collab` with `PAGE_SIZE = 30`, `visible(all, pageCount, pageSize)` returning the prefix of `all` of length `min(pageCount * pageSize, all.size)` (empty when `pageCount <= 0`), and `shouldLoadMore(lastVisibleIndex, loadedCount, threshold = 5)`
    - _Requirements: 2.7, 2.8_

  - [ ]* 13.4 Write property test for `PageWindow.visible`
    - **Property 9: Paged_Loading exposes a growing prefix that eventually covers the whole list**
    - Generate random lists and page counts (0, mid, beyond size); assert `visible` is a prefix of the input, has length `min(pageCount*pageSize, size)`, is monotonic non-decreasing in `pageCount`, and equals the full list once `pageCount` is large enough
    - **Validates: Requirements 2.7, 2.8**

- [x] 14. Enrich `Leaderboard.Entry` with Preamble_ID
  - [x] 14.1 Add `preambleId` to `Leaderboard.Entry` and propagate it through `Leaderboard.ranking`
    - Add the `preambleId: String = ""` field to `Leaderboard.Entry` in `com.theblankstate.preamble.collab`
    - Extend `Leaderboard.ranking` to also receive/propagate a per-uid Preamble_ID map (sourced from the friend directory map the ranking already receives), populating each entry's `preambleId` so leaderboard rows satisfy `SocialSearch.Searchable`
    - _Requirements: 9.3_

  - [ ]* 14.2 Write unit test for Preamble_ID propagation in ranking
    - Verify `Leaderboard.ranking` populates each entry's `preambleId` from the directory map and leaves ordering/points unchanged when ids are absent
    - _Requirements: 9.3_

- [x] 15. Checkpoint - Ensure all tests pass
  - Ensure the new pure-module and ranking tests pass, ask the user if questions arise.

- [x] 16. Extend `WorkspaceViewModel` for load state, pending count, and leaderboard ids
  - [x] 16.1 Add `socialHubLoadState` and `retryLoad()`
    - Add `socialHubLoadState: StateFlow<SocialHubLoadState>` (`Loading | Loaded | Error(message)`) derived from the first friends/leaderboard emission and list-listener failures, retaining prior loaded data on transient failure
    - Add `retryLoad()` that re-subscribes the failed friends/leaderboard flows
    - _Requirements: 1.6, 1.7_

  - [x] 16.2 Add `pendingRequestsCount`
    - Add `pendingRequestsCount: StateFlow<Int>` derived from `requestsSections` as `outgoing.size + incoming.size` to drive the on-demand Requests control badge
    - _Requirements: 5.6_

  - [x] 16.3 Populate leaderboard entries with `preambleId`
    - Supply the per-uid Preamble_ID map (from the friend records the ViewModel already holds) into the enriched `Leaderboard.ranking` so emitted `leaderboard` entries carry `preambleId`
    - _Requirements: 9.3_

  - [ ]* 16.4 Write unit tests for the ViewModel additions
    - Verify `socialHubLoadState` transitions Loading → Loaded on first emission and → Error on listener failure with `retryLoad()` re-subscribing (1.6, 1.7); `pendingRequestsCount` equals outgoing + incoming (5.6); leaderboard entries carry `preambleId` (9.3)
    - _Requirements: 1.6, 1.7, 5.6, 9.3_

- [x] 17. Checkpoint - Ensure all tests pass
  - Ensure the ViewModel tests pass, ask the user if questions arise.

- [ ] 18. Enhance the `SectionOrganizer` with persistent control, paging, and search
  - [x] 18.1 Make the section control persistent, labeled, and active-aware
    - Render the segmented control as a sticky header that stays visible above the active pane, naming both the Leaderboard and Friends areas and marking the active one via the Expressive selected state
    - _Requirements: 2.6_

  - [x] 18.2 Wire client-side `Paged_Loading` for BOTH panes via `PageWindow`
    - Give each pane a `loadedPageCount` state; render `PageWindow.visible(fullList, loadedPageCount)` plus a trailing sentinel; a `LaunchedEffect` on the pane `LazyListState` increments the page count when `PageWindow.shouldLoadMore` is satisfied, until the whole list is shown, for both the Friends_List and the Leaderboard panes
    - _Requirements: 2.7, 2.8_

  - [x] 18.3 Add the `Social_Search` field to both panes, filtering before windowing
    - Add an Expressive search field above each pane bound to per-pane query state; apply `SocialSearch.filter` to the FULL in-memory list before `PageWindow.visible`, so search covers the whole set; clearing the query restores the unfiltered list
    - _Requirements: 9.1, 9.2, 9.4, 9.6_

  - [ ]* 18.4 Write unit/UI tests for the section organizer enhancements
    - Verify the persistent control names both areas and marks the active one (2.6); scrolling toward the end increments the loaded page count for both panes (2.7, 2.8); the search field renders in both panes and clearing the query restores the unfiltered list (9.1, 9.4)
    - _Requirements: 2.6, 2.7, 2.8, 9.1, 9.4_

- [x] 19. Add Loading_State, Error_State, and empty states to the surface
  - [x] 19.1 Render the Loading_State skeleton and the Error_State with retry
    - While `socialHubLoadState == Loading`, render an Expressive skeleton/shimmer instead of a blank surface; on `Error`, render an Expressive Error_State card describing the failure with a retry control calling `retryLoad()`
    - _Requirements: 1.6, 1.7_

  - [x] 19.2 Add the no-friends and no-match empty states
    - Show the no-friends empty-state with an "add a friend" control in the Friends pane; show a no-match empty-state when a `Social_Search` query matches nothing in the active pane
    - _Requirements: 2.4, 9.5_

  - [ ]* 19.3 Write unit/UI tests for the edge states
    - Verify the skeleton shows while Loading (1.6), the Error_State shows on failure with a working retry (1.7), the no-friends empty-state shows an add control (2.4), and a non-matching query shows the no-match empty-state (9.5)
    - _Requirements: 1.6, 1.7, 2.4, 9.5_

- [x] 20. Bind the on-demand Requests control to the pending count
  - [x] 20.1 Wire the Requests control badge to `pendingRequestsCount`
    - Bind the existing top-bar Requests entry point to `pendingRequestsCount` and render an Expressive badge showing the number of pending outgoing + incoming invites; tapping it opens the Requests_List at any time
    - _Requirements: 5.6_

  - [ ]* 20.2 Write unit test for the pending-count badge
    - Verify the control reflects the pending count and opens the Requests_List on demand
    - _Requirements: 5.6_

- [x] 21. Style the Circles_Entry with `RandomBackgrounds` imagery
  - [x] 21.1 Incorporate PNG-pack imagery into the `CirclesEntryCard`
    - Strengthen the `CirclesEntryCard` styling to incorporate imagery from the `RandomBackgrounds` PNG image packs, consistent with the Social_Hub_Design_Language, while retaining its descriptive "shared friend group"/create-a-Circle content
    - _Requirements: 3.4_

- [x] 22. Confirm the same invite sheet for manual and link entry
  - [x] 22.1 Verify/adjust `InviteEntrySheet` parity across both entry paths
    - Confirm the same on-theme `InviteEntrySheet` is presented for both manual Preamble_ID entry and Invite_Link/deep-link entry (only the initial value differs, fed into the same `targetId` state); adjust only if a divergence is found so neither path shows a plain default text box
    - _Requirements: 6.6_

- [x] 23. Apply Material 3 Expressive components and motion across the enhanced surfaces
  - [x] 23.1 Apply Expressive components and morphing-shape/motion to the new surfaces
    - Apply the latest Material 3 Expressive components and expressive morphing-shape/motion across the enhanced surfaces (Loading_State, Error_State, Social_Search field, and the persistent section control), consistent with the existing Cardfolio stacked cards, lime hero card, and `RandomBackgrounds` packs
    - _Requirements: 1.2, 1.3_

  - [ ]* 23.2 Add snapshot/visual review coverage for the enhanced surfaces
    - Snapshot the Loading_State, Error_State, search field, and section control to confirm Expressive components and expressive motion/shape alongside the preserved design language
    - _Requirements: 1.2, 1.3_

- [x] 24. Checkpoint - Ensure all tests pass
  - Ensure all new pure-module, ViewModel, and UI tests pass, ask the user if questions arise.

- [ ] 25. Deploy / build verification of the shipped invite-send and deep-link behavior
  - [~] 25.1 Deploy the Firestore rules so the `/outgoingInvites` create rule is live
    - Run `firebase deploy --only firestore:rules`; confirm the `/users/{uid}/outgoingInvites/{targetId}` create rule is live (the atomic batch in `sendInvite` fails entirely without it); re-run the firebase-rules-tests suite against the deployed ruleset
    - _Requirements: 4.1, 5.4_

  - [~] 25.2 Rebuild and reinstall the app
    - Rebuild and reinstall so the installed APK includes this spec's `sendInvite` batch, `navigateToRequests`, persistent Outgoing_Invite, and the `invite/` → `showFriendsScreen` routing rather than the stale single-write/`selectedTab = 4` behavior
    - _Requirements: 4.1, 5.1, 7.1_

  - [~] 25.3 Re-verify the end-to-end invite-send navigation and persistent Outgoing_Invite
    - On the running build, send an invite and confirm it navigates to the Requests_List, the Outgoing_Invite persists with an "awaiting response" status, and the transient toast is not the sole confirmation
    - _Requirements: 4.1, 4.2, 4.5, 5.1, 5.2_

  - [~] 25.4 Re-verify the deep-link landing including cold start
    - Open `https://preamble.theblankstate.com/invite/{id}` from a fully-killed app and confirm it lands on the Social_Hub with the id pre-filled (not the workspace); confirm a warm `onNewIntent` launch behaves the same
    - _Requirements: 7.1, 7.2, 7.5_

- [ ] 26. Make the Social_Hub fully scrollable with no content hidden behind the bottom inset
  - [ ] 26.1 Delegate the bottom inset to the panes in the overlay `Scaffold` (Fix 1)
    - In `WorkspaceScreen`'s overlay `Scaffold`, set `contentWindowInsets` to apply the **top and horizontal** system-bar insets but NOT the bottom (e.g. `ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)`), so the outer `PullToRefreshBox` no longer consumes the bottom inset and each pane's `LazyColumn` can extend to the physical bottom edge — avoiding double-counting the inset; the custom top bar continues to handle the status-bar inset
    - _Requirements: 10.5_

  - [ ] 26.2 Apply unconditional bottom `contentPadding` >= Bottom_System_Inset on BOTH panes (Fix 2)
    - In `SectionOrganizer`, resolve the Bottom_System_Inset once (`WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` plus a ~24dp comfortable gap) and pass it as `contentPadding = PaddingValues(bottom = paneBottomPadding)` to BOTH pane `LazyColumn`s (Friends_List and Friends_Leaderboard), applied UNCONDITIONALLY regardless of paging state so the clearance is `>= Bottom_System_Inset` by construction on every device
    - _Requirements: 10.3, 10.6_

  - [ ] 26.3 Stop using the conditional 80.dp spacer as the clearance mechanism (Fix 3)
    - Remove the fixed `80.dp` `Spacer` from `PagingSentinel`'s `else` (fully-loaded) branch as the clearance mechanism (it becomes `Spacer(Modifier.height(0.dp))` or is omitted); keep the `16.dp`-padded `CircularProgressIndicator` only as the loading affordance while `hasMore` is true — clearance now comes from the unconditional pane `contentPadding` added in 26.2, removing the paging-state coupling
    - _Requirements: 10.6_

  - [ ]* 26.4 Write UI tests for unconditional bottom clearance and scroll-to-end visibility
    - Assert each pane's `LazyColumn` carries a non-zero bottom `contentPadding` resolved from `WindowInsets.navigationBars` (>= Bottom_System_Inset) UNCONDITIONALLY — present whether or not more pages remain (regression guard against the old `hasMore`-coupled 80.dp spacer)
    - Add a scroll-to-end test confirming the final Friends_List row is fully visible above the Bottom_System_Inset, including the single-page case where the whole list fits without a paging spinner
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.6_

## Notes

- Tasks marked with `*` are optional (tests) and can be skipped for a faster MVP.
- All UI tasks extend the existing `WorkspaceScreen` and its composables in place — the current layout, Cardfolio stacked cards, and lime hero ID card are preserved and enhanced, never rebuilt.
- Each property from the design's Correctness Properties section maps to exactly one property-based test (jqwik for the Kotlin pure modules, a TypeScript test for the reward gate), tagged `// Feature: social-hub-redesign, Property {n}: ...` and running a minimum of 100 iterations.
- Look-and-feel requirements (1.2, 1.3, 1.4, 3.4, 4.3, 6.1, 6.5) are covered by unit/UI, snapshot, and manual review rather than property tests.
- Each task references specific granular requirements for traceability; checkpoints ensure incremental validation.
- Enhancement pass: Properties 8 (`SocialSearch.filter`) and 9 (`PageWindow.visible`) each map to exactly one new jqwik property test (tasks 13.2, 13.4), tagged `// Feature: social-hub-redesign, Property {n}: ...` at a minimum of 100 iterations.
- The deploy/build-verification group (task 25) re-verifies the already-shipped invite-send (Req 4, 5) and deep-link (Req 7) behavior against the running, deployed artifact, addressing the reported "still shows toast / still opens Workspace" symptoms; it is the only group that is not purely code.
- Requirement 10 (task 26) is a layout/window-inset fix — full scrollability and bottom `contentPadding` >= Bottom_System_Inset. Per the design, it adds NO new correctness property (its data-coverage half is already underwritten by Property 9); 26.4 is example/UI coverage, not a property test. Tasks 26.1–26.3 all touch `WorkspaceScreen.kt` (the overlay `Scaffold`, `SectionOrganizer`, and `PagingSentinel`), so they are scheduled sequentially in separate waves to avoid same-file conflicts.

## Task Dependency Graph

The graph below covers the NEW enhancement-pass leaf tasks (13.1 onward). Tasks 1–12 are
already complete and are not scheduled. Tasks that write the same file (the `WorkspaceViewModel`
in group 16; the enhanced `WorkspaceScreen`/`SectionOrganizer` surface across groups 18–20, 23;
and the Req 10 fix in group 26, which touches the same `WorkspaceScreen.kt`)
are placed in separate waves; the deploy/verification steps (25.x) run last and in order, and
the Req 10 tasks (26.1 → 26.2 → 26.3 → 26.4) run sequentially after the existing waves.

```json
{
  "waves": [
    { "id": 0, "tasks": ["13.1", "13.3"] },
    { "id": 1, "tasks": ["13.2", "13.4", "14.1"] },
    { "id": 2, "tasks": ["14.2", "16.1"] },
    { "id": 3, "tasks": ["16.2"] },
    { "id": 4, "tasks": ["16.3", "21.1"] },
    { "id": 5, "tasks": ["16.4", "18.1"] },
    { "id": 6, "tasks": ["18.2"] },
    { "id": 7, "tasks": ["18.3"] },
    { "id": 8, "tasks": ["18.4", "19.1"] },
    { "id": 9, "tasks": ["19.2"] },
    { "id": 10, "tasks": ["19.3", "20.1"] },
    { "id": 11, "tasks": ["20.2", "22.1"] },
    { "id": 12, "tasks": ["23.1"] },
    { "id": 13, "tasks": ["23.2"] },
    { "id": 14, "tasks": ["25.1"] },
    { "id": 15, "tasks": ["25.2"] },
    { "id": 16, "tasks": ["25.3", "25.4"] },
    { "id": 17, "tasks": ["26.1"] },
    { "id": 18, "tasks": ["26.2"] },
    { "id": 19, "tasks": ["26.3"] },
    { "id": 20, "tasks": ["26.4"] }
  ]
}
```
