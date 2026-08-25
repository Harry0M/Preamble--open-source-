/**
 * Dedicated AI Chat Prompt Builder — Isolated from Task Parser.
 * Handles conversational tone, long-term memory, advice, and task management.
 */
import { PREDEFINED_TAGS } from "./config";

export interface TaskSnapshot {
  title: string;
  createdDate: string;
  deadlineTime?: string;
  priority: number;
  isCompleted: boolean;
  isSyncing?: boolean;
}

export interface MemoryFact {
  key: string;
  value: string;
  category: string;
}

export function buildChatSystemPrompt(opts: {
  tasks?: TaskSnapshot[];
  memoryFacts?: MemoryFact[];
  userName?: string;
  userRole?: string;
  userGoals?: string;
  conciseMode?: boolean;
  taskContextBlock?: string;
}): string {
  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const time = now.toTimeString().slice(0, 5);
  const sb: string[] = [];

  sb.push(`You are Preamble AI, a smart personal assistant and productivity companion. Today is ${today}, current time is ${time}.`);
  sb.push("");

  // Response Style Guidelines
  sb.push("RESPONSE STYLE (MANDATORY — applies to every response):");
  sb.push("- Be direct, friendly, and helpful. Avoid robotic filler like 'Sure!', 'Of course!', 'As an AI language model'.");
  sb.push("- When giving advice or answering questions: be clear, insightful, and concise (max 3-4 paragraphs unless detail is requested).");
  sb.push("- When executing task actions: confirm in one short phrase (e.g., 'Added to your schedule for Thursday 9am.').");
  sb.push("");
  sb.push("FORMATTING (use markdown — the app renders it):");
  sb.push("  - Lists: use '- item' (one dash, one space). Use bullets whenever you have 2+ items.");
  sb.push("  - Sub-points: indent 2 spaces ('  - sub-item').");
  sb.push("  - Section headers: '## Header' for major sections, '### Sub-header' for nested.");
  sb.push("  - Emphasis: **bold** for key terms, `code` for IDs / commands.");
  sb.push("  - Numbered steps: '1. step' only when order matters.");
  sb.push("  - Always blank line before/after a list or header.");
  sb.push("");

  // Long-Term Memory Section
  if (opts.memoryFacts && opts.memoryFacts.length > 0) {
    sb.push("USER CONTEXT (long-term memory — use naturally to personalize your response):");
    if (opts.userName) sb.push(`  - Name: ${opts.userName}`);
    if (opts.userRole) sb.push(`  - Role: ${opts.userRole}`);
    if (opts.userGoals) sb.push(`  - Primary goals: ${opts.userGoals}`);
    const grouped: Record<string, MemoryFact[]> = {};
    for (const f of opts.memoryFacts) {
      if (!grouped[f.category]) grouped[f.category] = [];
      grouped[f.category].push(f);
    }
    const order = ["identity", "relationship", "preference", "goal", "interest", "context"];
    const allCats = [...new Set([...order, ...Object.keys(grouped)])];
    for (const cat of allCats) {
      const rows = grouped[cat];
      if (!rows || rows.length === 0) continue;
      sb.push(`  [${cat.toUpperCase()}]`);
      for (const m of rows) {
        sb.push(`    - ${m.key}: ${m.value}`);
      }
    }
    sb.push("");
  }

  if (opts.taskContextBlock) {
    sb.push(opts.taskContextBlock.trimEnd());
    sb.push("");
  }

  // Conversational Rules
  sb.push("RULE 1 — CONVERSATIONAL & TASK INTEGRATION:");
  sb.push("If the user is asking a question, having a conversation, or requesting advice (e.g., 'how can I study better', 'what's on my plate'), respond conversationally.");
  sb.push("If the user asks you to create, modify, delete, or complete tasks, use the appropriate task tools (add_task, modify_task, delete_task, complete_task).");
  sb.push("");

  // Tag Reference
  sb.push("RULE 2 — TASK TAGS:");
  sb.push(`When adding tasks, choose tags from: ${PREDEFINED_TAGS.join(", ")}`);
  sb.push("");

  // Existing Tasks Context
  if (opts.tasks && opts.tasks.length > 0) {
    const realTasks = opts.tasks.filter(t => !t.isSyncing);
    if (realTasks.length > 0) {
      sb.push("EXISTING TASKS:");
      realTasks.slice(0, 20).forEach((t, i) => {
        const timeStr = t.deadlineTime ? ` at ${t.deadlineTime}` : "";
        const priStr = t.priority > 0 ? ` [P${t.priority}]` : "";
        sb.push(`  ${i + 1}. "${t.title}" on ${t.createdDate}${timeStr}${priStr}`);
      });
      sb.push("");
    }
  }

  return sb.join("\n");
}
