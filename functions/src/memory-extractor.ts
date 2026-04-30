/**
 * Server-side memory extraction — runs asynchronously after each user message.
 * Ported from MemoryExtractor.kt.
 */
import { GoogleGenAI } from "@google/genai";
import type { Firestore } from "firebase-admin/firestore";

const EXTRACTION_PROMPT = `You are Preamble's long-term memory controller. Decide whether the latest USER message should change permanent memory.

Memory is durable, cross-chat user knowledge. It should help future replies without the user repeating themselves.

Save only facts that are:
- stable beyond this chat or likely useful for many future chats
- about the user or the user's close relationships
- explicitly stated by the user, not inferred from the assistant
- safe to store

Allowed categories:
- identity: name, age range, role, job/study, city/region, timezone, language
- preference: communication style, UI/style preferences, likes/dislikes, work style
- goal: long-term career, health, study, habit, or learning goals
- interest: enduring hobbies, favorite topics, recurring passions
- relationship: close people and their stable details
- context: stable background such as "night-shift worker", "preparing for UPSC", "runs a small shop"

Never save:
- passwords, OTPs, API keys, auth tokens, card/bank/government IDs, private exact addresses
- medical/legal/financial secrets unless user explicitly asks to remember a non-sensitive preference
- one-off tasks, reminders, shopping items, today's plan, current mood, greetings, confirmations
- generic knowledge questions or facts not about the user
- guesses, stereotypes, diagnoses, or anything the user did not clearly say

Use existing memory to avoid duplicates and handle corrections:
- If the message corrects an old fact, upsert the same key with the new value.
- If the user asks to forget/remove something, emit a delete op for the matching key.
- If a new fact overlaps an existing key, prefer updating that key over creating a near-duplicate.

Output ONLY a JSON array. No markdown, no prose.
Each item:
{"op":"upsert","key":"snake_case","value":"short phrase <=80 chars","category":"identity|preference|goal|interest|relationship|context","confidence":0.7-1.0}
or:
{"op":"delete","key":"snake_case","value":"","category":"context","confidence":0.8-1.0}

Return [] when nothing should change. Be conservative.`;

interface ExtractedFact {
  op?: string;
  key: string;
  value: string;
  category: string;
  confidence: number;
}

export interface ExistingMemoryFact {
  key: string;
  value: string;
  category?: string;
}

export function hasExplicitMemoryIntent(userMessage: string): boolean {
  const lower = userMessage.toLowerCase().trim();
  if (lower.length < 4) return false;
  return (
    /\b(remember|save|memorize|store|update|change|set|correct)\b.{0,80}\b(name|preference|goal|memory|remember)\b/.test(lower) ||
    /\b(name|preference|goal)\b.{0,40}\b(as|to|is)\b/.test(lower) ||
    /\b(call me|you can call me|my name is|my name's)\b/.test(lower) ||
    /\b(forget|remove|delete|don't remember|do not remember|stop remembering)\b/.test(lower) ||
    /\b(yaad rakh|yaad rakho|save kar|naam|mera naam|meri naam|bhool ja|bhul ja|yaad mat rakh|hata do)\b/.test(lower)
  );
}

/**
 * Cheap local pre-filter so normal knowledge/chat turns do not trigger an
 * extra model call. The extractor prompt is still the source of truth.
 */
export function shouldAttemptMemoryExtraction(userMessage: string): boolean {
  const lower = userMessage.toLowerCase().trim();
  if (lower.length < 12) return false;

  if (hasExplicitMemoryIntent(userMessage)) {
    return true;
  }

  const knowledgeQuestion =
    /^(what|who|when|where|why|how|explain|define|describe|tell me)\b/.test(lower) ||
    /^(kya|kaun|kab|kahan|kyu|kyon|kaise)\b/.test(lower);
  if (knowledgeQuestion && !/\b(i am|i'm|my|me|mera|mujhe|main|mein)\b/.test(lower)) {
    return false;
  }

  const durableSignals = [
    "my name is", "i am", "i'm", "i work", "i live", "i study",
    "i like", "i love", "i hate", "i prefer", "my goal", "my dream",
    "my plan is to", "i want to become", "i'm trying to", "i am trying to",
    "i usually", "i always", "i mostly", "i often", "i never",
    "my timezone", "my city", "my job", "my college", "my school",
    "my wife", "my husband", "my sister", "my brother", "my friend",
    "my father", "my mother", "my son", "my daughter",
    "mera naam", "meri naam", "main ", "mein ", "mujhe pasand", "meri goal",
    "mera goal", "main chahta", "main chahti", "main padh", "main kaam",
  ];
  return durableSignals.some(signal => lower.includes(signal));
}

function cleanNameCandidate(value: string): string {
  const name = cleanValue(value)
    .replace(/^["'`]+|["'`]+$/g, "")
    .replace(/\b(please|pls|thanks|thank you)\b.*$/i, "")
    .replace(/\b(and|aur|but|lekin)\b.*$/i, "")
    .replace(/\b(hai|he|hain|is)$/i, "")
    .replace(/[.!?,;:]+$/g, "")
    .trim()
    .slice(0, 60);
  if (/^(what|who|kya|kaun|kab|kaise|why|how)\b/i.test(name)) return "";
  return name;
}

function deterministicMemoryFacts(userMessage: string): ExtractedFact[] {
  const text = userMessage.trim();
  const lower = text.toLowerCase();

  if ((/\b(forget|remove|delete|don't remember|do not remember|stop remembering)\b/.test(lower) ||
       /\b(yaad mat rakh|bhool ja|bhul ja|delete kar|hata do)\b/.test(lower)) &&
      /\b(name|naam)\b/.test(lower)) {
    return [{ op: "delete", key: "name", value: "", category: "identity", confidence: 0.95 }];
  }

  const namePatterns = [
    /\b(?:save|remember|memorize|store|update|change|set|correct)\s+(?:my\s+)?name\s+(?:as|to|is)\s+([A-Za-z][A-Za-z .'-]{1,60})/i,
    /\b(?:my name is|my name's|call me|you can call me)\s+([A-Za-z][A-Za-z .'-]{1,60})/i,
    /\b(?:mera naam|meri naam)\s+([A-Za-z][A-Za-z .'-]{1,60})(?:\s+hai|\s+he|\s+hain)?/i,
  ];

  for (const pattern of namePatterns) {
    const match = text.match(pattern);
    const name = match?.[1] ? cleanNameCandidate(match[1]) : "";
    if (name && name.length <= 60) {
      return [{ op: "upsert", key: "name", value: name, category: "identity", confidence: 0.98 }];
    }
  }

  return [];
}

function buildExtractionUserPrompt(
  userMessage: string,
  existingMemories: ExistingMemoryFact[] = [],
): string {
  const memoryRows = existingMemories.slice(0, 40).map(m => ({
    key: String(m.key || "").slice(0, 64),
    value: String(m.value || "").slice(0, 140),
    category: String(m.category || "context").slice(0, 32),
  })).filter(m => m.key && m.value);

  return JSON.stringify({
    existing_memory: memoryRows,
    latest_user_message: userMessage,
  });
}

function cleanKey(value: unknown): string {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 64);
}

function cleanValue(value: unknown): string {
  return String(value ?? "")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 120);
}

function isLikelySensitiveMemory(key: string, value: string): boolean {
  const combined = `${key} ${value}`.toLowerCase();
  if (/\b(password|passcode|otp|one[_\s-]?time|pin|api[_\s-]?key|secret|token|bearer|private[_\s-]?key)\b/.test(combined)) return true;
  if (/\b(card|credit|debit|cvv|cvc|bank|account|ifsc|routing|ssn|aadhaar|aadhar|pan card|passport)\b/.test(combined)) return true;
  if (/\b\d{12,19}\b/.test(value.replace(/[\s-]/g, ""))) return true;
  if (/AIza[0-9A-Za-z_-]{20,}/.test(value)) return true;
  if (/sk-[0-9A-Za-z_-]{20,}/.test(value)) return true;
  return false;
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
  existingMemories: ExistingMemoryFact[] = [],
): Promise<number> {
  try {
    let facts: ExtractedFact[] = deterministicMemoryFacts(userMessage);
    if (facts.length === 0) {
      const ai = new GoogleGenAI({ apiKey });
      const extractionInput = buildExtractionUserPrompt(userMessage, existingMemories);
      const response = await ai.models.generateContent({
        model,
        contents: [{ role: "user", parts: [{ text: extractionInput }] }],
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

      facts = JSON.parse(jsonMatch[0]);
    }
    if (!Array.isArray(facts) || facts.length === 0) return 0;

    const allowedCategories = new Set(["identity", "preference", "goal", "interest", "context", "relationship"]);
    const allowedOps = new Set(["upsert", "delete"]);

    let saved = 0;
    const memRef = db.collection(`users/${uid}/ai_memory`);

    for (const fact of facts) {
      const op = allowedOps.has(String(fact.op || "upsert")) ? String(fact.op || "upsert") : "upsert";
      if (!fact.key) continue;
      if (fact.confidence < 0.7) continue;

      const key = cleanKey(fact.key);
      const value = cleanValue(fact.value);
      const category = allowedCategories.has(fact.category) ? fact.category : "context";
      if (!key) continue;
      if (op === "upsert" && !value) continue;
      if (op === "upsert" && isLikelySensitiveMemory(key, value)) continue;

      const existing = await memRef.where("key", "==", key).limit(10).get();
      const now = Date.now();

      if (op === "delete") {
        if (!existing.empty) {
          await Promise.all(existing.docs.map(doc => doc.ref.delete()));
          saved++;
        }
        continue;
      }

      if (!existing.empty) {
        const doc = existing.docs[0];
        const prev = doc.data();
        const sameValue = String(prev.value || "").trim().toLowerCase() === value.toLowerCase();
        await doc.ref.update({
          value: sameValue && (prev.confidence || 0) > fact.confidence ? prev.value : value,
          category,
          confidence: Math.max(fact.confidence, doc.data().confidence || 0),
          lastUsedAt: now,
          updatedAt: now,
          source: "chat",
        });
        for (const duplicate of existing.docs.slice(1)) {
          await duplicate.ref.delete();
        }
      } else {
        await memRef.add({
          userId: uid,
          key,
          value,
          category,
          confidence: fact.confidence,
          source: "chat",
          createdAt: now,
          lastUsedAt: now,
          updatedAt: now,
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
