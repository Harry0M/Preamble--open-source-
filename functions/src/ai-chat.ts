/**
 * Main AI chat endpoint with SSE streaming.
 *
 * Flow:
 *   1. Verify auth, pre-check credits.
 *   2. Resolve model: request override > server config (Firestore config/ai) > default.
 *   3. Pull compact memory/history and task context only when useful.
 *   4. Build a small chat-only system instruction server-side.
 *   5. Stream Gemini/Mistral.
 *   6. Capture token usage.
 *   7. Save assistant message (cloud is sole writer; client skips Firestore mirror).
 *   8. Deduct credits based on actual tokens consumed.
 *   9. Async memory extraction.
 */
import { onRequest } from "firebase-functions/v2/https";
import { GoogleGenAI } from "@google/genai";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { TASK_TOOLS, TASK_TOOLS_V2, toGeminiFunctionDeclarations, shouldUseTaskTools } from "./tools-schema";
import {
  DEFAULT_MODEL,
  DEFAULT_MODE,
  isMistralPremium,
  // Flash limits
  flashDailyMsgField,
  getFlashMsgsRemaining,
  // Mistral token budget
  mistralUsedField,
  mistralBonusField,
  getMistralDailyBudget,
  getMistralTokensRemaining,
  mistralWeightedTokens,
} from "./config";
import { extractMemoryFacts, hasExplicitMemoryIntent, shouldAttemptMemoryExtraction } from "./memory-extractor";
import { getAiConfig } from "./ai-config";
import {
  buildChatSystemPrompt,
  buildMemoryContext,
  buildTaskContext,
  geminiGenerationConfig,
  MemoryFact,
  TaskSnapshot,
} from "./chat-prompt";
import { buildRenderBlocks } from "./render-blocks";

const GEMINI_KEY = process.env.GEMINI_API_KEY || "";
const MISTRAL_KEY = process.env.MISTRAL_API_KEY || "";
const HISTORY_WINDOW = 12;

function isMistralModel(model: string): boolean {
  return model.includes("mistral") || model.includes("mixtral");
}

async function verifyAuth(authHeader: string | undefined): Promise<string | null> {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    const decoded = await getAuth().verifyIdToken(authHeader.slice(7));
    return decoded.uid;
  } catch {
    return null;
  }
}

/**
 * SSE events emitted to client:
 *   event: delta       → { text: "partial..." }
 *   event: tool_calls  → { calls: [{name, args}...] }
 *   event: done        → { creditsUsed, creditsRemaining, model, inputTokens, outputTokens, hasToolCalls }
 *   event: error       → { message: "..." }
 */
function sseWrite(res: any, event: string, data: any) {
  res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
}

function safeClientMessageId(value: unknown): string | null {
  if (typeof value !== "string") return null;
  if (!/^[A-Za-z0-9_-]{8,128}$/.test(value)) return null;
  return value;
}

function shouldIncludeMemory(message: string | null, taskToolsEnabled: boolean): boolean {
  if (!message) return true;
  const lower = message.toLowerCase().trim();
  if (taskToolsEnabled) return true;
  if (/\b(i|my|me|myself|i'm|i've|mera|meri|mere|mujhe|main|mein|hum|hamara)\b/i.test(lower)) return true;
  if (/\b(plan|focus|routine|goal|habit|preference|remember|forget|personal|profile)\b/i.test(lower)) return true;
  const genericQuestion =
    /^(what|who|when|where|why|how|explain|define|describe|tell me)\b/.test(lower) ||
    /^(kya|kaun|kab|kahan|kyu|kyon|kaise)\b/.test(lower);
  return !genericQuestion;
}

function cleanMemoryKey(value: unknown): string {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 64);
}

async function saveMessage(msgCol: any, clientMessageId: string | null, data: Record<string, any>) {
  if (clientMessageId) {
    await msgCol.doc(clientMessageId).set(data, { merge: true });
  } else {
    await msgCol.add(data);
  }
}

function parseStoredToolCalls(value: unknown): Array<{ name: string; args: Record<string, string> }> {
  if (typeof value !== "string" || !value.trim()) return [];
  try {
    const parsed = JSON.parse(value);
    if (!Array.isArray(parsed)) return [];
    return parsed.map((entry: any) => ({
      name: String(entry?.name || ""),
      args: Object.fromEntries(
        Object.entries(entry?.args || {}).map(([k, v]) => [k, String(v)])
      ),
    })).filter(entry => entry.name);
  } catch {
    return [];
  }
}

export const aiChat = onRequest(
  {
    cors: true,
    timeoutSeconds: 120,
    memory: "512MiB",
    maxInstances: 50,
  },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    const startedAt = Date.now();
    const db = getFirestore("preamble");

    // --- Resolve model: request > server config > default ---
    const config = await getAiConfig(db);

    if (config.killSwitch) {
      res.status(503).json({ error: "AI is temporarily disabled by admin." });
      return;
    }

    const requestedModel: string | undefined = req.body.model;
    const model: string = requestedModel || config.chatModel || DEFAULT_MODEL;
    const mode: string = req.body.mode || DEFAULT_MODE;
    const smartMode: boolean = req.body.smartMode !== false;
    const conversationId: string = req.body.conversationId || "default";
    const appVersionCode: number = req.body.appVersionCode || 0;
    const userMessageId = safeClientMessageId(req.body.userMessageId);
    const assistantMessageId = safeClientMessageId(req.body.assistantMessageId);
    const message: string | null = req.body.message ?? null;
    const toolResults: Array<{ name: string; result: string }> | null = req.body.toolResults ?? null;

    if (!message && !toolResults) {
      res.status(400).json({ error: "message or toolResults required" });
      return;
    }

    const userDocRef = db.doc(`users/${uid}`);
    const userDoc = await userDocRef.get();
    const userData = userDoc.data() || {};

    if (isMistralPremium(model)) {
      // --- Mistral: check daily token budget ---
      const usedField  = mistralUsedField(model);
      const bonusField = mistralBonusField(model);
      const usedToday  = (userData[usedField]  ?? 0) as number;
      const bonusToday = (userData[bonusField] ?? 0) as number;
      const tokensLeft = getMistralTokensRemaining(model, usedToday, bonusToday);
      if (tokensLeft <= 0) {
        res.status(429).json({
          error: "Daily token budget exhausted for this model.",
          tokensRemaining: 0,
          resetAt: "midnight UTC",
          hint: "Watch an ad to refill your budget.",
        });
        return;
      }
    } else {
      // --- Flash: check daily message limit (anti-abuse) ---
      const fmField  = flashDailyMsgField(model);
      const fmUsed   = (userData[fmField] ?? 0) as number;
      const fmLeft   = getFlashMsgsRemaining(model, fmUsed);
      if (fmLeft === 0) {
        res.status(429).json({
          error: "Daily message limit reached. Resets at midnight.",
          messagesRemaining: 0,
        });
        return;
      }
    }

    // --- Set up SSE ---
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
      "X-Accel-Buffering": "no",
    });

    try {
      const taskToolsEnabled = message ? shouldUseTaskTools(message) : false;
      const baseWindow = Math.min(config.maxHistoryWindow || HISTORY_WINDOW, HISTORY_WINDOW);
      const isSimpleQuery = message != null &&
        message.trim().split(/\s+/).length < 25 &&
        !/\b(earlier|before|said|mentioned|we (talked|discussed)|last time|previous|above|that|it)\b/i.test(message);
      const historyWindow = isSimpleQuery ? Math.min(4, baseWindow) : baseWindow;

      // --- Pull compact user memory ---
      let memoryFacts: MemoryFact[] = [];
      if (smartMode) {
        const memorySnap = await db.collection(`users/${uid}/ai_memory`)
          .orderBy("lastUsedAt", "desc").limit(50).get();
        const byKey = new Map<string, MemoryFact>();
        const duplicateRefs: any[] = [];
        for (const doc of memorySnap.docs) {
          const data = doc.data();
          const key = cleanMemoryKey(data.key);
          const value = String(data.value || "").trim();
          if (!key || !value) continue;
          if (byKey.has(key)) {
            duplicateRefs.push(doc.ref);
            continue;
          }
          byKey.set(key, {
            key,
            value,
            category: data.category || "context",
            confidence: data.confidence || 0.8,
            lastUsedAt: data.lastUsedAt || 0,
            source: data.source || "chat",
          });
        }
        memoryFacts = [...byKey.values()];
        if (duplicateRefs.length > 0) {
          Promise.all(duplicateRefs.map(ref => ref.delete())).catch(() => {});
        }
      }

      const userName = userDoc.data()?.displayName || userDoc.data()?.name || "";
      const memoryContext = smartMode && shouldIncludeMemory(message, taskToolsEnabled)
        ? buildMemoryContext(memoryFacts, userName, message || undefined)
        : "";

      // --- Pull task context only for task-related turns (Zero Firestore reads: client sends Room DB tasks) ---
      let taskContext = "";
      if (taskToolsEnabled) {
        const clientTasks = req.body.tasks;
        const tasks: TaskSnapshot[] = Array.isArray(clientTasks)
          ? clientTasks.map((t: any) => ({
              title: String(t.title || ""),
              createdDate: String(t.createdDate || ""),
              deadlineTime: t.deadlineTime || undefined,
              priority: Number(t.priority) || 0,
              isCompleted: Boolean(t.isCompleted),
            }))
          : [];
        taskContext = buildTaskContext(tasks);
      }

      // --- Pull chat history ---
      const histSnap = await db.collection(`users/${uid}/ai_chat/${conversationId}/messages`)
        .orderBy("timestamp", "desc")
        .limit(historyWindow)
        .get();
      const historyRows = histSnap.docs
        .map(d => d.data())
        .reverse()
        .filter(m => (m.role === "user" || m.role === "assistant") && m.content);

      const summarySnap = await db.collection(`users/${uid}/ai_chat/${conversationId}/messages`)
        .where("role", "==", "summary")
        .limit(1)
        .get();
      const conversationSummary = summarySnap.docs
        .map(d => String(d.data().content || "").trim())
        .find(Boolean) || "";

      // --- Build lean chat instruction ---
      const systemPrompt = buildChatSystemPrompt({
        conciseMode: mode === "concise",
        taskToolsEnabled,
        memoryContext,
        taskContext,
        conversationSummary,
        appVersionCode,
      });

      // --- Build messages ---
      const contents: Array<{ role: string; parts: Array<{ text: string }> }> = [];
      const SUGGEST_RE = /\[SUGGEST:\s*\{[^\]]*\}\]/g;
      const recentHistory = historyRows.slice(-historyWindow);
      for (const row of recentHistory) {
        if (row.role === "user" && row.content === message) continue;
        const content = row.role === "assistant"
          ? row.content.replace(SUGGEST_RE, "").trim()
          : row.content;
        if (!content) continue;
        contents.push({
          role: row.role === "assistant" ? "model" : "user",
          parts: [{ text: content }],
        });
      }
      if (message) {
        contents.push({ role: "user", parts: [{ text: message }] });
      }
      if (toolResults && Array.isArray(toolResults)) {
        // BUG-FIX: list_tasks must dump full result, not "brief confirmation".
        const hasListTasks = toolResults.some(r => r.name === "list_tasks");
        const toolText = toolResults
          .map(r => `Tool "${r.name}" returned:\n${r.result}`)
          .join("\n\n");
        const instruction = hasListTasks
          ? "Show this list to the user, formatted as a markdown bullet list. Group by status (pending vs done) if helpful. Add a one-line opener like 'Here's what you have:'. DO NOT summarize or hide items."
          : "Provide a brief natural-language confirmation to the user based on these results.";
        contents.push({
          role: "user",
          parts: [{ text: `[Tool execution results]\n${toolText}\n\n${instruction}` }],
        });
      }

      // --- Call provider ---
      let fullText = "";
      let thinkingText = "";
      let toolCalls: Array<{ name: string; args: Record<string, string> }> = [];
      let inputTokens = 0;
      let outputTokens = 0;


      if (isMistralModel(model)) {
        const mistralMessages: any[] = [{ role: "system", content: systemPrompt }];
        for (const c of contents) {
          mistralMessages.push({ role: c.role === "model" ? "assistant" : c.role, content: c.parts[0].text });
        }
        const mistralTools = taskToolsEnabled ? (appVersionCode >= 8 ? TASK_TOOLS_V2 : TASK_TOOLS).map(t => ({
          type: "function" as const,
          function: { name: t.name, description: t.description, parameters: t.parameters },
        })) : undefined;
        const mistralBody: any = {
          model,
          messages: mistralMessages,
          stream: true,
          max_tokens: 4096,
        };
        if (mistralTools) mistralBody.tools = mistralTools;

        const mistralRes = await fetch("https://api.mistral.ai/v1/chat/completions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${MISTRAL_KEY}`,
          },
          body: JSON.stringify(mistralBody),
        });
        if (!mistralRes.ok) {
          throw new Error(`Mistral API error: ${mistralRes.status} ${await mistralRes.text()}`);
        }

        const reader = mistralRes.body?.getReader();
        const decoder = new TextDecoder();
        if (reader) {
          let buffer = "";
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split("\n");
            buffer = lines.pop() || "";
            for (const line of lines) {
              if (!line.startsWith("data: ") || line.includes("[DONE]")) continue;
              try {
                const json = JSON.parse(line.slice(6));
                const delta = json.choices?.[0]?.delta;
                if (delta?.content) {
                  fullText += delta.content;
                  sseWrite(res, "delta", { text: delta.content });
                }
                if (delta?.tool_calls) {
                  for (const tc of delta.tool_calls) {
                    if (tc.function?.name) {
                      const args: Record<string, string> = {};
                      try {
                        const parsed = JSON.parse(tc.function.arguments || "{}");
                        for (const [k, v] of Object.entries(parsed)) args[k] = String(v);
                      } catch {}
                      toolCalls.push({ name: tc.function.name, args });
                    }
                  }
                }
                // Mistral returns usage in the final chunk
                if (json.usage) {
                  inputTokens = json.usage.prompt_tokens || 0;
                  outputTokens = json.usage.completion_tokens || 0;
                }
              } catch {}
            }
          }
        }
      } else {
        const ai = new GoogleGenAI({ apiKey: GEMINI_KEY });
        const tools: any = taskToolsEnabled ? [{ functionDeclarations: toGeminiFunctionDeclarations(appVersionCode) }] : undefined;
        const geminiModel = model.startsWith("gemini") ? model : DEFAULT_MODEL;
        const response = await ai.models.generateContentStream({
          model: geminiModel,
          contents,
          config: geminiGenerationConfig({
            systemPrompt,
            tools,
            conciseMode: mode === "concise",
            model: geminiModel,
          }),
        });

        for await (const chunk of response) {
          const parts = chunk.candidates?.[0]?.content?.parts;
          if (parts) {
            for (const part of parts) {
              if ((part as any).thought === true) {
                thinkingText += part.text || "";
                sseWrite(res, "thinking", { text: part.text || "" });
              } else if (part.functionCall) {
                const fc = part.functionCall;
                const args: Record<string, string> = {};
                if (fc.args) {
                  for (const [k, v] of Object.entries(fc.args)) args[k] = String(v);
                }
                toolCalls.push({ name: fc.name || "", args });
              } else if (part.text) {
                fullText += part.text;
                sseWrite(res, "delta", { text: part.text });
              }
            }
          }
          // usage metadata typically arrives in last chunk
          const usage = (chunk as any).usageMetadata;
          if (usage) {
            inputTokens = usage.promptTokenCount || inputTokens;
            outputTokens = usage.candidatesTokenCount || outputTokens;
          }
        }
      }

      if (toolCalls.length > 0) {
        sseWrite(res, "tool_calls", { calls: toolCalls });
      }

      // --- Save assistant message to Firestore (cloud is sole writer; client skips its own sync) ---
      const msgCol = db.collection(`users/${uid}/ai_chat/${conversationId}/messages`);
      const now = Date.now();

      if (message) {
        await saveMessage(msgCol, userMessageId, {
          role: "user",
          content: message,
          timestamp: now - 1,
          userId: uid,
          conversationId,
          syncPending: 0,
        });
      }
      const storedText = fullText.replace(/\[SUGGEST:\s*\{[^\]]*\}\]/g, "").trim();
      const incomingToolResults = (toolResults || []).map(r => ({
        name: r.name,
        result: r.result,
      }));
      const renderBlocks = buildRenderBlocks(storedText, toolCalls, incomingToolResults);
      if (storedText || toolCalls.length > 0) {
        await saveMessage(msgCol, assistantMessageId, {
          role: "assistant",
          content: storedText,
          timestamp: now,
          userId: uid,
          conversationId,
          modelUsed: model,
          toolCalls: toolCalls.length > 0 ? JSON.stringify(toolCalls) : null,
          renderBlocksJson: renderBlocks ? JSON.stringify(renderBlocks) : null,
          syncPending: 0,
        });
      }

      // --- Track usage ---
      const totalTokens = inputTokens + outputTokens;
      const usageUpdate: Record<string, any> = {};
      let tokensRemaining = -1;

      if (isMistralPremium(model)) {
        const usedField  = mistralUsedField(model);
        const bonusField = mistralBonusField(model);
        const weighted   = mistralWeightedTokens(model, totalTokens);
        usageUpdate[usedField] = FieldValue.increment(weighted);
        const prevUsed  = (userData[usedField]  ?? 0) as number;
        const prevBonus = (userData[bonusField] ?? 0) as number;
        tokensRemaining = getMistralTokensRemaining(model, prevUsed + weighted, prevBonus);
      } else {
        const fmField = flashDailyMsgField(model);
        usageUpdate[fmField] = FieldValue.increment(1);
        const fmUsed = (userData[fmField] ?? 0) as number;
        tokensRemaining = getFlashMsgsRemaining(model, fmUsed + 1);
      }

      if (Object.keys(usageUpdate).length > 0) {
        await userDocRef.set(usageUpdate, { merge: true });
      }

      // --- Async memory extraction ---
      if (smartMode && message && GEMINI_KEY && shouldAttemptMemoryExtraction(message)) {
        if (hasExplicitMemoryIntent(message)) {
          await extractMemoryFacts(uid, message, GEMINI_KEY, db, config.memoryModel, memoryFacts);
        } else {
          extractMemoryFacts(uid, message, GEMINI_KEY, db, config.memoryModel, memoryFacts).catch(() => {});
        }
      }

      const dt = Date.now() - startedAt;
      console.log("aiChat success", {
        uid, model, mode,
        msgLen: message?.length ?? 0,
        respLen: fullText.length,
        toolCalls: toolCalls.length,
        inputTokens, outputTokens,
        tokensRemaining,
        durationMs: dt,
      });

      sseWrite(res, "done", {
        tokensRemaining,
        model,
        inputTokens,
        outputTokens,
        hasToolCalls: toolCalls.length > 0,
        renderBlocksVersion: renderBlocks?.version || 1,
        renderBlocks: renderBlocks?.blocks || [],
      });
      res.end();
    } catch (err: any) {
      console.error("aiChat error:", { uid, model, error: err.message, stack: err.stack });
      sseWrite(res, "error", { message: err.message || "Internal error" });
      res.end();
    }
  }
);

/**
 * Tool results follow-up endpoint.
 * Called after client executes tool calls locally.
 * Treats list_tasks specially — dumps full list rather than confirming.
 */
export const aiChatContinue = onRequest(
  { cors: true, timeoutSeconds: 60, memory: "512MiB" },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }
    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    const startedAt = Date.now();
    const db = getFirestore("preamble");
    const config = await getAiConfig(db);

    const requestedModel: string | undefined = req.body.model;
    const model: string = requestedModel || config.chatModel || DEFAULT_MODEL;
    const mode: string = req.body.mode || DEFAULT_MODE;
    const conversationId: string = req.body.conversationId || "default";
    const appVersionCode: number = req.body.appVersionCode || 0;
    const assistantMessageId = safeClientMessageId(req.body.assistantMessageId);
    const toolResults: Array<{ name: string; result: string }> = req.body.toolResults;

    if (!toolResults || !Array.isArray(toolResults)) {
      res.status(400).json({ error: "toolResults array required" });
      return;
    }

    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
    });

    try {
      const hasListTasks = toolResults.some(r => r.name === "list_tasks");
      const toolText = toolResults
        .map(r => `Tool "${r.name}" returned:\n${r.result}`)
        .join("\n\n");

      const instruction = hasListTasks
        ? "Show this list to the user, formatted as a markdown bullet list (each task on its own '- ' line). Preserve the status marks (✓ for done, ○ for pending). Add a one-line opener like \"Here's what you have:\". Don't summarize or hide items."
        : "Provide a brief natural-language confirmation based on these results.";

      // Pull a tiny bit of recent context
      const histSnap = await db.collection(`users/${uid}/ai_chat/${conversationId}/messages`)
        .orderBy("timestamp", "desc").limit(6).get();
      const recent = histSnap.docs.reverse().map(d => d.data()).filter(m => m.content);

      const SUGGEST_RE2 = /\[SUGGEST:\s*\{[^\]]*\}\]/g;
      const contents: Array<{ role: string; parts: Array<{ text: string }> }> = [];
      for (const row of recent) {
        const content = row.role === "assistant"
          ? row.content.replace(SUGGEST_RE2, "").trim()
          : row.content;
        if (!content) continue;
        contents.push({
          role: row.role === "assistant" ? "model" : "user",
          parts: [{ text: content }],
        });
      }
      contents.push({
        role: "user",
        parts: [{ text: `[Tool execution results]\n${toolText}\n\n${instruction}` }],
      });

      const styleSystem = buildChatSystemPrompt({
        conciseMode: mode === "concise",
        taskToolsEnabled: false,
        appVersionCode,
      });

      let fullText = "";
      let inputTokens = 0;
      let outputTokens = 0;

      if (isMistralModel(model)) {
        const messages: any[] = [
          { role: "system", content: styleSystem },
          ...contents.map(c => ({
            role: c.role === "model" ? "assistant" : c.role,
            content: c.parts[0].text,
          })),
        ];
        const mistralRes = await fetch("https://api.mistral.ai/v1/chat/completions", {
          method: "POST",
          headers: { "Content-Type": "application/json", "Authorization": `Bearer ${MISTRAL_KEY}` },
          body: JSON.stringify({
            model,
            messages,
            stream: true,
            max_tokens: 4096,
          }),
        });
        if (!mistralRes.ok) throw new Error(`Mistral error: ${mistralRes.status}`);
        const reader = mistralRes.body?.getReader();
        const decoder = new TextDecoder();
        if (reader) {
          let buffer = "";
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split("\n");
            buffer = lines.pop() || "";
            for (const line of lines) {
              if (!line.startsWith("data: ") || line.includes("[DONE]")) continue;
              try {
                const json = JSON.parse(line.slice(6));
                const text = json.choices?.[0]?.delta?.content;
                if (text) {
                  fullText += text;
                  sseWrite(res, "delta", { text });
                }
                if (json.usage) {
                  inputTokens = json.usage.prompt_tokens || 0;
                  outputTokens = json.usage.completion_tokens || 0;
                }
              } catch {}
            }
          }
        }
      } else {
        const ai = new GoogleGenAI({ apiKey: GEMINI_KEY });
        const geminiModel = model.startsWith("gemini") ? model : DEFAULT_MODEL;
        const response = await ai.models.generateContentStream({
          model: geminiModel,
          contents,
          config: geminiGenerationConfig({
            systemPrompt: styleSystem,
            conciseMode: mode === "concise",
            model: geminiModel,
          }),
        });
        for await (const chunk of response) {
          const text = chunk.candidates?.[0]?.content?.parts?.[0]?.text;
          if (text) {
            fullText += text;
            sseWrite(res, "delta", { text });
          }
          const usage = (chunk as any).usageMetadata;
          if (usage) {
            inputTokens = usage.promptTokenCount || inputTokens;
            outputTokens = usage.candidatesTokenCount || outputTokens;
          }
        }
      }

      // Save assistant follow-up
      const msgCol = db.collection(`users/${uid}/ai_chat/${conversationId}/messages`);
      let previousToolCalls: Array<{ name: string; args: Record<string, string> }> = [];
      if (assistantMessageId) {
        const existingAssistant = await msgCol.doc(assistantMessageId).get();
        previousToolCalls = parseStoredToolCalls(existingAssistant.data()?.toolCalls);
      }
      const toolResultsWithArgs = toolResults.map(result => {
        const matchingCall = previousToolCalls.find(call => call.name === result.name);
        return {
          name: result.name,
          result: result.result,
          args: matchingCall?.args || {},
        };
      });
      const renderBlocks = buildRenderBlocks(fullText, previousToolCalls, toolResultsWithArgs);
      if (fullText) {
        await saveMessage(msgCol, assistantMessageId, {
          role: "assistant",
          content: fullText,
          timestamp: Date.now(),
          userId: uid,
          conversationId,
          modelUsed: model,
          toolResults: JSON.stringify(toolResults),
          renderBlocksJson: renderBlocks ? JSON.stringify(renderBlocks) : null,
          syncPending: 0,
        });
      }

      // Track usage for follow-up call
      const userDocRef = db.doc(`users/${uid}`);
      if (isMistralPremium(model) && (inputTokens + outputTokens) > 0) {
        const usedField = mistralUsedField(model);
        const weighted  = mistralWeightedTokens(model, inputTokens + outputTokens);
        await userDocRef.set({ [usedField]: FieldValue.increment(weighted) }, { merge: true });
      }
      const dt = Date.now() - startedAt;

      console.log("aiChatContinue success", {
        uid, model, hasListTasks, toolResultCount: toolResults.length,
        respLen: fullText.length, inputTokens, outputTokens, durationMs: dt,
      });

      sseWrite(res, "done", {
        model,
        inputTokens,
        outputTokens,
        hasToolCalls: false,
        renderBlocksVersion: renderBlocks?.version || 1,
        renderBlocks: renderBlocks?.blocks || [],
      });
      res.end();
    } catch (err: any) {
      console.error("aiChatContinue error:", { uid, model, error: err.message });
      sseWrite(res, "error", { message: err.message || "Internal error" });
      res.end();
    }
  }
);
