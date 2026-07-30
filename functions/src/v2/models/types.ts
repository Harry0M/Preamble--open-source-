/**
 * AI V2 Ecosystem — Core TypeScript Interfaces and Type Definitions
 *
 * This module defines all shared types for the V2 system.
 * V2 is completely isolated from V1 — no shared imports.
 */

// ─── Type Unions ───────────────────────────────────────────────────────────────

/**
 * Classification categories for memory entries.
 */
export type MemoryCategory =
  | "identity"
  | "preference"
  | "goal"
  | "interest"
  | "context"
  | "relationship"
  | "habit"
  | "schedule"
  | "project"
  | "important_date"
  | "location"
  | "productivity_pattern";

/**
 * Subscription pricing tiers — same AI features, different daily budgets.
 */
export type SubscriptionTier = "pro_student" | "pro_youth" | "pro_standard";

/**
 * V2 operation types for trace logging.
 */
export type V2Operation = "parse" | "chat" | "chat_continue" | "briefing" | "tool_exec";

/**
 * Read-only tools that retrieve user data.
 */
export type ReadTool =
  | "get_today_tasks"
  | "get_tasks_by_date_range"
  | "get_task_by_name"
  | "get_friends_list"
  | "get_social_circles";

/**
 * Write tools that modify user data.
 */
export type WriteTool =
  | "create_task"
  | "update_task"
  | "complete_task"
  | "create_circle"
  | "add_circle_members";

// ─── Model Router Interfaces ───────────────────────────────────────────────────

/**
 * Configuration for a single AI model in the Model Registry.
 */
export interface ModelConfig {
  provider: "google" | "anthropic" | "openai" | "mistral";
  modelId: string;
  displayName: string;
  costPerMillionTokens: number;
  supportsReasoning: boolean;
  maxContextWindow: number;
  enabled: boolean;
  isDefault: boolean;
}

/**
 * Uniform request format sent to any AI provider via ModelRouter.
 */
export interface ModelRequest {
  systemPrompt: string;
  messages: ConversationMessage[];
  tools?: ToolDefinition[];
  forceToolCall?: boolean;
  responseSchema?: JSONSchema;
  temperature?: number;
  maxOutputTokens?: number;
}

/**
 * Uniform response format returned by any AI provider.
 */
export interface ModelResponse {
  text: string;
  toolCalls?: ToolCall[];
  thinkingText?: string;
  inputTokens: number;
  outputTokens: number;
  finishReason: "stop" | "tool_calls" | "length" | "error";
}

/**
 * A single chunk emitted during SSE streaming.
 */
export interface StreamChunk {
  type: "delta" | "thinking" | "tool_calls" | "done" | "error";
  text?: string;
  toolCalls?: ToolCall[];
  inputTokens?: number;
  outputTokens?: number;
  finishReason?: ModelResponse["finishReason"];
  error?: string;
}

// ─── Prompt Factory Interfaces ─────────────────────────────────────────────────

/**
 * Assembled prompt payload ready to send to ModelRouter.
 */
export interface PromptPayload {
  systemPrompt: string;
  messages: ConversationMessage[];
  tools?: ToolDefinition[];
  forceToolCall?: boolean;
  responseSchema?: JSONSchema;
  temperature?: number;
  maxOutputTokens?: number;
}

/**
 * Input for Parser_V2 prompt construction.
 */
export interface ParseInput {
  text: string;
  timezone: string;
  currentDateTime: string;
  memories: MemoryEntry[];
  language?: DetectedLanguage;
}

/**
 * Input for Chat_V2 prompt construction.
 */
export interface ChatInput {
  message: string;
  conversationId: string;
  history: ConversationMessage[];
  memories: MemoryEntry[];
  userTasks?: TaskSnapshot[];
  mode: "concise" | "detailed";
  activeModel: ModelConfig;
}

/**
 * Input for Daily Briefing prompt construction.
 */
export interface BriefingInput {
  uid: string;
  timezone: string;
  currentDateTime: string;
  memories: MemoryEntry[];
  todayTasks: TaskSnapshot[];
  upcomingDeadlines: TaskSnapshot[];
  taskHistoryDays?: number;
}

// ─── Memory Interfaces ─────────────────────────────────────────────────────────

/**
 * A stored memory entry with vector embedding for semantic search.
 */
export interface MemoryEntry {
  id: string;
  uid: string;
  text: string;
  embedding: number[];
  category: MemoryCategory;
  memoryType: "short_term" | "long_term";
  confidence: number;
  linkedEntryIds: string[];
  source: "chat" | "parse" | "system";
  createdAt: number;
  lastAccessedAt: number;
  accessCount: number;
  conversationId?: string;
}

/**
 * Options for semantic memory search.
 */
export interface SearchOptions {
  limit?: number;
  minConfidence?: number;
  categories?: MemoryCategory[];
  memoryType?: "short_term" | "long_term" | "all";
}

/**
 * Input for creating a new memory entry (before ID and embedding generation).
 */
export interface NewMemoryEntry {
  text: string;
  category: MemoryCategory;
  memoryType: "short_term" | "long_term";
  confidence: number;
  source: "chat" | "parse" | "system";
  linkedEntryIds?: string[];
  conversationId?: string;
}

// ─── Token Economy Interfaces ──────────────────────────────────────────────────

/**
 * Record of token consumption for a single AI request.
 */
export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
  model: string;
  costPerMillionTokens: number;
  normalizedCost: number;
  traceId: string;
}

/**
 * Current token balance and budget status for a user.
 */
export interface TokenBalance {
  consumed: number;
  budget: number;
  remaining: number;
  tier: SubscriptionTier;
  resetTime: string;
}

/**
 * Result of a pre-request budget check.
 */
export interface BudgetCheck {
  allowed: boolean;
  remainingBudget: number;
  dailyBudget: number;
  resetTime: string;
}

// ─── Tool Call Interfaces ──────────────────────────────────────────────────────

/**
 * A tool call proposed by the AI model.
 */
export interface ToolCall {
  name: string;
  category: "read" | "write";
  description: string;
  targetData: string;
  args: Record<string, unknown>;
}

/**
 * A tool call that has been explicitly approved by the user.
 */
export interface ApprovedToolCall extends ToolCall {
  approvedAt: number;
  traceId: string;
}

/**
 * Result returned after executing an approved tool call.
 */
export interface ToolResult {
  success: boolean;
  data?: unknown;
  error?: string;
  toolName: string;
  traceId: string;
  executedAt: number;
}

// ─── Trace / Observability Interfaces ──────────────────────────────────────────

/**
 * Context object for tracing a V2 operation end-to-end.
 */
export interface TraceContext {
  traceId: string;
  uid: string;
  operation: V2Operation;
  startTime: number;
}

// ─── Conversation Interfaces ───────────────────────────────────────────────────

/**
 * A single message in a conversation history.
 */
export interface ConversationMessage {
  role: "user" | "assistant" | "system";
  content: string;
  toolCalls?: ToolCall[];
  toolResults?: ToolResult[];
  createdAt?: number;
}

/**
 * Definition of a tool that can be provided to the AI model.
 */
export interface ToolDefinition {
  name: string;
  description: string;
  category: "read" | "write";
  parameters: JSONSchema;
}

// ─── Supporting Types ──────────────────────────────────────────────────────────

/**
 * Detected language/script information for multilingual input.
 */
export interface DetectedLanguage {
  language: string;
  script?: string;
  confidence: number;
}

/**
 * Snapshot of a user's task for inclusion in prompt context.
 */
export interface TaskSnapshot {
  id: string;
  title: string;
  date?: string;
  time?: string;
  priority?: number;
  tags?: string[];
  completed: boolean;
}

/**
 * Validation error returned when structured output fails schema check.
 */
export interface ValidationError {
  valid: false;
  errors: Array<{
    path: string;
    message: string;
    keyword: string;
  }>;
}

/**
 * Validated parse result from StructuredOutputValidator.
 */
export interface ParseResult {
  valid: true;
  data: {
    title: string;
    date?: string;
    time?: string;
    priority?: number;
    tags?: string[];
    recurrence?: {
      recurrenceType: "daily" | "weekly" | "monthly" | "yearly";
      recurrenceInterval: number;
      recurrenceDays?: number[];
      recurrenceEndDate?: string;
    };
    description?: string;
    confidence: number;
    detectedLanguage?: string;
    duplicates?: Array<{
      taskId: string;
      similarity: number;
      title: string;
    }>;
  };
}

/**
 * Validated chat result from StructuredOutputValidator.
 */
export interface ChatResult {
  valid: true;
  data: {
    content: string;
    thinking?: string;
    toolCalls?: ToolCall[];
    citations?: Array<{
      title: string;
      url: string;
      snippet?: string;
    }>;
    memoryUpdates?: Array<{
      op: "upsert" | "delete";
      text: string;
      category: string;
    }>;
  };
}

/**
 * Validated tool call execution result from StructuredOutputValidator.
 */
export interface ToolCallResult {
  valid: true;
  data: {
    toolName: string;
    success: boolean;
    result?: unknown;
    error?: string;
  };
}

/**
 * Result of scope validation for a tool call.
 */
export interface ScopeValidation {
  valid: boolean;
  reason?: string;
}

/**
 * Generic JSON Schema type (subset used for structured output enforcement).
 */
export interface JSONSchema {
  $schema?: string;
  type: string;
  required?: string[];
  properties?: Record<string, unknown>;
  items?: unknown;
  enum?: unknown[];
  [key: string]: unknown;
}
