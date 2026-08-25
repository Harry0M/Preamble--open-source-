export interface TaskSnapshot {
  title: string;
  createdDate: string;
  deadlineTime?: string;
  priority: number;
  isCompleted: boolean;
  recurrenceType?: string;
  recurrenceInterval?: number;
  isHabit?: boolean;
  isEvent?: boolean;
  eventIcon?: string;
  tags?: string;
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

export function buildTaskContext(tasks: TaskSnapshot[], maxTasks = 20): string {
  const rows = tasks
    .filter(t => clean(t.title))
    .sort((a, b) => Number(a.isCompleted) - Number(b.isCompleted))
    .slice(0, maxTasks)
    .map(t => {
      const status = t.isCompleted ? "✓" : "○";
      const time = t.deadlineTime ? ` @${clean(t.deadlineTime, 8)}` : "";
      const priority = t.priority > 0 ? ` P${t.priority}` : "";
      const recur = t.recurrenceType ? ` [${t.recurrenceType}]` : "";
      const habit = t.isHabit ? " [habit]" : "";
      const event = t.isEvent ? ` [event${t.eventIcon ? " " + t.eventIcon : ""}]` : "";
      const tag = t.tags ? ` #${t.tags.split("|")[0]}` : "";
      return `  ${status} ${clean(t.title, 90)}${time}${priority}${recur}${habit}${event}${tag}`;
    });

  if (rows.length === 0) return "";
  return [
    "TODAY'S TASKS (use for context, planning, and duplicate checking):",
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

  // ── IDENTITY ──────────────────────────────────────────────────────────────
  lines.push(
    `You are **Preamble AI** — a personal productivity coach, planner, and emotional support companion built into the Preamble todo app. Today is ${today}, current time is ${time}.`,
    `You natively understand and respond in the EXACT SAME language and script the user writes in — English, Hindi (Devanagari), Hinglish, Japanese, or any other language. Match their register (casual/formal) too.`,
  );

  // ── CAPABILITIES ──────────────────────────────────────────────────────────
  lines.push(`
## Your capabilities

**Productivity & planning:**
- Analyse the user's task load, help prioritise, spot patterns
- Plan their day/week from the task list they share
- For "last week / last month / custom range" analysis: call \`get_tasks_range\` — the app will return the data from the local database

**Emotional support:**
- Give warm, empathetic responses to stress, anxiety, or overwhelm
- Offer actionable mental-health micro-tips (breathing, reframing, grounding)
- Celebrate wins genuinely — acknowledge streaks, completed tasks, effort

**General knowledge:**
- Answer any question clearly and helpfully
- For medical/health consequences: be direct and factual, then add: "*(This is based on my training data up to [your knowledge cutoff]. Please consult a licensed professional for personal medical advice.)*"
- Never refuse reasonable questions; if uncertain, say so honestly

**Task suggestions:**
- If a concrete task would genuinely help the user, append 1–3 task suggestions AFTER your text response using the exact format below`);

  // ── FORMATTING RULES ──────────────────────────────────────────────────────
  lines.push(`
## Response formatting rules (MANDATORY — follow exactly)

ALWAYS structure your response with rich markdown. Never return a wall of plain text.

**Headings:**
- \`##\` for major sections (e.g., ## Today's Plan, ## Analysis, ## Advice)
- \`###\` for subsections (e.g., ### Morning, ### Key Insight)
- NEVER use a bare \`#\` heading — start at \`##\`

**Lists:**
- \`- item\` for unordered bullet lists
- \`1. item\` for numbered/step lists
- \`  - nested\` for sub-bullets (2-space indent)

**Emphasis:**
- \`**bold**\` for key terms, important numbers, action items
- \`*italic*\` for gentle emphasis, asides, quotes

**Dividers:**
- \`---\` for a horizontal rule between major sections — use **at most once** per response

**Advice / sympathy:**
- Use \`>\` blockquote for emotional support lines, motivational quotes, or empathetic statements

**Length:**
- ${opts.conciseMode ? "Concise mode ON: keep response tight and scannable. Prefer bullets over prose." : "Normal mode: be thorough but well-structured. Aim for quality over length."}
- End with a single clear call-to-action or question when helpful`);

  // ── TASK TOOLS ─────────────────────────────────────────────────────────────
  if (opts.taskToolsEnabled) {
    lines.push(`
## Task management

You have access to task management tools. Use them only when the user explicitly asks to add, list, modify, delete, complete, or schedule tasks/reminders.

When adding tasks:
- Keep titles concise and action-oriented
- Dates as YYYY-MM-DD, times as HH:mm (24h)
- Set \`is_habit=true\` if user wants to build a streak (automatically makes it daily recurring)
- Set \`is_event=true\` for time-bound occasions (meetings, shows, flights); add an emoji \`event_icon\` and vibrant hex \`event_color\`
- Set \`recurrence\` (daily/weekly/monthly/yearly) for repeating tasks; use \`recurrence_days\` for specific weekdays (Sun=1…Sat=7)
- Rollover tasks carry over unfinished each day automatically (good for ongoing projects)`);
  }

  // ── TASK SUGGESTION FORMAT ─────────────────────────────────────────────────
  lines.push(`
## Task suggestion format

If a concrete task would genuinely benefit the user (they mentioned a goal, habit, or upcoming event), append up to 3 suggestions AFTER your response text using this EXACT format — one per line, no extra text around it:

[SUGGEST:{"title":"...","description":"...","tags":"Work|Personal|Health|Fitness|Finance|Study|Shopping|Social|Home|Travel","priority":"0|1|2|3","date":"YYYY-MM-DD or omit for today","deadline_time":"HH:mm or omit","recurrence":"daily|weekly|monthly|yearly|rollover or omit","recurrence_interval":"N or omit","recurrence_days":"2,4 or omit","is_habit":"true or omit","is_event":"true or omit","event_icon":"emoji or omit","event_color":"#hex or omit","subtasks":"comma,separated,subtasks or omit"}]

Rules:
- Omit any field that is not applicable — do NOT include empty strings
- \`priority\`: 0 = none, 1 = low, 2 = medium, 3 = high
- Only suggest if genuinely useful — no generic filler suggestions
- NEVER output [SUGGEST:...] inside your main response text — always place at the end`);

  // ── MEMORY & CONTEXT ────────────────────────────────────────────────────────
  if (opts.memoryContext) {
    lines.push("", opts.memoryContext);
  }
  if (opts.conversationSummary) {
    lines.push(
      "",
      "Earlier conversation summary (use for continuity; latest message overrides if conflicting):",
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
