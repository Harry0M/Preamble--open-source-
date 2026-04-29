/**
 * Server-side memory extraction — runs asynchronously after each user message.
 * Ported from MemoryExtractor.kt.
 */
import { GoogleGenAI } from "@google/genai";
import type { Firestore } from "firebase-admin/firestore";

const EXTRACTION_PROMPT = `You are a memory triage agent. Given a user message, decide what (if anything) to save to LONG-TERM MEMORY.

## Two tiers — pick the right one:
MEMORY = permanent, cross-chat user facts. Survives forever. Shared across ALL chats.
  → identity (name, age, gender, location, job, timezone)
  → stable preferences (language, theme, work style, likes/dislikes)
  → long-term goals (career, health, learning targets)
  → enduring interests (hobbies, passions, favorite topics)
  → relationships (family names, key people)

CONTEXT = ephemeral, only relevant to THIS conversation. Do NOT save these.
  → what user just asked about, current task discussion
  → today's plan, today's mood, one-off decisions
  → generic knowledge questions
  → greetings, confirmations, task additions, reminders

## Output
JSON array. Each element: {"key":<snake_case>,"value":<short phrase ≤60 chars>,"category":<identity|preference|goal|interest|relationship>,"confidence":<0.7-1.0>}
Return [] if nothing qualifies as MEMORY.
No markdown, no prose, no explanation. ONLY the JSON array.

## Rules
- ONLY emit MEMORY-tier facts about the USER themselves.
- Confidence ≥ 0.7 or skip.
- Keys: lowercase snake_case english. Values: user's language or english.
- NEVER save: task content, reminders, generic questions, greetings, ephemeral plans.
- When unsure → []. Being conservative is correct.`;

interface ExtractedFact {
  key: string;
  value: string;
  category: string;
  confidence: number;
}

/**
 * Cheap local pre-filter so normal knowledge/chat turns do not trigger an
 * extra model call. The extractor prompt is still the source of truth.
 */
export function shouldAttemptMemoryExtraction(userMessage: string): boolean {
  const lower = userMessage.toLowerCase().trim();
  if (lower.length < 20) return false;

  const knowledgeQuestion =
    /^(what|who|when|where|why|how|explain|define|describe|tell me)\b/.test(lower) ||
    /^(kya|kaun|kab|kahan|kyu|kyon|kaise)\b/.test(lower);
  if (knowledgeQuestion && !/\b(i am|i'm|my|me|mera|mujhe|main|mein)\b/.test(lower)) {
    return false;
  }

  const durableSignals = [
    "my name is", "i am", "i'm", "i work", "i live", "i study",
    "i like", "i love", "i hate", "i prefer", "my goal", "my dream",
    "my wife", "my husband", "my sister", "my brother", "my friend",
    "mera naam", "main ", "mein ", "mujhe pasand", "meri goal",
  ];
  return durableSignals.some(signal => lower.includes(signal));
}

/**
 * Extract durable memory facts from user message and save to Firestore.
 * Runs asynchronously — errors are swallowed.
 */
export async function extractMemoryFacts(
  uid: string,
  userMessage: string,
  apiKey: string,
  db: Firestore,
  model = "gemini-2.5-flash-lite",
): Promise<number> {
  try {
    const ai = new GoogleGenAI({ apiKey });
    const response = await ai.models.generateContent({
      model,
      contents: [{ role: "user", parts: [{ text: userMessage }] }],
      config: {
        systemInstruction: EXTRACTION_PROMPT,
        temperature: 0,
        maxOutputTokens: 192,
        responseMimeType: "application/json",
      } as any,
    });

    const text = response.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
    if (!text) return 0;

    // Parse JSON array from response
    const jsonMatch = text.match(/\[[\s\S]*\]/);
    if (!jsonMatch) return 0;

    const facts: ExtractedFact[] = JSON.parse(jsonMatch[0]);
    if (!Array.isArray(facts) || facts.length === 0) return 0;

    const allowedCategories = new Set([
      "identity", "preference", "goal", "interest", "context", "relationship",
    ]);

    let saved = 0;
    const memRef = db.collection(`users/${uid}/ai_memory`);

    for (const fact of facts) {
      if (!fact.key || !fact.value) continue;
      if (fact.confidence < 0.7) continue;

      const cleanKey = fact.key.trim().toLowerCase();
      const cleanValue = fact.value.trim();
      const category = allowedCategories.has(fact.category) ? fact.category : "context";

      // Check for existing key — update if exists
      const existing = await memRef.where("key", "==", cleanKey).limit(1).get();
      const now = Date.now();

      if (!existing.empty) {
        const doc = existing.docs[0];
        await doc.ref.update({
          value: cleanValue,
          category,
          confidence: Math.max(fact.confidence, doc.data().confidence || 0),
          lastUsedAt: now,
          source: "chat",
        });
      } else {
        await memRef.add({
          userId: uid,
          key: cleanKey,
          value: cleanValue,
          category,
          confidence: fact.confidence,
          source: "chat",
          createdAt: now,
          lastUsedAt: now,
          syncPending: 0,
        });
      }
      saved++;
    }

    return saved;
  } catch (err) {
    console.warn("Memory extraction failed:", err);
    return 0;
  }
}
