# Implementation Plan: AI V2 Ecosystem

## Overview

This plan implements the complete AI V2 Ecosystem as a new, isolated system alongside V1. The implementation is split across two codebases: Firebase Cloud Functions (TypeScript) for all server-side intelligence, and the Android app (Kotlin/Compose) for the thin client rendering layer. Tasks are ordered to build foundational infrastructure first (types, interfaces, model router), then core logic (prompt factory, memory engine, token economy), then integration (endpoints, client wiring), and finally advanced features (proactive insights, admin panel).

## Tasks

- [x] 1. Set up V2 project structure and core interfaces
  - [x] 1.1 Create V2 directory structure and TypeScript interfaces
    - Create `functions/src/v2/` directory with subdirectories: `models/`, `services/`, `schemas/`, `endpoints/`, `utils/`
    - Define all core TypeScript interfaces: `ModelConfig`, `ModelRequest`, `ModelResponse`, `StreamChunk`, `PromptPayload`, `ParseInput`, `ChatInput`, `MemoryEntry`, `SearchOptions`, `TokenUsage`, `TokenBalance`, `BudgetCheck`, `ToolCall`, `ApprovedToolCall`, `ToolResult`, `TraceContext`
    - Define type unions: `MemoryCategory`, `SubscriptionTier`, `V2Operation`, `ReadTool`, `WriteTool`
    - _Requirements: 26.1, 26.4, 19.1_

  - [x] 1.2 Create JSON Schema definitions for structured output validation
    - Create `functions/src/v2/schemas/parse-response.schema.ts` with the Task Parse Response Schema (title, date, time, priority, tags, recurrence, description, confidence, detectedLanguage, duplicates)
    - Create `functions/src/v2/schemas/chat-response.schema.ts` with the Chat Response Schema (content, thinking, toolCalls, citations, memoryUpdates)
    - Create `functions/src/v2/schemas/tool-call-result.schema.ts` for tool execution result validation
    - _Requirements: 1.1, 1.2, 21.1_

  - [x] 1.3 Set up testing infrastructure with fast-check
    - Install `fast-check` as dev dependency in `functions/`
    - Configure test runner (vitest or jest) for `functions/tests/unit/`, `functions/tests/property/`, `functions/tests/integration/` directories
    - Create test utility helpers for generating arbitrary memory entries, model configs, and parse inputs
    - _Requirements: 21.1, 21.2_

- [x] 2. Implement StructuredOutputValidator
  - [x] 2.1 Implement schema validation service
    - Create `functions/src/v2/services/structured-output-validator.ts`
    - Implement `validateParseResponse()` — validates any object against the parse JSON Schema, returns typed `ParseResult` or `ValidationError`
    - Implement `validateChatResponse()` — validates against chat JSON Schema
    - Implement `validateToolCallResult()` — validates tool call results
    - Use a JSON Schema validator library (e.g., ajv) for runtime validation
    - _Requirements: 1.2, 21.1, 21.2_

  - [x]* 2.2 Write property test for schema validation correctness
    - **Property 2: Structured output validation correctness**
    - Generate arbitrary JSON objects (conforming and non-conforming) and verify the validator accepts all conforming and rejects all non-conforming with zero false positives/negatives
    - **Validates: Requirements 1.2, 21.2**

  - [x]* 2.3 Write property test for parse output schema conformance
    - **Property 1: Parse output schema conformance**
    - Generate arbitrary valid inputs (1–500 chars, any script) and verify output conforms: title ≤200 chars, priority 1–4, tags ≤10 items each ≤50 chars, description ≤1000 chars, confidence in [0.0, 1.0]
    - **Validates: Requirements 1.1, 1.4, 2.1**

- [x] 3. Implement ModelRouter
  - [x] 3.1 Implement multi-model abstraction layer
    - Create `functions/src/v2/services/model-router.ts`
    - Implement provider adapters for Google GenAI, Anthropic, OpenAI, and Mistral SDKs behind a uniform `ModelRouter` interface
    - Implement `generate()` for single-shot requests and `streamGenerate()` for SSE streaming
    - Implement `estimateTokens()` for pre-request cost estimation
    - Handle model-specific request/response format translation
    - Read active model config from Firestore `v2_config/models`
    - _Requirements: 15.3, 15.6, 15.7, 15.8_

  - [x]* 3.2 Write unit tests for ModelRouter
    - Test routing to correct provider based on config
    - Test fallback to default model when user's selected model is disabled
    - Test error when all models disabled (503 response)
    - Test uniform response format across providers
    - _Requirements: 15.6, 15.7, 15.8_

- [x] 4. Implement TokenEconomyGuard
  - [x] 4.1 Implement token budget enforcement service
    - Create `functions/src/v2/services/token-economy-guard.ts`
    - Implement `checkBudget()` — verifies user's cumulative consumption + estimated cost ≤ daily budget
    - Implement `recordUsage()` — records actual token usage with normalized cost calculation: `(inputTokens + outputTokens) × costPerMillionTokens / 1,000,000`
    - Implement `getBalance()` — returns current consumption, remaining budget, tier, and reset time
    - Implement `resetDailyUsage()` — resets consumption at start of new calendar day in user's timezone
    - Read tier budgets from `v2_config/models.tierBudgets`
    - Store usage in `v2_token_usage/{uid}` collection
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 18.1, 18.4_

  - [x]* 4.2 Write property test for token cost normalization
    - **Property 12: Token cost normalization correctness**
    - For arbitrary (inputTokens, outputTokens, costPerMillionTokens), verify normalizedCost = (inputTokens + outputTokens) × costPerMillionTokens / 1,000,000 and result is non-negative
    - **Validates: Requirements 16.3, 17.1**

  - [x]* 4.3 Write property test for budget enforcement
    - **Property 13: Token budget enforcement**
    - For arbitrary user consumption/budget combinations, verify requests rejected when consumed ≥ budget OR estimated cost > remaining, and consumed + remaining = budget invariant holds
    - **Validates: Requirements 17.2, 17.4, 17.5**

- [x] 5. Implement MemoryEngine (Memory_V2)
  - [x] 5.1 Implement memory storage with vector embeddings
    - Create `functions/src/v2/services/memory-engine.ts`
    - Implement `store()` — generates 768-dim embedding via Vertex AI text-embedding-005, stores entry in `v2_memory/{uid}/entries/` with all MemoryEntry fields
    - Implement memory count cap: check count before store, evict least-recently-accessed if at 1000 limit
    - Implement `merge()` — updates existing entry with semantic deduplication (similarity ≥ 0.85 triggers merge)
    - Implement `elevateToLongTerm()` — promotes short-term entry to long-term
    - Implement `expireStaleShortTerm()` — removes short-term entries not accessed within retention period
    - _Requirements: 10.1, 11.1, 12.1, 12.2, 12.3, 12.4, 13.1, 13.4_

  - [x] 5.2 Implement semantic search and retrieval
    - Implement `search()` — performs Firestore Vector Search with cosine distance, filters by uid, returns top 10 entries ordered by descending similarity, applies min confidence threshold (0.3), supports category and memoryType filters
    - Implement `getLinkedEntries()` — retrieves up to 5 directly linked entries above confidence threshold
    - Implement `findDuplicates()` — vector search for deduplication with configurable threshold (default 0.85)
    - Handle 5-second timeout: return empty result set on timeout
    - _Requirements: 10.2, 10.3, 10.4, 10.5, 11.3, 11.4, 11.5, 14.1_

  - [x] 5.3 Implement memory categorization and relationship linking
    - Implement automatic classification into exactly one MemoryCategory from the allowed set
    - Default to "context" category when classification confidence is insufficient
    - Detect shared entities across entries and create relationship links (max 20 per entry)
    - Implement extraction retry with exponential backoff (max 3 attempts)
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 13.2, 13.3_

  - [x]* 5.4 Write property test for memory retrieval ordering and filtering
    - **Property 10: Memory retrieval ordering and filtering**
    - Verify results ordered by descending similarity, limited to 10, confidence in [0.0, 1.0], no entry below 0.3 threshold, linked entries ≤5, relationship links ≤20
    - **Validates: Requirements 10.2, 10.3, 10.4, 11.4, 11.5**

  - [x]* 5.5 Write property test for memory categorization invariant
    - **Property 11: Memory categorization invariant**
    - For any new memory entry, verify exactly one category assigned from the allowed set
    - **Validates: Requirements 11.1**

  - [x]* 5.6 Write property test for memory entry count cap
    - **Property 17: Memory entry count cap**
    - Verify total entries never exceed 1000 per user and new entries trigger eviction of least-recently-accessed
    - **Validates: Requirements 10.1**

- [x] 6. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Implement TraceLogger
  - [x] 7.1 Implement structured logging service
    - Create `functions/src/v2/services/trace-logger.ts`
    - Implement `startTrace()` — generates UUID v4 trace ID, records operation start
    - Implement `logMemoryRetrieval()` — logs entry count and top confidence score
    - Implement `logModelCall()` — logs model name, token counts, latency
    - Implement `logFailure()` — logs failure reason and elapsed time
    - Implement `endTrace()` — logs final outcome
    - Ensure no user message content or memory text is logged in production (metadata only)
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5_

- [x] 8. Implement PromptFactoryV2
  - [x] 8.1 Implement unified prompt factory
    - Create `functions/src/v2/services/prompt-factory.ts`
    - Implement `buildParsePrompt()` — assembles system prompt with language detection, memory context, timezone, current datetime, and force-tool-call mode for structured task extraction
    - Implement `buildChatPrompt()` — assembles system prompt with conversation history, memory context, tool schemas, user tasks, and active model-specific parameters
    - Implement `buildBriefingPrompt()` — assembles prompt for daily briefing generation
    - Implement conversation history windowing: include recent messages, summarize older ones when exceeding context window
    - Inject memory entries from MemoryEngine into prompt context
    - _Requirements: 19.1, 19.2, 19.3, 9.1, 9.2, 9.3, 9.4_

- [x] 9. Implement ConversationManager
  - [x] 9.1 Implement thread-safe conversation processing
    - Create `functions/src/v2/services/conversation-manager.ts`
    - Implement `processMessage()` — uses Firestore transaction with per-conversation lock document to enforce sequential processing
    - Implement message queueing via Cloud Tasks for messages arriving during processing
    - Implement `getHistory()` — retrieves conversation messages with limit
    - Implement `summarizeOlderMessages()` — compresses older messages preserving facts, decisions, and outcomes
    - Store conversations in `v2_conversations/{uid}/threads/{conversationId}` with messages in subcollection
    - _Requirements: 22.1, 22.2, 22.3, 9.1, 9.2_

- [x] 10. Implement ToolExecutor
  - [x] 10.1 Implement tool execution service
    - Create `functions/src/v2/services/tool-executor.ts`
    - Implement `validateScope()` — verifies all Firestore paths in tool call are scoped to authenticated user's UID
    - Implement `execute()` — executes approved tool calls against user's data
    - Implement read tools: `get_today_tasks`, `get_tasks_by_date_range`, `get_task_by_name`, `get_friends_list`, `get_social_circles`
    - Implement write tools: `create_task`, `update_task`, `complete_task`, `create_circle`, `add_circle_members`
    - Reject any tool call referencing another user's data with scope violation error
    - _Requirements: 7.2, 7.4, 7.5, 7.7, 8.1, 8.2, 8.4_

  - [x]* 10.2 Write property test for user data scope enforcement
    - **Property 9: User data scope enforcement**
    - For arbitrary requests, verify all Firestore paths scoped to authenticated UID, tool calls referencing other users rejected, unauthenticated requests rejected before data access, memory searches return only authenticated user's entries
    - **Validates: Requirements 8.1, 8.2, 8.5, 8.6**

  - [x]* 10.3 Write property test for tool permission gating
    - **Property 14: Tool permission gating**
    - For N tool calls proposed, verify exactly N permission prompts presented (with category, description, target data), only granted calls executed, denied/unanswered results in zero execution
    - **Validates: Requirements 7.1, 7.3, 7.6**

- [x] 11. Implement Parser_V2 endpoint
  - [x] 11.1 Implement v2Parse Cloud Function
    - Create `functions/src/v2/endpoints/v2-parse.ts`
    - Implement input validation: reject empty, whitespace-only, or >500 char input with 400 error before calling AI model
    - Wire auth verification → token budget check → PromptFactory (buildParsePrompt) → MemoryEngine (retrieve context) → ModelRouter (generate) → StructuredOutputValidator (validate) → retry on failure → deduplication check → record token usage → return result
    - Include Parse_Confidence score in response
    - Include detected language in response
    - Include duplicate indicators (taskId, similarity, title) for matches ≥ 0.85
    - Return trace ID in response
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 5.1, 5.2, 5.3, 5.4_

  - [x]* 11.2 Write property test for invalid input rejection
    - **Property 3: Invalid input rejection without model invocation**
    - For empty, whitespace-only, or >500 char inputs, verify rejection without AI model invocation
    - **Validates: Requirements 1.6**

  - [x]* 11.3 Write property test for temporal resolution
    - **Property 4: Temporal resolution correctness**
    - For arbitrary relative temporal expressions + current datetime + timezone, verify correct absolute date/time resolution and valid HH:mm within 00:00–23:59
    - **Validates: Requirements 2.5, 3.1, 3.4**

  - [x]* 11.4 Write property test for low confidence field omission
    - **Property 5: Low confidence field omission**
    - For parse results where temporal/recurrence field confidence < 0.5, verify field is omitted rather than included with a guessed value
    - **Validates: Requirements 2.6, 3.5, 3.6, 4.5**

  - [x]* 11.5 Write property test for language/script preservation
    - **Property 6: Language and script preservation**
    - For inputs with non-Latin scripts (Devanagari, Arabic, CJK), verify output preserves original script without transliteration
    - **Validates: Requirements 2.4**

  - [x]* 11.6 Write property test for recurrence extraction
    - **Property 7: Recurrence extraction schema conformance and title cleaning**
    - For inputs with recurrence expressions, verify output has valid recurrence schema (type, interval) and title does not contain recurrence words
    - **Validates: Requirements 4.1, 4.2**

  - [x]* 11.7 Write property test for deduplication threshold
    - **Property 8: Deduplication threshold partitioning**
    - For parsed tasks with existing tasks and similarity scores, verify all and only tasks with similarity ≥ 0.85 are flagged, and parsed task is always returned
    - **Validates: Requirements 5.2, 5.3**

- [x] 12. Implement Chat_V2 endpoints
  - [x] 12.1 Implement v2Chat Cloud Function with SSE streaming
    - Create `functions/src/v2/endpoints/v2-chat.ts`
    - Implement SSE streaming response: emit events for delta (text chunks), thinking (reasoning), tool_calls (proposed tools), and done (completion)
    - Wire auth verification → token budget check → ConversationManager (get history) → MemoryEngine (retrieve) → PromptFactory (buildChatPrompt) → ModelRouter (streamGenerate) → StructuredOutputValidator → detect tool calls → emit stream events → record usage
    - Include trace ID in response
    - Store conversation messages in Firestore
    - Extract and store new memory entries from conversation
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 13.1, 14.2, 14.3_

  - [x] 12.2 Implement v2ChatContinue Cloud Function
    - Create `functions/src/v2/endpoints/v2-chat-continue.ts`
    - Accept tool results from client after user grants/denies permissions
    - Re-invoke model with tool results to generate final response
    - Handle denied tool calls gracefully (inform user action not performed)
    - _Requirements: 7.2, 7.3, 7.7, 7.8_

  - [x] 12.3 Implement v2TokenBalance and v2Config endpoints
    - Create `functions/src/v2/endpoints/v2-token-balance.ts` — returns current usage, remaining budget, tier, reset time
    - Create `functions/src/v2/endpoints/v2-config.ts` — returns model registry for client display (enabled models with display names)
    - _Requirements: 17.5, 15.3_

  - [x] 12.4 Implement v2DailyBriefing Cloud Function
    - Create `functions/src/v2/endpoints/v2-daily-briefing.ts`
    - Generate daily briefing with: today's tasks, deadlines within 7 days, relevant memory context
    - Check if briefing already delivered today (at most once per calendar day)
    - Only trigger when current time is 04:00–12:00 local time
    - Include proactive insights when ≥14 days of task history available
    - _Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7_

- [x] 13. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 14. Implement V2 Firestore collection isolation
  - [x] 14.1 Implement collection path enforcement and V1 isolation guard
    - Create `functions/src/v2/utils/collection-paths.ts` — central module defining all V2 collection paths (v2_memory, v2_conversations, v2_token_usage, v2_config)
    - Add runtime guard to reject any V2 code path attempting to access V1 collections
    - Ensure no shared module imports between V1 entry points and V2 entry points
    - _Requirements: 26.1, 26.2, 26.4, 26.5, 26.6_

  - [x]* 14.2 Write property test for V2 Firestore collection isolation
    - **Property 15: V2 Firestore collection isolation**
    - For arbitrary V2 operations, verify all collection paths use V2-prefixed collections and never read/write V1 collections
    - **Validates: Requirements 26.4**

- [x] 15. Implement Android V2 client service
  - [x] 15.1 Create CloudAiV2Service in Kotlin
    - Create `app/src/main/java/.../ai/v2/CloudAiV2Service.kt` — no shared code with V1 `CloudAiService`
    - Implement `chat()` — POST to v2Chat endpoint, return `Flow<V2StreamEvent>` for SSE events
    - Implement `chatContinue()` — POST tool results to v2ChatContinue
    - Implement `parse()` — POST to v2Parse, return `V2ParseResult`
    - Implement `getTokenBalance()` — GET v2TokenBalance
    - Implement `getModelConfig()` — GET v2Config
    - Implement `getDailyBriefing()` — POST to v2DailyBriefing
    - Set up Hilt DI for V2 service injection
    - _Requirements: 26.1, 15.3, 17.5_

  - [x] 15.2 Implement RichResponseRenderer in Compose
    - Create `app/src/main/java/.../ai/v2/ui/RichResponseRenderer.kt`
    - Implement rendering for `RenderBlock.Markdown` — full markdown with headings, bold, italic, lists, links, blockquotes
    - Implement rendering for `RenderBlock.CodeBlock` — syntax highlighting when language specified, plain monospace otherwise
    - Implement rendering for `RenderBlock.MathBlock` — LaTeX rendering, fallback to raw LaTeX on parse failure
    - Implement rendering for `RenderBlock.ThinkingBlock` — collapsible section, default collapsed
    - Implement rendering for `RenderBlock.Citation` — tappable link with title and snippet (≤200 chars)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_

  - [x]* 15.3 Write property test for rich response render block type correctness
    - **Property 16: Rich response render block type correctness**
    - For arbitrary RenderBlocks, verify renderer produces non-null composable of correct type for each block type
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7**

  - [x] 15.4 Implement ToolPermissionDialog in Compose
    - Create `app/src/main/java/.../ai/v2/ui/ToolPermissionDialog.kt`
    - Display tool call category (read/write), description, and target data
    - Present separate dialog for each tool call when multiple are proposed
    - Return grant/deny result per tool call back to CloudAiV2Service
    - _Requirements: 7.1, 7.3, 7.6, 7.8_

- [x] 16. Implement task intent detection and natural conversation features
  - [x] 16.1 Implement task intent detection in chat
    - Extend PromptFactoryV2 chat prompt to instruct model to detect task intent in natural conversation
    - When task intent detected, model proposes `create_task` tool call with extracted details (title, date, time)
    - Client displays extracted task details for confirmation before tool permission prompt
    - Implement task decomposition suggestion: model offers subtask breakdown for complex tasks via tool calls
    - _Requirements: 25.1, 25.2, 25.3, 25.4, 24.1, 24.2_

  - [x] 16.2 Implement productivity pattern memory storage
    - Extend MemoryEngine to store task completion duration data as Long_Term_Memory entries in "productivity_pattern" category
    - Store time-from-creation-to-completion by task type/tags
    - Implement time estimation lookup for Chat_V2 to provide estimates based on historical patterns
    - _Requirements: 24.3, 24.4_

- [x] 17. Implement Admin Panel AI V2 configuration section
  - [x] 17.1 Add AI V2 model management to admin panel
    - Create new section in `admin-panel/src/pages/` for AI V2 configuration
    - Implement CRUD for Model_Registry entries: provider (1–100 chars), model ID (1–200 chars), display name (1–60 chars), enabled/disabled, cost_per_million_tokens (positive number), max 50 entries
    - Implement default model selection: exactly one enabled model as default
    - Enforce rule: cannot disable/remove default model without designating new default
    - _Requirements: 15.1, 15.2, 15.4, 15.5, 16.1_

  - [x] 17.2 Add subscription tier budget management to admin panel
    - Implement UI for viewing and adjusting Daily_Token_Budget for Pro_Student, Pro_Youth, Pro_Standard tiers
    - Changes apply starting from next budget reset
    - Display current rate per model for reference
    - _Requirements: 18.1, 18.2, 18.3, 16.2_

- [x] 18. Final integration and wiring
  - [x] 18.1 Wire all V2 Cloud Function exports
    - Create `functions/src/v2/index.ts` — exports all V2 Cloud Function entry points (v2Chat, v2ChatContinue, v2Parse, v2TokenBalance, v2Config, v2DailyBriefing)
    - Ensure no imports from V1 modules in V2 index
    - Add rate limiting: max 10 requests/minute per user
    - Register endpoints in `functions/src/index.ts` with V2-specific naming
    - _Requirements: 26.1, 26.2, 26.3, 26.6_

  - [x] 18.2 Wire Android V2 UI into existing app
    - Connect CloudAiV2Service to chat UI screens
    - Wire daily briefing check on first chat open per day (04:00–12:00 window)
    - Wire token balance display in UI
    - Wire model info display from v2Config
    - Handle budget exceeded (429) with countdown UI
    - Handle auth errors (401) with login redirect
    - _Requirements: 23.1, 23.3, 17.2, 17.5, 15.3_

  - [x]* 18.3 Write integration tests for end-to-end flows
    - Test parse flow: input → auth → budget check → memory → model → validation → dedup → response
    - Test chat flow: message → auth → budget → history → memory → model → streaming → tool calls
    - Test token economy cycle: track → hit limit → reject → reset
    - Test tool call cycle: propose → permission → execute → result
    - _Requirements: 1.1, 9.1, 17.2, 7.2_

- [x] 19. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Cloud Functions are TypeScript (Node.js 20), Android client is Kotlin/Compose
- All V2 code is completely isolated from V1 — no shared imports, separate collections, separate endpoints

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["2.1", "7.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "3.1", "4.1"] },
    { "id": 3, "tasks": ["3.2", "4.2", "4.3", "5.1"] },
    { "id": 4, "tasks": ["5.2", "5.3"] },
    { "id": 5, "tasks": ["5.4", "5.5", "5.6", "8.1"] },
    { "id": 6, "tasks": ["9.1", "10.1", "14.1"] },
    { "id": 7, "tasks": ["10.2", "10.3", "14.2", "11.1"] },
    { "id": 8, "tasks": ["11.2", "11.3", "11.4", "11.5", "11.6", "11.7", "12.1"] },
    { "id": 9, "tasks": ["12.2", "12.3", "12.4"] },
    { "id": 10, "tasks": ["15.1", "15.2", "16.1", "16.2"] },
    { "id": 11, "tasks": ["15.3", "15.4", "17.1", "17.2"] },
    { "id": 12, "tasks": ["18.1", "18.2"] },
    { "id": 13, "tasks": ["18.3"] }
  ]
}
```
