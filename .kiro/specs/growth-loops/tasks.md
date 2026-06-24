# Implementation Plan: Growth Loops

## Overview

This plan adds two growth-loop mechanics — rewarded two-sided referral invites and shareable moments — on top of the shipped `collaborative-tasks` friend-invite system and the existing AI-credits economy, changing no existing behavior. The strategy front-loads the four genuinely pure functions (client referral attribution decision, server referral eligibility classification, share-kind → content mapping, and share-caption construction) so the 4 correctness properties can be validated early with property-based tests. It then wires that logic through the Cloud Functions reward layer (the `onReferralFriendship` Firestore trigger + atomic credit transaction), the Firestore Security_Rules (`/referrals/{referredUid}` + keeping `ai_credits` client-read-only), the Android client referral path (pending-referrer capture, attribution write at first signup, the `Referral_CTA`, funnel analytics), a newly added `FileProvider`, and finally the shareable capture/share/orchestration stack and the three branded Compose shareables.

Implementation language: **Kotlin** for the Android client and pure client logic, **TypeScript (Node)** for the Cloud Functions and the `classifyReferralEligibility` pure logic, and **JavaScript (Node, `@firebase/rules-unit-testing`)** for the Firestore rules verification suite, as specified in the design.

Each step builds on the previous ones and ends by wiring the new logic into the running app, so no code is left orphaned. The manual friend-invite flow, the AI-credits flow, and the recap/stats screens keep working unchanged.

## Tasks

- [x] 1. Implement growth-loop pure logic
  - [x] 1.1 Implement `referral/ReferralAttribution.kt` attribution-candidate decision
    - Create `referral/ReferralAttribution.kt` as a pure object (no Android/Firebase deps): define the `AttributionDecision` sealed interface (`Attribute(referrerUid, referrerPreambleId)` / `Skipped(reason)`) and the `Skip` enum (`NoPendingReferrer`, `Unresolved`, `SelfReferral`)
    - Implement `decide(pendingReferrerId, resolvedReferrerUid, newAccountUid, newAccountPreambleId)` per the decision table: blank/null pending id → `Skipped(NoPendingReferrer)`; unresolved uid → `Skipped(Unresolved)`; resolved uid equals new uid OR pending id equals new account's Preamble_ID → `Skipped(SelfReferral)`; otherwise `Attribute(resolvedReferrerUid, pendingReferrerId)`, naming at most one referrer and always permitting account creation to continue
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6_

  - [ ]* 1.2 Write property test for the attribution decision
    - **Property 1: Attribution decision selects at most one non-self, resolvable referrer**
    - Tag `// Feature: growth-loops, Property 1: ...`, `@Property(tries = 100)` minimum (jqwik); generators produce blank/null/normal referrer ids, resolution results (uid or unresolved), and new-account identities (including self-matching by uid and by Preamble_ID)
    - **Validates: Requirements 2.2, 2.3, 2.4, 2.5, 2.6**

  - [x] 1.3 Implement `share/ShareableContent.kt` share-kind → content mapping
    - Create `share/ShareableContent.kt` as pure logic: define `ShareKind` (`WEEKLY_RECAP`, `STREAK_MILESTONE`, `PERFECT_DAY`), the `ShareableContent(kind, headline, metricLabel, subtitle)` and `WeeklyRecapSummary` data carriers, and the `ShareableContentMapper` object
    - Implement `fromStreak(days)` (content fields include the streak length in days), `fromPerfectDay(tasksCompleted)` (content fields include the completed-task count), and `fromWeeklyRecap(recap)` (content fields reflect the supplied recap summary values) — pure projection, no new computation
    - _Requirements: 7.3, 8.2, 9.2_

  - [ ]* 1.4 Write property test for shareable content mapping
    - **Property 3: Shareable content embeds its defining metric**
    - Tag `// Feature: growth-loops, Property 3: ...`, `@Property(tries = 100)` minimum (jqwik); generators produce arbitrary streak days, task counts, and recap summaries
    - **Validates: Requirements 7.3, 8.2, 9.2**

  - [x] 1.5 Implement `share/ShareCaption.kt` caption construction
    - Create `share/ShareCaption.kt` as a pure object reusing `InviteLink.build`: implement `build(kind, normalizedPreambleId)` returning non-empty body text for the kind plus a trailing `InviteLink.build(normalizedPreambleId)` line when the id is non-blank, and the same body with no link line when the id is blank/null, always using the normalized form of the id
    - _Requirements: 11.1, 11.2, 11.3_

  - [ ]* 1.6 Write property test for caption construction
    - **Property 4: Every caption carries the invite link exactly when a Preamble_ID is present**
    - Tag `// Feature: growth-loops, Property 4: ...`, `@Property(tries = 100)` minimum (jqwik); generators span all `ShareKind` values and arbitrary (blank/null/mixed-case/normal) Preamble_IDs, asserting inclusion/exclusion and normalization against `InviteLink.build`
    - **Validates: Requirements 11.1, 11.2, 11.3**

  - [x] 1.7 Implement `classifyReferralEligibility` in `functions/src/referrals.ts`
    - Create `functions/src/referrals.ts` and add the pure `classifyReferralEligibility(input: ReferralInput): ReferralDecision`: define the `ReferralInput` and `ReferralDecision` types and evaluate the gates in order — `state === "rewarded"` → `RejectAlreadyRewarded` (short-circuit), `referrerUid === referredUid` → `RejectSelfReferral`, `!friendshipEstablished` → `RejectNoFriendship`, `referredAccountCreatedAt` outside the `attributedAt ± windowMs` window → `RejectNotNewAccount`, otherwise `Eligible`
    - _Requirements: 3.1, 3.2, 3.3, 4.1, 4.2, 4.3_

  - [ ]* 1.8 Write property test for referral eligibility classification
    - **Property 2: Referral eligibility classification is exactly the conjunction of all gates**
    - Tag `// Feature: growth-loops, Property 2: ...`, `fc.assert(..., { numRuns: 100 })` minimum (fast-check, TypeScript); generators produce `ReferralInput`s spanning all gate combinations, asserting `Eligible` ⇔ conjunction of gates and the specific reject kind for each violation (already-rewarded beats everything)
    - **Validates: Requirements 3.1, 3.2, 3.3, 4.1, 4.2, 4.3**

- [x] 2. Checkpoint - Ensure all pure-logic tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Implement the Cloud Functions reward layer (`functions/`, TypeScript)
  - [x] 3.1 Add referral constants to `functions/src/config.ts`
    - Add `REFERRAL_REWARD = 50` and `REFERRAL_NEW_ACCOUNT_WINDOW_MS = 24 * 60 * 60 * 1000` to `functions/src/config.ts`
    - _Requirements: 3.6, 4.2_

  - [x] 3.2 Implement the `onReferralFriendship` Firestore trigger
    - In `functions/src/referrals.ts`, add the `onDocumentCreated("users/{ownerUid}/friends/{friendUid}")` trigger: select the `/referrals/{ownerUid}` or `/referrals/{friendUid}` doc whose `{referrerUid, referredUid}` matches the pair (exit if none → manual invite, no reward), verify reciprocal friendship, read `admin.auth().getUser(referredUid).metadata.creationTime` for `referredAccountCreatedAt`, and run `classifyReferralEligibility(...)`
    - On a non-`Eligible` result, set `/referrals/{referredUid}.state = "rejected"` with `rejectedReason` and exit without incrementing; on `Eligible`, run a single Firestore transaction that re-checks `state === "pending"`, increments both users' `ai_credits` (+50) and `ai_credits_total_earned` via `FieldValue.increment`, and flips `state` `pending → rewarded` with `rewardedAt` atomically; treat an auth-metadata lookup failure as fail-closed (reject)
    - Track `referral-friendship` on first reciprocal establishment and `referral-reward-granted` after commit (best-effort, never rolls back the grant)
    - _Requirements: 3.1, 3.2, 3.3, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 6.4, 6.5_

  - [x] 3.3 Export `onReferralFriendship` from `functions/src/index.ts`
    - Add `export { onReferralFriendship } from "./referrals";` alongside the existing triggers
    - _Requirements: 3.1_

  - [ ]* 3.4 Write Cloud Functions integration tests
    - Add a Node test layer (e.g. `firebase-functions-test` against the emulator + Admin SDK): eligible signup → both balances increase by exactly 50 and `state → rewarded` (3.1, 3.6); double/concurrent friend-doc creation → single grant (3.2, 3.5); friend remove + re-add after reward → no additional grant (4.4); self-referral, pre-existing account (out-of-window creation time), and missing friendship → `state → rejected`, no increment (3.3, 4.1, 4.2); friendship with no `/referrals` attribution → manual flow untouched, no credit change (5.1, 5.2); `referral-friendship`/`referral-reward-granted` emitted (6.4, 6.5)
    - _Requirements: 3.1, 3.2, 3.3, 3.5, 3.6, 4.1, 4.2, 4.4, 5.1, 5.2, 6.4, 6.5_

- [x] 4. Checkpoint - Ensure all functions tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement and verify the Firestore Security_Rules
  - [x] 5.1 Add the `/referrals/{referredUid}` rules and keep `ai_credits` client-read-only
    - In `firebase-firestore-rules.rules`, add the `match /referrals/{referredUid}` block: read for the owner or the referrer named in the doc; create only when the creator is the referred user, the doc is keyed by their own uid, `referrerUid` is a string `!= request.auth.uid`, and `state == "pending"`; deny all `update` and `delete` (`if false`) so a client can never flip to `"rewarded"`/`"rejected"` or change `referrerUid`
    - Keep `match /users/{uid}/ai_credits/{doc}` read-only for the owner (`allow read: if isOwner(uid)`) with no client write, so the reward path remains Admin-SDK-only
    - _Requirements: 2.2, 2.6, 3.4, 3.5, 4.1_

  - [ ]* 5.2 Extend the emulator rules suite for referrals
    - Extend the `@firebase/rules-unit-testing` suite: referred user creates their own `pending` attribution (allow); naming self, creating for another uid, setting `state: "rewarded"`, or any `update`/`delete` (deny); referrer can read the attribution naming them and non-parties cannot; any client write to `users/{uid}/ai_credits` (deny)
    - _Requirements: 2.2, 2.6, 3.4, 3.5, 4.1_

- [x] 6. Checkpoint - Ensure all rules tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Wire the client referral path
  - [x] 7.1 Implement `referral/PendingReferrerStore.kt`
    - Create `referral/PendingReferrerStore.kt` as a thin `SharedPreferences` wrapper over the existing `"preamble_prefs"` file: `save(context, preambleId)` stores the normalized referrer id under key `"pending_referrer"`, and `consume(context): String?` reads and clears it (single-use, so a stale referrer cannot attach to a later account)
    - _Requirements: 2.1_

  - [x] 7.2 Capture the referrer in the `MainActivity` deep-link path
    - In `MainActivity`, where an `invite/{id}` deep link is parsed and no account exists yet, persist the referring Preamble_ID via `PendingReferrerStore.save(...)`, and record a `referral-invite-opened` analytics event
    - _Requirements: 2.1, 6.2_

  - [x] 7.3 Implement `referral/ReferralRepository.kt`
    - Create `referral/ReferralRepository.kt`: `createPendingAttribution(referrerUid, referrerPreambleId)` writes `/referrals/{currentUid}` with `state:"pending"`, `referredCreatedAt`, `attributedAt`, no-op success if a doc already exists (at-most-one referrer); `resolveReferrer(preambleId): String?` reuses `WorkspaceRepository.resolvePreambleId`; wrap writes in `runCatching` so failure never blocks sign-up
    - _Requirements: 2.2, 2.3, 2.4_

  - [x] 7.4 Write the pending attribution at first signup
    - In the account-creation path, when `AuthManager.SignInResult.isNewUser == true`, read and `consume` the pending referrer, call `ReferralRepository.resolveReferrer`, run `ReferralAttribution.decide(...)`, and on an `Attribute` decision call `ReferralRepository.createPendingAttribution` and record a `referral-signup` event; on any `Skipped` decision allow account creation to complete unchanged with no `/referrals` write
    - _Requirements: 2.2, 2.4, 2.5, 2.6, 6.3_

  - [x] 7.5 Add the `ReferralCta` composable to the friends surface
    - Create `ui/components/ReferralCta.kt` shown on the friends/workspace surface: state that both sides receive the 50-credit Referral_Reward, display the user's `InviteLink.build(myPreambleId)`, a Copy control (clipboard + confirmation snackbar) and a Share control (`ACTION_SEND` text carrying the link), reusing `WorkspaceViewModel.myPreambleId`/`buildInviteLink()`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 7.6 Wire the referral funnel analytics through `AnalyticsManager`
    - Route `referral-invite-shared` (on CTA copy/share) and the `referral-signup` event through the existing `AnalyticsManager.captureEvent`, alongside the `referral-invite-opened` event from the deep-link path
    - _Requirements: 6.1, 6.3_

  - [ ]* 7.7 Write unit tests for the attribution write and funnel
    - With a fake `ReferralRepository`/`AnalyticsManager`: a new-user signup with a resolvable non-self referrer writes exactly one `pending` attribution and fires `referral-signup`; unresolved/self/absent referrer writes nothing and still completes signup (2.4, 2.5, 2.6); CTA copy/share fires `referral-invite-shared` (6.1)
    - _Requirements: 2.4, 2.5, 2.6, 6.1, 6.3_

- [x] 8. Checkpoint - Ensure all client referral tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Add the `FileProvider` for shareable images
  - [x] 9.1 Register the `FileProvider` and `file_paths.xml`
    - Add the `androidx.core.content.FileProvider` `<provider>` (authority `${applicationId}.fileprovider`, `exported="false"`, `grantUriPermissions="true"`, `FILE_PROVIDER_PATHS` meta-data) inside `<application>` in `AndroidManifest.xml`, and create `app/src/main/res/xml/file_paths.xml` with a `<cache-path name="shared_images" path="shared_images/" />` entry
    - _Requirements: 7.4, 8.3, 9.3_

- [x] 10. Implement the shareable capture, share, and orchestration stack
  - [x] 10.1 Implement `share/ShareableImageRenderer.kt`
    - Create `share/ShareableImageRenderer.kt` that renders a branded Composable to a `Bitmap`: primary path hosts the shareable in a `rememberGraphicsLayer()` and calls `graphicsLayer.toImageBitmap().asAndroidBitmap()` after layout; fallback path uses an off-screen `ComposeView` + `PixelCopy` (API 24+) / `view.draw(Canvas)`; returns `Result<Bitmap>` and never throws into the caller
    - _Requirements: 7.2, 8.2, 9.2, 10.3_

  - [x] 10.2 Implement `share/ShareSheetLauncher.kt`
    - Create `share/ShareSheetLauncher.kt`: `share(context, bitmap, caption): Result<Unit>` writes the PNG to `context.cacheDir/shared_images/`, obtains a content `Uri` via `FileProvider.getUriForFile(context, "${applicationId}.fileprovider", file)`, builds `ACTION_SEND` (`type="image/png"`, `EXTRA_STREAM`, `EXTRA_TEXT=caption`, `FLAG_GRANT_READ_URI_PERMISSION`) and launches `createChooser`, returning `Result.failure` on any IO/provider error
    - _Requirements: 7.4, 8.3, 9.3_

  - [x] 10.3 Implement `share/ShareableViewModel.kt`
    - Create `share/ShareableViewModel.kt`: `requestShare(kind, content)` launches rendering on `Dispatchers.Default` wrapped in `withTimeout(10_000)`, exposes a `StateFlow<ShareUiState>` (`Idle | Generating | Error`) so the UI shows progress and the main thread stays free; on success builds the caption via `ShareCaption.build` + content via `ShareableContentMapper`, calls `ShareSheetLauncher.share`, and tracks `moment-shared` with the kind; on failure/timeout sets `Error` and does not open the share sheet
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x] 10.4 Add and wire the three branded shareable composables
    - Create `ui/components/ShareableComposables.kt` with `WeeklyRecapShareable`, `StreakMilestoneShareable`, and `PerfectDayShareable` consuming `ShareableContent`, each exposing a share control bound to `ShareableViewModel.requestShare`; wire the weekly-recap control into the recap surface (`RecapScreen` data), the streak control into the stats/celebration surface (`CelebrationEvent.StreakDay`), and the perfect-day control into the celebration surface (`CelebrationEvent.PerfectDay`), routing `moment-shared` through `AnalyticsManager`
    - _Requirements: 7.1, 8.1, 9.1, 10.5, 11.1, 11.2, 11.3_

  - [ ]* 10.5 Write unit tests for the `ShareableViewModel` state machine
    - With a fake renderer/launcher and a virtual `TestDispatcher`: progress is shown during generation (10.2); a render failure → `Error` and no share sheet (10.3); a >10 s delayed renderer → `Error` and no share sheet (10.4); a successful share fires `moment-shared` with the kind (10.5)
    - _Requirements: 10.2, 10.3, 10.4, 10.5_

  - [ ]* 10.6 Write Compose/instrumented tests for capture and share
    - Share controls render on the recap/streak/perfect-day surfaces (7.1, 8.1, 9.1); `ShareableImageRenderer` produces a non-null bitmap for each kind (7.2, 8.2, 9.2); `ShareSheetLauncher` builds an `ACTION_SEND` `image/png` intent with a `FileProvider` `EXTRA_STREAM`, `FLAG_GRANT_READ_URI_PERMISSION`, and an `EXTRA_TEXT` caption containing the link, and omits the link when no Preamble_ID is present (7.4, 8.3, 9.3, 11.1, 11.3)
    - _Requirements: 7.1, 7.2, 7.4, 8.1, 8.2, 8.3, 9.1, 9.2, 9.3, 11.1, 11.3_

- [x] 11. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core (non-test) implementation tasks are never optional.
- Each task references specific granular requirements clauses for traceability.
- Property tests (Properties 1–4) target only the four pure functions — `ReferralAttribution.decide`, `classifyReferralEligibility`, `ShareableContentMapper`, and `ShareCaption.build` — and are placed immediately beside the function they validate, so correctness issues surface before the Cloud Functions/rules/UI wiring depends on them.
- Firestore Security_Rules (Requirements 2.2, 2.6, 3.4, 3.5, 4.1), the Cloud Functions reward transaction and FCM/trigger wiring (Requirements 3.x, 4.x, 5.x, 6.4, 6.5), and the off-screen bitmap capture/share path (Requirements 7.x, 8.x, 9.x, 10.x) are verified by the emulator suite, Cloud Functions integration tests, and Compose/instrumented tests, not by property-based tests, matching the design's testing strategy.
- Checkpoints provide incremental validation at the boundaries between pure logic, the Cloud Functions reward layer, the security rules, the client referral path, and the shareables.
- **Test execution environment:** JVM unit-test execution (the jqwik property tests and the Compose tests) is currently blocked because the build environment lacks a complete JDK 21 with `jlink` (`gradle.properties` pins JDK 17). Until a full JDK 21 toolchain is available, the Kotlin pure-logic and Compose test sources are verified by **compilation** (they must compile cleanly) rather than by running the suite. The Cloud Functions `fast-check` tests and the Firestore emulator rules tests run under Node, are not subject to this constraint, and are executable now — providing executable verification of the eligibility logic, reward path, and rules in the interim.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.3", "1.5", "1.7", "3.1", "5.1", "9.1", "7.1"] },
    { "id": 1, "tasks": ["1.2", "1.4", "1.6", "1.8", "3.2", "5.2", "7.2", "7.3", "7.6", "10.1", "10.2"] },
    { "id": 2, "tasks": ["3.3", "7.4", "7.5", "10.3"] },
    { "id": 3, "tasks": ["3.4", "7.7", "10.4"] },
    { "id": 4, "tasks": ["10.5", "10.6"] }
  ]
}
```
