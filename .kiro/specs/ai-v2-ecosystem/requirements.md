# Requirements Document

## Introduction

This feature introduces a completely new AI V2 ecosystem for the Preamble Android app, designed to coexist alongside the existing AI system without replacing it. Old app versions continue using the current AI; new versions adopt AI V2. The ecosystem comprises two user-facing capabilities — AI Task Parsing V2 (natural language to structured task) and AI Chat V2 (conversational AI integrated with the todo app) — underpinned by shared infrastructure: a unified semantic memory system (RAG with Firestore Vector Search), an admin-configurable multi-model backend, a token/credit economy with subscription tiers, and tool-call permissions for safe data access.

The V2 system resolves critical architectural weaknesses in the V1 AI (no shared memory between parser and chat, no structured output enforcement, no semantic search, brittle heuristics, duplicated prompt logic, no observability) while adding competitive features: rich response rendering (code blocks, math, thinking process, web citations), conversational tool calls with explicit user permissions, proactive productivity insights, and multilingual support (Hindi, English, Hinglish, and beyond).

This document defines WHAT the AI V2 ecosystem must do. Technical design (prompt architecture, vector index schema, Cloud Function decomposition, Android UI composition) is addressed in the design phase.

## Glossary

- **AI_V2_Ecosystem**: The complete new AI system comprising Parser_V2, Chat_V2, Memory_V2, the Model_Registry, the Token_Economy, and the Tool_Permission system, deployed separately from the existing AI.
- **Parser_V2**: The AI V2 component that converts natural language input into a structured Task object, with access to Memory_V2 for context and language detection for multilingual handling.
- **Chat_V2**: The AI V2 conversational interface that provides rich responses, tool calls, and productivity features, deeply integrated with the user's task data.
- **Memory_V2**: The shared semantic memory system accessible by both Parser_V2 and Chat_V2, backed by Firestore Vector Search (RAG), with categorized short-term and long-term memory, relationship linking, and confidence scoring.
- **Model_Registry**: The admin-configurable collection of AI model providers (e.g., Claude, GPT-4o, Gemini, Mistral) with per-model cost rates, stored server-side and fetched by the client without requiring an app update.
- **Active_Model**: The AI model currently selected for a request, determined by the admin-set default or a user's explicit choice from the Model_Registry.
- **Token_Economy**: The credit/token system governing AI V2 usage, with daily budgets per user, multi-model cost normalization, and subscription-tier-based quotas.
- **Daily_Token_Budget**: The maximum number of normalized tokens a user may consume per calendar day, determined by the user's Subscription_Tier.
- **Subscription_Tier**: One of the pricing categories — Pro_Student, Pro_Youth, or Pro_Standard — each sharing the same AI features but differing in Daily_Token_Budget and pricing.
- **Tool_Call**: An action the Chat_V2 AI proposes to execute against the user's data (read or write), requiring explicit user permission before execution.
- **Tool_Permission**: An in-chat authorization prompt presented to the user before a Tool_Call is executed, specifying what action will be performed and on what data.
- **Prompt_Factory_V2**: The single, server-side prompt generation system used by both Parser_V2 and Chat_V2, eliminating the V1 split between parse and chat prompt factories.
- **Structured_Output**: A JSON Schema-validated response format enforced on all AI model outputs, ensuring deterministic parsing of model responses.
- **Memory_Category**: One of the defined classification labels for a memory entry: identity, preference, goal, interest, context, relationship, habit, schedule, project, important_date, location, or productivity_pattern.
- **Memory_Confidence**: A numeric score (0.0 to 1.0) indicating the retrieval system's confidence that a memory entry is relevant to the current context.
- **Short_Term_Memory**: Memory entries scoped to the current conversation or session, automatically elevated to Long_Term_Memory based on importance scoring.
- **Long_Term_Memory**: Persistent memory entries that survive across sessions and are retrievable by semantic similarity.
- **Rich_Response**: A Chat_V2 response containing one or more rich content blocks: markdown with code blocks, math equations, thinking/reasoning process, or web search citations.
- **Web_Citation**: A source reference in a Rich_Response linking to an external webpage, including a title, URL, and optional snippet.
- **Thinking_Block**: A collapsible section in a Rich_Response showing the AI model's reasoning process, displayed when the Active_Model supports reasoning transparency.
- **Admin_Panel**: The existing React/Vite admin panel, extended with an AI V2 configuration section for managing the Model_Registry, token rates, and subscription quotas.
- **Cloud_AI_V2_Path**: The server-side Cloud Functions (TypeScript) that host Prompt_Factory_V2 logic, model routing, structured output validation, memory retrieval, and tool execution — the single backend for all AI V2 requests.
- **Parse_Confidence**: A numeric score (0.0 to 1.0) indicating the parser's confidence in the extracted task fields.
- **Language_Detection**: The pipeline that identifies the language/script of user input before processing, enabling script-aware parsing and response.
- **Proactive_Insight**: An AI-generated productivity observation or suggestion surfaced to the user based on their usage patterns (e.g., task completion trends, optimal work times).
- **Daily_Briefing**: A proactive morning summary generated by Chat_V2 covering the user's tasks, calendar, and relevant context for the day ahead.
- **User_Data_Scope**: The security boundary ensuring that all AI V2 operations (tool calls, memory, parsing) access only the authenticated user's own data, with no cross-user or system-wide data leakage.

## Requirements

---

## Section A — AI Task Parsing V2

### Requirement 1: Natural language to structured task conversion

**User Story:** As a user, I want to type or speak a task in natural language and have it converted into a structured task, so that I can add tasks quickly without filling in fields manually.

#### Acceptance Criteria

1. WHEN a user submits natural language input of 1 to 500 characters to Parser_V2, THE Parser_V2 SHALL extract a structured Task object containing at minimum: title (maximum 200 characters), and optionally date, time, priority (integer 1–4), tags (maximum 10 tags, each maximum 50 characters), recurrence, and description (maximum 1000 characters).
2. THE Parser_V2 SHALL enforce Structured_Output by validating the AI model's response against a defined JSON Schema before accepting the parse result.
3. IF the AI model's response does not conform to the JSON Schema, THEN THE Parser_V2 SHALL reject the response, retry once within 10 seconds, and if the retry also fails, return an error indicating the task could not be parsed.
4. WHEN Parser_V2 produces a parse result, THE Parser_V2 SHALL include a Parse_Confidence score (0.0 to 1.0) indicating the extraction certainty.
5. THE Parser_V2 SHALL access Memory_V2 during parsing to resolve references to known entities (e.g., "meeting with Rahul" resolves Rahul's identity from memory).
6. IF the user submits empty, whitespace-only, or input exceeding 500 characters, THEN THE Parser_V2 SHALL reject the input and return an error indicating invalid input length without calling the AI model.
7. IF Memory_V2 is unavailable or returns no matching entries during parsing, THEN THE Parser_V2 SHALL proceed with parsing using only the input text and SHALL NOT fail the parse request due to memory unavailability.

### Requirement 2: Multilingual and multi-script parsing

**User Story:** As a multilingual user, I want to type tasks in Hindi, English, Hinglish, or any language, so that the parser works in my natural communication style.

#### Acceptance Criteria

1. WHEN a user submits input in any language or script (including Hindi, English, Hinglish, and mixed-script), THE Parser_V2 SHALL process the input and produce a structured Task conforming to the JSON Schema defined in Requirement 1, with Parse_Confidence reflecting extraction certainty for that language.
2. WHEN Parser_V2 processes multilingual input, THE Parser_V2 SHALL run Language_Detection to identify the primary language and script before parsing.
3. IF Language_Detection cannot identify the primary language with sufficient confidence, THEN THE Parser_V2 SHALL proceed with parsing using a default language-agnostic strategy and SHALL set Parse_Confidence below 0.5 for the result.
4. THE Parser_V2 SHALL preserve the original language and script of the task title and description in the structured output without translating or transliterating.
5. WHEN the input contains date or time references in Hindi or English (including Hinglish expressions mixing both), THE Parser_V2 SHALL resolve those references to the correct absolute date and time relative to the user's timezone.
6. IF the input contains a date or time reference that Parser_V2 cannot resolve to an absolute date or time, THEN THE Parser_V2 SHALL omit the date or time field from the structured output rather than guessing.

### Requirement 3: Temporal parsing with timezone awareness

**User Story:** As a user, I want dates and times in my task input resolved correctly relative to my timezone and current moment, so that "tomorrow at 3pm" means the right day and time for me.

#### Acceptance Criteria

1. WHEN Parser_V2 resolves a relative date or time expression (e.g., "tomorrow", "next Tuesday", "in 2 hours"), THE Parser_V2 SHALL resolve it relative to the user's current local date and time obtained from the device.
2. THE Parser_V2 SHALL retrieve and use the user's timezone from Memory_V2 for all date and time resolution.
3. IF the user's timezone is not available in Memory_V2, THEN THE Parser_V2 SHALL fall back to the device-reported timezone for date and time resolution.
4. WHEN Parser_V2 extracts a time value, THE Parser_V2 SHALL validate that the resolved time is a valid `HH:mm` value within 00:00–23:59.
5. IF Parser_V2 extracts a time value that does not resolve to a valid `HH:mm` value within 00:00–23:59, THEN THE Parser_V2 SHALL omit the time field from the structured output.
6. IF Parser_V2 cannot resolve a date or time expression and the Parse_Confidence for the temporal field is below 0.5, THEN THE Parser_V2 SHALL omit the date or time field from the structured output rather than guessing.

### Requirement 4: Recurrence parsing beyond keywords

**User Story:** As a user, I want to express recurring tasks naturally (e.g., "gym every weekday", "water plants twice a week"), so that the parser handles complex recurrence without me knowing a specific syntax.

#### Acceptance Criteria

1. WHEN a user's input contains a recurrence expression, THE Parser_V2 SHALL extract a structured recurrence rule containing: recurrenceType (one of daily, weekly, monthly, yearly), recurrenceInterval (integer 1–365 indicating frequency), recurrenceDays (list of applicable days of the week or days of the month), and optionally recurrenceEndDate.
2. WHEN Parser_V2 extracts a recurrence rule from the input, THE Parser_V2 SHALL remove the recurrence expression from the extracted task title so that the title contains only the task description (e.g., input "gym every weekday" yields title "gym" and recurrence rule for weekdays).
3. THE Parser_V2 SHALL parse natural recurrence expressions including interval patterns (e.g., "every other day", "every 3 weeks"), day-list patterns (e.g., "on weekdays", "on Mon Wed Fri"), count-per-period patterns (e.g., "twice a week", "3 times a month"), and ordinal patterns (e.g., "first Monday of every month", "last day of the month").
4. IF the input contains both a specific start date and a recurrence expression (e.g., "starting next Monday, every other day"), THEN THE Parser_V2 SHALL extract the start date into the task's date field and the repeating pattern into the recurrence rule independently.
5. IF Parser_V2 cannot determine the recurrence pattern with a Parse_Confidence of 0.5 or above for the recurrence field, THEN THE Parser_V2 SHALL omit the recurrence field from the structured output and report the recurrence field's Parse_Confidence as below 0.5.

### Requirement 5: Parse-time task deduplication

**User Story:** As a user, I want the parser to warn me if a very similar task already exists, so that I avoid accidental duplicates.

#### Acceptance Criteria

1. WHEN Parser_V2 produces a structured Task, THE Parser_V2 SHALL perform a semantic similarity check against the user's existing incomplete tasks by retrieving the top 5 most similar results from Memory_V2 vector search.
2. IF the semantic similarity score (on a 0.0 to 1.0 scale) between the parsed task and any retrieved existing task equals or exceeds 0.85, THEN THE Parser_V2 SHALL flag the parse result as a potential duplicate by including a duplicate indicator, the similarity score, and the matching task's identifier for each task that meets the threshold.
3. THE Parser_V2 SHALL NOT automatically discard or reject a flagged duplicate; the user retains the choice to create or discard the task.
4. IF Memory_V2 vector search is unavailable or returns an error during the deduplication check, THEN THE Parser_V2 SHALL skip the deduplication step and proceed with the parse result without a duplicate flag.

---

## Section B — AI Chat V2

### Requirement 6: Rich response rendering

**User Story:** As a user, I want the AI chat to display responses with code blocks, math equations, and formatted text, so that complex answers are readable and useful.

#### Acceptance Criteria

1. WHEN Chat_V2 returns a response containing markdown, THE Chat_V2 client SHALL render the full markdown including headings, bold, italic, ordered and unordered lists, tappable links, and blockquotes.
2. WHEN Chat_V2 returns a response containing fenced code blocks with a specified language tag, THE Chat_V2 client SHALL render them with syntax highlighting appropriate to that language.
3. IF a fenced code block has no language tag or specifies an unrecognized language, THEN THE Chat_V2 client SHALL render the block as plain monospaced text without syntax highlighting.
4. WHEN Chat_V2 returns a response containing LaTeX math expressions (inline delimited by single dollar signs or block delimited by double dollar signs), THE Chat_V2 client SHALL render them as formatted mathematical equations.
5. IF a LaTeX math expression cannot be parsed or rendered, THEN THE Chat_V2 client SHALL display the raw LaTeX source text inline so that the content remains readable.
6. WHERE the Active_Model's Model_Registry entry indicates reasoning transparency support, THE Chat_V2 client SHALL display a collapsible Thinking_Block showing the model's reasoning process, defaulting to the collapsed state.
7. WHEN Chat_V2 returns a response containing Web_Citations, THE Chat_V2 client SHALL display each citation with its title, tappable URL, and snippet (when provided by the response), with snippet text truncated to a maximum of 200 characters.

### Requirement 7: Tool calls with explicit user permissions

**User Story:** As a user, I want the AI to be able to check my tasks and help manage them through chat, but only after I explicitly grant permission for each action, so that I stay in control.

#### Acceptance Criteria

1. WHEN Chat_V2 determines that a Tool_Call is needed to fulfill the user's request, THE Chat_V2 SHALL present a Tool_Permission prompt to the user that includes: the Tool_Call category ("read" or "write"), a description of the specific action to be performed, and the target data to be accessed or modified.
2. WHEN a user grants a Tool_Permission, THE Chat_V2 SHALL execute the Tool_Call and incorporate its result into the response.
3. WHEN a user denies a Tool_Permission, THE Chat_V2 SHALL NOT execute the Tool_Call and SHALL inform the user that the action was not performed.
4. THE Chat_V2 SHALL support read Tool_Calls including: retrieve today's tasks, retrieve tasks by date range, retrieve task by name or description, retrieve friends list, and retrieve social circles.
5. THE Chat_V2 SHALL support write Tool_Calls including: create a new task, update an existing task (title, date, time, priority, tags, description), mark a task as complete, and manage social circles (create, add members).
6. WHEN Chat_V2 determines that multiple Tool_Calls are needed in a single response, THE Chat_V2 SHALL present a separate Tool_Permission prompt for each Tool_Call, and SHALL execute only those Tool_Calls the user has individually granted.
7. IF a granted Tool_Call fails during execution (network error, timeout, or server error), THEN THE Chat_V2 SHALL inform the user that the action failed, indicate the reason for failure, and SHALL NOT alter the user's data if the operation could not complete fully.
8. IF Chat_V2 receives no user response to a Tool_Permission prompt within the current conversation session, THEN THE Chat_V2 SHALL treat the Tool_Permission as neither granted nor denied and SHALL NOT execute the Tool_Call.

### Requirement 8: User data scope and security

**User Story:** As a user, I want the AI to access only my data and never another user's data, so that my information is private and secure.

#### Acceptance Criteria

1. THE Cloud_AI_V2_Path SHALL enforce User_Data_Scope by accepting only the authenticated user's UID for all data queries and mutations, and SHALL scope all Firestore document paths to the authenticated user's UID before executing any read or write operation.
2. THE Cloud_AI_V2_Path SHALL reject any Tool_Call that references data outside the authenticated user's document scope and SHALL NOT execute the Tool_Call or return any data from the referenced scope.
3. THE Chat_V2 SHALL NOT include other users' task data, memory entries, or personal information in any AI model prompt, except publicly shared names within the user's own friends list or circles.
4. IF a Tool_Call attempts to access or modify data for a user other than the authenticated user, THEN THE Cloud_AI_V2_Path SHALL deny the call, return an authorization error indicating the request was denied due to a scope violation, and log the attempt with the trace identifier and requesting UID.
5. IF a request to Cloud_AI_V2_Path arrives without a valid authenticated UID (missing, malformed, or expired authentication token), THEN THE Cloud_AI_V2_Path SHALL reject the request before any data access or AI processing occurs and SHALL return an authentication error.
6. THE Memory_V2 SHALL scope all vector search retrieval and memory entry storage to the authenticated user's UID, such that no memory entries from other users are retrievable regardless of semantic similarity score.

### Requirement 9: Conversation context and history

**User Story:** As a user, I want the AI to remember what we discussed in the current conversation and draw on my longer history, so that I don't have to repeat myself.

#### Acceptance Criteria

1. THE Chat_V2 SHALL maintain conversation context that includes the full current conversation history up to the Active_Model's context window limit, prioritizing recent messages over older messages when the context window must accommodate both conversation history and memory entries.
2. WHEN the conversation history exceeds the context window limit, THE Chat_V2 SHALL apply a summarization strategy that compresses older messages into a condensed context block preserving user-stated facts, decisions made, and task-related outcomes from the summarized portion.
3. THE Chat_V2 SHALL retrieve relevant Long_Term_Memory entries from Memory_V2 that exceed the Memory_Confidence threshold and include a maximum of 20 entries as context for each response generation.
4. THE Chat_V2 SHALL retrieve conversation-specific Short_Term_Memory entries associated with the active conversation that exceed the Memory_Confidence threshold, up to a maximum of 10 entries.
5. IF Memory_V2 retrieval fails during response generation (network error, timeout, or service unavailability), THEN THE Chat_V2 SHALL proceed with response generation using only the available conversation history and SHALL NOT return an error to the user.

---

## Section C — Unified Memory System (Memory_V2)

### Requirement 10: Semantic memory storage and retrieval (RAG)

**User Story:** As a user, I want the AI to remember things about me across conversations (my preferences, goals, relationships), so that it becomes more helpful over time.

#### Acceptance Criteria

1. THE Memory_V2 SHALL store memory entries as vector embeddings in Firestore Vector Search, enabling semantic similarity retrieval, up to a maximum of 1000 memory entries per user.
2. WHEN Memory_V2 retrieves memory entries for a context, THE Memory_V2 SHALL rank entries by semantic similarity to the current query and return the top 10 entries ordered by descending relevance.
3. THE Memory_V2 SHALL assign a Memory_Confidence score (0.0 to 1.0) to each retrieved entry indicating its relevance to the current context.
4. THE Memory_V2 SHALL filter retrieved entries by a minimum Memory_Confidence threshold of 0.3, excluding entries below the threshold from context.
5. IF Memory_V2 retrieval fails due to network error, timeout exceeding 5 seconds, or index unavailability, THEN THE Memory_V2 SHALL return an empty result set and allow the requesting component (Parser_V2 or Chat_V2) to proceed without memory context.

### Requirement 11: Memory categorization and relationships

**User Story:** As a user, I want the AI to organize what it knows about me into categories and link related memories together, so that it can surface the right context at the right time.

#### Acceptance Criteria

1. WHEN Memory_V2 stores a new memory entry, THE Memory_V2 SHALL classify the entry into exactly one Memory_Category: identity, preference, goal, interest, context, relationship, habit, schedule, project, important_date, location, or productivity_pattern.
2. IF Memory_V2 cannot classify a memory entry into a single Memory_Category with sufficient certainty, THEN THE Memory_V2 SHALL assign the entry to the "context" category as the default fallback.
3. WHEN Memory_V2 detects shared entities (persons, projects, locations, or dates) across two or more memory entries during storage or retrieval, THE Memory_V2 SHALL create a relationship link between those entries.
4. THE Memory_V2 SHALL support a maximum of 20 relationship links per memory entry.
5. WHEN Memory_V2 retrieves a memory entry, THE Memory_V2 SHALL also retrieve up to 5 directly linked memory entries that exceed the minimum Memory_Confidence threshold, ordered by Memory_Confidence descending.

### Requirement 12: Short-term and long-term memory distinction

**User Story:** As a user, I want the AI to distinguish between things relevant right now and things relevant long-term, so that recent context is prioritized without losing older knowledge.

#### Acceptance Criteria

1. THE Memory_V2 SHALL classify memory entries as either Short_Term_Memory or Long_Term_Memory based on scope and persistence.
2. THE Memory_V2 SHALL create Short_Term_Memory entries for conversation-specific context that is relevant only to the current session or day.
3. THE Memory_V2 SHALL elevate Short_Term_Memory entries to Long_Term_Memory when they are referenced across multiple conversations or explicitly marked as important.
4. THE Memory_V2 SHALL expire Short_Term_Memory entries that have not been accessed or elevated within a configurable retention period.

### Requirement 13: Memory extraction with reliability

**User Story:** As a user, I want the AI to reliably extract and store new memories from our conversations, so that useful information is never silently lost.

#### Acceptance Criteria

1. WHEN a conversation message contains information worthy of memory storage (identity facts, preferences, goals, relationships, schedules), THE Memory_V2 SHALL extract and store it as a new memory entry.
2. IF memory extraction fails (network error, timeout, or processing failure), THEN THE Memory_V2 SHALL retry extraction with exponential backoff up to a maximum of three attempts.
3. THE Memory_V2 SHALL NOT skip memory extraction for messages regardless of message length.
4. THE Memory_V2 SHALL detect and merge duplicate memory entries using semantic similarity rather than string normalization alone.

### Requirement 14: Shared memory between parser and chat

**User Story:** As a user, I want the parser and chat to share the same memory, so that context learned in one feature benefits the other.

#### Acceptance Criteria

1. THE Memory_V2 SHALL be the single memory source accessed by both Parser_V2 and Chat_V2.
2. WHEN Chat_V2 stores a new memory entry, THE Parser_V2 SHALL be able to retrieve that entry on subsequent parse requests.
3. WHEN Parser_V2 processes a task that yields new contextual information, THE Parser_V2 SHALL store that information in Memory_V2 for retrieval by Chat_V2.
4. THE Memory_V2 SHALL store the user's timezone as a Long_Term_Memory entry in the "location" category, accessible by both Parser_V2 and Chat_V2.

---

## Section D — Admin-Configurable Model Registry

### Requirement 15: Dynamic model configuration without app updates

**User Story:** As an admin, I want to add, remove, or change AI models from the admin panel without shipping an Android app update, so that I can respond quickly to model availability and pricing changes.

#### Acceptance Criteria

1. THE Admin_Panel SHALL provide an AI V2 configuration section where admins can manage the Model_Registry.
2. THE Model_Registry SHALL store each model entry with: provider name (1–100 characters), model identifier (1–200 characters), display name (1–60 characters), enabled/disabled status, and cost_per_million_tokens rate (a positive numeric value greater than zero), supporting a maximum of 50 model entries.
3. WHEN an admin adds, removes, or modifies a model in the Model_Registry, THE Cloud_AI_V2_Path SHALL reflect the change on the next client request without requiring an Android app update.
4. THE Admin_Panel SHALL allow admins to set exactly one enabled model as the default Active_Model, which is used when a user has not explicitly selected a model.
5. IF an admin attempts to disable or remove the model currently set as the default Active_Model, THEN THE Admin_Panel SHALL require the admin to designate another enabled model as the new default before completing the operation.
6. IF a user's explicitly selected model is disabled or removed from the Model_Registry, THEN THE Cloud_AI_V2_Path SHALL fall back to the default Active_Model for that user's subsequent requests.
7. IF all models in the Model_Registry are disabled or removed, THEN THE Cloud_AI_V2_Path SHALL reject AI V2 requests and return an error response indicating that no models are currently available.
8. THE Cloud_AI_V2_Path SHALL use a standard response format for all models, such that the Android app handles any model's output uniformly without model-specific client logic.

### Requirement 16: Model cost rate management

**User Story:** As an admin, I want to configure and update cost-per-token rates for each model, so that the token economy stays accurate as provider pricing changes.

#### Acceptance Criteria

1. THE Admin_Panel SHALL allow admins to set and update the cost_per_million_tokens rate for each model in the Model_Registry.
2. WHEN an admin updates a model's cost rate, THE Token_Economy SHALL apply the new rate to all subsequent requests using that model.
3. THE Token_Economy SHALL normalize token consumption across models, such that a user's Daily_Token_Budget is consumed at different rates depending on the Active_Model's cost.

---

## Section E — Token Economy and Subscription Tiers

### Requirement 17: Daily token budget enforcement

**User Story:** As a user, I want to know my daily AI usage limit and be prevented from exceeding it, so that my usage stays within my subscription plan.

#### Acceptance Criteria

1. THE Token_Economy SHALL track each user's cumulative normalized token consumption for the current calendar day, where normalization accounts for the Active_Model's cost_per_million_tokens rate.
2. WHEN a user's cumulative daily token consumption reaches the Daily_Token_Budget, THE Token_Economy SHALL reject further AI V2 requests and SHALL return a response indicating that the daily limit has been reached, including the budget amount and the reset time.
3. WHEN a new calendar day begins in the user's configured timezone, THE Token_Economy SHALL reset that user's cumulative daily consumption to zero.
4. IF a single request's estimated normalized token cost would cause the user's cumulative consumption to exceed the Daily_Token_Budget, THEN THE Token_Economy SHALL reject the request before sending it to the AI model and SHALL return a response indicating insufficient remaining budget.
5. THE Token_Economy SHALL provide the user's current cumulative consumption and remaining Daily_Token_Budget allowance when requested, expressed in normalized tokens.
6. IF the Token_Economy is unable to verify a user's cumulative consumption at the time of a request, THEN THE Token_Economy SHALL reject the request and SHALL return a response indicating a temporary service issue.

### Requirement 18: Subscription tier quotas

**User Story:** As an admin, I want to assign different daily usage quotas to different subscription tiers, so that pricing tiers offer proportional AI access.

#### Acceptance Criteria

1. THE Token_Economy SHALL define a Daily_Token_Budget for each Subscription_Tier: Pro_Student, Pro_Youth, and Pro_Standard.
2. THE Admin_Panel SHALL allow admins to view and adjust the Daily_Token_Budget for all three Subscription_Tiers.
3. WHEN an admin adjusts a Subscription_Tier's Daily_Token_Budget, THE Token_Economy SHALL apply the new budget to all users of that tier starting from the next budget reset.
4. THE Token_Economy SHALL determine the user's applicable Daily_Token_Budget from the user's current Subscription_Tier at the time of each request.

---

## Section F — Observability and Architecture

### Requirement 19: Server-side prompt management

**User Story:** As an engineer, I want all AI prompts managed server-side in a single prompt factory, so that prompts can be updated without app releases and there is no drift between components.

#### Acceptance Criteria

1. THE Prompt_Factory_V2 SHALL reside entirely within the Cloud_AI_V2_Path (Cloud Functions), with no prompt templates or prompt logic in the Android client.
2. THE Prompt_Factory_V2 SHALL serve prompts for both Parser_V2 and Chat_V2 from the same module, eliminating separate prompt factories.
3. WHEN an engineer updates a prompt template in Prompt_Factory_V2, THE Cloud_AI_V2_Path SHALL use the updated prompt on the next request without requiring an Android app update.

### Requirement 20: Observable AI processing

**User Story:** As an engineer, I want all AI processing steps logged server-side with trace identifiers, so that I can debug failures and measure quality.

#### Acceptance Criteria

1. THE Cloud_AI_V2_Path SHALL log each AI request with a unique trace identifier (UUID v4), the requesting user's UID, the Active_Model used, token counts (input and output), request latency in milliseconds, and the request outcome (success, error type).
2. WHEN a request uses Memory_V2, THE Cloud_AI_V2_Path SHALL log memory retrieval results including the number of entries retrieved and the top Memory_Confidence score, associated with the request's trace identifier.
3. IF an AI request fails, THEN THE Cloud_AI_V2_Path SHALL log the failure reason, the trace identifier, and the elapsed time in milliseconds before failure.
4. THE Cloud_AI_V2_Path SHALL NOT log the full content of user messages or memory entries in production logs, limiting logged content to metadata and identifiers.
5. WHEN the Cloud_AI_V2_Path completes processing an AI request, THE Cloud_AI_V2_Path SHALL return the trace identifier in the response to the client, enabling correlation between client-observed behavior and server-side logs.

### Requirement 21: Structured output enforcement

**User Story:** As an engineer, I want all AI model responses validated against a schema, so that downstream code never handles malformed output.

#### Acceptance Criteria

1. THE Cloud_AI_V2_Path SHALL define a JSON Schema for each AI V2 operation (task parsing, chat response, tool call result).
2. WHEN an AI model returns a response, THE Cloud_AI_V2_Path SHALL validate the response against the applicable JSON Schema before forwarding it to the client.
3. IF a model response fails schema validation, THEN THE Cloud_AI_V2_Path SHALL retry the request once with the same input and context.
4. IF the retry also fails schema validation, THEN THE Cloud_AI_V2_Path SHALL return a structured error response to the client indicating a generation failure.

### Requirement 22: Thread-safe conversation handling

**User Story:** As a user sending rapid messages, I want the AI to handle my messages without corrupting conversation state, so that responses stay coherent.

#### Acceptance Criteria

1. THE Cloud_AI_V2_Path SHALL process messages within a single conversation sequentially, such that no two messages from the same conversation are processed concurrently.
2. THE Cloud_AI_V2_Path SHALL support multiple concurrent conversations from the same user without interference between conversations.
3. IF a message arrives while a prior message in the same conversation is still being processed, THEN THE Cloud_AI_V2_Path SHALL queue the incoming message and process it after the prior message completes.

---

## Section G — Proactive and Special Features

### Requirement 23: Daily briefing and productivity insights

**User Story:** As a user, I want the AI to proactively show me a morning summary and productivity insights, so that I start my day informed and can improve my habits.

#### Acceptance Criteria

1. WHEN the user opens Chat_V2 for the first time on a calendar day (determined by the user's timezone) and the current time is between 04:00 and 12:00 local time, THE Chat_V2 SHALL automatically generate and display a Daily_Briefing containing the user's scheduled tasks for that day, deadlines occurring within the next 7 calendar days, and Memory_V2 context entries exceeding the Memory_Confidence threshold relevant to those tasks.
2. IF the user has no scheduled tasks or upcoming deadlines within the next 7 days, THEN THE Chat_V2 SHALL display a Daily_Briefing indicating no pending items rather than omitting the briefing entirely.
3. THE Chat_V2 SHALL display the Daily_Briefing at most once per calendar day per user; subsequent chat opens on the same calendar day SHALL NOT re-trigger the briefing.
4. WHEN Memory_V2 contains at least 14 days of task completion history for the user, THE Chat_V2 SHALL generate Proactive_Insights based on the user's task completion patterns stored in Memory_V2 (e.g., optimal productivity times, completion rate trends).
5. WHEN the user discusses scheduling or planning a task in Chat_V2 and Proactive_Insights are available, THE Chat_V2 SHALL surface relevant Proactive_Insights inline (e.g., suggesting a better time to schedule based on past completion patterns).
6. THE Daily_Briefing and Proactive_Insights SHALL be based solely on the user's own data within User_Data_Scope.
7. IF Memory_V2 contains fewer than 14 days of task completion history, THEN THE Chat_V2 SHALL NOT generate Proactive_Insights and SHALL NOT indicate the absence of insights to the user.

### Requirement 24: Task decomposition and time estimation

**User Story:** As a user, I want the AI to suggest breaking complex tasks into subtasks and learn how long tasks take me, so that my planning becomes more realistic over time.

#### Acceptance Criteria

1. WHEN a user creates or discusses a complex task in Chat_V2, THE Chat_V2 SHALL offer to decompose the task into smaller subtasks with suggested titles.
2. WHEN a user accepts a task decomposition suggestion, THE Chat_V2 SHALL create the subtasks via write Tool_Calls (subject to Tool_Permission).
3. THE Memory_V2 SHALL store task completion duration data (time from creation to completion by task type/tags) as Long_Term_Memory entries in the "productivity_pattern" category.
4. WHERE sufficient historical data exists for a task type, THE Chat_V2 SHALL provide a time estimate based on the user's past completion patterns.

### Requirement 25: Natural conversation to task creation

**User Story:** As a user, I want to mention tasks naturally in conversation and have the AI offer to create them, so that I never have to switch to a separate "add task" flow.

#### Acceptance Criteria

1. WHEN a user mentions a potential task during a Chat_V2 conversation (e.g., "I need to buy groceries tomorrow"), THE Chat_V2 SHALL detect the task intent and offer to create it.
2. WHEN Chat_V2 detects a task intent, THE Chat_V2 SHALL present the extracted task details (title, date, time) to the user for confirmation before creating.
3. WHEN a user confirms task creation from chat, THE Chat_V2 SHALL create the task via a write Tool_Call (subject to Tool_Permission).
4. THE Chat_V2 SHALL NOT automatically create tasks without user confirmation.

---

## Section H — Coexistence and Migration

### Requirement 26: V1 and V2 coexistence

**User Story:** As an app owner, I want AI V2 to operate completely separately from AI V1, so that old app versions continue working and V2 can be developed independently.

#### Acceptance Criteria

1. THE AI_V2_Ecosystem SHALL use separate Cloud Function endpoints from the existing AI V1 system, such that V1 and V2 operate on independent code paths with no shared runtime logic or shared module imports between V1 and V2 function entry points.
2. THE AI_V2_Ecosystem SHALL NOT modify, depend on, or interfere with the existing V1 Cloud Functions (aiParseTask, aiChat, aiChatContinue, aiPlanDay).
3. WHEN an older app version that predates AI V2 makes an AI request, THE V1 Cloud Functions SHALL continue to handle the request with identical behavior, unaffected by any V2 deployment or configuration change.
4. THE AI_V2_Ecosystem SHALL use its own Firestore collections for Memory_V2 data, separate from any V1 memory storage, and SHALL NOT read from or write to Firestore collections used by V1 AI functions.
5. THE AI_V2_Ecosystem SHALL maintain its own admin configuration (model selection, kill-switch, token rates) in the Model_Registry, independent of V1's AI configuration, such that changes to V2 configuration do not alter V1 behavior and vice versa.
6. IF a V2 Cloud Function encounters a failure, THEN THE V1 Cloud Functions SHALL remain operational and unaffected by the V2 failure.
