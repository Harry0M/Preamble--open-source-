/**
 * Preamble AI Cloud Functions — Entry Point.
 * Exports all HTTP endpoints.
 */
import { initializeApp } from "firebase-admin/app";

// Initialize Firebase Admin (uses default credentials in Cloud Functions)
initializeApp();

// Chat endpoints
export { aiChat, aiChatContinue } from "./ai-chat";

// Task parsing (voice, notification, task sheet)
export { aiParseTask } from "./ai-parse-task";

// Plan-My-Day (Track A — credit-charged one-shot scheduling proposal)
export { aiPlanDay } from "./ai-plan-day";
// aiResolveAssignees removed: assignee resolution now runs client-side in
// AiParsingWorker via DefaultAssigneeResolver (Requirement 9.9).

// Credits endpoints
export { aiCreditsReward, aiCreditsBalance } from "./ai-credits";

// User problem reports
export { submitProblemReport } from "./problem-reports";

// Database task counter triggers
export { onTaskCreated, onTaskDeleted } from "./counters";

// Social engagement: nudge callable + kudos reaction trigger (data-only FCM)
export { sendNudge } from "./nudge";
export { onCollaborativeTaskReaction } from "./kudos";

// WS7 social/collaborative notifications: invite-accepted + task assigned/changed/completed (data-only FCM)
export { onInviteAccepted, onCollaborativeTaskCreated, onCollaborativeTaskUpdated } from "./social-notifications";

// Growth loops: two-sided referral reward trigger (Admin-SDK credit transaction)
export { onReferralFriendship } from "./referrals";

// Shared circles: membership propagation + delete cascade trigger (Admin-SDK fan-out)
export { onCirclesMembershipChanged } from "./circles";

// ─── AI V2 Endpoints ───────────────────────────────────────────────────────────
// NOTE: V2 endpoints are imported directly from their individual modules to avoid
// circular dependency with the V2 barrel export (which also re-exports types/utils).
// Rate limiting: max 10 requests/minute per user enforced within each endpoint via
// per-user timestamp checks. Cloud Functions v2 maxInstances provides server-side throttle.

// Chat: main SSE streaming endpoint (POST)
export { v2Chat } from "./v2/endpoints/v2-chat";

// Chat Continue: process tool results, generate final response (POST)
export { v2ChatContinue } from "./v2/endpoints/v2-chat-continue";

// Parse: natural language → structured task (POST)
export { v2Parse } from "./v2/endpoints/v2-parse";

// Token Balance: current token usage and budget (GET)
export { v2TokenBalance } from "./v2/endpoints/v2-token-balance";

// Config: model registry for client display (GET)
export { v2Config } from "./v2/endpoints/v2-config";

// Daily Briefing: generate daily morning briefing on demand (POST)
export { v2DailyBriefing } from "./v2/endpoints/v2-daily-briefing";
