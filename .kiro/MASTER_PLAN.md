# Preamble — Collaborative & Social Enhancement Master Plan

> Persistent plan capturing the full scope discussed for enhancing the collaborative-task
> and social features after `social-hub-redesign`. This is the source of truth for the
> work ahead. Update it as workstreams are scoped into specs and completed.

## Working agreement / global principles

- **Design language (apply everywhere):** stay consistent with the Social Hub / Friends
  screen theme — Cardfolio-style vibrant stacked cards, the lime hero ID card,
  `RandomBackgrounds` PNG packs, **Material 3 Expressive** components, and **live / "alive"
  morphing shapes + expressive motion**. Everything should *feel alive*. Impress on design.
- **Rewards:** NO referral rewards / AI credits during development. The server-side reward
  gate (`REFERRAL_REWARDS_ENABLED`) stays disabled; CTA copy must not promise credits.
- **AI model:** use the **Mistral** model that already runs in our Cloud Functions / AI chat.
- **Multilingual:** AI must handle any script / language in task title, description, and
  user input.
- **Process:** each workstream below is scoped into the relevant existing spec
  (requirements → design → tasks) before implementation.

## Workstreams

### WS1 — Social Hub / Friends screen (spec: `social-hub-redesign`, extend)
- The UI is loved; **organize it better** and handle edge cases. Goal: a full, engaging,
  beautiful "social screen" that feels alive.
- **Leaderboard + friends scalability:** with 1000 friends AND a 1000-row leaderboard
  stacked on one screen, scrolling is painful. Need an intelligent + beautiful separation
  (segmented panes exist from social-hub-redesign; enhance with search, lazy paging, clear
  navigation, alive transitions).
- **Circles entry:** make it visually obvious — "oh, Circles = I can create a friend group."
  Use the PNG packs; make it beautiful and self-explanatory.
- **Invite-sent feedback:** the small "invite sent" toast is hard to notice. Replace with a
  **persistent representation like incoming invites**, and **navigate the user to the
  Requests list** after sending so they see the sent invite. Requests list should be
  organized (low priority).
- **Invite entry experience:** replace the plain text input (manual entry and via link) with
  a **beautiful, alive, theme-consistent sheet** including the PNG-pack styling.
- **Deep-link fix:** an `invite/{id}` link must open the **Friends screen**, not the
  workspace. (Implemented in `social-hub-redesign` task 8.1 — verify it's built/deployed.)

### WS2 — Task creation: send to Circles + searchable picker (specs: `collaborative-tasks`, `shared-circles`)
- From the **task creation sheet**, allow sending a task to a **Circle** the same way you
  send to friends.
- When selecting friends/circles in the creation sheet, open a **bottom sheet with search**
  (must scale to 1000s of friends/circles).
- Clarify the **"shareable"** feature for the user (see "Clarifications" below).

### WS3 — Collaborative task creation correctness (spec: `collaborative-tasks`)
- **Bug:** if a task is shared with a friend while AI is still parsing it, saving mid-parse
  saves the task **without** AI-parsed info AND it **never gets sent** to friends. Fix: don't
  finalize/send the collaborative task until AI parse completes (or reconcile + send after
  parse), so assignees always receive the fully-parsed task.
- **Bug:** when internet is off / slow, collaborative tasks **don't work** (works fine
  online). Fix: offline queue / retry / clear error + eventual delivery.

### WS4 — Task list visuals (specs: `collaborative-tasks`, `social-engagement`)
- Replace the avatar **circles** in the task list with **live / alive Material shapes**.
- Try to load each member's **Google profile image** into the shape; if not available (or
  Google returns only an initials/alphabetic avatar), show **our default**.
- **Incoming task request** currently shows on top with oversized Accept/Decline buttons.
  Instead: render it as a **normal task card on top** (under the progress bars) with full
  task metadata, but add an **Accept button (bigger, more horizontal length than the cross)**
  and a **Cross/decline button** — alive Material styling, similar to how time is shown.

### WS5 — Plan My Day AI (spec: `ai-planning-and-gating`, extend)
- **Time/date/weather/environment awareness:** must know the real current time, day, date,
  weather, and today's environment; never schedule into the **past** (e.g. it's 1 PM, don't
  plan from 9 AM).
- **Replanning / conversational adjustment:** let the user describe their situation and have
  the AI **adapt / alter** the plan and advise accordingly.
- **Realism / self-awareness:** don't be trickable — reason about task **kind** from
  title/description/genre; reject impossible plans (e.g. 100 tasks in 4 hours) regardless of
  what the user claims.
- **Model:** use **Mistral** (Cloud Functions, same as AI chat). **Multilingual**.
- **UX:** today, tapping the icon shows **nothing**, then the sheet opens after seconds, and
  sometimes throws a **500**. Instead: tapping opens a **new screen immediately** with a
  clean, beautiful, **alive Material 3 expressive loading/progress** state; handle errors
  gracefully (no raw 500).
- **Entry point:** move it to a **FAB** (remove it from beside the "Preamble" text) and make
  the FAB **alive** with a Material shape.

### WS6 — Reactions latency (spec: `social-engagement`)
- **Bug:** a reaction appeared **too late** — only became visible after removing the user.
  Fix reaction propagation/visibility so reactions appear promptly and correctly.

### WS7 — Notifications: new channels (specs: `social-engagement`, `collaborative-tasks`)
- New dedicated notification channels / events:
  - **Invites:** when the user sends an invite; when a friend accepts an invite.
  - **Collaborative tasks:** when admin sends a task; when admin changes a task; when someone
    completes a task; when someone sends a reaction; nudges.

## Clarifications

### What "Shareable" means (answer to the user's question)
"Shareable" in this codebase is **NOT** about sharing a task with friends. It is the
**Growth-loops "Shareable Moment"** feature: the app renders a **branded image card** for a
celebration moment — **Weekly Recap**, **Streak Milestone**, or **Perfect Day** — and lets
the user share that image to social apps. The share caption automatically includes the
user's **invite link** (so it doubles as a growth/referral loop). Code lives under
`app/.../share/` (`ShareableViewModel`, `ShareableContentMapper`, `ShareableImageRenderer`,
`ShareSheetLauncher`, `ShareableComposables.kt`) and is triggered from the Recap screen and
the celebration overlay. **When to use it:** after finishing a perfect day, hitting a streak
milestone, or viewing the weekly recap — tap Share to post a branded image + invite link.
(Task-to-friend/circle sending is the separate *collaborative task* flow, WS2.)

## Open questions / decisions needed
- **Weather/environment source** for Plan My Day (which API; location permission needed?).
- **Google profile image source** — confirm Google Sign-In `photoUrl` is available for
  members and how members without it are detected (initials/alphabetic fallback).
- Requests-list organization detail (low priority per user).

## Suggested execution order
1. **WS1** — Social Hub / invite system + screen (start here, per user).
2. WS2 — Task → Circle + searchable picker.
3. WS3 — Collaborative creation correctness (AI-parse + offline).
4. WS4 — Task list visuals (shapes + profile images + request card).
5. WS5 — Plan My Day AI overhaul.
6. WS6 — Reactions latency fix.
7. WS7 — Notifications channels.

## Session 2 — detailed enhancement brief (verbatim capture)

> User wants to enhance the whole collaborative-task + social feature set, starting with
> WS1 (invite system + social screen). Many WS1 items already exist in `social-hub-redesign`
> requirements but the user reports they are NOT behaving correctly in the build, or wants
> them taken further. Capturing every point so nothing is lost.

### WS1 — Social Hub / Friends screen (start here)
- UI is loved; **organize it better**, handle edge cases. Make it a **full social screen**
  with more engaging features, more beautiful, consistent theme, **M3 Expressive**, alive
  morphing shapes — "make it feel alive, impress me."
- **Leaderboard + friends list scalability:** both can be ~1000 rows stacked on one screen →
  scrolling is painful. Need an intelligent + beautiful separation.
- **Circles entry:** make it visually obvious that Circles = "create a friend group." Reuse
  the existing PNG sets. Make it beautiful.
- **Invite-sent feedback (still broken):** sending an invite shows only a small "invite sent"
  toast that is hard to notice, then nothing. Replace with the **same persistent
  representation used for incoming invites**, and **navigate the user to the sent/requests
  list** after sending so they see it. Requests list should be organized (LEAST priority).
- **Referral reward:** user invited a friend via link and **got no credits** — and that's
  fine: **no rewards during development** (do not promise/grant credits).
- **Deep-link bug (still broken):** tapping the invite link opens the **Workspace screen**,
  not the Friends screen; user had to navigate manually. Must land on Friends/Social Hub.
- **Invite entry experience:** both manual Preamble-ID entry AND link entry currently show a
  **plain input box** → replace with a **beautiful, alive, theme-consistent sheet** using the
  PNG packs styling.

### WS2 — Task creation: send to Circles + searchable picker
- From the **task creation sheet**, allow sending a task to a **Circle**, the same way tasks
  are sent to friends today.
- When selecting friends/circles in the creation sheet, open a **bottom sheet with search**
  (must scale to 1000s).
- User asked: **"explain what 'shareable' means — I don't know how/when to use it."**
  (See Clarifications section — it's the growth-loops Shareable Moment image-share, NOT
  task-to-friend sharing. Need to explain this to the user.)

### WS3 — Collaborative task creation correctness (bugs)
- **Bug:** if collaborating with a friend while AI is still parsing the task and the user
  saves mid-parse, the task saves WITHOUT AI-parsed info AND is NEVER sent to friends.
- **Bug:** when internet is off/slow the collaborative task doesn't work (works fine online).

### WS4 — Task list visuals
- Replace avatar **circles** in the task list with **live/alive Material shapes**.
- Load each member's **Google profile image** into the shape; if missing or Google returns
  only an initials/alphabetic avatar, show **our default**.
- **Incoming task request** currently shows on top with oversized Accept/Decline buttons.
  Instead render it as a **normal task card on top** (under the progress bars) with full task
  metadata, plus an **Accept button (bigger, more horizontal length than the cross)** and a
  **Cross/decline button** — alive Material styling, similar to how time is shown on cards.

### WS5 — Plan My Day AI
- **Time/date/weather/environment awareness:** must know real current time/day/date, weather,
  today's environment; never plan into the **past** (it's 1 PM → don't start at 9 AM).
- **Replanning / conversational adjustment:** user can describe their situation and the AI
  adapts/alters the plan and advises accordingly.
- **Self-aware realism:** not trickable — reason about task **kind** from title/description/
  genre; reject impossible plans (100 tasks in 4 hours) regardless of user claims.
- **Model:** Mistral (Cloud Functions, same as AI chat). **Multilingual** (any script in
  title/description/user input).
- **UX bug:** tapping the icon shows **nothing**, then the sheet opens after seconds, and
  sometimes throws a **500**. Instead: tap opens a **new screen immediately** with a clean,
  beautiful, **alive M3 expressive loading/progress** state; handle errors gracefully (no
  raw 500).
- **Entry point:** move it to a **FAB** (remove it from beside the "Preamble" text); make the
  FAB alive with a Material shape.

### WS6 — Reactions latency (bug)
- User reacted in a collaborative task but it appeared **too late** — only visible after
  removing the user. Fix reaction propagation/visibility so reactions show promptly.

### WS7 — Notifications: new channels
- New dedicated notification channels/events:
  - **Invites:** when user sends an invite; when a friend accepts an invite.
  - **Collaborative tasks:** when admin sends a task; when admin changes a task; when someone
    completes a task; when someone sends a reaction; **nudges**.

## Status log
- _(init)_ Plan captured. `social-hub-redesign` spec implemented (29/43 tasks; optional
  test tasks skipped). Firestore rule for `outgoingInvites` mirror added (needs deploy).
- _(session 2)_ Full enhancement brief captured above. Starting with WS1. NOTE: deep-link
  routing and persistent invite-sent feedback were marked done in `social-hub-redesign`
  (tasks 8.1, 4.x, 5.x) but the user reports both still misbehave in the build — WS1 must
  re-verify the actual implementation, not just the spec.
- _(session 2)_ **WS1 fully re-specced** in `social-hub-redesign` (requirements → design →
  tasks all updated in place). Requirements expanded to 9 (added loading/error/empty states,
  persistent labeled section nav, paged loading for both panes, search, same themed sheet for
  manual+link, cold-start deep-link). Design adds two pure modules (`SocialSearch`,
  `PageWindow`), enriches `Leaderboard.Entry` with `preambleId`, and **diagnoses the two
  reported "still broken" bugs**: (1) stale installed build running old code, (2) the
  `/outgoingInvites` Firestore create rule never deployed → atomic batch send fails with
  PERMISSION_DENIED. Tasks 1–12 remain shipped/checked; new "Enhancement Pass" tasks 13–25
  added, ending in a **deploy + rebuild + re-verify** group (task 25) that is the actual fix
  for the toast-only / opens-Workspace symptoms. READY TO IMPLEMENT.
  - Likely-fastest path to unblock the user's reported bugs: run task 25 (deploy rules +
    reinstall) early, even before the UI enhancement tasks.
- _(session 2)_ **WS1 implementation: code tasks 13–24 COMPLETE** via Run All. Shipped:
  `SocialSearch` + `PageWindow` pure modules; `Leaderboard.Entry` enriched with `preambleId`;
  `WorkspaceViewModel` gained `socialHubLoadState`/`retryLoad()`/`pendingRequestsCount` and
  leaderboard preambleId population; `SectionOrganizer` now has a persistent labeled
  active-aware control, client-side paged loading for both panes, and per-pane Social_Search
  over the full list; Loading/Error(+retry)/no-friends/no-match states; Requests badge bound
  to `pendingRequestsCount`; Circles card PNG bumped to match hero; invite-sheet parity
  confirmed; expressive M3 polish on the search field + verified across surfaces. All
  checkpoints green (`:app:compileDebugKotlin` + `:app:testDebugUnitTest` pass). Optional
  test tasks (jqwik Properties 8 & 9, UI/snapshot) were skipped per MVP.
  - **REMAINING: task 25 (deploy/verify) needs the USER** — `firebase deploy --only
    firestore:rules` (the `/outgoingInvites` create rule is confirmed present but undeployed),
    rebuild+reinstall the app, then manual on-device re-verification of invite-send navigation
    + persistent Outgoing_Invite (Req 4,5) and the deep-link landing incl. cold start (Req 7).
    This group is the actual fix for the toast-only / opens-Workspace symptoms.
- _(session 3)_ **NEW WS1 REGRESSION reported by user** (in the just-shipped Social Hub):
  the social screen is **not scrollable** — some elements sit below the screen chin / nav,
  and the **friends list is not fully visible**. User couldn't see a friend, assumed they had
  unfriended them, tried to re-add, and got "you're already friends." ROOT-CAUSE HYPOTHESIS:
  the WS1 layout uses a fixed header Column + a `weight(1f)` Section_Organizer with per-pane
  `LazyColumn`s; the panes scroll internally, but if the outer Column/overlay doesn't give the
  panes enough height (or the bottom nav/insets overlap), rows fall off-screen. The Friends
  pane must be reliably scrollable to its end with proper bottom content padding for the nav
  bar / system insets. Fix belongs to the **social-hub-redesign** spec (bug in shipped WS1).
  HIGH PRIORITY — actively misleads the user about their friendships.
- _(session 3)_ **WS1 scroll regression FIXED.** Added Requirement 10 + design section +
  task 26 to `social-hub-redesign`, and implemented the fix in `WorkspaceScreen.kt`
  (compiles clean): (1) overlay `Scaffold` `contentWindowInsets` now applies top+horizontal
  insets only (not bottom); (2) BOTH pane `LazyColumn`s get unconditional bottom
  `contentPadding = WindowInsets.navigationBars bottom + 24dp`; (3) removed the conditional
  80.dp `PagingSentinel` spacer that only appeared when fully loaded. Root cause was: panes
  had no bottom contentPadding and clearance depended on a paging-state-coupled magic spacer,
  so the last friend rows hid behind the nav chin. NOTE: spec task tool unavailable outside
  Run-All, so tasks.md checkboxes 26.1–26.3 remain unticked though the code is done. Needs a
  rebuild+reinstall to see on-device (same as task 25). NEXT: WS3 + WS4 in the
  `collaborative-tasks` spec (per user's session-3 choice: scroll fix first, then WS3+WS4).
- _(session 3)_ **WS3 + WS4 IMPLEMENTED** in `collaborative-tasks` (Iteration 3, tasks 18–24)
  via Run All; clean compile + unit tests green at both checkpoints. Shipped — WS3:
  `CollaborativeSend` pure state machine + `collabSendStatus` Room column (MIGRATION 30→31);
  durable `CollaborativeSendWorker` (idempotent, retry/backoff, terminal send_failed); removed
  the inline send from `AiParsingWorker` (fixes "parse returned nothing ⇒ never sent");
  `ConnectivityProbe` + parse→send WorkManager unique-work chain in `TaskViewModel` (offline =
  queued-and-delivered-on-reconnect, not lost); Send_Status chip on the task row. WS4:
  `AvatarSource` precedence (real photo → default, initials-placeholder detection); photoUrl
  plumbed from Google sign-in → directory → Friend(on accept) → canonical doc memberStates;
  Expressive morphing member shapes replacing circles; `MemberAvatar` (Coil photo + fallback);
  `IncomingTaskCard` = normal task card with Accept wider than the cross.
  FOLLOW-UPS: (a) optional jqwik+UI tests (18.2,19.4,21.2,21.3,23.4) skipped per MVP; (b) no
  bundled Default_Avatar drawable — initials used as default, drop in a real one later;
  (c) admin-row avatar passes photoUrl=null (admin photo not on local Task projection);
  (d) DEVICE-DEPENDENT verification owed: Room migration on a real v30 DB, WorkManager durable
  send across restart/reconnect, Google photoUrl capture with a real account.
- _(pending, both specs)_ A rebuild + reinstall is owed to see all session-2/3 work on device,
  plus `firebase deploy --only firestore:rules` for the WS1 invite fix (social-hub-redesign
  task 25).
- _(session 3)_ **WS2 IMPLEMENTED** in `collaborative-tasks` (Iteration 4, tasks 25–28); clean
  compile + unit tests green. "Send to a Circle" = members-as-assignees, snapshot at send (no
  circleId stored): pure `Recipient` + `RecipientResolution` (dedupe + exclude sender + 50-cap),
  a searchable Material 3 `RecipientPicker` (reusing SocialSearch + PageWindow) listing friends
  AND circles, wired into `AddTaskSheet` (replaces the inline dropdown/chips) feeding the
  unchanged assignment path; HomeScreen sources circles from `CircleViewModel`. Optional tests
  (Property 23 + UI) skipped per MVP.
  - REMAINING WORKSTREAMS: WS5 (Plan My Day AI → ai-planning-and-gating), WS6 (reactions
    latency → social-engagement), WS7 (notification channels → new `notifications` spec).
  - OPEN DECISION for WS5: weather/environment source — plan is time/date/day ALWAYS from the
    device (deterministic, fixes the "plans at 9am when it's 1pm" bug); weather/environment
    best-effort/optional and gracefully skipped when unavailable (no hard location dependency).

## Session 3 — WS3 + WS4 detail refresh (verbatim, user pasted under "ws2")

> NOTE: user said "get to our ws2" but the pasted content maps to WS3 (collaborative task
> correctness) and WS4 (task list visuals), NOT the master-plan WS2 (task→Circle + searchable
> picker). Plus the new WS1 scroll regression above. Capturing the refreshed detail:

### WS3 — Collaborative task creation correctness (spec: `collaborative-tasks`)
- **Bug:** if collaborating with a friend while AI is still parsing the task and the user
  saves mid-parse, the task saves WITHOUT the AI-parsed info AND is NEVER sent to friends.
- **Bug:** when internet is off/slow the collaborative task doesn't work (works fine online).

### WS4 — Task list visuals (specs: `collaborative-tasks` / `social-engagement`)
- Replace the avatar **circles** in the task list with **live/alive Material shapes**.
- Try to load each member's **Google profile image** into the shape; if not fetched, or Google
  returns only an initials/alphabetic avatar, show **our default**.
- **Incoming task request** currently shows on top with large Accept/Decline buttons. Instead:
  render it as a **normal task card on top** (under the progress bars) showing all the task
  metadata (like the time is shown on a normal card), plus an **Accept button** and a **Cross
  button** — the **Accept button has more horizontal length than the Cross**, both alive
  Material.

- _(session 4)_ **WS5 (Plan My Day AI overhaul) IMPLEMENTED** in `ai-planning-and-gating`
  (Track A Iteration 2, tasks 10–14) via wave dispatch; clean `:app:compileDebugKotlin` +
  `functions` tsc build at the checkpoint. Shipped:
  * **Pure core (10.1):** `PlanClock` (`fun interface` + `system()`, device clock/zone injected,
    deterministic), `DayWindow` (`SCHEDULE_LEAD_TIME_MIN=10`, `effectiveWindowStart` clamped
    0..30 lead), `DayPlanInput.earliestStartMinute` (Effective_Window_Start floor) +
    `ProposedSchedule.unplaced`; `ScheduleNormalizer.normalize` now floors every candidate slot
    at `earliestStartMinute` (never schedules in the past) and PARTITIONS schedulable ids into
    `assignments ∪ unplaced` (each id exactly once; surplus tasks surfaced, never dropped).
  * **Orchestration (11.1):** `DayPlanService` takes a `PlanClock`; `gatherInput` computes
    `nowMinuteOfDay`/`earliestStartMinute`, returns a richer `GatheredInput(input, date,
    dayOfWeek, nowTime)`; short-circuits to `NoRemainingTimeToday` (no AI call) when
    `earliestStartMinute >= dayEndMinute`.
  * **Cloud AI (11.2):** `aiPlanDay` TS + `CloudAiService.planDay` extended with
    `context{dayOfWeek,nowTime}`, `dayStart=Effective_Window_Start`, per-task description/tags,
    optional `priorAssignments`+`adjustment`, reserved `weather` left null (MVP, no weather API);
    prompt instructs Task_Kind_Estimate realism + leave-unfit-unplaced. Mistral branch unchanged.
  * **ViewModel (12.1/12.2):** added retryable `Error` state (timeout/network/500/parse remap;
    `CouldNotGenerate` reserved for valid-but-unusable model response) + `retry()`;
    `submitAdjustment(text)` (Review or post-Applied) replans through the SAME cached
    `DayPlanInput`/normalizer with `priorAssignments`+adjustment; `Review.advisory` surfaces
    unplaced tasks (Req 15.5).
  * **UI (13.1/13.2):** NEW full-screen `PlanningScreen.kt` (opens immediately; alive M3
    Expressive lime morphing-shape loading; Review with title+HH:mm rows, Plan_Adjustment field
    +Revise, unplaced/advisory callout; retryable Error with human-readable copy + Retry; all
    terminal states in-screen). `DayPlanReviewSheet.kt` DELETED. Top-bar `AutoAwesome` icon
    removed; alive morphing-shape `PlanMyDayFab` added to the FAB column; gate wiring opens the
    screen immediately on unlocked (else upsell), `requestPlan()` kicked off. Also fixed a
    pre-existing `BottomWaveAnimation` compile error (undefined `transition`).
  * Optional `*` tests (Property 13 / example / Compose: 10.2, 11.3, 12.3, 13.3) skipped per MVP.
  * **Task-tool note:** the DAG status tool currently errors on these backtick-containing task
    IDs (stuck execution id on in_progress), so statuses were tracked by ticking tasks.md
    checkboxes directly; all 7 core sub-tasks + checkpoint 14 + parents 10–14 are ticked.
  * **DEVICE-DEPENDENT verification owed:** real device-clock time-awareness (no past
    scheduling), `aiPlanDay` end-to-end with Mistral + credits, the alive FAB/screen motion,
    and replanning/adjustment round-trips. Needs rebuild+reinstall (+ functions deploy for the
    extended `aiPlanDay`).
  - REMAINING WORKSTREAMS: WS6 (reactions latency → `social-engagement`), WS7 (notification
    channels → new `notifications` spec).

- _(session 4)_ **WS6 (reactions latency bug) FIXED** in `social-engagement` (Iteration 2,
  Requirement 13 + design note + task 13). ROOT CAUSE (confirmed via investigation):
  `WorkspaceViewModel.mergeRemoteCollaboration(local, remote)` rebuilt an already-mirrored Task
  with `local.copy(<enumerated remote fields>)` but OMITTED `reactionsJson` — so a reaction-only
  change on `/collaborativeTasks/{taskId}` was projected by `TaskProjection.documentToTask` yet
  dropped during the merge; `taskDao.insertTask(merged)` wrote the stale reactions back to Room.
  Other members' reactions only surfaced after an unrelated mutation (e.g. `removeMember`) rewrote
  the row — exactly the "only visible after removing the user" symptom. The optimistic
  self-reaction path already updated the local row, so the reactor saw their own reaction
  immediately; the lag was for REMOTE reactions. FIX: added `reactionsJson = remote.reactionsJson`
  and `collabSendStatus = remote.collabSendStatus` (WS3 consistency) to the merge `copy`. Clean
  `:app:compileDebugKotlin`. No other layer touched. DEVICE-DEPENDENT verification owed (two
  devices, real-time reaction propagation within the 5 s window). Needs rebuild+reinstall.
  - REMAINING: WS7 (notification channels → new `notifications` spec).

- _(session 4)_ **WS7 (notification channels/events) IMPLEMENTED** as a NEW cross-cutting
  `notifications` spec (requirements → design → tasks → implement, all authored this session;
  feature, requirements-first). Core tasks 1, 3, 4, 5, 7, 8, 9, 10 + checkpoints 2, 6, 11 all
  done. Shipped:
  * **Pure logic** `functions/src/notifications/logic.ts` — `classifyTaskUpdate` (before/after
    diff over MEMBER_RELEVANT_TASK_KEYS for content-change; completion = status crossing into
    "completed"), `selectRecipients` (anti-self-notify + per-event targeting), `buildPayload`
    (plain copy, no reward language; deep links + channelType per event).
  * **Triggers** `functions/src/social-notifications.ts` (Admin SDK, db "preamble"):
    `onInviteAccepted` (friend-doc create; Inviter-vs-Actor disambiguated via the
    `outgoingInvites` mirror → notifies inviter only), `onCollaborativeTaskCreated`
    (assignment → assignees), `onCollaborativeTaskUpdated` (change → members−admin, with the D7
    AI-finalize 60 s grace-window guard; completion → admin only). Shared `claimEvent`
    idempotency ledger (`/notificationEvents/{eventId}` via `create()`, dedupes CloudEvent
    retries) + `sendToRecipients` (per-recipient token lookup + log-and-swallow). Exported from
    `index.ts`.
  * **Re-categorization:** `kudos.ts` + `nudge.ts` `channelType` "broadcast" → "social_kudos".
  * **Security rules:** deny-all `/notificationEvents/{doc}` (mirrors `/nudges`) + TTL note.
  * **Client:** `PreambleFcmService` registers 3 social channels (`preamble_social_invites`
    "Invites & Friends", `preamble_social_collab` "Shared Tasks", `preamble_social_kudos`
    "Kudos & Nudges") and a `when(channelType)→channelId` map (unknown → broadcast; only "promo"
    low-priority). `MainActivity` adds `preamble://social` → Friends overlay and
    `preamble://task/{id}` → Shared Tasks tab (also fixes existing kudos/nudge routing).
    `WorkspaceViewModel.sendInvite` confirmation now names the recipient on success and on
    failure (Req 2, client-local per Decision D2 — no self-push).
  * Optional `*` tests skipped per MVP: fast-check PBTs (Properties 1–5, runner setup in 1.2),
    emulator integration tests (3.5), JVM mapping/deep-link example tests (8.2, 9.2).
  * Verification: `functions` `npm run build` (tsc) green; `:app:compileDebugKotlin` green.
  * **DEPLOY + DEVICE-DEPENDENT verification owed:** `firebase deploy --only functions` (4 new
    exports) and `--only firestore:rules` (notificationEvents deny-all); then on-device verify
    invite-accepted / task-assigned / task-changed / task-completed pushes land on the right
    channels and deep-link correctly, dedup holds, and the actor is never self-notified.

## ALL WORKSTREAMS COMPLETE (code)
- WS1 (Social Hub redesign + scroll fix), WS2 (send-to-Circle picker), WS3 (collab correctness),
  WS4 (task-list visuals), WS5 (Plan My Day AI overhaul), WS6 (reactions latency fix), and
  WS7 (notification channels) are ALL implemented and compile-verified.
- **Outstanding (user-owed, device/deploy-dependent):**
  1. `firebase deploy --only firestore:rules` (WS1 outgoingInvites rule + WS7 notificationEvents
     deny-all) and `firebase deploy --only functions` (WS5 extended `aiPlanDay`; WS7's 4 new
     triggers + the kudos/nudge channelType change).
  2. Rebuild + reinstall the app to see all session-2/3/4 work on device.
  3. On-device verification of: invite-send navigation + deep-link cold start (WS1); Room
     migration v30/v31, durable collaborative send across restart/reconnect, Google photoUrl
     (WS3/WS4); Plan-My-Day time-awareness + alive FAB/screen + replanning (WS5); real-time
     reaction propagation across two devices (WS6); the new notification channels + routing +
     dedup (WS7).
