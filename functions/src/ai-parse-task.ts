/**
 * Task parsing endpoint — used by voice input, notification edit, and task sheet.
 * Returns structured task JSON instead of conversational response.
 *
 * Zero Firestore reads per call:
 *  - Model is hardcoded here (change & redeploy to update)
 *  - Task context sent by client from local Room DB
 *  - Preferred languages sent by client from SharedPreferences
 */
import { onRequest } from "firebase-functions/v2/https";
import { GoogleGenAI } from "@google/genai";
import { getAuth } from "firebase-admin/auth";
import { buildSystemPrompt, TaskSnapshot } from "./prompt-builder";
import { TASK_TOOLS_V2, TASK_TOOLS, toGeminiFunctionDeclarations } from "./tools-schema";

const GEMINI_KEY = process.env.GEMINI_API_KEY || "";
const MISTRAL_KEY = process.env.MISTRAL_API_KEY || "";

/**
 * ─── AI MODEL CONFIGURATION ──────────────────────────────────────────────────
 * To change the model: edit this value and run `firebase deploy --only functions`
 * No Firestore read needed — zero cost overhead per request.
 *
 * Gemini options:  "gemini-2.5-flash-lite" | "gemini-2.5-flash" | "gemini-2.5-pro"
 * Mistral options: "mistral-small-latest"  | "mistral-medium-latest" | "mistral-large-latest"
 */
const PARSE_MODEL = "gemini-2.5-flash-lite";
// ─────────────────────────────────────────────────────────────────────────────

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

    const {
      text,
      subtaskIntensity = 1,
      isNotificationEdit = false,
      appVersionCode = 11,
      preferredLanguages,
      // Client sends task context from local Room DB — no Firestore read needed
      tasks: clientTasks,
    } = req.body;

    if (!text || typeof text !== "string") {
      res.status(400).json({ error: "text required" });
      return;
    }

    try {
      // Use task context sent by client (from Room DB on device — free)
      // Falls back to empty array if client doesn't send tasks (older app versions)
      const tasks: TaskSnapshot[] = Array.isArray(clientTasks)
        ? clientTasks.map((t: any) => ({
            title: String(t.title || ""),
            createdDate: String(t.createdDate || ""),
            deadlineTime: t.deadlineTime || undefined,
            priority: Number(t.priority) || 0,
            isCompleted: Boolean(t.isCompleted),
            isSyncing: Boolean(t.isSyncing),
          }))
        : [];

      // Preferred languages sent by client from SharedPreferences (free)
      const langs: string[] = Array.isArray(preferredLanguages) ? preferredLanguages : [];

      // Build system prompt
      const systemPrompt = buildSystemPrompt({
        tasks,
        subtaskIntensity,
        isNotificationEdit,
        appVersionCode,
        preferredLanguages: langs.length > 0 ? langs : undefined,
      });

      const parseModel = PARSE_MODEL;
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
          tools: (appVersionCode >= 8 ? TASK_TOOLS_V2 : TASK_TOOLS).map(t => ({
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
            tools: [{ functionDeclarations: toGeminiFunctionDeclarations(appVersionCode) }] as any,
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
