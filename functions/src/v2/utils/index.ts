/**
 * V2 Utilities barrel export.
 *
 * ISOLATION RULE:
 * V2 entry points (v2/index.ts) MUST NOT import from any V1 module.
 * V1 entry points (src/index.ts) MUST NOT import from any V2 module.
 * This ensures complete runtime isolation between the two systems
 * as required by Requirements 26.1, 26.2, 26.4, 26.5, 26.6.
 */

export {
  V2_COLLECTIONS,
  V2_COLLECTION_LIST,
  V1_FORBIDDEN_COLLECTIONS,
  V1_FORBIDDEN_SUBCOLLECTION_PATTERNS,
  V2IsolationViolationError,
  assertV2Collection,
  isV2Collection,
  memoryEntriesPath,
  memoryEntryPath,
  conversationThreadsPath,
  conversationThreadPath,
  tokenUsagePath,
  modelConfigPath,
} from "./collection-paths";

export type { V2CollectionName } from "./collection-paths";

export {
  checkRateLimit,
  enforceRateLimit,
} from "./rate-limiter";

export type { RateLimitResult } from "./rate-limiter";

export {
  validateParseInput,
} from "./parse-input-validator";

export type { ParseInputValidationResult } from "./parse-input-validator";
