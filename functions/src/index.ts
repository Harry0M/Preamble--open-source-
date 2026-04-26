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

// Credits endpoints
export { aiCreditsReward, aiCreditsBalance } from "./ai-credits";
