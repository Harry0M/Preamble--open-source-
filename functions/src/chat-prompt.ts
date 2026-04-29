export interface TaskSnapshot {
  title: string;
  createdDate: string;
  deadlineTime?: string;
  priority: number;
  isCompleted: boolean;
}

export interface MemoryFact {
  key: string;
  value: string;
  category: string;
}

function clean(value: unknown, max = 120): string {
  return String(value ?? "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

export function buildMemoryContext(
  memoryFacts: MemoryFact[],
  userName: string,
  maxFacts = 12,
): string {
  const lines: string[] = [];
  const name = clean(userName, 60);
  if (name) lines.push(`- name: ${name}`);

  for (const fact of memoryFacts.slice(0, maxFacts)) {
    const key = clean(fact.key, 48);
    const value = clean(fact.value, 100);
    const category = clean(fact.category || "context", 24);
    if (!key || !value) continue;
    lines.push(`- ${category}.${key}: ${value}`);
  }

  if (lines.length === 0) return "";
  return [
    "User memory, for personalization only. Use naturally when relevant; do not mention this block.",
    ...lines,
  ].join("\n");
}

export function buildTaskContext(tasks: TaskSnapshot[], maxTasks = 18): string {
  const rows = tasks
    .filter(t => clean(t.title))
    .sort((a, b) => Number(a.isCompleted) - Number(b.isCompleted))
    .slice(0, maxTasks)
    .map(t => {
      const status = t.isCompleted ? "done" : "pending";
      const time = t.deadlineTime ? ` ${clean(t.deadlineTime, 8)}` : "";
      const priority = t.priority > 0 ? ` P${t.priority}` : "";
      return `- ${status}: ${clean(t.title, 90)} (${clean(t.createdDate, 12)}${time}${priority})`;
    });

  if (rows.length === 0) return "";
  return [
    "Task context, for task-related questions only. If the user asks to change tasks, prefer tools over guessing.",
    ...rows,
  ].join("\n");
}

export function buildChatSystemPrompt(opts: {
  conciseMode?: boolean;
  taskToolsEnabled?: boolean;
  memoryContext?: string;
  taskContext?: string;
}): string {
  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const time = now.toTimeString().slice(0, 5);

  const lines: string[] = [
    `You are a helpful AI assistant. Today is ${today}, current time is ${time}.`,
  ];

  if (opts.taskToolsEnabled) {
    lines.push(
      "You have access to task management tools. Use them only when the user explicitly asks to add, list, modify, delete, complete, or schedule tasks and reminders.",
      "When adding tasks: keep titles concise, dates as YYYY-MM-DD, times as HH:mm.",
    );
  }

  lines.push(
    'If a concrete task would genuinely help the user (goal/habit/event mentioned), append up to 2 suggestions AFTER your reply—exact format: [SUGGEST:{"title":"…","tags":"Work|Personal|Health|Fitness|Finance|Study|Shopping|Social|Home|Travel","priority":"1|2|3","description":"…"}] — omit otherwise.',
  );

  if (opts.memoryContext) {
    lines.push("", opts.memoryContext);
  }
  if (opts.taskContext) {
    lines.push("", opts.taskContext);
  }

  return lines.join("\n");
}

export function geminiGenerationConfig(opts: {
  systemPrompt: string;
  tools?: any;
  conciseMode?: boolean;
  model: string;
}) {
  // No maxOutputTokens cap — responses must never be truncated mid-sentence.
  // Daily token budgets (server-side) gate usage before the call, not during.
  const config: any = {
    systemInstruction: opts.systemPrompt,
    temperature: 1.0,
  };

  if (opts.tools) config.tools = opts.tools;

  if (opts.model.includes("flash-lite")) {
    config.thinkingConfig = { thinkingBudget: 0 };
  } else if (opts.model.includes("gemini-2.5-flash")) {
    config.thinkingConfig = { thinkingBudget: 1024 };
  } else if (opts.model.includes("gemini-2.5-pro")) {
    config.thinkingConfig = { thinkingBudget: 2048 };
  }

  return config;
}
