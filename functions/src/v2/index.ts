/**
 * AI V2 Ecosystem — Main entry point.
 *
 * All V2 Cloud Function exports are registered here.
 * This module has ZERO imports from V1 modules.
 *
 * ISOLATION RULE:
 * - V2 entry points MUST NOT import from any V1 module (src/ai-chat.ts, src/ai-parse-task.ts, etc.)
 * - V1 entry points (src/index.ts) MUST NOT import from any V2 module (src/v2/**)
 * - V2 code MUST ONLY access V2-prefixed Firestore collections (v2_memory, v2_conversations, v2_token_usage, v2_config)
 *
 * RATE LIMITING:
 * - Each V2 endpoint enforces max 10 requests/minute per authenticated user.
 * - Firebase Cloud Functions v2 supports `rateLimiter` options per-function for
 *   built-in throttling. Additionally, each endpoint validates per-user request
 *   frequency using the v2_token_usage/{uid} document's lastRequestAt timestamp.
 * - `maxInstances` is configured per endpoint as a basic server-side throttle.
 */

// Re-export all V2 types for convenience
export * from "./models";

// Re-export V2 utilities (collection paths, isolation guards)
export * from "./utils";

// ─── V2 Cloud Function Endpoints ───────────────────────────────────────────────

// Chat: main SSE streaming endpoint
export { v2Chat } from "./endpoints/v2-chat";

// Chat Continue: process tool results and generate final response
export { v2ChatContinue } from "./endpoints/v2-chat-continue";

// Parse: natural language → structured task
export { v2Parse } from "./endpoints/v2-parse";

// Token Balance: GET current token usage and budget
export { v2TokenBalance } from "./endpoints/v2-token-balance";

// Config: GET model registry for client display
export { v2Config } from "./endpoints/v2-config";

// Daily Briefing: generate daily morning briefing on demand
export { v2DailyBriefing } from "./endpoints/v2-daily-briefing";
