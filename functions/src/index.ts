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
