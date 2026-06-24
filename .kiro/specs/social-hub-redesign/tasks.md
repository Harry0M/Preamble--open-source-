# Implementation Plan: Social Hub Redesign

## Overview

This plan evolves the existing `WorkspaceScreen` (the "Friends" overlay) in place into the Social_Hub. The existing screen, its layout, the Cardfolio-style stacked cards, and the lime hero ID card are GOOD and are preserved — all UI tasks extend/enhance the existing composables and layer in Material 3 Expressive "alive" material and the new capabilities rather than rebuilding the screen.

Work is sequenced bottom-up: the input-dependent decision logic is built first as pure, JVM-testable modules in `com.theblankstate.preamble.collab` (verified with jqwik property tests), then the repository/ViewModel data plumbing, then the in-place UI enhancements, the deep-link routing fix, and finally the server-side referral reward gate. Each step builds on the previous and is wired into the existing surface as it lands, so no orphaned code is left behind.

Property tests follow the repository's existing jqwik conventions (see `TestInfrastructureTest.kt`): they live in `app/src/test/java/com/theblankstate/preamble/collab`, run a minimum of 100 iterations (`@Property(tries = 100)` or higher), and each is tagged with a single-line comment of the form `// Feature: social-hub-redesign, Property {n}: {property text}`.

## Tasks

- [ ] 1. Add pure decision modules and the OutgoingInvite data model
  - [ ] 1.1 Create the `OutgoingInvite` data model and `OutgoingInviteReconciler`
    - Add `OutgoingInvite` data class (`targetUid`, `targetPreambleId`, `timestamp`) in `com.theblankstate.preamble.collab`
    - Implement `OutgoingInviteReconciler.visibleOutgoing(mirrored, friendUids)` returning mirrored invites whose target uid is not in the friend set, with no duplication or fabrication
    - _Requirements: 4.1, 4.4, 5.2_

  - [ ]* 1.2 Write property test for outgoing-invite reconciliation
    - **Property 3: Outgoing-invite reconciliation persists unresolved invites and drops accepted ones**
    - Generate random mirrored-invite lists and friend-uid sets; assert output equals mirrored-minus-friends with no loss/duplication/fabrication
    - **Validates: Requirements 4.1, 4.4, 5.2**

  - [ ] 1.3 Implement `RequestsListOrganizer`
    - Add `RequestsListOrganizer` with `Sections(outgoing, incoming)` (plus `isEmpty`) and `organize(outgoing, incoming)` partitioning invites into outgoing/incoming groups with no loss, duplication, or cross-grouping
    - _Requirements: 5.3, 5.5_

  - [ ]* 1.4 Write property test for requests-list grouping
    - **Property 5: Requests_List grouping partitions invites without loss or duplication**
    - Generate random outgoing and incoming invite lists; assert `organize` partitions them exactly
    - **Validates: Requirements 5.3**

  - [ ] 1.5 Implement `DeepLinkInviteState`
    - Add `DeepLinkInviteState` with `toPresent(pending)` and `consume(pending)` such that after consume the value to present is null and consuming again stays null (idempotent)
    - _Requirements: 7.3_

  - [ ]* 1.6 Write property test for deep-link consumption
    - **Property 6: Deep-link invite presentation is consumed at most once**
    - Generate arbitrary pending ids; assert `toPresent` after `consume` is null and idempotent
    - **Validates: Requirements 7.3**

  - [ ]* 1.7 Write property tests for Preamble_ID input handling (reusing existing `PreambleId.normalize`)
    - **Property 1: Preamble_ID input normalization is uppercase, trimmed, and idempotent** — generate arbitrary strings; assert `PreambleId.normalize` output is trimmed, has no lowercase, and `normalize(normalize(x)) == normalize(x)`. **Validates: Requirements 6.3**
    - **Property 2: The send control is enabled exactly when the entered id is non-blank** — generate whitespace-only and mixed strings; assert send-enabled ⇔ normalized id is non-blank. **Validates: Requirements 6.4**

- [ ] 2. Checkpoint - Ensure all tests pass
  - Ensure all pure-module tests pass, ask the user if questions arise.

- [ ] 3. Extend `WorkspaceRepository` for the outgoing-invite mirror
  - [ ] 3.1 Add the outgoing-invite mirror read/write to `WorkspaceRepository`
    - Add `getOutgoingInvitesFlow(): Flow<List<OutgoingInvite>>` over `/users/{senderUid}/outgoingInvites`, following the existing snapshot-listener pattern (error closes the flow, last value retained)
    - Extend `sendInvite(...)` so the successful write becomes a single Firestore batch writing BOTH the recipient's incoming Friend_Request and the sender mirror at `/users/{senderUid}/outgoingInvites/{targetUid}`, preserving all existing validation gates so a failed send creates neither doc
    - Add `withdrawInvite(targetUid): Result<Unit>` deleting both docs
    - _Requirements: 4.1, 5.4_

  - [ ]* 3.2 Write unit tests for the repository batch and withdraw behavior
    - Verify both docs are written in one batch on success and neither on failure; verify withdraw deletes both docs
    - _Requirements: 4.1, 5.4_

- [ ] 4. Extend `WorkspaceViewModel` to orchestrate outgoing invites and deep-link presentation
  - [ ] 4.1 Wire outgoing invites, requests sections, and send/withdraw orchestration
    - Add `outgoingInvites: StateFlow<List<OutgoingInvite>>` derived from `getOutgoingInvitesFlow()` reconciled with `_friends` via `OutgoingInviteReconciler.visibleOutgoing`
    - Add `requestsSections: StateFlow<RequestsListOrganizer.Sections>` combining `outgoingInvites` and existing `invites`
    - Extend `sendInvite` to optimistically add the mirror and emit a one-shot "navigate to Requests_List" event on success; on failure surface `WorkspaceUiState.Error` and revert the optimistic entry so the outgoing set is unchanged
    - Add `withdrawInvite(targetUid)`
    - _Requirements: 4.4, 5.1, 5.2, 5.4_

  - [ ] 4.2 Route deep-link invite presentation through `DeepLinkInviteState`
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

- [ ] 5. Checkpoint - Ensure all tests pass
  - Ensure all repository and ViewModel tests pass, ask the user if questions arise.

- [ ] 6. Enhance the existing `WorkspaceScreen` (Social_Hub) in place
  - [ ] 6.1 Extend the `WorkspaceScreen` signature and preserved composition with Social_Hub wiring
    - Add the additive parameters (`onOpenCircles`, deep-link consumption) to the existing `WorkspaceScreen`; keep all existing parameters and the established layout/composition intact
    - Keep the surface a full-screen overlay opened from Home and the deep link; do NOT create a new screen
    - _Requirements: 1.1_

  - [ ] 6.2 Enrich the existing lime hero ID card in place
    - Extend the existing hero ID card composable to keep showing the signed-in user's Preamble_ID and current productivity score, retaining the lime styling and `RandomBackgrounds` PNG, and layer in an expressive morphing shape treatment (Expressive_Motion_And_Shape)
    - _Requirements: 1.4, 1.5, 1.3_

  - [ ] 6.3 Add the always-visible `CirclesEntryCard` to the existing composition
    - Add a styled card (consistent with the existing design language, may reuse `RandomBackgrounds`) carrying descriptive text that a Circle is a shared friend group, visible without opening any menu, conveying the meaning and a "create a Circle" option even with no Circles, and invoking `onOpenCircles` to route to the existing `CirclesScreen`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ] 6.4 Add the `SectionOrganizer` layered onto the existing list composition
    - Add `SocialSection` enum and a `SectionOrganizer` composable using a Material 3 Expressive segmented/tab control that switches between the Leaderboard pane and the Friends_List pane
    - Give each pane its own `LazyListState` so scroll positions are preserved independently; keep the established card styling within each pane
    - Render the Friends_List pane as a `LazyColumn` so only visible rows are composed regardless of total size; show a no-friends empty-state with an "add a friend" control
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 2.4, 2.5_

  - [ ] 6.5 Adopt Material 3 Expressive components and expressive motion across the enhanced surface
    - Replace/wrap the surface's primary controls and cards with Expressive_Component elements and apply Expressive_Motion_And_Shape (animated/morphing shapes) while preserving the existing Cardfolio stacked-card layout, lime hero card, and `RandomBackgrounds` packs
    - _Requirements: 1.2, 1.3, 1.4_

  - [ ]* 6.6 Write unit/UI tests for the enhanced Social_Hub composition
    - Verify the hero card renders Preamble_ID and productivity score (1.5); the surface presents all named sub-surfaces (1.1); Circles_Entry is visible with descriptive text and create option including the no-circles case and invokes `onOpenCircles` (3.1, 3.2, 3.3, 3.5); the Section_Organizer exposes two distinct selectable areas reachable with a large list, retains independent scroll positions, and shows the no-friends empty-state with an add control (2.1, 2.2, 2.4, 2.5)
    - _Requirements: 1.1, 1.5, 2.1, 2.2, 2.4, 2.5, 3.1, 3.2, 3.3, 3.5_

  - [ ]* 6.7 Add visual/snapshot review coverage for preserved design language and expressive treatment
    - Snapshot the enhanced surface to confirm preserved Cardfolio styling, lime hero card, and `RandomBackgrounds` reuse alongside Material 3 Expressive components and expressive motion/shape
    - _Requirements: 1.2, 1.3, 1.4, 3.4_

- [ ] 7. Add the `InviteEntrySheet` (Invite_Entry_Experience) and the `RequestsList`
  - [ ] 7.1 Implement the `InviteEntrySheet` replacing the plain add-friend dialog
    - Add an on-theme entry surface (Expressive_Component + `RandomBackgrounds` + expressive motion) styled consistently with the existing surface, in place of the plain add-friend dialog
    - Normalize entry to uppercase as the user types via `PreambleId.normalize`; support a pre-filled id from a link/deep link; keep the send control disabled while the normalized id is blank
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

  - [ ] 7.2 Implement the `RequestsList` and wire post-send navigation
    - Render Outgoing_Invites and Incoming_Invites in grouped sections using `RequestsListOrganizer`, each Outgoing_Invite showing recipient Preamble_ID and an "awaiting response" status with prominence comparable to and visually distinguished from incoming invites; show an empty-state when there are no invites
    - Navigate the user to the `RequestsList` on a successful send (consuming the ViewModel one-shot event), ensuring the just-sent invite is present
    - _Requirements: 4.2, 4.3, 5.1, 5.2, 5.3, 5.5_

  - [ ]* 7.3 Write unit tests for the invite-entry sheet and requests list
    - Verify the sheet pre-fills from a link/deep link (6.2); the outgoing invite renders recipient Preamble_ID + awaiting-response status (4.2); the Requests_List empty-state shows when there are no invites (5.5)
    - _Requirements: 4.2, 5.5, 6.2_

- [ ] 8. Fix the invite deep-link routing to land on the Social_Hub
  - [ ] 8.1 Route `invite/{id}` to the enhanced friends overlay
    - Change the `invite/` branch in `PreambleApp`'s deep-link `LaunchedEffect` to open the enhanced friends overlay (`showFriendsScreen = true`) instead of selecting the collaborative-tasks tab (`selectedTab = 4`), retaining the existing `initialInviteId` plumbing into `WorkspaceScreen`
    - _Requirements: 7.1, 7.2_

  - [ ]* 8.2 Write unit test for deep-link routing
    - Verify `invite/{id}` maps to the Social_Hub overlay rather than `selectedTab = 4`
    - _Requirements: 7.1_

- [ ] 9. Checkpoint - Ensure all tests pass
  - Ensure all client UI and routing tests pass, ask the user if questions arise.

- [ ] 10. Implement the server-side referral reward gate
  - [ ] 10.1 Add the `REFERRAL_REWARDS_ENABLED` flag and `gatedReferralDecision`
    - Add `REFERRAL_REWARDS_ENABLED` to `functions/src/config.ts` (sourced from the `REFERRAL_REWARDS_ENABLED` env var, defaulting to disabled in Development_Mode)
    - Add the pure `gatedReferralDecision(input, rewardsEnabled)` in `functions/src/referrals.ts` returning `RejectRewardsDisabled` when disabled and otherwise delegating to the existing `classifyReferralEligibility`
    - _Requirements: 8.1, 8.4, 8.5_

  - [ ] 10.2 Wire the gate into `onReferralFriendship`
    - Call `gatedReferralDecision(..., REFERRAL_REWARDS_ENABLED)` so the credit-increment transaction never runs while disabled, leaving attribution, friendship establishment, and funnel logging untouched (reversible via configuration)
    - _Requirements: 8.1, 8.3, 8.4, 8.5_

  - [ ]* 10.3 Write property test for the referral reward gate (TypeScript functions test suite)
    - **Property 7: The referral reward gate grants nothing while rewards are disabled**
    - Generate arbitrary `ReferralInput`; assert `gatedReferralDecision(input, rewardsEnabled = false)` is never `Eligible`
    - **Validates: Requirements 8.1, 8.5**

  - [ ]* 10.4 Write integration test for the disabled-reward invite/accept flow
    - With the reward disabled, verify sending and accepting an invite still establishes the reciprocal friendship and modifies no AI-credit balances (1–2 representative cases against the functions emulator/mocks)
    - _Requirements: 8.3_

- [ ] 11. Reconcile the Referral CTA copy with the disabled reward
  - [ ] 11.1 Update `ReferralCta` reward-statement copy
    - Change the reused `ReferralCta` copy so it no longer states that both sides receive AI credits while the reward is disabled, keeping it wired into the enhanced Social_Hub
    - _Requirements: 8.2_

  - [ ]* 11.2 Write unit test for CTA copy gating
    - Verify the CTA omits the credits statement when the reward is disabled and that flipping the flag re-enables the eligibility path
    - _Requirements: 8.2, 8.4_

- [ ] 12. Final checkpoint - Ensure all tests pass
  - Ensure all client and server tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional (tests) and can be skipped for a faster MVP.
- All UI tasks extend the existing `WorkspaceScreen` and its composables in place — the current layout, Cardfolio stacked cards, and lime hero ID card are preserved and enhanced, never rebuilt.
- Each property from the design's Correctness Properties section maps to exactly one property-based test (jqwik for the Kotlin pure modules, a TypeScript test for the reward gate), tagged `// Feature: social-hub-redesign, Property {n}: ...` and running a minimum of 100 iterations.
- Look-and-feel requirements (1.2, 1.3, 1.4, 3.4, 4.3, 6.1, 6.5) are covered by unit/UI, snapshot, and manual review rather than property tests.
- Each task references specific granular requirements for traceability; checkpoints ensure incremental validation.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.3", "1.5", "10.1"] },
    { "id": 1, "tasks": ["1.2", "1.4", "1.6", "1.7", "3.1", "10.2", "11.1"] },
    { "id": 2, "tasks": ["3.2", "4.1", "4.2", "10.3", "10.4", "11.2"] },
    { "id": 3, "tasks": ["4.3", "4.4", "6.1"] },
    { "id": 4, "tasks": ["6.2", "6.3", "6.4", "6.5"] },
    { "id": 5, "tasks": ["6.6", "6.7", "7.1", "7.2", "8.1"] },
    { "id": 6, "tasks": ["7.3", "8.2"] }
  ]
}
```
