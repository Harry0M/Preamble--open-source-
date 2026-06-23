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
// aiResolveAssignees removed: assignee resolution now runs client-side in
// AiParsingWorker via DefaultAssigneeResolver (Requirement 9.9).

// Credits endpoints
export { aiCreditsReward, aiCreditsBalance } from "./ai-credits";

// User problem reports
export { submitProblemReport } from "./problem-reports";

// Database task counter triggers
export { onTaskCreated, onTaskDeleted } from "./counters";
