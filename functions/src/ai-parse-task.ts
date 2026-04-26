/**
 * Task parsing endpoint — used by voice input, notification edit, and task sheet.
 * Same prompt system as chat, but returns structured task JSON instead of conversational response.
 *
 * Always FREE — no credits needed. Uses the configured parse model (default: gemini-2.5-flash-lite).
 *
 * Flow:
 *   1. Client sends raw text (e.g. "kal subah 7 baje gym karna hai")
 *   2. Function builds system prompt (same AiPromptFactory rules)
 *   3. Calls AI with task tools enabled
 *   4. Returns parsed tool call results as structured JSON
 */
import { onRequest } from "firebase-functions/v2/https";
import { GoogleGenAI } from "@google/genai";
import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { buildSystemPrompt, TaskSnapshot, MemoryFact } from "./prompt-builder";
import { TASK_TOOLS, toGeminiFunctionDeclarations } from "./tools-schema";
import { getAiConfig } from "./ai-config";

const GEMINI_KEY = process.env.GEMINI_API_KEY || "";
const MISTRAL_KEY = process.env.MISTRAL_API_KEY || "";

async function verifyAuth(authHeader: string | undefined): Promise<string | null> {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    const decoded = await getAuth().verifyIdToken(authHeader.slice(7));
    return decoded.uid;
  } catch {
    return null;
  }
}

export const aiParseTask = onRequest(
  { cors: true, timeoutSeconds: 30, memory: "256MiB" },
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

    const { text, subtaskIntensity = 0, isNotificationEdit = false } = req.body;
    if (!text || typeof text !== "string") {
      res.status(400).json({ error: "text required" });
      return;
    }

    const db = getFirestore("preamble");

    try {
      // Get server-side AI config (model selection)
      const config = await getAiConfig(db);

      // Pull user's existing tasks for modify/delete context
      const tasksSnap = await db.collection("tasks")
        .where("uid", "==", uid).limit(isNotificationEdit ? 40 : 20).get();
      const tasks: TaskSnapshot[] = tasksSnap.docs.map(d => ({
        title: d.data().title,
        createdDate: d.data().createdDate || "",
        deadlineTime: d.data().deadlineTime || undefined,
        priority: d.data().priority || 0,
        isCompleted: d.data().isCompleted || false,
        isSyncing: d.data().isSyncing || false,
      }));

      // Pull memory for context-aware parsing
      const memorySnap = await db.collection(`users/${uid}/ai_memory`)
        .orderBy("lastUsedAt", "desc").limit(20).get();
      const memoryFacts: MemoryFact[] = memorySnap.docs.map(d => ({
        key: d.data().key, value: d.data().value,
        category: d.data().category || "context",
      }));

      // Build system prompt — SAME as offline AiPromptFactory
      const systemPrompt = buildSystemPrompt({
        tasks,
        memoryFacts,
        conciseMode: true,
        subtaskIntensity,
        isNotificationEdit,
        forceToolCall: true, // parse path: must produce a tool call
      });

      // Call AI with tools
      const parseModel = config.parseModel || "gemini-2.5-flash-lite";
      const isMistral = parseModel.includes("mistral") || parseModel.includes("mixtral");

      let toolCalls: Array<{ name: string; args: Record<string, string> }> = [];
      let responseText = "";

      if (isMistral) {
        // Mistral path
        const mistralBody: any = {
          model: parseModel,
          messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: text },
          ],
          tools: TASK_TOOLS.map(t => ({
            type: "function",
            function: { name: t.name, description: t.description, parameters: t.parameters },
          })),
          tool_choice: "auto",
        };

        const mistralRes = await fetch("https://api.mistral.ai/v1/chat/completions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${MISTRAL_KEY}`,
          },
          body: JSON.stringify(mistralBody),
        });

        if (!mistralRes.ok) {
          throw new Error(`Mistral error: ${mistralRes.status}`);
        }

        const json: any = await mistralRes.json();
        const choice = json.choices?.[0]?.message;
        responseText = choice?.content || "";

        if (choice?.tool_calls) {
          for (const tc of choice.tool_calls) {
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
      } else {
        // Gemini path
        const ai = new GoogleGenAI({ apiKey: GEMINI_KEY });
        const response = await ai.models.generateContent({
          model: parseModel,
          contents: [{ role: "user", parts: [{ text }] }],
          config: {
            systemInstruction: systemPrompt,
            tools: [{ functionDeclarations: toGeminiFunctionDeclarations() }] as any,
          },
        });

        const parts = response.candidates?.[0]?.content?.parts;
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
            if (part.text) responseText += part.text;
          }
        }
      }

      // Return structured result
      res.json({
        success: true,
        toolCalls,
        text: responseText,
        model: parseModel,
      });

    } catch (err: any) {
      console.error("Parse task error:", err);
      res.status(500).json({
        success: false,
        error: err.message || "Internal error",
      });
    }
  }
);
