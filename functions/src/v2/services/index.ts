/**
 * V2 Services barrel export.
 */

export {
  validateParseResponse,
  validateChatResponse,
  validateToolCallResult,
} from "./structured-output-validator";

export {
  generate,
  streamGenerate,
  estimateTokens,
  resolveModel,
  getEnabledModels,
  invalidateConfigCache,
  ModelRouterError,
} from "./model-router";

export {
  checkBudget,
  recordUsage,
  getBalance,
  resetDailyUsage,
} from "./token-economy-guard";

export {
  store,
  merge,
  elevateToLongTerm,
  expireStaleShortTerm,
  generateEmbedding,
  cosineSimilarity,
  search,
  getLinkedEntries,
  findDuplicates,
} from "./memory-engine";

export {
  classifyMemory,
  detectAndLinkEntities,
  withRetry,
} from "./memory-categorizer";

export {
  execute,
  validateScope,
} from "./tool-executor";

export {
  buildParsePrompt,
  buildChatPrompt,
  buildBriefingPrompt,
  needsWindowing,
  windowHistory,
  summarizeOlderMessages as summarizeHistoryForWindow,
  PARSE_RESPONSE_SCHEMA,
  READ_TOOLS,
  WRITE_TOOLS,
} from "./prompt-factory";

export {
  processMessage,
  getHistory,
  summarizeOlderMessages,
} from "./conversation-manager";

export {
  recordTaskCompletion,
  getTimeEstimate,
} from "./productivity-patterns";
