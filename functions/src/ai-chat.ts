/**
 * Main AI chat endpoint with SSE streaming.
 *
 * Flow:
 *   1. Verify auth, pre-check credits.
 *   2. Resolve model: request override > server config (Firestore config/ai) > default.
 *   3. Pull memory + history + tasks from Firestore.
 *   4. Build system prompt server-side.
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
import { buildSystemPrompt, MemoryFact, TaskSnapshot } from "./prompt-builder";
import { TASK_TOOLS, toGeminiFunctionDeclarations, isSimpleQuery } from "./tools-schema";
import {
  computeTokenCredits,
  preflightCreditCheck,
  isFreeModel,
  DEFAULT_MODEL,
  DEFAULT_MODE,
} from "./config";
import { extractMemoryFacts } from "./memory-extractor";
import { getAiConfig } from "./ai-config";

const GEMINI_KEY = process.env.GEMINI_API_KEY || "";
const MISTRAL_KEY = process.env.MISTRAL_API_KEY || "";
const HISTORY_WINDOW = 16;

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
    const conversationId: string = req.body.conversationId || "default";
    const message: string | null = req.body.message ?? null;
    const toolResults: Array<{ name: string; result: string }> | null = req.body.toolResults ?? null;

    if (!message && !toolResults) {
      res.status(400).json({ error: "message or toolResults required" });
      return;
    }

    // --- Pre-flight credit check (anti-spam: must have at least 1 credit for paid tiers) ---
    const userDocRef = db.doc(`users/${uid}`);
    const userDoc = await userDocRef.get();
    const balance: number = userDoc.data()?.ai_credits ?? 0;
    const preflight = preflightCreditCheck(model, balance);
    if (!preflight.ok) {
      res.status(402).json({
        error: "Insufficient credits",
        creditsNeeded: preflight.needed,
        creditsRemaining: balance,
      });
      return;
    }

    // --- Set up SSE ---
    res.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      "Connection": "keep-alive",
      "X-Accel-Buffering": "no",
    });

    try {
      // --- Pull user memory ---
      const memorySnap = await db.collection(`users/${uid}/ai_memory`)
        .orderBy("lastUsedAt", "desc").limit(40).get();
      const memoryFacts: MemoryFact[] = memorySnap.docs.map(d => ({
        key: d.data().key,
        value: d.data().value,
        category: d.data().category || "context",
      }));

      const userName = userDoc.data()?.displayName || userDoc.data()?.name || "";

      // --- Pull tasks ---
      const tasksSnap = await db.collection("tasks").where("uid", "==", uid).limit(30).get();
      const tasks: TaskSnapshot[] = tasksSnap.docs.map(d => ({
        title: d.data().title,
        createdDate: d.data().createdDate || "",
        deadlineTime: d.data().deadlineTime || undefined,
        priority: d.data().priority || 0,
        isCompleted: d.data().isCompleted || false,
      }));

      // --- Pull chat history ---
      const histSnap = await db.collection(`users/${uid}/ai_chat/${conversationId}/messages`)
        .orderBy("timestamp", "asc")
        .limit(config.maxHistoryWindow * 4)
        .get();
      const historyRows = histSnap.docs
        .map(d => d.data())
        .filter(m => (m.role === "user" || m.role === "assistant") && m.content);

      // --- Build prompt ---
      const simple = message ? isSimpleQuery(message) : false;
      const systemPrompt = buildSystemPrompt({
        tasks: simple ? undefined : tasks,
        memoryFacts,
        userName,
        conciseMode: mode === "concise",
        forceToolCall: false, // chat path: free conversation allowed
      });

      // --- Build messages ---
      const contents: Array<{ role: string; parts: Array<{ text: string }> }> = [];
      const recentHistory = historyRows.slice(-(config.maxHistoryWindow || HISTORY_WINDOW));
      for (const row of recentHistory) {
        if (row.role === "user" && row.content === message) continue;
        contents.push({
          role: row.role === "assistant" ? "model" : "user",
          parts: [{ text: row.content }],
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
      let toolCalls: Array<{ name: string; args: Record<string, string> }> = [];
      let inputTokens = 0;
      let outputTokens = 0;

      if (isMistralModel(model)) {
        const mistralMessages: any[] = [{ role: "system", content: systemPrompt }];
        for (const c of contents) {
          mistralMessages.push({ role: c.role === "model" ? "assistant" : c.role, content: c.parts[0].text });
        }
        const mistralTools = simple ? undefined : TASK_TOOLS.map(t => ({
          type: "function" as const,
          function: { name: t.name, description: t.description, parameters: t.parameters },
        }));
        const mistralBody: any = { model, messages: mistralMessages, stream: true };
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
        const tools: any = simple ? undefined : [{ functionDeclarations: toGeminiFunctionDeclarations() }];
        const geminiModel = model.startsWith("gemini") ? model : DEFAULT_MODEL;
        const response = await ai.models.generateContentStream({
          model: geminiModel,
          contents,
          config: { systemInstruction: systemPrompt, tools },
        });

        for await (const chunk of response) {
          const parts = chunk.candidates?.[0]?.content?.parts;
          if (parts) {
            for (const part of parts) {
              if (part.functionCall) {
                const fc = part.functionCall;
                const args: Record<string, string> = {};
                if (fc.args) {
                  for (const [k, v] of Object.entries(fc.args)) args[k] = String(v);
                }
                toolCalls.push({ name: fc.name || "", args });
              }
              if (part.text) {
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
        await msgCol.add({
          role: "user",
          content: message,
          timestamp: now - 1,
          userId: uid,
          conversationId,
          syncPending: 0,
        });
      }
      if (fullText || toolCalls.length > 0) {
        await msgCol.add({
          role: "assistant",
          content: fullText || "",
          timestamp: now,
          userId: uid,
          conversationId,
          modelUsed: model,
          toolCalls: toolCalls.length > 0 ? JSON.stringify(toolCalls) : null,
          syncPending: 0,
        });
      }

      // --- Deduct credits based on real token usage ---
      const tokenCost = computeTokenCredits(model, inputTokens, outputTokens);
      if (tokenCost > 0) {
        await userDocRef.set({
          ai_credits: FieldValue.increment(-tokenCost),
          ai_credits_total_spent: FieldValue.increment(tokenCost),
        }, { merge: true });
      }

      // --- Async memory extraction ---
      if (message && message.length >= 20 && GEMINI_KEY) {
        extractMemoryFacts(uid, message, GEMINI_KEY, db).catch(() => {});
      }

      // --- Final event ---
      const updatedDoc = await userDocRef.get();
      const creditsRemaining = updatedDoc.data()?.ai_credits ?? 0;
      const dt = Date.now() - startedAt;

      console.log("aiChat success", {
        uid, model, mode,
        msgLen: message?.length ?? 0,
        respLen: fullText.length,
        toolCalls: toolCalls.length,
        inputTokens, outputTokens,
        creditsUsed: tokenCost,
        creditsRemaining,
        durationMs: dt,
      });

      sseWrite(res, "done", {
        creditsUsed: tokenCost,
        creditsRemaining,
        model,
        inputTokens,
        outputTokens,
        hasToolCalls: toolCalls.length > 0,
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

      const contents: Array<{ role: string; parts: Array<{ text: string }> }> = [];
      for (const row of recent) {
        contents.push({
          role: row.role === "assistant" ? "model" : "user",
          parts: [{ text: row.content }],
        });
      }
      contents.push({
        role: "user",
        parts: [{ text: `[Tool execution results]\n${toolText}\n\n${instruction}` }],
      });

      // Build a minimal style-only system prompt (no rules — already executed)
      const styleSystem = mode === "concise"
        ? "You are Preamble AI. Use markdown formatting: '- ' for bullets, '## ' for headers, **bold**, `code`. Be direct. No filler openers."
        : "You are Preamble AI. Use markdown formatting: '- ' for bullets, '## ' for headers, **bold**, `code`.";

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
          body: JSON.stringify({ model, messages, stream: true }),
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
          config: { systemInstruction: styleSystem },
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
      if (fullText) {
        await db.collection(`users/${uid}/ai_chat/${conversationId}/messages`).add({
          role: "assistant",
          content: fullText,
          timestamp: Date.now(),
          userId: uid,
          conversationId,
          modelUsed: model,
          syncPending: 0,
        });
      }

      // Deduct credits for the follow-up call (token-based)
      const userDocRef = db.doc(`users/${uid}`);
      const tokenCost = computeTokenCredits(model, inputTokens, outputTokens);
      if (tokenCost > 0 && !isFreeModel(model)) {
        await userDocRef.set({
          ai_credits: FieldValue.increment(-tokenCost),
          ai_credits_total_spent: FieldValue.increment(tokenCost),
        }, { merge: true });
      }
      const updatedDoc = await userDocRef.get();
      const creditsRemaining = updatedDoc.data()?.ai_credits ?? 0;
      const dt = Date.now() - startedAt;

      console.log("aiChatContinue success", {
        uid, model, hasListTasks, toolResultCount: toolResults.length,
        respLen: fullText.length, inputTokens, outputTokens,
        creditsUsed: tokenCost, creditsRemaining, durationMs: dt,
      });

      sseWrite(res, "done", {
        creditsUsed: tokenCost,
        creditsRemaining,
        model,
        inputTokens,
        outputTokens,
        hasToolCalls: false,
      });
      res.end();
    } catch (err: any) {
      console.error("aiChatContinue error:", { uid, model, error: err.message });
      sseWrite(res, "error", { message: err.message || "Internal error" });
      res.end();
    }
  }
);
