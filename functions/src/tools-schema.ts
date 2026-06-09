/**
 * Tool definitions for AI function calling — ported from TaskTools.kt.
 * Used by all providers.
 */
import { PREDEFINED_TAGS } from "./config";

export interface ToolDef {
  name: string;
  description: string;
  parameters: {
    type: "object";
    properties: Record<string, { type: string; description: string; enum?: string[] }>;
    required: string[];
  };
}

export const TASK_TOOLS: ToolDef[] = [
  {
    name: "add_task",
    description: "Add a new task. If the user specifies a date, use date parameter (YYYY-MM-DD). If they specify a time, use deadline_time (HH:mm 24h). ALWAYS provide at least 1 tag. Set 'rollover' per RULE 5B.",
    parameters: {
      type: "object",
      properties: {
        title:         { type: "string", description: "Short but self-explanatory task title. Remove temporal/urgency words." },
        date:          { type: "string", description: "Date in YYYY-MM-DD format. Omit for today." },
        deadline_time: { type: "string", description: "Deadline time in HH:mm 24-hour format." },
        priority:      { type: "string", description: "Priority: 0=None, 1=Low, 2=Medium, 3=High." },
        tags:          { type: "string", description: `REQUIRED. 1-4 comma-separated tags from: ${PREDEFINED_TAGS.join(", ")}` },
        recurrence:    { type: "string", description: "Recurrence pattern: daily, weekly, monthly, yearly." },
        rollover:      { type: "string", description: "true/false. Task stays sticky day-to-day until completed." },
        subtasks:      { type: "string", description: "Comma-separated subtask items when user lists 2+ items." },
        description:   { type: "string", description: "Brief 1-2 sentence description. Use same language as user." },
      },
      required: ["title", "tags"],
    },
  },
  {
    name: "modify_task",
    description: "Modify an existing task. Finds closest matching task.",
    parameters: {
      type: "object",
      properties: {
        target_title: { type: "string", description: "Current title of the task to modify." },
        new_title:    { type: "string", description: "New title (if changing)." },
        new_date:     { type: "string", description: "New date in YYYY-MM-DD." },
        new_time:     { type: "string", description: "New deadline time in HH:mm." },
        new_priority: { type: "string", description: "New priority: 0-3." },
        new_tags:     { type: "string", description: "New tags." },
      },
      required: ["target_title"],
    },
  },
  {
    name: "delete_task",
    description: "Delete a task by title.",
    parameters: {
      type: "object",
      properties: {
        title: { type: "string", description: "Task title to delete." },
      },
      required: ["title"],
    },
  },
  {
    name: "complete_task",
    description: "Mark a task as completed.",
    parameters: {
      type: "object",
      properties: {
        title: { type: "string", description: "Task title to mark complete." },
      },
      required: ["title"],
    },
  },
  {
    name: "list_tasks",
    description: "List all tasks for a date. Defaults to today.",
    parameters: {
      type: "object",
      properties: {
        date: { type: "string", description: "Date in YYYY-MM-DD. Omit for today." },
      },
      required: [],
    },
  },
  {
    name: "set_reminder",
    description: "Add a task with an alarm reminder.",
    parameters: {
      type: "object",
      properties: {
        title:       { type: "string", description: "The task/reminder title." },
        time:        { type: "string", description: "Time in HH:mm 24-hour format." },
        date:        { type: "string", description: "Date in YYYY-MM-DD. Omit for today." },
        subtasks:    { type: "string", description: "Comma-separated subtasks." },
        description: { type: "string", description: "Brief description." },
      },
      required: ["title", "time"],
    },
  },
];

export const TASK_TOOLS_V2: ToolDef[] = TASK_TOOLS.map(t => {
  if (t.name === "add_task") {
    return {
      ...t,
      parameters: {
        ...t.parameters,
        properties: {
          ...t.parameters.properties,
          is_habit: { type: "string", description: "true if user wants to build a habit/streak. false otherwise." },
          is_event: { type: "string", description: "true if this is a time-bound occasion/event to attend, not actionable work. false otherwise." },
          event_icon: { type: "string", description: "If is_event=true, an emoji representing the event. Otherwise omit." },
          event_color: { type: "string", description: "If is_event=true, a hex color code representing the event's vibe. Otherwise omit." },
          recurrence_interval: { type: "string", description: "Number of units between recurrences (e.g. for 'every 2 days' or 'every after one day', set recurrence=daily and recurrence_interval=2). Default is 1." },
          recurrence_days: { type: "string", description: "For weekly recurrence only. Comma-separated day numbers (Sunday=1, Monday=2, Tuesday=3, Wednesday=4, Thursday=5, Friday=6, Saturday=7). E.g. '2,4' for Mon and Wed. If user says 'twice a week' without specifying days, guess 2 logical days separated by 3-4 days (e.g. Monday and Thursday: '2,5')." },
        }
      }
    };
  }
  return t;
});

/** Convert our tool format to Gemini's function declaration format */
export function toGeminiFunctionDeclarations(versionCode: number = 0) {
  const tools = versionCode >= 8 ? TASK_TOOLS_V2 : TASK_TOOLS;
  return tools.map(t => ({
    name: t.name,
    description: t.description,
    parameters: {
      type: "OBJECT" as const,
      properties: Object.fromEntries(
        Object.entries(t.parameters.properties).map(([key, val]) => [
          key,
          { type: "STRING" as const, description: val.description },
        ])
      ),
      required: t.parameters.required,
    },
  }));
}

/**
 * Detect whether task tools are likely useful for this chat turn.
 * The chat surface should behave like a normal AI by default, so this stays
 * conservative and only enables tool schemas for task/reminder management.
 */
export function shouldUseTaskTools(text: string): boolean {
  const lower = text.toLowerCase().trim();
  if (!lower) return false;

  const conversationalPatterns = [
    "what is", "what's", "who is", "how many", "tell me",
    "explain", "describe", "define", "meaning of",
    "kya hai", "kaun hai", "kitne",
  ];
  const taskDomainWords = [
    "task", "tasks", "todo", "to-do", "reminder", "reminders",
    "schedule", "deadline", "due", "focus", "plate", "done",
  ];
  if (
    conversationalPatterns.some(p => lower.includes(p)) &&
    !taskDomainWords.some(w => lower.includes(w))
  ) {
    return false;
  }

  const directSignals = [
    "add task", "create task", "new task", "make a task",
    "add reminder", "set reminder", "remind me",
    "delete task", "remove task", "cancel task",
    "mark done", "mark complete", "complete task",
    "move my", "shift my", "reschedule", "change the time",
    "list my tasks", "show my tasks", "today's tasks", "todays tasks",
    "my todo", "my to-do", "what's on my plate", "what is on my plate",
    "what should i focus", "plan my day", "got done", "completed this week",
    "task banana", "task banao", "kaam add", "yaad dilana",
    "hata do", "delete karo", "complete kar", "done mark",
    "shift karo", "badal do", "kal shift", "aaj ke tasks",
  ];
  if (directSignals.some(s => lower.includes(s))) return true;

  if (/\b(tasks?|todos?|to-dos?|reminders?)\b/.test(lower)) return true;

  const dateOrTime =
    /\b(today|tomorrow|tonight|morning|afternoon|evening|night|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b/.test(lower) ||
    /\b\d{1,2}(:\d{2})?\s?(am|pm)\b/.test(lower) ||
    /\b(aaj|kal|parso|subah|dopahar|shaam|raat)\b/.test(lower);

  const taskVerb =
    /\b(buy|call|email|finish|submit|pay|pack|clean|visit|meet|gym|doctor|hospital|appointment|meeting)\b/.test(lower) ||
    /\b(karna hai|jana hai|jaana hai|laana hai|lana hai|banana hai|bhejna hai)\b/.test(lower);

  return dateOrTime && taskVerb;
}

/**
 * Backward-compatible helper: true means no tools needed.
 */
export function isSimpleQuery(text: string): boolean {
  return !shouldUseTaskTools(text);
}
