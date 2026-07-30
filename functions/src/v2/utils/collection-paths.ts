/**
 * V2 Collection Paths — Central definition of all Firestore collections used by V2.
 *
 * ISOLATION RULE: V2 code MUST ONLY access these collections.
 * V1 collections (users/{uid}/ai_memory, config/ai) are NEVER accessed by V2 code.
 *
 * Requirements: 26.1, 26.2, 26.4, 26.5, 26.6
 */

// ─── V2 Collection Constants ───────────────────────────────────────────────────

/**
 * All Firestore collections that V2 code is permitted to access.
 * These are the ONLY collection prefixes V2 operations may use.
 */
export const V2_COLLECTIONS = {
  /** v2_memory/{uid}/entries/{entryId} — semantic memory storage with vector embeddings */
  MEMORY: "v2_memory",
  /** v2_conversations/{uid}/threads/{conversationId} — conversation history and state */
  CONVERSATIONS: "v2_conversations",
  /** v2_token_usage/{uid} — daily token consumption tracking */
  TOKEN_USAGE: "v2_token_usage",
  /** v2_config/models — admin-managed model registry and tier budgets */
  CONFIG: "v2_config",
} as const;

/** Type for any valid V2 collection name */
export type V2CollectionName = (typeof V2_COLLECTIONS)[keyof typeof V2_COLLECTIONS];

/**
 * All V2 collection names as an array (for runtime checks).
 */
export const V2_COLLECTION_LIST: readonly string[] = Object.values(V2_COLLECTIONS);

// ─── V1 Forbidden Collections ──────────────────────────────────────────────────

/**
 * V1 collection paths/patterns that V2 MUST NEVER access.
 * Any attempt to access these from V2 code indicates an isolation violation.
 */
export const V1_FORBIDDEN_COLLECTIONS = [
  "ai_memory",
  "config/ai",
] as const;

/**
 * V1 subcollection patterns (under user documents) that V2 must never touch.
 * These are matched as substrings within Firestore paths.
 */
export const V1_FORBIDDEN_SUBCOLLECTION_PATTERNS = [
  "/ai_memory",
  "/ai_history",
] as const;

// ─── Runtime Isolation Guard ───────────────────────────────────────────────────

/**
 * Error thrown when V2 code attempts to access a V1 collection.
 * This is a hard runtime boundary to prevent cross-system data contamination.
 */
export class V2IsolationViolationError extends Error {
  public readonly collectionPath: string;

  constructor(collectionPath: string) {
    super(
      `V2 ISOLATION VIOLATION: Attempted to access V1 collection "${collectionPath}". ` +
      `V2 code must only access V2-prefixed collections: ${V2_COLLECTION_LIST.join(", ")}.`
    );
    this.name = "V2IsolationViolationError";
    this.collectionPath = collectionPath;
  }
}

/**
 * Asserts that a given Firestore collection path is a valid V2 path.
 * Throws `V2IsolationViolationError` if the path matches a V1 forbidden pattern.
 *
 * Use this guard at the boundary of any Firestore operation in V2 code
 * to enforce collection isolation at runtime.
 *
 * @param path - The Firestore collection or document path to validate
 * @throws V2IsolationViolationError if the path matches a V1 collection
 *
 * @example
 * ```ts
 * assertV2Collection("v2_memory/uid123/entries"); // OK
 * assertV2Collection("users/uid123/ai_memory");   // THROWS
 * assertV2Collection("config/ai");                // THROWS
 * ```
 */
export function assertV2Collection(path: string): void {
  // Normalize the path for comparison
  const normalizedPath = path.trim().toLowerCase();

  // Check direct V1 forbidden collection matches
  for (const forbidden of V1_FORBIDDEN_COLLECTIONS) {
    if (normalizedPath === forbidden || normalizedPath.startsWith(`${forbidden}/`)) {
      throw new V2IsolationViolationError(path);
    }
  }

  // Check V1 subcollection patterns (e.g., "users/{uid}/ai_memory")
  for (const pattern of V1_FORBIDDEN_SUBCOLLECTION_PATTERNS) {
    if (normalizedPath.includes(pattern)) {
      throw new V2IsolationViolationError(path);
    }
  }
}

/**
 * Checks whether a given Firestore path starts with a known V2 collection prefix.
 * This is a softer check — returns true/false without throwing.
 *
 * @param path - The Firestore collection or document path to check
 * @returns true if the path begins with a V2 collection prefix
 */
export function isV2Collection(path: string): boolean {
  const normalizedPath = path.trim().toLowerCase();
  return V2_COLLECTION_LIST.some(
    (col) => normalizedPath === col || normalizedPath.startsWith(`${col}/`)
  );
}

// ─── Path Builder Helpers ──────────────────────────────────────────────────────

/**
 * Builds the path to a user's memory entries subcollection.
 * @param uid - The authenticated user's UID
 */
export function memoryEntriesPath(uid: string): string {
  return `${V2_COLLECTIONS.MEMORY}/${uid}/entries`;
}

/**
 * Builds the path to a specific memory entry document.
 * @param uid - The authenticated user's UID
 * @param entryId - The memory entry document ID
 */
export function memoryEntryPath(uid: string, entryId: string): string {
  return `${V2_COLLECTIONS.MEMORY}/${uid}/entries/${entryId}`;
}

/**
 * Builds the path to a user's conversation threads subcollection.
 * @param uid - The authenticated user's UID
 */
export function conversationThreadsPath(uid: string): string {
  return `${V2_COLLECTIONS.CONVERSATIONS}/${uid}/threads`;
}

/**
 * Builds the path to a specific conversation thread document.
 * @param uid - The authenticated user's UID
 * @param conversationId - The conversation thread ID
 */
export function conversationThreadPath(uid: string, conversationId: string): string {
  return `${V2_COLLECTIONS.CONVERSATIONS}/${uid}/threads/${conversationId}`;
}

/**
 * Builds the path to a user's token usage document.
 * @param uid - The authenticated user's UID
 */
export function tokenUsagePath(uid: string): string {
  return `${V2_COLLECTIONS.TOKEN_USAGE}/${uid}`;
}

/**
 * Builds the path to the V2 model registry config document.
 */
export function modelConfigPath(): string {
  return `${V2_COLLECTIONS.CONFIG}/models`;
}
