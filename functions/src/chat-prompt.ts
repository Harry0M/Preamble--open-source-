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
  confidence?: number;
  lastUsedAt?: number;
  source?: string;
}

function clean(value: unknown, max = 120): string {
  return String(value ?? "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

function cleanKey(value: unknown): string {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 64);
}

function queryTokens(query?: string): Set<string> {
  const stop = new Set([
    "the", "and", "for", "you", "are", "what", "who", "when", "where", "why", "how",
    "tell", "about", "mera", "meri", "mere", "mujhe", "main", "mein", "kya", "kaise",
    "hai", "ho", "hu", "hun", "kar", "karo", "please", "pls",
  ]);
  return new Set(
    String(query || "")
      .toLowerCase()
      .split(/[^a-z0-9]+/)
      .filter(t => t.length >= 3 && !stop.has(t)),
  );
}

function scoreMemory(fact: MemoryFact, query?: string): number {
  const key = cleanKey(fact.key);
  const value = clean(fact.value, 160).toLowerCase();
  const category = clean(fact.category || "context", 24).toLowerCase();
  const categoryWeight: Record<string, number> = {
    identity: 5,
    relationship: 4,
    preference: 4,
    goal: 4,
    interest: 2.5,
    context: 2,
  };

  let score = categoryWeight[category] ?? 1.5;
  score += Math.min(Math.max(Number(fact.confidence ?? 0.8), 0), 1) * 2;
  if (fact.source === "onboarding" || fact.source === "user_edit") score += 1.5;

  const ageDays = fact.lastUsedAt
    ? Math.max(0, (Date.now() - Number(fact.lastUsedAt)) / 86_400_000)
    : 30;
  score += Math.max(0, 2 - Math.min(ageDays, 60) / 30);

  const tokens = queryTokens(query);
  if (tokens.size > 0) {
    const haystack = `${key} ${value} ${category}`;
    let hits = 0;
    for (const token of tokens) {
      if (haystack.includes(token)) hits++;
    }
    score += Math.min(5, hits * 2.5);
  }

  // These are broadly useful profile anchors even when the current query is vague.
  if (["name", "role", "language", "timezone", "primary_goals"].includes(key)) score += 2;
  return score;
}

function rankMemories(memoryFacts: MemoryFact[], query?: string, maxFacts = 12): MemoryFact[] {
  const byKey = new Map<string, MemoryFact>();
  for (const fact of memoryFacts) {
    const key = cleanKey(fact.key);
    const value = clean(fact.value, 100);
    if (!key || !value) continue;
    const normalized = { ...fact, key, value };
    const existing = byKey.get(key);
    if (!existing || scoreMemory(normalized, query) > scoreMemory(existing, query)) {
      byKey.set(key, normalized);
    }
  }

  return [...byKey.values()]
    .sort((a, b) => scoreMemory(b, query) - scoreMemory(a, query))
    .slice(0, maxFacts);
}

export function buildMemoryContext(
  memoryFacts: MemoryFact[],
  userName: string,
  query?: string,
  maxFacts = 12,
): string {
  const lines: string[] = [
    "User memory. Use only when relevant, never announce it, and prefer newer facts if anything conflicts.",
  ];

  const categoryOrder = ["identity", "relationship", "preference", "goal", "interest", "context"];
  const grouped = new Map<string, MemoryFact[]>();
  const rankedMemories = rankMemories(memoryFacts, query, maxFacts);
  const hasSavedName = rankedMemories.some(f => cleanKey(f.key) === "name" && clean(f.value, 60));
  const name = clean(userName, 60);
  if (name && !hasSavedName) lines.push(`- account_name: ${name}`);

  for (const fact of rankedMemories) {
    const category = clean(fact.category || "context", 24);
    grouped.set(category, [...(grouped.get(category) || []), fact]);
  }

  for (const category of [...categoryOrder, ...[...grouped.keys()].filter(c => !categoryOrder.includes(c))]) {
    const rows = grouped.get(category);
    if (!rows?.length) continue;
    lines.push(`[${category}]`);
    for (const fact of rows) {
      const key = clean(fact.key, 48);
      const value = clean(fact.value, 100);
      if (!key || !value) continue;
      lines.push(`- ${key}: ${value}`);
    }
  }

  return lines.length > 1 ? lines.join("\n") : "";
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
  conversationSummary?: string;
  appVersionCode?: number;
}): string {
  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const time = now.toTimeString().slice(0, 5);

  const isV2 = (opts.appVersionCode || 0) >= 8;
  const lines: string[] = [];

  if (isV2) {
    lines.push(`You are a helpful AI assistant. Today is ${today}, current time is ${time}. You MUST natively understand and respond in the EXACT SAME LANGUAGE AND SCRIPT that the user uses (e.g., Hinglish, Devanagari Hindi, Chinese, Japanese).`);
  } else {
    lines.push(`You are a helpful AI assistant. Today is ${today}, current time is ${time}.`);
  }

  if (opts.taskToolsEnabled) {
    lines.push(
      "You have access to task management tools. Use them only when the user explicitly asks to add, list, modify, delete, complete, or schedule tasks and reminders.",
      "When adding tasks: keep titles concise, dates as YYYY-MM-DD, times as HH:mm.",
    );
    if (isV2) {
      lines.push(
        "For add_task, infer habits and events. If user wants to build a streak/habit: is_habit=true (NO rollover). If it is an occasion/meeting/show: is_event=true, and provide an emoji event_icon and vibrant hex event_color."
      );
    }
  }

  lines.push(
    'If a concrete task would genuinely help the user (goal/habit/event mentioned), append up to 2 suggestions AFTER your reply—exact format: [SUGGEST:{"title":"…","tags":"Work|Personal|Health|Fitness|Finance|Study|Shopping|Social|Home|Travel","priority":"1|2|3","description":"…"}] — omit otherwise.',
  );

  if (opts.memoryContext) {
    lines.push("", opts.memoryContext);
  }
  if (opts.conversationSummary) {
    lines.push(
      "",
      "Earlier conversation summary. Use for continuity only; latest user message overrides this if there is conflict.",
      clean(opts.conversationSummary, 900),
    );
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
