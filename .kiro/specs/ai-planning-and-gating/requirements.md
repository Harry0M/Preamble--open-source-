# Requirements Document

## Introduction

This feature adds two independent capabilities to the Preamble Android app (Kotlin, Jetpack Compose, Room local database, Firebase Firestore backend, TypeScript Cloud Functions). The two tracks share no runtime dependency on each other and are specified separately so they can be designed, built, and shipped independently.

**Track A — AI Plan-My-Day (auto-schedule):** A user can ask the app to plan their day. The AI proposes start times (the existing `Task.deadlineTime` field) for today's unscheduled, incomplete tasks, while treating already-scheduled tasks and calendar events as fixed and not to be double-booked. The proposed schedule is presented for the user to review before anything changes. The user either accepts the proposal, which applies the proposed times to the underlying tasks through the existing task-update path, or discards it, leaving every task unchanged. The AI runs through the existing Cloud AI path (the same server proxy used by `CloudAiService.parseTask` / `aiChat`) and may consume AI credits like other AI features. The call is bounded by a timeout and failures never lose task data.

**Track B — Premium gating infrastructure (default OFF, no live paywall):** This track replaces the currently stubbed, always-unlocked gating (`FeatureGate.isUnlocked` returns `true` unconditionally) with a real gating decision driven by two inputs: the user's `EntitlementTier` and a remotely fetched master switch that defaults to OFF. The master switch is the critical safety mechanism: while it is OFF, every feature stays unlocked for every user, exactly as production behaves today, with no observable change. Only when an owner deliberately turns the master switch ON do designated premium features become locked for non-premium tiers, while premium tiers and the promotional launch cohort retain full access. Locked features present an upsell instead of the feature. This track delivers the gating decision as a pure, testable function and the supporting infrastructure; it does not introduce a live paywall, purchase flow, or billing.

This document defines WHAT each track must do. Technical design (data structures, function decomposition, prompt shape, UI composition) is addressed in the design phase.

## Glossary

- **Plan_My_Day**: The Track A feature by which a user requests an AI-generated schedule for the current day's eligible tasks.
- **Day_Plan_Service**: The Android-side component that gathers eligible tasks, invokes the Cloud AI path, produces a Proposed_Schedule, and applies or discards it. Acts as the Track A system name.
- **Schedulable_Task**: A task that is eligible for Plan_My_Day scheduling: dated to the current day, not completed, having no existing `deadlineTime`, and not an event (`isEvent == false`).
- **Fixed_Commitment**: A current-day task or calendar event that already has a `deadlineTime` (or an event time range), treated as immovable by Plan_My_Day and not eligible to be rescheduled.
- **Proposed_Schedule**: The AI-generated set of proposed `deadlineTime` assignments, one per Schedulable_Task, presented to the user for review and not yet applied to any task.
- **Day_Plan_Review**: The UI state in which the Proposed_Schedule is shown to the user with accept and discard actions, before any task is mutated.
- **Plan_Apply**: The operation that writes each accepted proposed time onto its Schedulable_Task through the existing task-update path.
- **Cloud_AI_Path**: The existing server-side AI proxy reached through `CloudAiService` (e.g. `parseTask` via `aiParseTask`, `chat` via `aiChat`), which holds prompts server-side and enforces the AI credit economy.
- **AI_Credit**: A unit in the existing server-enforced credit economy (`AiCreditsManager`), where some models are free (rate 0) and others cost credits per 1000 tokens.
- **EntitlementTier**: The existing server-driven entitlement enum in `data/Entitlement.kt` with values `FREE_TIER`, `UNPREMIUM`, `PROMOTIONAL`, `PREMIUM`, `PREMIUM_STUDENT`, and `PREMIUM_YOUNGSTER`, synced from `users/{uid}.entitlement_*`.
- **Premium_Tier**: An EntitlementTier whose holder receives full access to premium features: `PREMIUM`, `PREMIUM_STUDENT`, `PREMIUM_YOUNGSTER`, and the launch cohort `PROMOTIONAL`.
- **Non_Premium_Tier**: An EntitlementTier whose holder is subject to gating when the master switch is ON: `FREE_TIER` and `UNPREMIUM`.
- **Premium_Gating_Master_Switch**: A single remotely fetched boolean (the flag `premium_gating_enabled`) that defaults to OFF/false and governs whether any gating is enforced at all.
- **Gating_Decision**: A pure function that returns whether a given Premium_Feature is unlocked, computed solely from the feature, the effective EntitlementTier, and the Premium_Gating_Master_Switch value.
- **Premium_Feature**: A member of the existing `PremiumFeature` enum that is designated as a gating candidate.
- **Upsell**: A screen or bottom sheet shown in place of a locked Premium_Feature that informs the user the feature requires premium access.
- **Live_User**: An existing production user on the published Play Store build whose experience must not change while the Premium_Gating_Master_Switch is OFF.

### Glossary additions (Track A — Iteration 2: WS5)

- **Current_Local_Datetime**: The real current local date, day-of-week, and time-of-day for the user, obtained deterministically from the device system clock and the device time zone at the moment Plan_My_Day is invoked. This is the always-on, deterministic environment input for planning.
- **Schedule_Lead_Time**: A small, non-negative buffer duration (no greater than 30 minutes) added to the Current_Local_Datetime to compute the earliest time at which a proposed task may start on the current day, so the plan does not propose a start time that is effectively already past.
- **Effective_Window_Start**: For the current day, the later of the configured working-window start and the Current_Local_Datetime time-of-day plus the Schedule_Lead_Time. This is the earliest minute-of-day any Schedulable_Task may be proposed for.
- **Weather_Context**: Optional, best-effort information about today's weather and environment for the user's location, used only to inform the plan when available. Weather_Context is never required for a plan to be produced.
- **Plan_Adjustment**: A free-text, natural-language message a user submits after a Proposed_Schedule has been produced or applied, describing their situation or a requested change, which the AI uses to revise the plan.
- **Revised_Schedule**: A Proposed_Schedule produced in response to a Plan_Adjustment, which is subject to the same Day_Plan_Review gate as the original Proposed_Schedule.
- **Infeasible_Plan_Request**: A Plan_My_Day request (or Plan_Adjustment) in which the Schedulable_Tasks, given a realistic minimum duration per task inferred from each task's kind and effort, cannot all fit within the remaining time of the working window — for example, more tasks than can physically be completed in the time that remains.
- **Task_Kind_Estimate**: The AI's inferred kind and realistic effort/duration of a task, derived from the task's title, description, and tags, used to produce a realistic schedule.
- **Mistral_Model**: The Mistral chat model already configured for the app's Cloud AI path (selected by `getAiConfig` in Cloud Functions, e.g. `mistral-small-latest` / `mistral-medium-latest`), used by the AI chat path and by `aiPlanDay`.
- **Planning_Screen**: A dedicated, full-screen Plan_My_Day surface that opens immediately when the user invokes Plan_My_Day, presenting an alive Material 3 Expressive loading/progress state while the AI works, then the reviewable plan, error states, or terminal messages.
- **Plan_My_Day_FAB**: The Plan_My_Day entry point rendered as a floating action button styled with an alive Material 3 Expressive shape, replacing the previous top-bar icon located beside the screen title.

## Requirements

---

## Track A — AI Plan-My-Day

### Requirement 1: Request a day plan

**User Story:** As a user, I want to ask the app to plan my day, so that the AI proposes start times for my unscheduled tasks without me arranging them manually.

#### Acceptance Criteria

1. THE Day_Plan_Service SHALL provide a Plan_My_Day action that a user can invoke for the current day.
2. WHEN a user invokes Plan_My_Day, THE Day_Plan_Service SHALL select the set of Schedulable_Tasks as the current-day tasks that are not completed, have no existing `deadlineTime`, and have `isEvent` equal to false.
3. WHEN a user invokes Plan_My_Day, THE Day_Plan_Service SHALL collect the current-day Fixed_Commitments, comprising current-day tasks and calendar events that already have a scheduled time, as fixed inputs that are not candidates for rescheduling.
4. IF the set of Schedulable_Tasks is empty when a user invokes Plan_My_Day, THEN THE Day_Plan_Service SHALL NOT invoke the Cloud_AI_Path and SHALL display a message indicating that there are no unscheduled tasks to plan.
5. WHEN a user invokes Plan_My_Day with one or more Schedulable_Tasks, THE Day_Plan_Service SHALL request a Proposed_Schedule through the Cloud_AI_Path.

### Requirement 2: Generate a non-conflicting proposed schedule

**User Story:** As a user, I want the proposed schedule to respect my existing commitments and task priorities, so that the plan is realistic.

#### Acceptance Criteria

1. WHEN the Cloud_AI_Path returns a Proposed_Schedule, THE Day_Plan_Service SHALL produce at most one proposed `deadlineTime` for each Schedulable_Task and SHALL produce no proposed time for any task that is not a Schedulable_Task.
2. THE Day_Plan_Service SHALL treat every Fixed_Commitment time as reserved, such that no proposed `deadlineTime` in the Proposed_Schedule coincides with a Fixed_Commitment's reserved time.
3. THE Proposed_Schedule SHALL assign each Schedulable_Task a distinct proposed `deadlineTime`, such that no two Schedulable_Tasks are proposed for the same time.
4. WHERE two Schedulable_Tasks have different `priority` values, THE Day_Plan_Service SHALL order their proposed times so that the task with the higher `priority` value is scheduled no later than the task with the lower `priority` value.
5. THE Day_Plan_Service SHALL express every proposed `deadlineTime` in the same `HH:mm` time-of-day format used by the existing `Task.deadlineTime` field.

### Requirement 3: Review before apply

**User Story:** As a user, I want to review the proposed schedule before it changes anything, so that I stay in control of my tasks.

#### Acceptance Criteria

1. WHEN a Proposed_Schedule is produced, THE Day_Plan_Service SHALL enter the Day_Plan_Review state and SHALL present the Proposed_Schedule to the user with an accept action and a discard action.
2. WHILE the Day_Plan_Service is in the Day_Plan_Review state, THE Day_Plan_Service SHALL leave every task unchanged and SHALL NOT write any proposed `deadlineTime` to any task.
3. THE Day_Plan_Review presentation SHALL show, for each Schedulable_Task, the task title and the proposed `deadlineTime`.

### Requirement 4: Apply or discard the plan

**User Story:** As a user, I want to accept or discard the proposed schedule, so that the plan is applied only when I confirm it.

#### Acceptance Criteria

1. WHEN a user accepts the Proposed_Schedule from the Day_Plan_Review state, THE Day_Plan_Service SHALL apply each proposed `deadlineTime` to its Schedulable_Task through the existing task-update path.
2. WHEN the Day_Plan_Service applies the Proposed_Schedule, THE Day_Plan_Service SHALL change only the `deadlineTime` of each affected Schedulable_Task and SHALL leave the title, description, priority, tags, recurrence fields, and completion state of every task unchanged.
3. WHEN a user discards the Proposed_Schedule from the Day_Plan_Review state, THE Day_Plan_Service SHALL leave every task unchanged and SHALL exit the Day_Plan_Review state.
4. IF applying any proposed `deadlineTime` during Plan_Apply fails, THEN THE Day_Plan_Service SHALL preserve every task's pre-apply state and SHALL display a message indicating that the schedule could not be applied.

### Requirement 5: Bounded AI call and graceful failure

**User Story:** As a user, I want the planning request to fail safely, so that my task data is never lost when the AI is slow or unavailable.

#### Acceptance Criteria

1. WHEN the Day_Plan_Service requests a Proposed_Schedule, THE Day_Plan_Service SHALL bound the Cloud_AI_Path call by a timeout.
2. IF the Cloud_AI_Path call does not return within the timeout, THEN THE Day_Plan_Service SHALL abandon the request, SHALL leave every task unchanged, and SHALL display a message indicating that the day plan could not be generated.
3. IF the Cloud_AI_Path returns an error or a response that yields no valid proposed time for any Schedulable_Task, THEN THE Day_Plan_Service SHALL leave every task unchanged and SHALL display a message indicating that the day plan could not be generated.
4. THE Day_Plan_Service SHALL route the planning request through the Cloud_AI_Path under the same AI_Credit economy as other AI features, such that a planning request consumes AI_Credits according to the model used.
5. IF the Cloud_AI_Path rejects the planning request because the user has insufficient AI_Credits, THEN THE Day_Plan_Service SHALL leave every task unchanged and SHALL display a message indicating that more AI credits are required.

### Requirement 6: Plan-My-Day analytics

**User Story:** As a product owner, I want plan request, accept, and discard tracked, so that I can measure how the feature is used.

#### Acceptance Criteria

1. WHEN a user invokes Plan_My_Day with one or more Schedulable_Tasks, THE Day_Plan_Service SHALL record an analytics event indicating a day plan was requested, including the count of Schedulable_Tasks.
2. WHEN a user accepts a Proposed_Schedule, THE Day_Plan_Service SHALL record an analytics event indicating the day plan was accepted.
3. WHEN a user discards a Proposed_Schedule, THE Day_Plan_Service SHALL record an analytics event indicating the day plan was discarded.

---

## Track B — Premium Gating Infrastructure

### Requirement 7: Remotely fetched master switch defaulting OFF

**User Story:** As an app owner, I want a remote master switch that defaults to off, so that gating stays dormant in production until I deliberately enable it.

#### Acceptance Criteria

1. THE Gating_Decision SHALL read the Premium_Gating_Master_Switch from a remotely fetched configuration value identified by the key `premium_gating_enabled`.
2. WHERE the Premium_Gating_Master_Switch value has not been fetched, cannot be read, or is absent from the remote configuration, THE Gating_Decision SHALL treat the Premium_Gating_Master_Switch as OFF.
3. THE remotely fetched configuration SHALL define the default value of `premium_gating_enabled` as false.
4. THE Premium_Gating_Master_Switch SHALL transition to ON only when the remotely fetched value of `premium_gating_enabled` is true.

### Requirement 8: No gating while the master switch is OFF

**User Story:** As a live user, I want nothing to become locked unexpectedly, so that my current experience is unaffected until premium gating is intentionally turned on.

#### Acceptance Criteria

1. WHILE the Premium_Gating_Master_Switch is OFF, THE Gating_Decision SHALL return unlocked for every Premium_Feature for every EntitlementTier.
2. WHILE the Premium_Gating_Master_Switch is OFF, THE Gating_Decision SHALL return unlocked for a Non_Premium_Tier holder identically to a Premium_Tier holder, such that the result does not depend on the EntitlementTier.
3. WHILE the Premium_Gating_Master_Switch is OFF, THE Day_Plan_Service and every other feature SHALL behave for a Live_User exactly as they behave with no gating present, with no Upsell shown and no feature withheld.

### Requirement 9: Gating decision as a pure function

**User Story:** As an engineer, I want the gating decision to be a pure function of its inputs, so that it is deterministic and unit-testable.

#### Acceptance Criteria

1. THE Gating_Decision SHALL compute the unlocked result solely from three inputs: the Premium_Feature, the effective EntitlementTier, and the Premium_Gating_Master_Switch value.
2. WHEN the Gating_Decision is evaluated twice with identical values for the Premium_Feature, the effective EntitlementTier, and the Premium_Gating_Master_Switch, THE Gating_Decision SHALL return the same unlocked result both times.
3. THE Gating_Decision SHALL determine the effective EntitlementTier using the existing entitlement expiry resolution, such that an expired Premium_Tier is treated as `UNPREMIUM`.

### Requirement 10: Locking premium features when the switch is ON

**User Story:** As an app owner, I want designated premium features locked for non-premium users only when gating is on, so that premium becomes meaningful without affecting paying or promotional users.

#### Acceptance Criteria

1. WHILE the Premium_Gating_Master_Switch is ON, THE Gating_Decision SHALL return locked for a Premium_Feature when the effective EntitlementTier is a Non_Premium_Tier.
2. WHILE the Premium_Gating_Master_Switch is ON, THE Gating_Decision SHALL return unlocked for a Premium_Feature when the effective EntitlementTier is a Premium_Tier.
3. WHILE the Premium_Gating_Master_Switch is ON, THE Gating_Decision SHALL return unlocked for a Premium_Feature when the effective EntitlementTier is `PROMOTIONAL`.
4. THE Gating_Decision SHALL designate as Premium_Features exactly the candidate set: AI auto-planning (the Track A Plan_My_Day capability), advanced statistics including the Wrapped experience, and unlimited AI credits, mapped onto the existing `PremiumFeature` enum members.
5. WHILE the Premium_Gating_Master_Switch is ON, THE Gating_Decision SHALL return unlocked for any feature that is not a designated Premium_Feature, for every EntitlementTier.

### Requirement 11: Upsell for locked features

**User Story:** As a non-premium user encountering a locked feature, I want to see an upsell, so that I understand the feature requires premium and is not broken.

#### Acceptance Criteria

1. WHEN a user attempts to use a Premium_Feature for which the Gating_Decision returns locked, THE Day_Plan_Service or hosting feature SHALL present an Upsell and SHALL NOT perform the locked feature's action.
2. WHEN the Gating_Decision returns unlocked for a feature a user attempts to use, THE hosting feature SHALL perform the feature's action and SHALL NOT present an Upsell.
3. THE Upsell SHALL identify the Premium_Feature being gated.

### Requirement 12: Gating analytics

**User Story:** As a product owner, I want gate evaluations and upsell displays tracked, so that I can measure gating impact when it is enabled.

#### Acceptance Criteria

1. WHEN the Gating_Decision is evaluated for a Premium_Feature at a feature entry point, THE hosting feature SHALL record an analytics event indicating a gate was evaluated, including the Premium_Feature and the resulting unlocked state.
2. WHEN an Upsell is presented, THE hosting feature SHALL record an analytics event indicating an upsell was shown, including the Premium_Feature.

---

## Track A — Iteration 2: WS5 Plan-My-Day overhaul

This section extends Track A (Requirements 1–6). It does not change Track B gating semantics (Requirements 7–12); the new Planning_Screen continues to be gated by the existing Gating_Decision for `AI_AUTO_PLANNING`. Requirements 13–20 add real time/date/day awareness, best-effort weather, conversational replanning, realism, the Mistral model and multilingual handling, and a dedicated alive planning screen reached from an alive FAB.

### Requirement 13: Deterministic time, date, and day awareness — never schedule into the past

**User Story:** As a user planning my day partway through it, I want the AI to know the real current time and date, so that it never proposes start times that have already passed.

#### Acceptance Criteria

1. WHEN a user invokes Plan_My_Day, THE Day_Plan_Service SHALL obtain the Current_Local_Datetime deterministically from the device system clock and device time zone.
2. WHEN the Day_Plan_Service plans for the current day, THE Day_Plan_Service SHALL compute the Effective_Window_Start as the later of the configured working-window start and the Current_Local_Datetime time-of-day plus the Schedule_Lead_Time.
3. WHEN the Cloud_AI_Path returns a Proposed_Schedule for the current day, THE Day_Plan_Service SHALL ensure every proposed `deadlineTime` is at or after the Effective_Window_Start.
4. THE Schedule_Lead_Time SHALL be a non-negative duration no greater than 30 minutes.
5. THE Day_Plan_Service SHALL provide the Current_Local_Datetime, including the current date, day-of-week, and time-of-day, to the Cloud_AI_Path as planning context.
6. IF the Effective_Window_Start is at or after the configured working-window end, THEN THE Day_Plan_Service SHALL NOT invoke the Cloud_AI_Path and SHALL display a message indicating that there is no remaining time today to plan.
7. WHEN the Day_Plan_Service computes the Current_Local_Datetime twice for the same device clock value and time zone, THE Day_Plan_Service SHALL produce the same Current_Local_Datetime both times.

### Requirement 14: Best-effort weather and environment awareness

**User Story:** As a user, I want the plan to account for today's weather when it can, so that it is more realistic, without the feature breaking when weather is unavailable.

#### Acceptance Criteria

1. WHERE Weather_Context is available, THE Day_Plan_Service MAY provide the Weather_Context to the Cloud_AI_Path as additional planning context.
2. IF Weather_Context is unavailable, cannot be retrieved, or the required location access has not been granted, THEN THE Day_Plan_Service SHALL produce a Proposed_Schedule using the Current_Local_Datetime alone, without failing the request.
3. THE Day_Plan_Service SHALL treat the Current_Local_Datetime as an always-required, deterministic input and SHALL treat Weather_Context as an optional, best-effort input.
4. THE Day_Plan_Service SHALL NOT make Weather_Context a precondition for producing a Proposed_Schedule.

### Requirement 15: Conversational replanning and adjustment

**User Story:** As a user, I want to tell the AI about my situation after I see a plan, so that it can revise the schedule and advise me before anything is applied.

#### Acceptance Criteria

1. WHILE the Day_Plan_Service is in the Day_Plan_Review state, THE Day_Plan_Service SHALL accept a free-text Plan_Adjustment from the user.
2. WHEN a user submits a Plan_Adjustment, THE Day_Plan_Service SHALL request a Revised_Schedule through the Cloud_AI_Path, providing the prior Proposed_Schedule, the Plan_Adjustment text, the Schedulable_Tasks, the Fixed_Commitments, and the Current_Local_Datetime as context.
3. WHEN the Cloud_AI_Path returns a Revised_Schedule, THE Day_Plan_Service SHALL apply the same correctness constraints defined for a Proposed_Schedule, including the Effective_Window_Start constraint of Requirement 13.
4. WHEN a Revised_Schedule is produced, THE Day_Plan_Service SHALL re-enter the Day_Plan_Review state and SHALL NOT write any proposed `deadlineTime` to any task until the user accepts the Revised_Schedule.
5. WHERE the Plan_Adjustment requests a change that cannot be satisfied, THE Day_Plan_Service SHALL present an advisory message explaining what could not be accommodated and SHALL leave the prior Proposed_Schedule available for review.
6. WHEN a user submits a Plan_Adjustment after a plan was applied, THE Day_Plan_Service SHALL produce a Revised_Schedule that is presented in the Day_Plan_Review state before any further task is changed.

### Requirement 16: Self-aware realism

**User Story:** As a user, I want the AI to be honest about what fits in my day, so that it never fabricates an impossible schedule just because I asked.

#### Acceptance Criteria

1. WHEN the Day_Plan_Service requests a Proposed_Schedule, THE Day_Plan_Service SHALL provide each task's title, description, and tags to the Cloud_AI_Path so the model can form a Task_Kind_Estimate for realistic scheduling.
2. IF a Plan_My_Day request or Plan_Adjustment is an Infeasible_Plan_Request, THEN THE Day_Plan_Service SHALL schedule only the Schedulable_Tasks that fit within the remaining working window and SHALL clearly indicate which Schedulable_Tasks could not be placed.
3. THE Day_Plan_Service SHALL NOT assign two distinct Schedulable_Tasks the same proposed `deadlineTime` and SHALL NOT assign any proposed `deadlineTime` that coincides with a Fixed_Commitment, regardless of the content of the user's request.
4. WHERE the user's request asserts that more tasks fit than the remaining working window allows, THE Day_Plan_Service SHALL base the Proposed_Schedule on the actual remaining working window rather than on the user's assertion.

### Requirement 17: Mistral model and multilingual handling

**User Story:** As a multilingual user, I want planning to use the same AI as chat and to understand my tasks in any language, so that the plan works regardless of the script I use.

#### Acceptance Criteria

1. THE Day_Plan_Service SHALL route the planning request through the Cloud_AI_Path using the Mistral_Model selected by the Cloud Functions AI configuration, consistent with the model used by the AI chat path.
2. WHEN a Schedulable_Task title or description, or a Plan_Adjustment, is written in any language or script, THE Day_Plan_Service SHALL include that content unmodified in the Cloud_AI_Path request.
3. WHEN the Cloud_AI_Path returns a Proposed_Schedule for tasks whose titles are in any language or script, THE Day_Plan_Service SHALL preserve each task's original title unchanged when presenting and applying the plan.

### Requirement 18: Dedicated planning screen with alive loading and graceful errors

**User Story:** As a user, I want tapping Plan-My-Day to open a beautiful screen right away that shows progress and handles errors cleanly, so that I am never left staring at a blank delay or a raw server error.

#### Acceptance Criteria

1. WHEN a user invokes Plan_My_Day and the feature is unlocked, THE Planning_Screen SHALL open before the Cloud_AI_Path call completes.
2. WHILE the Day_Plan_Service is gathering inputs or awaiting the Cloud_AI_Path response, THE Planning_Screen SHALL display a Material 3 Expressive loading state with animated progress.
3. WHEN a Proposed_Schedule is ready for review, THE Planning_Screen SHALL present the Day_Plan_Review content, showing each task title and proposed `deadlineTime`, with accept and discard actions.
4. IF the Cloud_AI_Path returns an error, including an HTTP 500, or the request times out, THEN THE Planning_Screen SHALL display a human-readable error state with a retry action and SHALL NOT display a raw status code or a blank screen.
5. WHEN a user activates the retry action in the error state, THE Day_Plan_Service SHALL request a new Proposed_Schedule.
6. WHEN the Day_Plan_Service reaches the NoSchedulableTasks, CouldNotGenerate, or InsufficientCredits outcome, THE Planning_Screen SHALL present the corresponding human-readable message.

### Requirement 19: Alive FAB entry point

**User Story:** As a user, I want a distinctive, alive Plan-My-Day button, so that the feature is easy to find and feels part of the Social Hub theme.

#### Acceptance Criteria

1. THE Plan_My_Day_FAB SHALL be the entry point that invokes Plan_My_Day.
2. THE Plan_My_Day_FAB SHALL be styled as a Material 3 Expressive floating action button with an alive morphing shape consistent with the app theme.
3. THE Plan_My_Day entry point SHALL NOT appear beside the screen title text.

### Requirement 20: Planning screen respects the existing gate

**User Story:** As an app owner, I want the new planning screen to honor the existing premium gate, so that the overhaul does not bypass gating.

#### Acceptance Criteria

1. WHEN a user invokes Plan_My_Day, THE Plan_My_Day_FAB host SHALL evaluate the Gating_Decision for the `AI_AUTO_PLANNING` Premium_Feature before opening the Planning_Screen for planning.
2. IF the Gating_Decision returns locked for `AI_AUTO_PLANNING`, THEN THE Plan_My_Day_FAB host SHALL present the Upsell and SHALL NOT request a Proposed_Schedule.
3. WHEN the Gating_Decision returns unlocked for `AI_AUTO_PLANNING`, THE Planning_Screen SHALL proceed with the Plan_My_Day request.

---

## Known Technical Constraints and Design Flags

These notes carry context from the existing codebase into the design phase. They are not acceptance criteria.

- **Track A reuses the existing Cloud AI path.** Plan_My_Day should be implemented over `CloudAiService` using the same server-proxy style as `parseTask` (`aiParseTask`) / `chat` (`aiChat`), so prompts stay server-side and the existing AI_Credit economy (`AiCreditsManager`, with per-model `creditPer1kTokens`) applies. The design must decide the exact server entry point (extend an existing function or add a new one) and the proposal schema.
- **Track A applies through the existing task-update path.** Accepted times must be written via the established `TaskViewModel.updateTask` / `TaskRepository` update flow rather than a new mutation path, so alarms, reminders, and sync side effects remain consistent. Only `deadlineTime` changes.
- **`deadlineTime` is an `HH:mm` time-of-day string scoped to the task's `createdDate`.** The proposal must conform to this representation; it is not an absolute timestamp.
- **The gating master switch must default OFF and be fetched remotely.** It can be backed by Firebase Remote Config (the template currently defines BOOLEAN flags such as `ai_kill_switch`, defaulting via string `"false"`) or a PostHog feature flag (`AnalyticsManager.isFeatureEnabled` / `getFeatureFlagPayload`). Either source must resolve to OFF when unavailable. The design must pick one source and document it.
- **The gating decision must be a pure, testable function.** Today `FeatureGate.isUnlocked(ctx, feature)` returns `true` unconditionally. The replacement should expose a pure function over `(PremiumFeature, EntitlementTier, masterSwitchEnabled)` with the context/remote read handled at the edges, so the core decision is unit-testable without Android or network.
- **Reuse the existing entitlement model.** `EntitlementStore.effectiveTier` already resolves expiries (expired premium → `UNPREMIUM`) and `EntitlementStore.unlocks` already classifies tiers; the Gating_Decision should build on these rather than reimplementing tier logic.
- **The existing `PremiumFeature` enum already lists candidates** (`WRAPPED`, `AI_AUTO_SUBTASKS`, `AI_EDIT_FROM_NOTIFICATION`, `EXPRESSIVE_APPEARANCE`, `STATS_EXTENDED_RANGE`, `STATS_DEDICATED_SCREEN`). The design must map the Track B candidate set (AI auto-planning, advanced stats/Wrapped, unlimited AI credits) onto enum members and may add members if needed; keep the locked set explicit and small.
- **Turning gating ON is an explicit owner action; Live_Users must be unaffected while OFF.** The replacement of the always-true stub must be done carefully so that the OFF path is provably equivalent to today's behavior. `FeatureGateManager` (a separate stub exposing `themeUnlocked`/`isThemeUnlocked`) is related but distinct; the design should clarify how the two coexist.
- **The two tracks are independent.** Track A can ship with gating permanently OFF; Track B can ship without Track A. Track B references Track A only as one named gating candidate (AI auto-planning).

### Iteration 2 (WS5) design flags

- **Root cause of the "plans from 9 AM at 1 PM" bug.** `DayPlanService` currently uses a hardcoded working window (`DEFAULT_DAY_START_MINUTE = 9*60`, `DEFAULT_DAY_END_MINUTE = 21*60`) and never reads the current time, so it always proposes from 09:00. Requirement 13 requires computing an Effective_Window_Start from the device clock. The design must thread the Current_Local_Datetime through `DayPlanService.gatherInput` (and into the `aiPlanDay` request as `dayStart`/context), and the pure `ScheduleNormalizer` must drop candidate slots earlier than the Effective_Window_Start so correctness is still enforced client-side.
- **Time/date sourcing decision (already made).** Time, date, and day-of-week are sourced **deterministically from the device system clock and time zone** (e.g. `java.time.LocalDateTime.now()` / `ZonedDateTime`), not from the network or the model. This keeps planning testable (inject a clock/`Clock` for the pure layer) and fixes the past-scheduling bug without any new dependency or permission.
- **Weather/environment source — OPEN DECISION (flagged, do NOT hard-require a permission).** Requirement 14 keeps weather strictly best-effort. The design must choose a weather source and decide whether to use coarse location at all; the recommended path is to use only already-granted signals and skip weather entirely when location permission is absent, so **no new mandatory runtime permission is introduced**. Options to evaluate in design: (a) skip weather for the MVP and pass only time/date/day; (b) use a free weather API (e.g. Open-Meteo, no API key) with coarse last-known location *only if* location permission was already granted for an existing feature; (c) let the user optionally type their conditions via the Plan_Adjustment channel (Requirement 15). The deterministic time/date path must ship regardless of which weather option is chosen.
- **Conversational replanning reuses the Cloud AI path.** Requirement 15's Revised_Schedule should reuse `CloudAiService.planDay` / `aiPlanDay` with the prior proposal and the adjustment text added to the server-held prompt, then flow back through the same `ScheduleNormalizer` and Day_Plan_Review gate. No new mutation path; apply still goes through `TaskViewModel.updateTask`.
- **Model is Mistral via Cloud Functions.** `aiPlanDay` already branches on `isMistralModel`/`getAiConfig` and calls the Mistral chat completions endpoint, identical to `aiParseTask`/`aiChat`. Requirement 17 is satisfied by keeping model selection server-side (`getAiConfig(db)`); no client-side model choice. Multilingual handling means passing task text through unmodified and preserving original titles on the client (the normalizer keys on task id, never on title text).
- **UX overhaul: dedicated screen replaces the delayed bottom sheet.** Today the entry point is an `AutoAwesome` `IconButton` in the `HomeScreen` top-bar `actions`, and `DayPlanReviewSheet` (a `ModalBottomSheet`) renders **only** in the `Review` state, so during `Loading` nothing is shown and a server 500 surfaces as a bare toast. Requirements 18–19 require a `Planning_Screen` (new navigation destination or full-screen container) that opens immediately on tap and renders the `Loading`, `Review`, error, and terminal states as alive Material 3 Expressive surfaces, plus moving the entry point to a `Plan_My_Day_FAB` (remove the top-bar icon beside the title). The existing `DayPlanState` machine already has `Loading`, `Review`, `Applying`, `Applied`, `Failed`, `NoSchedulableTasks`, `CouldNotGenerate`, and `InsufficientCredits`; the design should add a distinct error state carrying a retry affordance (mapping the HTTP 500 / network error path that currently collapses into `CouldNotGenerate`) so Requirement 18.4–18.5 (graceful error + retry) can be met.
- **Gate semantics unchanged.** The `Plan_My_Day_FAB` host keeps the existing `FeatureGate.isUnlocked(ctx, AI_AUTO_PLANNING)` check (Requirement 20); Track B (Requirements 7–12) is untouched.
