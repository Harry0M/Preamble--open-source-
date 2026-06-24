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
