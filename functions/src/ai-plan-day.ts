/**
 * Plan-My-Day endpoint — Track A of ai-planning-and-gating.
 *
 * Proposes an `HH:mm` start time for each schedulable task, avoiding the times
 * already taken by fixed commitments and scheduling higher-priority tasks earlier.
 *
 * This is a one-shot JSON endpoint structured as a sibling of `aiParseTask`, but —
 * unlike the always-FREE parse path — it runs under the CREDIT-CHARGED economy
 * (same model-dependent balance check `aiChat` uses). On insufficient balance it
 * returns HTTP 402 `{ success:false, error:"INSUFFICIENT_CREDITS" }` and never
 * calls the model.
 *
 * The function only PROPOSES. It never mutates tasks (tasks are client-owned); the
 * client-side ScheduleNormalizer is the authority that validates/repairs the result.
 *
 * Flow:
 *   1. Verify auth (401 if missing).
 *   2. Resolve model via getAiConfig(db) (kill-switch honored).
 *   3. Server-side balance check mirroring aiChat (402 INSUFFICIENT_CREDITS if empty).
 *   4. Build the server-held planning prompt + call Gemini/Mistral.
 *   5. Parse a STRICT JSON array [{ id, time }] (robust: best-effort parse).
 *   6. Deduct usage post-call like aiChat, return { success, assignments, model }.
 */
import { onRequest } from "firebase-functions/v2/https";
import { GoogleGenAI } from "@google/genai";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";
import { getAiConfig } from "./ai-config";
import {
  DEFAULT_MODEL,
  isMistralPremium,
  flashDailyMsgField,
  getFlashMsgsRemaining,
  mistralUsedField,
  mistralBonusField,
  getMistralTokensRemaining,
  mistralWeightedTokens,
} from "./config";

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

function isMistralModel(model: string): boolean {
  return model.includes("mistral") || model.includes("mixtral");
}

interface SchedulableDto { id: string; title: string; priority: number; }
interface FixedDto { start: string; end?: string; }

/** Build the server-held planning prompt. Output is a STRICT JSON array [{id,time}]. */
function buildPlanPrompt(
  schedulable: SchedulableDto[],
  fixed: FixedDto[],
  date: string,
  dayStart: string,
  dayEnd: string,
): string {
  const taskLines = schedulable
    .map((t) => `- id="${t.id}" priority=${t.priority} title="${String(t.title).replace(/"/g, "'")}"`)
    .join("\n");
  const fixedLines = fixed.length
    ? fixed
        .map((f) => (f.end ? `- ${f.start}-${f.end} (busy)` : `- ${f.start} (busy)`))
        .join("\n")
    : "- (none)";

  return [
    "You are a scheduling assistant. Plan the user's day for " + date + ".",
    "Assign EACH schedulable task exactly one start time in 24-hour HH:mm format.",
    "",
    "Hard rules:",
    `1. Every assigned time MUST be within the working window [${dayStart}, ${dayEnd}].`,
    "2. Do NOT assign a time that collides with any fixed commitment (busy) time below.",
    "3. Give every task a DISTINCT time (no two tasks at the same time).",
    "4. Schedule higher-priority tasks earlier (priority is 0..3; 3 is highest).",
    "",
    "Schedulable tasks (one time each):",
    taskLines,
    "",
    "Fixed commitments (already busy — avoid these times):",
    fixedLines,
    "",
    "Respond with ONLY a strict JSON array, no prose, no markdown fences:",
    '[{ "id": "<task id>", "time": "HH:mm" }]',
  ].join("\n");
}

/** Best-effort parse of a model response into [{id,time}]. Untrusted — client normalizer is authority. */
function parseAssignments(text: string): Array<{ id: string; time: string }> {
  if (!text) return [];
  let raw = text.trim();
  // Strip markdown code fences if the model added them.
  const fence = raw.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fence) raw = fence[1].trim();
  // Isolate the first JSON array if there is surrounding prose.
  if (!raw.startsWith("[")) {
    const start = raw.indexOf("[");
    const end = raw.lastIndexOf("]");
    if (start !== -1 && end !== -1 && end > start) raw = raw.slice(start, end + 1);
  }
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .map((entry: any) => ({
        id: String(entry?.id ?? ""),
        time: String(entry?.time ?? ""),
      }))
      .filter((a) => a.id !== "");
  } catch {
    return [];
  }
}

export const aiPlanDay = onRequest(
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
      schedulable,
      fixed = [],
      date = "",
      dayStart = "09:00",
      dayEnd = "21:00",
      appVersionCode = 0,
    } = req.body || {};

    if (!Array.isArray(schedulable) || schedulable.length === 0) {
      res.status(400).json({ error: "schedulable required" });
      return;
    }

    const db = getFirestore("preamble");

    try {
      // --- Resolve model (server-side config; kill-switch honored like aiChat) ---
      const config = await getAiConfig(db);
      if (config.killSwitch) {
        res.status(503).json({ error: "AI is temporarily disabled by admin." });
        return;
      }
      const model: string = config.chatModel || DEFAULT_MODEL;

      // --- CREDIT-CHARGED path: mirror aiChat's model-dependent balance check ---
      // Insufficient balance => 402 INSUFFICIENT_CREDITS, model is NOT called, nothing mutated.
      const userDocRef = db.doc(`users/${uid}`);
      const userDoc = await userDocRef.get();
      const userData = userDoc.data() || {};

      if (isMistralPremium(model)) {
        const usedField = mistralUsedField(model);
        const bonusField = mistralBonusField(model);
        const usedToday = (userData[usedField] ?? 0) as number;
        const bonusToday = (userData[bonusField] ?? 0) as number;
        const tokensLeft = getMistralTokensRemaining(model, usedToday, bonusToday);
        if (tokensLeft <= 0) {
          res.status(402).json({ success: false, error: "INSUFFICIENT_CREDITS" });
          return;
        }
      } else {
        const fmField = flashDailyMsgField(model);
        const fmUsed = (userData[fmField] ?? 0) as number;
        const fmLeft = getFlashMsgsRemaining(model, fmUsed);
        if (fmLeft === 0) {
          res.status(402).json({ success: false, error: "INSUFFICIENT_CREDITS" });
          return;
        }
      }

      const schedulableDtos: SchedulableDto[] = (schedulable as any[]).map((t) => ({
        id: String(t?.id ?? ""),
        title: String(t?.title ?? ""),
        priority: Number(t?.priority ?? 0),
      }));
      const fixedDtos: FixedDto[] = (Array.isArray(fixed) ? fixed : []).map((f: any) => ({
        start: String(f?.start ?? ""),
        end: f?.end != null ? String(f.end) : undefined,
      }));

      const prompt = buildPlanPrompt(schedulableDtos, fixedDtos, String(date), String(dayStart), String(dayEnd));

      // --- Call provider ---
      let responseText = "";
      let inputTokens = 0;
      let outputTokens = 0;

      if (isMistralModel(model)) {
        const mistralRes = await fetch("https://api.mistral.ai/v1/chat/completions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${MISTRAL_KEY}`,
          },
          body: JSON.stringify({
            model,
            messages: [{ role: "user", content: prompt }],
            response_format: { type: "json_object" },
          }),
        });
        if (!mistralRes.ok) {
          throw new Error(`Mistral error: ${mistralRes.status}`);
        }
        const json: any = await mistralRes.json();
        responseText = json.choices?.[0]?.message?.content || "";
        if (json.usage) {
          inputTokens = json.usage.prompt_tokens || 0;
          outputTokens = json.usage.completion_tokens || 0;
        }
      } else {
        const ai = new GoogleGenAI({ apiKey: GEMINI_KEY });
        const geminiModel = model.startsWith("gemini") ? model : DEFAULT_MODEL;
        const response = await ai.models.generateContent({
          model: geminiModel,
          contents: [{ role: "user", parts: [{ text: prompt }] }],
        });
        const parts = response.candidates?.[0]?.content?.parts;
        if (parts) {
          for (const part of parts) {
            if (part.text) responseText += part.text;
          }
        }
        const usage = (response as any).usageMetadata;
        if (usage) {
          inputTokens = usage.promptTokenCount || 0;
          outputTokens = usage.candidatesTokenCount || 0;
        }
      }

      const assignments = parseAssignments(responseText);

      // --- Deduct usage post-call, consistent with how aiChat charges ---
      const usageUpdate: Record<string, any> = {};
      const totalTokens = inputTokens + outputTokens;
      if (isMistralPremium(model)) {
        const usedField = mistralUsedField(model);
        usageUpdate[usedField] = FieldValue.increment(mistralWeightedTokens(model, totalTokens));
      } else {
        const fmField = flashDailyMsgField(model);
        usageUpdate[fmField] = FieldValue.increment(1);
      }
      if (Object.keys(usageUpdate).length > 0) {
        await userDocRef.set(usageUpdate, { merge: true });
      }

      console.log("aiPlanDay success", {
        uid,
        model,
        schedulable: schedulableDtos.length,
        fixed: fixedDtos.length,
        assignments: assignments.length,
        inputTokens,
        outputTokens,
        appVersionCode,
      });

      res.json({ success: true, assignments, model });
    } catch (err: any) {
      console.error("Plan day error:", err);
      res.status(500).json({
        success: false,
        error: err.message || "Internal error",
      });
    }
  }
);
