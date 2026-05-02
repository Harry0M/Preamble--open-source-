type JsonMap = Record<string, any>;

export interface RenderBlocksEnvelope {
  version: 1;
  blocks: JsonMap[];
}

export function buildRenderBlocks(
  content: string,
  toolCalls: Array<{ name: string; args?: Record<string, string> }> = [],
  toolResults: Array<{ name: string; args?: Record<string, string>; result?: string }> = [],
): RenderBlocksEnvelope | null {
  const blocks: JsonMap[] = [];

  for (const result of toolResults) {
    const block = blockForToolResult(result);
    if (block) blocks.push(block);
  }

  for (const call of toolCalls) {
    if (call.name === "suggest_task") {
      const block = suggestionBlock(call);
      if (block) blocks.push(block);
    }
  }

  const suppressText = toolResults.some(r => r.name === "list_tasks");
  if (content.trim() && !suppressText) {
    blocks.push(contentBlock(content.trim()));
  }

  return blocks.length > 0 ? { version: 1, blocks } : null;
}

function blockForToolResult(result: { name: string; args?: Record<string, string>; result?: string }): JsonMap | null {
  const args = result.args || {};
  const output = result.result || "";
  const title =
    args.title ||
    args.target_title ||
    quotedTitle(output) ||
    output.split("\n").find(Boolean) ||
    "";
  if (!title) return null;

  const status =
    /^error:/i.test(output) ? "error" :
    /^no task/i.test(output) ? "warning" :
    "success";

  switch (result.name) {
    case "add_task":
      return {
        type: "task_created",
        title,
        subtitle: buildTaskSubtitle(args, "Added"),
        status,
        items: metadataItems(args),
      };
    case "set_reminder":
      return {
        type: "reminder_set",
        title,
        subtitle: buildTaskSubtitle(args, "Reminder set"),
        status,
        items: metadataItems(args),
      };
    case "modify_task":
      return {
        type: "task_updated",
        title,
        subtitle: "Task updated",
        status,
        items: metadataItems(args),
      };
    case "complete_task":
      return {
        type: "task_completed",
        title,
        subtitle: output || "Marked complete",
        status,
      };
    case "delete_task":
      return {
        type: "task_deleted",
        title,
        subtitle: output || "Task deleted",
        status,
      };
    case "list_tasks":
      return taskListBlock(output);
    default:
      return {
        type: "tool_result",
        title: toolTitle(result.name),
        subtitle: output.slice(0, 160),
        status,
      };
  }
}

function contentBlock(text: string): JsonMap {
  if (looksLikeCode(text)) {
    return {
      type: "code",
      title: codeTitle(text),
      markdown: text,
    };
  }

  return { type: "answer", markdown: text };
}

function looksLikeCode(text: string): boolean {
  if (/```[A-Za-z0-9_+\-.#]*\s*\n[\s\S]*?```/.test(text)) return true;
  const lines = text.split("\n").filter(l => l.trim());
  const codeLines = lines.filter(raw => {
    const line = raw.trim();
    return /^(def|class|import|from|print\(|if __name__|for\s+.+\s+in\s+.+:|while\s+.+:|try:|except\b|return\b|async\s+def|const|let|var|function|export|interface|typealias|fun|val|public|private)\b/.test(line) ||
      /^[A-Za-z_][A-Za-z0-9_]*\s*=\s*.+/.test(line) ||
      /^["'][^"']+["']\s*:\s*.+/.test(line) ||
      ["{", "}", "[", "]"].includes(line) ||
      line.startsWith("//");
  }).length;
  return codeLines >= 2;
}

function codeTitle(text: string): string {
  const language = text.match(/```([A-Za-z0-9_+\-.#]+)/)?.[1];
  return language ? `${language.charAt(0).toUpperCase()}${language.slice(1)}` : "Code";
}

function taskListBlock(output: string): JsonMap {
  const date = output.match(/Tasks for ([0-9]{4}-[0-9]{2}-[0-9]{2})/)?.[1];
  const groups: JsonMap[] = [];
  let currentTitle = "Tasks";
  let currentItems: JsonMap[] = [];

  const flush = () => {
    if (currentItems.length > 0) {
      groups.push({ title: currentTitle, items: currentItems });
      currentItems = [];
    }
  };

  for (const raw of output.split("\n")) {
    const line = raw.trim();
    if (/^Pending:$/i.test(line)) {
      flush();
      currentTitle = "Pending";
      continue;
    }
    if (/^Done:$/i.test(line)) {
      flush();
      currentTitle = "Done";
      continue;
    }
    if (/^\d+\.\s+[○✓]/.test(line)) {
      const done = line.includes("✓");
      const title = line.replace(/^\d+\.\s+[○✓]\s*/, "").trim();
      currentItems.push({ title, status: done ? "done" : "pending" });
    }
  }
  flush();

  return {
    type: "task_list",
    title: date ? `Tasks for ${date}` : "Tasks",
    groups,
    markdown: output,
  };
}

function suggestionBlock(call: { args?: Record<string, string> }): JsonMap | null {
  const args = call.args || {};
  if (!args.title) return null;
  return {
    type: "suggested_task",
    title: args.title,
    subtitle: args.description || "",
    args,
  };
}

function metadataItems(args: Record<string, string>): Array<{ label: string; value: string }> {
  const fields: Array<[string, string]> = [
    ["date", "Date"],
    ["deadline_time", "Time"],
    ["time", "Time"],
    ["priority", "Priority"],
    ["tags", "Tags"],
    ["recurrence", "Repeats"],
    ["rollover", "Rollover"],
    ["subtasks", "Subtasks"],
  ];
  return fields
    .map(([key, label]) => ({ label, value: String(args[key] || "").trim() }))
    .filter(item => item.value);
}

function buildTaskSubtitle(args: Record<string, string>, prefix: string): string {
  const pieces = [
    args.date ? `for ${args.date}` : "",
    (args.deadline_time || args.time) ? `at ${args.deadline_time || args.time}` : "",
    args.tags ? `tagged ${args.tags}` : "",
  ].filter(Boolean);
  return pieces.length ? `${prefix} ${pieces.join(" ")}` : prefix;
}

function quotedTitle(text: string): string | null {
  return text.match(/"([^"]+)"/)?.[1] || null;
}

function toolTitle(name: string): string {
  return name
    .replace(/_/g, " ")
    .replace(/\b\w/g, c => c.toUpperCase());
}
