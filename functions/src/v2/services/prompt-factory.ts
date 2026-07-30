/**
 * PromptFactoryV2 — Unified prompt assembly for AI V2 Ecosystem.
 *
 * Single module serving both parse, chat, and briefing prompts. Responsibilities:
 * - System prompt assembly from templates
 * - Conversation history windowing and summarization
 * - Memory context injection (from MemoryEngine)
 * - Language-detection-aware prompt adaptation
 * - Tool schema injection (for chat path)
 * - Force-tool-call mode (for parse path)
 *
 * Requirements: 19.1, 19.2, 19.3, 9.1, 9.2, 9.3, 9.4
 */

import {
  ParseInput,
  ChatInput,
  BriefingInput,
  PromptPayload,
  MemoryEntry,
  ConversationMessage,
  TaskSnapshot,
  ToolDefinition,
  JSONSchema,
} from "../models/types";

// ─── Constants ─────────────────────────────────────────────────────────────────

/**
 * Approximate tokens per message for windowing estimation.
 * Conservative average accounting for role overhead + content.
 */
const AVG_TOKENS_PER_MESSAGE = 150;

/**
 * Fraction of the context window allocated to conversation history.
 * Remaining space is for system prompt, memory, and response.
 */
const HISTORY_WINDOW_RATIO = 0.6;

/**
 * Maximum number of recent messages to keep verbatim before summarizing older ones.
 */
const RECENT_MESSAGES_TO_KEEP = 10;

// ─── Parse Response JSON Schema ────────────────────────────────────────────────

const PARSE_RESPONSE_SCHEMA: JSONSchema = {
  type: "object",
  required: ["title", "confidence"],
  properties: {
    title: { type: "string", maxLength: 200 },
    date: { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" },
    time: { type: "string", pattern: "^([01]\\d|2[0-3]):[0-5]\\d$" },
    priority: { type: "integer", minimum: 1, maximum: 4 },
    tags: {
      type: "array",
      items: { type: "string", maxLength: 50 },
      maxItems: 10,
    },
    recurrence: {
      type: "object",
      properties: {
        recurrenceType: { type: "string", enum: ["daily", "weekly", "monthly", "yearly"] },
        recurrenceInterval: { type: "integer", minimum: 1, maximum: 365 },
        recurrenceDays: { type: "array", items: { type: "integer" } },
        recurrenceEndDate: { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" },
      },
      required: ["recurrenceType", "recurrenceInterval"],
    },
    description: { type: "string", maxLength: 1000 },
    confidence: { type: "number", minimum: 0, maximum: 1 },
    detectedLanguage: { type: "string" },
    duplicates: {
      type: "array",
      items: {
        type: "object",
        properties: {
          taskId: { type: "string" },
          similarity: { type: "number" },
          title: { type: "string" },
        },
      },
    },
  },
};

// ─── Tool Definitions ──────────────────────────────────────────────────────────

const READ_TOOLS: ToolDefinition[] = [
  {
    name: "get_today_tasks",
    description: "Retrieve all tasks scheduled for today",
    category: "read",
    parameters: { type: "object", properties: {} },
  },
  {
    name: "get_tasks_by_date_range",
    description: "Retrieve tasks within a specified date range",
    category: "read",
    parameters: {
      type: "object",
      required: ["startDate", "endDate"],
      properties: {
        startDate: { type: "string", description: "Start date in YYYY-MM-DD format" },
        endDate: { type: "string", description: "End date in YYYY-MM-DD format" },
      },
    },
  },
  {
    name: "get_task_by_name",
    description: "Search for a task by name or description",
    category: "read",
    parameters: {
      type: "object",
      required: ["query"],
      properties: {
        query: { type: "string", description: "Search query for the task" },
      },
    },
  },
  {
    name: "get_friends_list",
    description: "Retrieve the user's friends list",
    category: "read",
    parameters: { type: "object", properties: {} },
  },
  {
    name: "get_social_circles",
    description: "Retrieve the user's social circles and members",
    category: "read",
    parameters: { type: "object", properties: {} },
  },
];

const WRITE_TOOLS: ToolDefinition[] = [
  {
    name: "create_task",
    description: "Create a new task for the user. Can also be used to create subtasks when decomposing a complex task.",
    category: "write",
    parameters: {
      type: "object",
      required: ["title"],
      properties: {
        title: { type: "string", description: "Task title (max 200 characters)" },
        date: { type: "string", description: "Task date in YYYY-MM-DD format" },
        time: { type: "string", description: "Task time in HH:mm format" },
        priority: { type: "integer", description: "Priority 1-4 (1=highest)" },
        tags: { type: "array", items: { type: "string" }, description: "Task tags" },
        description: { type: "string", description: "Task description" },
        parentTaskId: { type: "string", description: "ID of the parent task if this is a subtask (for task decomposition)" },
        isSubtask: { type: "boolean", description: "Whether this task is a subtask of a larger task" },
      },
    },
  },
  {
    name: "update_task",
    description: "Update an existing task's fields",
    category: "write",
    parameters: {
      type: "object",
      required: ["taskId"],
      properties: {
        taskId: { type: "string", description: "ID of the task to update" },
        title: { type: "string", description: "New title" },
        date: { type: "string", description: "New date" },
        time: { type: "string", description: "New time" },
        priority: { type: "integer", description: "New priority" },
        tags: { type: "array", items: { type: "string" }, description: "New tags" },
        description: { type: "string", description: "New description" },
      },
    },
  },
  {
    name: "complete_task",
    description: "Mark a task as complete",
    category: "write",
    parameters: {
      type: "object",
      required: ["taskId"],
      properties: {
        taskId: { type: "string", description: "ID of the task to complete" },
      },
    },
  },
  {
    name: "create_circle",
    description: "Create a new social circle",
    category: "write",
    parameters: {
      type: "object",
      required: ["name"],
      properties: {
        name: { type: "string", description: "Circle name" },
        description: { type: "string", description: "Circle description" },
      },
    },
  },
  {
    name: "add_circle_members",
    description: "Add members to a social circle",
    category: "write",
    parameters: {
      type: "object",
      required: ["circleId", "memberIds"],
      properties: {
        circleId: { type: "string", description: "ID of the circle" },
        memberIds: { type: "array", items: { type: "string" }, description: "IDs of members to add" },
      },
    },
  },
];

// ─── Prompt Templates ──────────────────────────────────────────────────────────

function buildParseSystemPrompt(
  timezone: string,
  currentDateTime: string,
  memories: MemoryEntry[],
  language?: { language: string; script?: string; confidence: number }
): string {
  const parts: string[] = [];

  parts.push(
    `You are Preamble's task parser. Your job is to extract a structured task from the user's natural language input.`
  );
  parts.push(
    `You MUST respond with a JSON object conforming to the task schema. Do NOT include any text outside the JSON.`
  );

  // Temporal context
  parts.push("");
  parts.push("## Temporal Context");
  parts.push(`- Current date and time: ${currentDateTime}`);
  parts.push(`- User's timezone: ${timezone}`);
  parts.push(
    `- Resolve all relative date/time references (e.g., "tomorrow", "next Tuesday", "in 2 hours") relative to the current date and time above.`
  );
  parts.push(
    `- All time values must be valid HH:mm in 00:00–23:59 range.`
  );
  parts.push(
    `- If you cannot confidently resolve a date or time, omit that field entirely.`
  );

  // Language detection context
  if (language) {
    parts.push("");
    parts.push("## Language Context");
    parts.push(`- Detected language: ${language.language}${language.script ? ` (${language.script})` : ""}`);
    parts.push(`- Detection confidence: ${language.confidence}`);
    parts.push(
      `- Preserve the original language and script in the title and description. Do NOT translate or transliterate.`
    );
  } else {
    parts.push("");
    parts.push("## Language Context");
    parts.push(
      `- Detect the language of the input. Preserve the original script in all output fields.`
    );
    parts.push(
      `- Report the detected language in the "detectedLanguage" field.`
    );
  }

  // Memory context
  if (memories.length > 0) {
    parts.push("");
    parts.push("## User Context (from memory)");
    for (const mem of memories) {
      parts.push(`- [${mem.category}] ${mem.text}`);
    }
  }

  // Output format instructions
  parts.push("");
  parts.push("## Output Format");
  parts.push(
    `Respond with a single JSON object. Required fields: "title" (string, max 200 chars), "confidence" (number 0.0-1.0).`
  );
  parts.push(
    `Optional fields: "date" (YYYY-MM-DD), "time" (HH:mm), "priority" (integer 1-4), "tags" (array of strings, max 10), "recurrence" (object), "description" (string, max 1000 chars), "detectedLanguage" (string).`
  );
  parts.push(
    `For recurrence: extract "recurrenceType" (daily/weekly/monthly/yearly), "recurrenceInterval" (1-365), optionally "recurrenceDays" (array of integers) and "recurrenceEndDate" (YYYY-MM-DD). Remove recurrence words from the title.`
  );
  parts.push(
    `If confidence for any temporal/recurrence field is below 0.5, omit that field rather than guessing.`
  );

  return parts.join("\n");
}

function buildChatSystemPrompt(
  memories: MemoryEntry[],
  userTasks: TaskSnapshot[] | undefined,
  mode: "concise" | "detailed",
  summarizedHistory?: string
): string {
  const parts: string[] = [];

  parts.push(
    `You are Preamble AI, a helpful productivity assistant integrated with the user's task management app.`
  );

  // Mode instructions
  if (mode === "concise") {
    parts.push(
      `Respond concisely — short, direct answers. Use bullet points for lists. Avoid unnecessary elaboration.`
    );
  } else {
    parts.push(
      `Respond in detail — provide thorough explanations, examples, and context when helpful.`
    );
  }

  // Tool usage instructions
  parts.push("");
  parts.push("## Available Actions");
  parts.push(
    `You can propose tool calls to read or modify the user's data. Each tool call requires explicit user permission.`
  );
  parts.push(
    `When you need to access or modify user data, propose the appropriate tool call. The user will approve or deny each action.`
  );

  // ─── Task Intent Detection (Requirements 25.1, 25.2, 25.3, 25.4) ────────────
  parts.push("");
  parts.push("## Task Intent Detection");
  parts.push(
    `Pay close attention to the user's messages for task intent. A task intent is detected when the user mentions something they need to do, plan to do, want to accomplish, or should remember to do.`
  );
  parts.push(
    `Examples of task intent signals: "I need to...", "I have to...", "Remind me to...", "I should...", "Don't forget to...", "Let me plan...", mentioning an activity with a date or time, or describing a commitment or obligation.`
  );
  parts.push(
    `When you detect task intent, follow this process:`
  );
  parts.push(
    `1. Extract the task details: title (what needs to be done), date (when, if mentioned), time (specific time, if mentioned), and priority (inferred from urgency cues).`
  );
  parts.push(
    `2. Present the extracted details to the user in a clear, readable format and ask for confirmation. For example: "It sounds like you want to create a task: **[title]** on [date] at [time]. Should I add this to your tasks?"`
  );
  parts.push(
    `3. Only after the user confirms, propose a create_task tool call with the confirmed details.`
  );
  parts.push(
    `4. NEVER automatically create tasks without explicit user confirmation. Always present details first and wait for approval.`
  );

  // ─── Task Decomposition (Requirements 24.1, 24.2) ───────────────────────────
  parts.push("");
  parts.push("## Task Decomposition");
  parts.push(
    `When the user mentions or creates a complex or multi-step task, offer to break it down into smaller, actionable subtasks.`
  );
  parts.push(
    `A task is complex if it involves multiple distinct steps, spans several days, requires different types of work, or has logical sub-components (e.g., "prepare for exam", "organize birthday party", "launch new project").`
  );
  parts.push(
    `When suggesting decomposition:`
  );
  parts.push(
    `1. Identify logical subtasks and present them as a numbered list with suggested titles, dates (if inferable), and priorities.`
  );
  parts.push(
    `2. Ask the user if they'd like you to create these subtasks.`
  );
  parts.push(
    `3. If confirmed, propose multiple create_task tool calls — one for each subtask — using the isSubtask and parentTaskId fields to link them to the parent task.`
  );
  parts.push(
    `4. Keep subtask titles concise and actionable. Each subtask should represent a single, completable action.`
  );

  // Memory context
  if (memories.length > 0) {
    parts.push("");
    parts.push("## What I know about you (from memory)");
    for (const mem of memories) {
      parts.push(`- [${mem.category}] ${mem.text}`);
    }
  }

  // User tasks context
  if (userTasks && userTasks.length > 0) {
    parts.push("");
    parts.push("## Your current tasks");
    for (const task of userTasks) {
      const status = task.completed ? "✓" : "○";
      const dateStr = task.date ? ` (${task.date}${task.time ? " " + task.time : ""})` : "";
      const priorityStr = task.priority ? ` [P${task.priority}]` : "";
      parts.push(`- ${status} ${task.title}${dateStr}${priorityStr}`);
    }
  }

  // Summarized history context
  if (summarizedHistory) {
    parts.push("");
    parts.push("## Earlier conversation summary");
    parts.push(summarizedHistory);
  }

  // Response format
  parts.push("");
  parts.push("## Response Guidelines");
  parts.push(
    `- Use markdown for formatting (headings, bold, lists, code blocks with language tags, LaTeX math with $ or $$).`
  );
  parts.push(
    `- Be helpful, warm, and grounded in the user's actual data and context.`
  );
  parts.push(
    `- When presenting extracted task details for confirmation, use bold text for the task title and clearly show any date/time/priority.`
  );

  return parts.join("\n");
}

function buildBriefingSystemPrompt(
  timezone: string,
  currentDateTime: string,
  memories: MemoryEntry[],
  todayTasks: TaskSnapshot[],
  upcomingDeadlines: TaskSnapshot[],
  taskHistoryDays?: number
): string {
  const parts: string[] = [];

  parts.push(
    `You are Preamble AI generating a daily morning briefing for the user.`
  );
  parts.push(
    `Create a concise, friendly summary of what the user has planned for today and upcoming deadlines.`
  );

  // Temporal context
  parts.push("");
  parts.push("## Current Context");
  parts.push(`- Date and time: ${currentDateTime}`);
  parts.push(`- Timezone: ${timezone}`);

  // Memory context
  if (memories.length > 0) {
    parts.push("");
    parts.push("## User Context (from memory)");
    for (const mem of memories) {
      parts.push(`- [${mem.category}] ${mem.text}`);
    }
  }

  // Today's tasks
  parts.push("");
  parts.push("## Today's Tasks");
  if (todayTasks.length === 0) {
    parts.push("- No tasks scheduled for today.");
  } else {
    for (const task of todayTasks) {
      const status = task.completed ? "✓" : "○";
      const timeStr = task.time ? ` at ${task.time}` : "";
      const priorityStr = task.priority ? ` [P${task.priority}]` : "";
      const tagsStr = task.tags && task.tags.length > 0 ? ` #${task.tags.join(" #")}` : "";
      parts.push(`- ${status} ${task.title}${timeStr}${priorityStr}${tagsStr}`);
    }
  }

  // Upcoming deadlines (within 7 days)
  parts.push("");
  parts.push("## Upcoming Deadlines (next 7 days)");
  if (upcomingDeadlines.length === 0) {
    parts.push("- No upcoming deadlines.");
  } else {
    for (const task of upcomingDeadlines) {
      const dateStr = task.date ? ` — ${task.date}` : "";
      const priorityStr = task.priority ? ` [P${task.priority}]` : "";
      parts.push(`- ${task.title}${dateStr}${priorityStr}`);
    }
  }

  // Proactive insights instruction
  if (taskHistoryDays && taskHistoryDays >= 14) {
    parts.push("");
    parts.push("## Productivity Insights");
    parts.push(
      `The user has ${taskHistoryDays} days of task history. If memory contains productivity patterns, include a brief insight (e.g., best focus times, completion trends).`
    );
  }

  // Output instructions
  parts.push("");
  parts.push("## Output Format");
  parts.push(
    `Generate a friendly, concise daily briefing in markdown. Include:`
  );
  parts.push(`1. A greeting appropriate to the time of day`);
  parts.push(`2. Summary of today's tasks (count, priorities)`);
  parts.push(`3. Any upcoming deadlines within 7 days`);
  parts.push(`4. A brief productivity insight if available`);
  parts.push(`Keep the total response under 500 words.`);

  return parts.join("\n");
}

// ─── History Windowing ─────────────────────────────────────────────────────────

/**
 * Determines if conversation history exceeds the context window and needs windowing.
 */
function needsWindowing(historyLength: number, maxContextWindow: number): boolean {
  const estimatedTokens = historyLength * AVG_TOKENS_PER_MESSAGE;
  const historyBudget = maxContextWindow * HISTORY_WINDOW_RATIO;
  return estimatedTokens > historyBudget;
}

/**
 * Summarizes older messages into a condensed context block.
 * Keeps recent messages verbatim and summarizes older ones.
 */
function summarizeOlderMessages(history: ConversationMessage[]): string {
  const olderMessages = history.slice(0, history.length - RECENT_MESSAGES_TO_KEEP);

  if (olderMessages.length === 0) return "";

  // Build a condensed summary preserving key facts, decisions, and outcomes
  const summaryParts: string[] = [];
  summaryParts.push("Summary of earlier conversation:");

  for (const msg of olderMessages) {
    const roleLabel = msg.role === "user" ? "User" : msg.role === "assistant" ? "Assistant" : "System";
    // Truncate long messages in the summary
    const content = msg.content.length > 200
      ? msg.content.substring(0, 200) + "..."
      : msg.content;
    summaryParts.push(`[${roleLabel}]: ${content}`);
  }

  return summaryParts.join("\n");
}

/**
 * Applies windowing to conversation history.
 * Returns the recent messages and a summary of older ones.
 */
function windowHistory(
  history: ConversationMessage[],
  maxContextWindow: number
): { recentMessages: ConversationMessage[]; summary: string } {
  if (!needsWindowing(history.length, maxContextWindow)) {
    return { recentMessages: history, summary: "" };
  }

  const recentMessages = history.slice(-RECENT_MESSAGES_TO_KEEP);
  const summary = summarizeOlderMessages(history);

  return { recentMessages, summary };
}

// ─── Public API: PromptFactoryV2 ───────────────────────────────────────────────

/**
 * Builds a prompt payload for the Parser_V2 path.
 *
 * Assembles system prompt with language detection, memory context, timezone,
 * current datetime, and structured output mode for task extraction.
 *
 * Requirements: 19.1, 19.2
 */
export function buildParsePrompt(input: ParseInput): PromptPayload {
  const systemPrompt = buildParseSystemPrompt(
    input.timezone,
    input.currentDateTime,
    input.memories,
    input.language
  );

  return {
    systemPrompt,
    messages: [{ role: "user", content: input.text }],
    responseSchema: PARSE_RESPONSE_SCHEMA,
    forceToolCall: false,
    temperature: 0.1,
    maxOutputTokens: 1024,
  };
}

/**
 * Builds a prompt payload for the Chat_V2 path.
 *
 * Assembles system prompt with conversation history (windowed to fit context),
 * memory entries, available tool definitions, user tasks, and active model-specific
 * parameters.
 *
 * Requirements: 19.1, 19.2, 9.1, 9.2, 9.3, 9.4
 */
export function buildChatPrompt(input: ChatInput): PromptPayload {
  // Apply conversation history windowing (Req 9.1, 9.2)
  const { recentMessages, summary } = windowHistory(
    input.history,
    input.activeModel.maxContextWindow
  );

  // Build system prompt with memory and task context (Req 9.3, 9.4)
  const systemPrompt = buildChatSystemPrompt(
    input.memories,
    input.userTasks,
    input.mode,
    summary || undefined
  );

  // Assemble the message list: history + current message
  const messages: ConversationMessage[] = [
    ...recentMessages,
    { role: "user", content: input.message },
  ];

  // Include all available tools (read + write)
  const tools = [...READ_TOOLS, ...WRITE_TOOLS];

  // Adjust temperature based on mode and model capabilities
  const temperature = input.mode === "concise" ? 0.3 : 0.7;

  return {
    systemPrompt,
    messages,
    tools,
    forceToolCall: false,
    temperature,
    maxOutputTokens: input.mode === "concise" ? 2048 : 4096,
  };
}

/**
 * Builds a prompt payload for the Daily Briefing path.
 *
 * Assembles prompt for generating a daily summary including today's tasks,
 * upcoming deadlines, and relevant memory context.
 *
 * Requirements: 19.1, 19.2
 */
export function buildBriefingPrompt(input: BriefingInput): PromptPayload {
  const systemPrompt = buildBriefingSystemPrompt(
    input.timezone,
    input.currentDateTime,
    input.memories,
    input.todayTasks,
    input.upcomingDeadlines,
    input.taskHistoryDays
  );

  return {
    systemPrompt,
    messages: [{ role: "user", content: "Generate my daily briefing." }],
    temperature: 0.5,
    maxOutputTokens: 2048,
  };
}

// ─── Exported Utilities (for testing) ──────────────────────────────────────────

export { needsWindowing, windowHistory, summarizeOlderMessages };
export { PARSE_RESPONSE_SCHEMA, READ_TOOLS, WRITE_TOOLS };
