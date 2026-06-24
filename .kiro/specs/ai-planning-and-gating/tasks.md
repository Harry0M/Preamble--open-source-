# Implementation Plan: AI Planning and Gating

## Overview

This plan delivers two **independent** capabilities — Track A (AI Plan-My-Day) and Track B (premium gating infrastructure, default OFF) — that share no runtime dependency and can ship separately. The strategy front-loads the pure, Android/network/AI-free logic so the 12 correctness properties are validated early with property-based tests (jqwik): Track A's `ScheduleNormalizer`/`PlanApply` in the new `com.theblankstate.preamble.planner` package (Properties 1–6) and Track B's `GatingDecision` plus the existing `EntitlementStore.effectiveTier` in the new `com.theblankstate.preamble.gating` package (Properties 7–12).

After the pure cores are in place, the plan wires them through their thin edges: Track A adds the credit-charged `aiPlanDay` Cloud Function, the `CloudAiService.planDay` client, the `DayPlanService`/`DayPlanViewModel` orchestration over the existing `TaskViewModel.updateTask` path, and the Compose review sheet/entry point; Track B adds the `premium_gating_enabled` Remote Config switch (default false), the `AiConfigService.premiumGatingEnabled()` reader, the replacement of the always-`true` `FeatureGate.isUnlocked` stub, the `PremiumUpsellSheet`, and analytics.

Implementation language: **Kotlin** for the Android client and both pure-logic packages, and **TypeScript (Node)** for the `aiPlanDay` Cloud Function and its tests, as specified in the design.

Each step builds on the previous ones and ends by wiring the new logic into the running app, so no code is left orphaned. Existing behavior is unchanged, and the Track B OFF path is implemented to be provably identical to today's always-unlocked behavior.

## Tasks

- [x] 1. Implement Track A Plan-My-Day pure logic in `com.theblankstate.preamble.planner`
  - [x] 1.1 Implement the planner data models and `ScheduleNormalizer.normalize`
    - Create the `com.theblankstate.preamble.planner` package (no Android/Firebase/AI imports): define `SchedulableTask(id, title, priority)`, `FixedCommitment(startMinute, endMinute?)`, `DayPlanInput(schedulable, fixed, dayStartMinute, dayEndMinute, slotMinutes=30)`, `RawAssignment(taskId, time)`, `ScheduledAssignment(taskId, time)`, `ProposedSchedule(assignments)`, and the `PlanOutcome` sealed interface (`Valid(schedule)` / `CouldNotGenerate`)
    - Implement `ScheduleNormalizer.normalize(input, raw)` as a pure function following the design algorithm: build the reserved minute-set from point and ranged `FixedCommitment`s, enumerate candidate slots from `dayStartMinute` by `slotMinutes` up to `dayEndMinute` dropping reserved slots, choose at most one legal free slot per `SchedulableTask` (dedup by id, ignore unknown ids, repair to the next free slot when the AI time is unusable), re-zip distinct chosen slots ascending against tasks ordered by `priority` descending (tie-break proposed-time then id) so higher priority is never later, format slots back to canonical `HH:mm`, and return `Valid` when at least one task is placed else `CouldNotGenerate`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 5.3_

  - [ ]* 1.2 Write property test for one-time-per-task validity
    - **Property 1: One canonical time per schedulable task, none for others**
    - Tag `// Feature: ai-planning-and-gating, Property 1: ...`, `@Property(tries = 100)` minimum; generator produces `DayPlanInput`s and `RawAssignment` lists with duplicate ids, unknown ids, and malformed/out-of-window time strings; assert at most one time per schedulable id, no time for non-schedulable ids, and every time matches `^([01]\d|2[0-3]):[0-5]\d$`
    - **Validates: Requirements 2.1, 2.5**

  - [ ]* 1.3 Write property test for fixed-commitment avoidance
    - **Property 2: Proposed times never coincide with fixed commitments**
    - Tag `// Feature: ai-planning-and-gating, Property 2: ...`, `@Property(tries = 100)` minimum; generator includes both point and ranged `FixedCommitment`s; assert no proposed time falls in any commitment's reserved minute-set
    - **Validates: Requirements 2.2**

  - [ ]* 1.4 Write property test for distinct times
    - **Property 3: All proposed times are distinct**
    - Tag `// Feature: ai-planning-and-gating, Property 3: ...`, `@Property(tries = 100)` minimum; assert the times in any `Valid` `ProposedSchedule` are pairwise distinct
    - **Validates: Requirements 2.3**

  - [ ]* 1.5 Write property test for priority ordering
    - **Property 4: Higher priority is scheduled no later than lower priority**
    - Tag `// Feature: ai-planning-and-gating, Property 4: ...`, `@Property(tries = 100)` minimum; generator varies priorities across schedulable tasks; assert for every pair with differing `priority` the higher-priority task's time is `<=` the lower-priority task's time
    - **Validates: Requirements 2.4**

  - [ ]* 1.6 Write property test for could-not-generate mapping
    - **Property 5: Invalid or unschedulable proposals map to "could not generate"**
    - Tag `// Feature: ai-planning-and-gating, Property 5: ...`, `@Property(tries = 100)` minimum; generator covers empty/all-malformed raw lists and fully-reserved windows alongside placeable cases; assert `CouldNotGenerate` exactly when no task can be placed and a non-empty `Valid` schedule otherwise
    - **Validates: Requirements 5.3**

  - [x] 1.7 Implement `PlanApply.withDeadlineTime`
    - Add `PlanApply` to the `planner` package: `withDeadlineTime(task: Task, time: String): Task = task.copy(deadlineTime = time)`, changing only `deadlineTime` and preserving all other `Task` fields (title, description, priority, tags, recurrence fields, completion state)
    - _Requirements: 4.2_

  - [ ]* 1.8 Write property test for deadline-time-only mutation
    - **Property 6: Applying a proposed time changes only `deadlineTime`**
    - Tag `// Feature: ai-planning-and-gating, Property 6: ...`, `@Property(tries = 100)` minimum; generator produces arbitrary `Task`s and `HH:mm` strings; assert the returned `Task` equals the original in every field except `deadlineTime`, which equals the given time
    - **Validates: Requirements 4.2**

- [x] 2. Implement Track B premium-gating pure logic in `com.theblankstate.preamble.gating`
  - [x] 2.1 Extend the `PremiumFeature` enum
    - In `data/Entitlement.kt`, add `AI_AUTO_PLANNING` and `UNLIMITED_AI_CREDITS` to the existing `PremiumFeature` enum, leaving existing members and `EntitlementTier`/`EntitlementStore.unlocks`/`EntitlementStore.effectiveTier` unchanged
    - _Requirements: 10.4_

  - [x] 2.2 Implement `GatingDecision`
    - Create the `com.theblankstate.preamble.gating` package (pure — no Android/network imports): define `PREMIUM_CANDIDATES = {AI_AUTO_PLANNING, WRAPPED, STATS_EXTENDED_RANGE, STATS_DEDICATED_SCREEN, UNLIMITED_AI_CREDITS}` and `isFeatureUnlocked(feature, tier, masterSwitchEnabled)` returning `true` when the switch is OFF (tier ignored), `true` when the feature is not a candidate, and otherwise delegating to the existing `EntitlementStore.unlocks(tier)`
    - _Requirements: 8.1, 8.2, 8.3, 9.1, 10.1, 10.2, 10.3, 10.4, 10.5_

  - [ ]* 2.3 Write property test for the OFF safety invariant
    - **Property 7: Master switch OFF is always unlocked for everyone (safety invariant)**
    - Tag `// Feature: ai-planning-and-gating, Property 7: ...`, `@Property(tries = 100)` minimum; enumerate the full `PremiumFeature` × `EntitlementTier` space with `masterSwitchEnabled = false`; assert the result is always `true` and independent of tier
    - **Validates: Requirements 8.1, 8.2, 8.3**

  - [ ]* 2.4 Write property test for determinism
    - **Property 8: Gating decision is deterministic and depends only on its inputs**
    - Tag `// Feature: ai-planning-and-gating, Property 8: ...`, `@Property(tries = 100)` minimum; assert two evaluations with identical `(feature, tier, switch)` return the same result with no hidden state
    - **Validates: Requirements 9.1, 9.2**

  - [ ]* 2.5 Write property test for expired-premium resolution
    - **Property 9: Expired premium resolves to UNPREMIUM**
    - Tag `// Feature: ai-planning-and-gating, Property 9: ...`, `@Property(tries = 100)` minimum; generator produces `Entitlement`s with a premium tier and a non-zero past `expiresAtMs`; assert `EntitlementStore.effectiveTier` resolves to `UNPREMIUM`
    - **Validates: Requirements 9.3**

  - [ ]* 2.6 Write property test for ON-locks-non-premium-candidates
    - **Property 10: Switch ON locks candidate features for non-premium tiers**
    - Tag `// Feature: ai-planning-and-gating, Property 10: ...`, `@Property(tries = 100)` minimum; for every candidate feature and every tier in `{FREE_TIER, UNPREMIUM}` with the switch ON, assert the result is `false`
    - **Validates: Requirements 10.1**

  - [ ]* 2.7 Write property test for ON-keeps-premium-unlocked
    - **Property 11: Switch ON keeps candidate features unlocked for premium tiers**
    - Tag `// Feature: ai-planning-and-gating, Property 11: ...`, `@Property(tries = 100)` minimum; for every candidate feature and every tier in `{PROMOTIONAL, PREMIUM, PREMIUM_STUDENT, PREMIUM_YOUNGSTER}` with the switch ON, assert the result is `true`
    - **Validates: Requirements 10.2, 10.3**

  - [ ]* 2.8 Write property test for ON-leaves-non-candidates-unlocked
    - **Property 12: Switch ON leaves non-candidate features unlocked for all tiers**
    - Tag `// Feature: ai-planning-and-gating, Property 12: ...`, `@Property(tries = 100)` minimum; for every non-candidate `PremiumFeature` and every `EntitlementTier` with the switch ON, assert the result is `true`
    - **Validates: Requirements 10.5**

- [x] 3. Checkpoint - Ensure all pure-logic tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement the Track A Cloud AI planning path
  - [x] 4.1 Implement the `aiPlanDay` Cloud Function
    - Add `functions/src/ai-plan-day.ts` (`aiPlanDay`) structured as a sibling of `aiParseTask`: `onRequest({ cors: true, timeoutSeconds: 30, memory: "256MiB" })`, `verifyAuth` on the bearer token, body `{ schedulable, fixed, date, dayStart, dayEnd, appVersionCode }`, server-held planning prompt instructing a single `HH:mm` per task within the window avoiding fixed times and scheduling higher priority earlier, model selection via `getAiConfig(db)`, and a strict `{ success, assignments:[{id,time}], model }` response; run under the credit-charged path with the same server-side balance check as `aiChat`, returning `success:false`/`error:"INSUFFICIENT_CREDITS"` (HTTP 402) on insufficient balance and never mutating tasks
    - _Requirements: 5.4, 5.5_

  - [x] 4.2 Export `aiPlanDay` from the functions entry point
    - Export `aiPlanDay` from `functions/src/index.ts` alongside the existing AI functions, following the existing export conventions
    - _Requirements: 5.4_

  - [ ]* 4.3 Write Cloud Functions integration tests for `aiPlanDay`
    - Add a Node test in `functions/`: a valid request returns `HH:mm` assignments and decrements credits (5.4); an insufficient-balance request returns `INSUFFICIENT_CREDITS` and mutates nothing (5.5); an unauthenticated request is rejected
    - _Requirements: 5.4, 5.5_

  - [x] 4.4 Implement `CloudAiService.planDay`
    - Add `planDay(schedulable, fixed, date, dayStart, dayEnd): PlanDayResult?` to `ai/CloudAiService.kt`, mirroring `parseTask`'s request/parse shape pointed at `aiPlanDay`: serialize the body with `org.json`, attach the Firebase ID-token bearer, parse `{ success, assignments, model }`, surface a distinct `INSUFFICIENT_CREDITS` result for HTTP `402`/`success:false`, map `429` to the existing daily-limit message, and return `null` on network/parse error
    - _Requirements: 5.1, 5.4, 5.5_

- [x] 5. Implement the Track A orchestration edge (`DayPlanService`/`DayPlanViewModel`)
  - [x] 5.1 Implement `DayPlanService` task gathering and selection
    - Add `DayPlanService` with `gatherInput(today)`: from `TaskRepository`, select `Schedulable_Task`s (current day, `!isCompleted`, `deadlineTime == null`, `!isEvent`) and `Fixed_Commitment`s (current-day tasks/events that already have a time, including event `[deadlineTime, endTime)` ranges), mapping minutes-of-day and the working window into a `DayPlanInput`
    - _Requirements: 1.2, 1.3_

  - [x] 5.2 Add Plan-My-Day analytics to `AnalyticsManager`
    - Add `trackDayPlanRequested(count)`, `trackDayPlanAccepted()`, and `trackDayPlanDiscarded()` to `analytics/AnalyticsManager.kt`, routed through the existing `captureEvent`
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 5.3 Implement the `DayPlanViewModel` state machine and apply path
    - Implement the `Idle → Loading → Review → Applying → Applied | Failed` state machine plus terminal `NoSchedulableTasks`/`CouldNotGenerate`/`InsufficientCredits` states: `requestPlan()` short-circuits to `NoSchedulableTasks` with no AI call when there are no schedulable tasks (1.4), else emits `trackDayPlanRequested(count)` (6.1) and calls `CloudAiService.planDay` under `withTimeout(PLAN_TIMEOUT_MS)` (5.1), mapping timeout/error/insufficient-credits to the matching terminal state with tasks untouched (5.2, 5.3, 5.5) and feeding success into `ScheduleNormalizer.normalize` (`CouldNotGenerate` → terminal, `Valid` → `Review`); `accept()` applies each `ScheduledAssignment` via `TaskViewModel.updateTask(task, newTitle = task.title, newDate = task.createdDate, newDeadlineTime = assignment.time)` using `PlanApply`, surfaces "could not apply" on failure (4.4), and emits `trackDayPlanAccepted()` (6.2); `discard()` returns to `Idle` with no mutation (4.3) and emits `trackDayPlanDiscarded()` (6.3); while in `Review` nothing is written (3.2)
    - _Requirements: 1.4, 1.5, 3.2, 4.1, 4.3, 4.4, 5.1, 5.2, 5.3, 5.5, 6.1, 6.2, 6.3_

  - [ ]* 5.4 Write unit tests for the orchestration edge
    - With a stubbed `CloudAiService`: empty schedulable ⇒ no AI call + message (1.4); `>=1` ⇒ `planDay` called (1.5); timeout ⇒ `CouldNotGenerate` + no writes (5.1, 5.2); insufficient credits ⇒ message + no writes (5.5); accept ⇒ one `updateTask` per assignment (4.1) + analytics (6.1–6.3); discard ⇒ no writes + `Idle` (4.3); apply failure ⇒ message (4.4); no writes while in `Review` (3.2)
    - _Requirements: 1.4, 1.5, 3.2, 4.1, 4.3, 4.4, 5.1, 5.2, 5.3, 5.5, 6.1, 6.2, 6.3_

- [x] 6. Implement the Track B gating edge and remote master switch
  - [x] 6.1 Add the `premium_gating_enabled` switch source
    - Add the BOOLEAN parameter `premium_gating_enabled` (default `"false"`) to `firebase/remote_config_template.json` consistent with `ai_kill_switch`, add the `K_PREMIUM_GATING = "premium_gating_enabled"` constant and a `false` in-app default to `AiConfigService.setDefaultsAsync(...)`, and add `premiumGatingEnabled(): Boolean` mirroring `killSwitch()` (reads the BOOLEAN, `getOrDefault(false)` so unfetched/absent/read-failure ⇒ OFF)
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [x] 6.2 Replace the `FeatureGate.isUnlocked` stub with the real edge
    - In `data/Entitlement.kt`, replace the always-`true` `FeatureGate.isUnlocked(ctx, feature)` stub with an edge that reads `EntitlementStore.effectiveTier(ctx)` (9.3) and `AiConfigService.premiumGatingEnabled()` (7.1/7.2) and delegates to `GatingDecision.isFeatureUnlocked(feature, tier, switch)`
    - **SAFETY:** the OFF path must remain provably identical to today's always-`true` behavior — while `premiumGatingEnabled()` is `false`, `GatingDecision` ignores the tier and returns unlocked for every feature, so no live paywall or observable change is introduced; leave the separate `ads/FeatureGateManager` theme stub unchanged
    - _Requirements: 8.3, 9.3, 11.1, 11.2_

  - [x] 6.3 Add gating analytics to `AnalyticsManager`
    - Add `trackGateEvaluated(feature, unlocked)` (12.1) and `trackUpsellShown(feature)` (12.2) to `analytics/AnalyticsManager.kt`, routed through the existing `captureEvent`
    - _Requirements: 12.1, 12.2_

  - [ ]* 6.4 Write gating edge and configuration tests
    - Example tests: missing/throwing Remote Config ⇒ `premiumGatingEnabled() == false` (7.2), `true ⇒ ON` / `false ⇒ OFF` (7.4); a smoke test asserting `GatingDecision.PREMIUM_CANDIDATES` equals exactly `{AI_AUTO_PLANNING, WRAPPED, STATS_EXTENDED_RANGE, STATS_DEDICATED_SCREEN, UNLIMITED_AI_CREDITS}` (10.4); and an OFF-equals-all-unlocked guard asserting that across the entire `PremiumFeature` × `EntitlementTier` matrix with the switch OFF every combination is unlocked (8.1–8.3)
    - _Requirements: 7.2, 7.4, 8.1, 8.2, 8.3, 10.4_

- [x] 7. Checkpoint - Ensure all wiring tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Wire the Compose UI for both tracks
  - [x] 8.1 Add the Plan-My-Day entry point and `DayPlanReviewSheet`
    - Add a "Plan my day" entry point on `ui/screens/HomeScreen.kt` that first runs the Track B gate for `PremiumFeature.AI_AUTO_PLANNING` via `FeatureGate.isUnlocked` (showing `PremiumUpsellSheet` and skipping the action when locked, emitting `trackGateEvaluated`/`trackUpsellShown`), and on unlocked triggers `DayPlanViewModel.requestPlan()` (1.1); add `ui/components/DayPlanReviewSheet.kt` as a modal bottom sheet shown only in the `Review` state, listing each task's title + proposed `HH:mm` with Accept/Discard actions and writing nothing while shown (3.1, 3.2, 3.3, 11.1, 11.2, 12.1, 12.2)
    - _Requirements: 1.1, 3.1, 3.2, 3.3, 11.1, 11.2, 12.1, 12.2_

  - [x] 8.2 Add the `PremiumUpsellSheet`
    - Add `ui/components/PremiumUpsellSheet.kt`: a bottom sheet that identifies the gated `PremiumFeature` and explains premium is required, with no purchase/billing flow; shown by the host when `FeatureGate.isUnlocked` returns locked
    - _Requirements: 11.3_

  - [ ]* 8.3 Write Compose tests for the review sheet and entry point
    - `DayPlanReviewSheet` renders each task's title + proposed `HH:mm` with working Accept/Discard (3.1, 3.3); the Plan-My-Day `HomeScreen` entry point is present and triggers the gated request (1.1)
    - _Requirements: 1.1, 3.1, 3.3_

  - [ ]* 8.4 Write Compose tests for the upsell sheet
    - `PremiumUpsellSheet` names the gated `PremiumFeature` (11.3)
    - _Requirements: 11.3_

- [x] 9. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Tasks — Track A Iteration 2: WS5 Plan-My-Day overhaul

These tasks extend Track A (Requirements 13–20) per the "Design — Track A Iteration 2: WS5 Plan-My-Day overhaul" section. They are **additive**: the existing pure `ScheduleNormalizer` stays the correctness authority and is upgraded to be time-aware and to surface unplaced tasks, while the new clock/date/replanning/UX work is layered on the existing `DayPlanService`/`DayPlanViewModel`/`HomeScreen` edges. Track B (Requirements 7–12) is unchanged; the new `Planning_Screen` keeps the existing `FeatureGate.isUnlocked(AI_AUTO_PLANNING)` gate. Implementation language is **Kotlin** (Android client + pure `planner` package) and **TypeScript (Node)** for the `aiPlanDay` Cloud Function, consistent with the existing plan.

- [x] 10. Upgrade the planner pure core to be time-aware and partition unplaced tasks
  - [x] 10.1 Add `PlanClock`, `DayWindow`, and the time-aware `ScheduleNormalizer` changes
    - In `com.theblankstate.preamble.planner` (still no Android/Firebase/AI imports): add the `PlanClock` `fun interface` with `now(): java.time.LocalDateTime` and `PlanClock.system()` (device clock + zone) so "now" is injected, not globally captured (13.1, 13.7)
    - Add `DayWindow` with `SCHEDULE_LEAD_TIME_MIN = 10` and `effectiveWindowStart(workingWindowStartMin, nowMinuteOfDay, leadTimeMin = SCHEDULE_LEAD_TIME_MIN): Int` returning `max(workingWindowStartMin, nowMinuteOfDay + leadTimeMin.coerceIn(0,30)).coerceIn(0, 24*60)` (lead time clamped to 0..30) (13.2, 13.4)
    - Add `earliestStartMinute: Int` to `DayPlanInput` (= `Effective_Window_Start`), keeping `dayStartMinute` as the configured working-window start
    - Add `unplaced: List<SchedulableTask> = emptyList()` to `ProposedSchedule`
    - Change `ScheduleNormalizer.normalize` so candidate-slot generation only emits a slot when `slot >= input.earliestStartMinute && slot in 0..1439 && slot !in reserved` (floors all proposals at `Effective_Window_Start`, so the model can never push a start into the past), and so schedulable tasks are partitioned into `assignments ∪ unplaced` — every schedulable id appears exactly once across the two (surplus tasks that cannot fit the remaining window land in `unplaced` instead of being silently dropped)
    - _Requirements: 13.2, 13.3, 13.4, 16.2_

  - [ ]* 10.2 Write property test for time-aware normalization
    - **Property 13: Time-aware normalization never schedules in the past, never conflicts, never duplicates, and partitions every task**
    - Add to `app/src/test/java/com/theblankstate/preamble/planner/`, tag `// Feature: ai-planning-and-gating, Property 13: Time-aware normalization never schedules in the past, never conflicts, never duplicates, and partitions every task`, `@Property(tries = 100)` minimum; generators produce random injected `PlanClock` values, working windows, lead-times in `0..30`, point/ranged `FixedCommitment`s, schedulable counts that deliberately exceed available slots, and adversarial `RawAssignment`s (past, duplicate, unknown-id, malformed, out-of-window, reserved-slot); assert (a) no time `< earliestStartMinute` or `> dayEndMinute`, (b) no time in any reserved minute-set, (c) pairwise-distinct times, (d) every schedulable id appears exactly once across `assignments ∪ unplaced`, and (e) identical inputs yield an identical result
    - **Validates: Requirements 13.2, 13.3, 13.7, 15.3, 16.2, 16.3, 16.4**

- [x] 11. Thread deterministic time/date through the orchestration and Cloud AI path
  - [x] 11.1 Inject `PlanClock` into `DayPlanService.gatherInput` and short-circuit when no time remains
    - Give `DayPlanService` a `PlanClock` (default `PlanClock.system()`); in `gatherInput`, read `clock.now()`, derive `nowMinuteOfDay = hour*60 + minute`, compute `earliestStartMinute = DayWindow.effectiveWindowStart(dayStartMinute, nowMinuteOfDay)`, set it on `DayPlanInput`, and include the current `date` and `dayOfWeek` so the ViewModel can pass them to `aiPlanDay` as planning context (13.1, 13.5)
    - When `earliestStartMinute >= dayEndMinute`, short-circuit to the new `DayPlanState.NoRemainingTimeToday` with **no** AI call (13.6)
    - _Requirements: 13.1, 13.5, 13.6_

  - [x] 11.2 Extend `aiPlanDay` and `CloudAiService.planDay` with context, realism, and replanning fields
    - In `functions/src/ai-plan-day.ts`: extend the request schema with `context { dayOfWeek, nowTime }`, set `dayStart = Effective_Window_Start`, add per-task `description`/`tags`, accept optional `priorAssignments` + `adjustment`, and accept a reserved `weather` field left `null` for MVP; extend `buildPlanPrompt` to pass `description`/`tags` and instruct the model to infer a `Task_Kind_Estimate`, schedule only what realistically fits the actual remaining window, and leave the rest unplaced rather than inventing collisions; keep model selection server-side via `getAiConfig(db)` (Mistral branch unchanged)
    - In `ai/CloudAiService.kt`: extend `planDay(...)` to serialize the new `context`, `dayStart = Effective_Window_Start`, per-task `description`/`tags`, and optional `priorAssignments`/`adjustment` with `org.json`, passing all task content (any language/script) unmodified
    - _Requirements: 13.5, 15.2, 16.1, 17.1_

  - [ ]* 11.3 Write example tests for the time/date edge
    - With a fixed injected `PlanClock` and stubbed `CloudAiService`: `DayWindow.effectiveWindowStart` boundaries (now before window start, now after, lead-time clamping at 0 and 30) and `SCHEDULE_LEAD_TIME_MIN` within `0..30` (13.2, 13.4); a late clock ⇒ `NoRemainingTimeToday` with `planDay` not called (13.6); the request payload carries `context` date/day/time and `dayStart == Effective_Window_Start` (13.5); MVP makes no weather/location call yet still produces a plan (14.2–14.4)
    - _Requirements: 13.2, 13.4, 13.5, 13.6, 14.2, 14.3, 14.4_

- [x] 12. Add retryable error states and conversational replanning to the ViewModel
  - [x] 12.1 Add `NoRemainingTimeToday` and retryable `Error` states and remap failures
    - Extend the `DayPlanState` sealed interface with `data object NoRemainingTimeToday` (13.6) and a retryable `data object Error` (18.4); in `requestPlan()`, remap `TimeoutCancellationException`, the generic exception catch, and the `result == null` (network/HTTP 500/parse) branches to `DayPlanState.Error` **instead of** `CouldNotGenerate`, reserving `CouldNotGenerate` for a valid-but-unusable model response; add `retry()` that re-invokes `requestPlan()`
    - _Requirements: 18.4, 18.5_

  - [x] 12.2 Implement `DayPlanViewModel.submitAdjustment` and the post-apply path
    - Add `submitAdjustment(text)` that is a no-op unless the current state is `Review` (15.1); it re-enters `Loading`, calls `CloudAiService.planDay(...)` with the prior assignments + adjustment text under the same `withTimeout(PLAN_TIMEOUT_MS)`, feeds the result through the **same** `ScheduleNormalizer.normalize` with the **same** `DayPlanInput` (including `earliestStartMinute`) so the `Revised_Schedule` obeys Req 13/16 (15.2, 15.3), and on `Valid` re-enters `Review` writing nothing until `accept()` (15.4); when the response is usable but the adjustment cannot be fully honored (e.g. tasks remain `unplaced`), re-enter `Review` carrying an `advisory` while leaving the prior schedule reviewable (15.5); support `submitAdjustment` after `Applied` so a `Revised_Schedule` is shown in `Review` before any further `updateTask` (15.6); apply still flows through `TaskViewModel.updateTask(task, newTitle = task.title, newDate = task.createdDate, newDeadlineTime = assignment.time)`
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6_

  - [ ]* 12.3 Write ViewModel example tests for replanning and error handling
    - With a stubbed `CloudAiService` + fixed `PlanClock`: `submitAdjustment` valid only in `Review` and sends `priorAssignments` + `adjustment` (15.1, 15.2); a revised result re-enters `Review` with no write pre-accept (15.4); an unsatisfiable adjustment ⇒ advisory + prior schedule retained (15.5); a post-apply adjustment ⇒ `Review` before any further `updateTask` (15.6); network/500/timeout ⇒ `DayPlanState.Error` and `retry()` re-calls `planDay` (18.4, 18.5)
    - _Requirements: 15.1, 15.2, 15.4, 15.5, 15.6, 18.4, 18.5_

- [x] 13. Build the dedicated Planning_Screen and alive FAB entry point
  - [x] 13.1 Add `PlanningScreen.kt` and retire `DayPlanReviewSheet`
    - Add `ui/screens/PlanningScreen.kt` as a state-driven full-screen overlay (same pattern as `showFriendsScreen`/`showCirclesScreen`: `AnimatedVisibility` + `BackHandler` that closes it and calls `dayPlanViewModel.reset()`): opens immediately on invoke while `state == Loading` (18.1); `Loading` renders an alive Material 3 Expressive animated progress surface (18.2); `Review` renders each task `title` + proposed `HH:mm` with Accept/Discard, the `Plan_Adjustment` text field with a Revise action, and an `unplaced` tasks callout when present (18.3, 15.1, 16.2); `Error` renders a human-readable message with a **Retry** button and never a raw status code or blank screen (18.4, 18.5); `NoSchedulableTasks`, `NoRemainingTimeToday`, `CouldNotGenerate`, and `InsufficientCredits` each render their own in-screen human-readable message (18.6)
    - Remove `ui/components/DayPlanReviewSheet.kt` and the `HomeScreen` block + `LaunchedEffect` that rendered the sheet and toasted terminal states (now shown inside the screen)
    - _Requirements: 18.1, 18.2, 18.3, 18.4, 18.5, 18.6_

  - [x] 13.2 Replace the top-bar icon with an alive `Plan_My_Day_FAB` and wire the gated overlay
    - In `ui/screens/HomeScreen.kt`: remove the `AutoAwesome` `IconButton` from the top-bar `actions` (19.3); add an alive Material 3 Expressive morphing-shape `Plan_My_Day_FAB` to the existing `floatingActionButton` `Column` (alongside Focus/Voice/Add) (19.1, 19.2); keep the existing gate in `onClick` — evaluate `FeatureGate.isUnlocked(ctx, PremiumFeature.AI_AUTO_PLANNING)` with `trackGateEvaluated(...)` before opening; if locked, show `PremiumUpsellSheet` + `trackUpsellShown(...)` and do **not** open for planning or call `planDay` (20.1, 20.2); if unlocked, set `showPlanningScreen = true` and call `dayPlanViewModel.requestPlan()` (20.3); add the `showPlanningScreen` overlay state + `BackHandler` wiring
    - _Requirements: 19.1, 19.2, 19.3, 20.1, 20.2, 20.3_

  - [ ]* 13.3 Write Compose tests for the Planning_Screen and FAB
    - `PlanningScreen` renders an alive progress indicator in `Loading` (18.1, 18.2); `Review` shows title + `HH:mm` with Accept/Discard, the adjustment field, and an `unplaced` callout (18.3, 16.2); `Error` shows a human-readable message + Retry and no raw status code (18.4); each terminal state renders its message (18.6); the `Plan_My_Day_FAB` is present and invokes planning while the top-bar `AutoAwesome` icon is gone (19); locked gate ⇒ upsell + screen not opened for planning, unlocked ⇒ screen opens + `requestPlan` (20.2, 20.3)
    - _Requirements: 16.2, 18.1, 18.2, 18.3, 18.4, 18.6, 19.1, 19.3, 20.2, 20.3_

- [x] 14. Iteration 2 checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core (non-test) implementation tasks are never optional.
- Each task references specific granular requirements clauses for traceability.
- Property tests (Properties 1–12) target only the pure logic — Track A's `ScheduleNormalizer`/`PlanApply` (1–6) and Track B's `GatingDecision`/`EntitlementStore.effectiveTier` (7–12) — and are placed immediately beside the function they validate so correctness issues surface before the Cloud Functions/UI wiring depends on them.
- The Cloud AI call (`aiPlanDay`/`CloudAiService.planDay`), the Remote Config read (`AiConfigService.premiumGatingEnabled`), and the Compose UI are validated by Cloud Functions integration tests, example/edge tests, and Compose tests rather than by property-based tests, matching the design's testing strategy.
- The two tracks are independent: Track A can ship with gating permanently OFF, and Track B can ship without Track A (it references Track A only as the named candidate `AI_AUTO_PLANNING`).
- **Track B safety:** replacing the `FeatureGate.isUnlocked` stub (task 6.2) keeps the OFF path provably identical to today's always-`true` behavior — while `premium_gating_enabled` is false the decision ignores the tier and returns unlocked for every feature, so no live paywall is introduced and Live_Users are unaffected until an owner deliberately turns the switch ON.
- **Test execution environment:** JVM unit-test execution (the jqwik property tests and the Compose tests) is currently blocked because the build environment lacks a complete JDK 21 with `jlink` (`gradle.properties` pins JDK 17). Until a full JDK 21 toolchain is available, the pure-logic and Compose test sources are verified by **compilation** (they must compile cleanly) rather than by running the suite. The Node-based Cloud Functions tests for `aiPlanDay` run independently of that constraint and are executable now.
- **Iteration 2 (Track A — WS5, tasks 10–14):** extends Track A for Requirements 13–20 and adds exactly one new pure property — **Property 13** (time-aware normalization: never-past, never-conflict, never-duplicate, total partition). Weather is intentionally skipped for MVP (Req 14 decision: deterministic time/date only, no new permission); the `weather` request field is reserved and `null`. The new `Planning_Screen` retires `DayPlanReviewSheet` and is reached from the alive `Plan_My_Day_FAB`, preserving the existing `FeatureGate.isUnlocked(AI_AUTO_PLANNING)` gate. Track B (Requirements 7–12, Properties 7–12) is unchanged.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.7", "2.1", "4.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "1.4", "1.5", "1.6", "1.8", "2.2", "4.2", "4.4"] },
    { "id": 2, "tasks": ["2.3", "2.4", "2.5", "2.6", "2.7", "2.8", "4.3", "5.1"] },
    { "id": 3, "tasks": ["5.2", "6.1"] },
    { "id": 4, "tasks": ["5.3", "6.2", "6.3"] },
    { "id": 5, "tasks": ["5.4", "6.4"] },
    { "id": 6, "tasks": ["8.1", "8.2"] },
    { "id": 7, "tasks": ["8.3", "8.4"] },
    { "id": 8, "tasks": ["10.1", "11.2"] },
    { "id": 9, "tasks": ["10.2", "11.1"] },
    { "id": 10, "tasks": ["11.3", "12.1"] },
    { "id": 11, "tasks": ["12.2"] },
    { "id": 12, "tasks": ["12.3", "13.1"] },
    { "id": 13, "tasks": ["13.2"] },
    { "id": 14, "tasks": ["13.3"] }
  ]
}
```
