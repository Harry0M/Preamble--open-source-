/**
 * Server-side AI configuration — stored in Firestore, changeable from Firebase Console.
 *
 * Document path: config/ai  (in the "preamble" database)
 *
 * Fields you can set in Firebase Console → Firestore → config/ai:
 *   chatModel       → default model for chat     (default: "gemini-2.5-flash-lite")
 *   parseModel      → default model for task parsing (default: "gemini-2.5-flash-lite")
 *   memoryModel     → model for memory extraction (default: "gemini-2.5-flash-lite")
 *   killSwitch      → true to disable all AI      (default: false)
 *   maxHistoryWindow → messages to include         (default: 12)
 *
 * To change models:
 *   1. Go to Firebase Console → Firestore → preamble database
 *   2. Navigate to: config/ai
 *   3. Edit the field (e.g., chatModel = "mistral-small-latest")
 *   4. Change takes effect IMMEDIATELY — no app update, no redeploy needed
 *
 * Model options:
 *   Gemini:  "gemini-2.5-flash-lite" (cheapest), "gemini-2.5-flash", "gemini-2.5-pro"
 *   Mistral: "mistral-small-latest", "mistral-medium-latest"
 */
import type { Firestore } from "firebase-admin/firestore";

export interface AiConfig {
  chatModel: string;
  parseModel: string;
  memoryModel: string;
  killSwitch: boolean;
  maxHistoryWindow: number;
}

const DEFAULT_CONFIG: AiConfig = {
  chatModel: "gemini-2.5-flash",  // Auto = Flash (thinking); change via Firestore config/ai
  parseModel: "gemini-2.5-flash-lite",
  memoryModel: "gemini-2.5-flash-lite",
  killSwitch: false,
  maxHistoryWindow: 12,
};

// Cache config for 60 seconds to avoid reading Firestore on every request
let cachedConfig: AiConfig | null = null;
let cacheTimestamp = 0;
const CACHE_TTL_MS = 60_000;

/**
 * Get current AI configuration. Reads from Firestore with 60s cache.
 * If the document doesn't exist, returns defaults.
 */
export async function getAiConfig(db: Firestore): Promise<AiConfig> {
  const now = Date.now();
  if (cachedConfig && (now - cacheTimestamp) < CACHE_TTL_MS) {
    return cachedConfig;
  }

  try {
    const doc = await db.doc("config/ai").get();
    if (doc.exists) {
      const data = doc.data()!;
      cachedConfig = {
        chatModel: data.chatModel || DEFAULT_CONFIG.chatModel,
        parseModel: data.parseModel || DEFAULT_CONFIG.parseModel,
        memoryModel: data.memoryModel || DEFAULT_CONFIG.memoryModel,
        killSwitch: data.killSwitch ?? DEFAULT_CONFIG.killSwitch,
        maxHistoryWindow: data.maxHistoryWindow ?? DEFAULT_CONFIG.maxHistoryWindow,
      };
    } else {
      cachedConfig = { ...DEFAULT_CONFIG };
    }
  } catch (err) {
    console.warn("Failed to read AI config, using defaults:", err);
    cachedConfig = { ...DEFAULT_CONFIG };
  }

  cacheTimestamp = now;
  return cachedConfig;
}

/** Force-refresh config cache (e.g., after admin update) */
export function invalidateConfigCache() {
  cachedConfig = null;
  cacheTimestamp = 0;
}
