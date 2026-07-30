/**
 * Test utility helpers: fast-check arbitraries for generating
 * arbitrary memory entries, model configs, and parse inputs.
 *
 * Used by property-based tests throughout the V2 ecosystem.
 */
import * as fc from "fast-check";

// ─── Memory Category ────────────────────────────────────────────────────────

export const MEMORY_CATEGORIES = [
  "identity",
  "preference",
  "goal",
  "interest",
  "context",
  "relationship",
  "habit",
  "schedule",
  "project",
  "important_date",
  "location",
  "productivity_pattern",
] as const;

export type MemoryCategory = (typeof MEMORY_CATEGORIES)[number];

export const arbMemoryCategory: fc.Arbitrary<MemoryCategory> =
  fc.constantFrom(...MEMORY_CATEGORIES);

// ─── Memory Entry ───────────────────────────────────────────────────────────

export interface MemoryEntry {
  id: string;
  uid: string;
  text: string;
  embedding: number[];
  category: MemoryCategory;
  memoryType: "short_term" | "long_term";
  confidence: number;
  linkedEntryIds: string[];
  source: "chat" | "parse" | "system";
  createdAt: number;
  lastAccessedAt: number;
  accessCount: number;
  conversationId?: string;
}

export const arbEmbedding768: fc.Arbitrary<number[]> = fc.array(
  fc.double({ min: -1, max: 1, noNaN: true }),
  { minLength: 768, maxLength: 768 }
);

export const arbMemoryEntry: fc.Arbitrary<MemoryEntry> = fc.record({
  id: fc.uuid(),
  uid: fc.uuid(),
  text: fc.string({ minLength: 1, maxLength: 500 }),
  embedding: arbEmbedding768,
  category: arbMemoryCategory,
  memoryType: fc.constantFrom("short_term" as const, "long_term" as const),
  confidence: fc.double({ min: 0, max: 1, noNaN: true }),
  linkedEntryIds: fc.array(fc.uuid(), { minLength: 0, maxLength: 20 }),
  source: fc.constantFrom("chat" as const, "parse" as const, "system" as const),
  createdAt: fc.integer({ min: 1_600_000_000_000, max: 2_000_000_000_000 }),
  lastAccessedAt: fc.integer({ min: 1_600_000_000_000, max: 2_000_000_000_000 }),
  accessCount: fc.nat({ max: 10_000 }),
  conversationId: fc.option(fc.uuid(), { nil: undefined }),
});

/**
 * Generate a memory entry with a confidence score above the retrieval threshold (0.3).
 */
export const arbRelevantMemoryEntry: fc.Arbitrary<MemoryEntry> = arbMemoryEntry.map(
  (entry) => ({
    ...entry,
    confidence: 0.3 + entry.confidence * 0.7, // Scale to [0.3, 1.0]
  })
);

// ─── Model Config ───────────────────────────────────────────────────────────

export interface ModelConfig {
  provider: "google" | "anthropic" | "openai" | "mistral";
  modelId: string;
  displayName: string;
  costPerMillionTokens: number;
  supportsReasoning: boolean;
  maxContextWindow: number;
  enabled: boolean;
  isDefault: boolean;
}

export const arbModelConfig: fc.Arbitrary<ModelConfig> = fc.record({
  provider: fc.constantFrom(
    "google" as const,
    "anthropic" as const,
    "openai" as const,
    "mistral" as const
  ),
  modelId: fc.string({ minLength: 1, maxLength: 200 }),
  displayName: fc.string({ minLength: 1, maxLength: 60 }),
  costPerMillionTokens: fc.double({ min: 0.01, max: 100, noNaN: true }),
  supportsReasoning: fc.boolean(),
  maxContextWindow: fc.integer({ min: 1024, max: 2_000_000 }),
  enabled: fc.boolean(),
  isDefault: fc.boolean(),
});

/**
 * Generate an enabled model config (useful when testing routing logic).
 */
export const arbEnabledModelConfig: fc.Arbitrary<ModelConfig> = arbModelConfig.map(
  (config) => ({ ...config, enabled: true })
);

// ─── Parse Input ────────────────────────────────────────────────────────────

export interface ParseInput {
  text: string;
  timezone: string;
  currentDateTime: string;
  memories: MemoryEntry[];
}

/** Common IANA timezone strings for generating realistic parse inputs. */
const TIMEZONES = [
  "Asia/Kolkata",
  "America/New_York",
  "America/Los_Angeles",
  "Europe/London",
  "Europe/Berlin",
  "Asia/Tokyo",
  "Australia/Sydney",
  "Pacific/Auckland",
  "America/Chicago",
  "Asia/Dubai",
  "Asia/Shanghai",
  "America/Sao_Paulo",
] as const;

export const arbTimezone: fc.Arbitrary<string> = fc.constantFrom(...TIMEZONES);

/** Generate a valid ISO 8601 datetime string. */
export const arbIsoDatetime: fc.Arbitrary<string> = fc
  .record({
    year: fc.integer({ min: 2020, max: 2030 }),
    month: fc.integer({ min: 1, max: 12 }),
    day: fc.integer({ min: 1, max: 28 }), // Stay safe with all months
    hour: fc.integer({ min: 0, max: 23 }),
    minute: fc.integer({ min: 0, max: 59 }),
    second: fc.integer({ min: 0, max: 59 }),
  })
  .map(
    ({ year, month, day, hour, minute, second }) =>
      `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}T${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}:${String(second).padStart(2, "0")}`
  );

/**
 * Generate valid parse input text (1-500 characters, non-empty, not whitespace-only).
 * Uses unicode_string to allow multilingual content.
 */
export const arbParseText: fc.Arbitrary<string> = fc
  .string({ minLength: 1, maxLength: 500 })
  .filter((s) => s.trim().length > 0);

export const arbParseInput: fc.Arbitrary<ParseInput> = fc.record({
  text: arbParseText,
  timezone: arbTimezone,
  currentDateTime: arbIsoDatetime,
  memories: fc.array(arbMemoryEntry, { minLength: 0, maxLength: 10 }),
});

/**
 * Generate invalid parse input text (empty, whitespace-only, or >500 chars).
 * Useful for testing input rejection.
 */
export const arbInvalidParseText: fc.Arbitrary<string> = fc.oneof(
  fc.constant(""),
  fc.array(fc.constantFrom(" ", "\t", "\n", "\r"), { minLength: 1, maxLength: 50 }).map((arr) => arr.join("")),
  fc.string({ minLength: 501, maxLength: 1000 })
);

// ─── Token Usage ────────────────────────────────────────────────────────────

export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
  model: string;
  costPerMillionTokens: number;
  normalizedCost: number;
  traceId: string;
}

export const arbTokenUsage: fc.Arbitrary<TokenUsage> = fc
  .record({
    inputTokens: fc.nat({ max: 1_000_000 }),
    outputTokens: fc.nat({ max: 100_000 }),
    model: fc.string({ minLength: 1, maxLength: 100 }),
    costPerMillionTokens: fc.double({ min: 0.01, max: 100, noNaN: true }),
    traceId: fc.uuid(),
  })
  .map(({ inputTokens, outputTokens, model, costPerMillionTokens, traceId }) => ({
    inputTokens,
    outputTokens,
    model,
    costPerMillionTokens,
    normalizedCost:
      ((inputTokens + outputTokens) * costPerMillionTokens) / 1_000_000,
    traceId,
  }));

// ─── Parse Response (for schema validation tests) ───────────────────────────

export interface ParseResponse {
  title: string;
  confidence: number;
  date?: string;
  time?: string;
  priority?: number;
  tags?: string[];
  recurrence?: {
    recurrenceType: "daily" | "weekly" | "monthly" | "yearly";
    recurrenceInterval: number;
    recurrenceDays?: number[];
    recurrenceEndDate?: string;
  };
  description?: string;
  detectedLanguage?: string;
  duplicates?: Array<{
    taskId: string;
    similarity: number;
    title: string;
  }>;
}

/** Generate a valid parse response conforming to the JSON Schema. */
export const arbValidParseResponse: fc.Arbitrary<ParseResponse> = fc.record(
  {
    title: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.double({ min: 0, max: 1, noNaN: true }),
    date: fc.option(
      fc
        .record({
          year: fc.integer({ min: 2020, max: 2030 }),
          month: fc.integer({ min: 1, max: 12 }),
          day: fc.integer({ min: 1, max: 28 }),
        })
        .map(
          ({ year, month, day }) =>
            `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`
        ),
      { nil: undefined }
    ),
    time: fc.option(
      fc
        .record({
          hour: fc.integer({ min: 0, max: 23 }),
          minute: fc.integer({ min: 0, max: 59 }),
        })
        .map(
          ({ hour, minute }) =>
            `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`
        ),
      { nil: undefined }
    ),
    priority: fc.option(fc.integer({ min: 1, max: 4 }), { nil: undefined }),
    tags: fc.option(
      fc.array(fc.string({ minLength: 1, maxLength: 50 }), {
        minLength: 0,
        maxLength: 10,
      }),
      { nil: undefined }
    ),
    recurrence: fc.option(
      fc.record(
        {
          recurrenceType: fc.constantFrom(
            "daily" as const,
            "weekly" as const,
            "monthly" as const,
            "yearly" as const
          ),
          recurrenceInterval: fc.integer({ min: 1, max: 365 }),
          recurrenceDays: fc.option(
            fc.array(fc.integer({ min: 0, max: 6 }), { minLength: 1, maxLength: 7 }),
            { nil: undefined }
          ),
          recurrenceEndDate: fc.option(
            fc
              .record({
                year: fc.integer({ min: 2020, max: 2030 }),
                month: fc.integer({ min: 1, max: 12 }),
                day: fc.integer({ min: 1, max: 28 }),
              })
              .map(
                ({ year, month, day }) =>
                  `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`
              ),
            { nil: undefined }
          ),
        },
        { requiredKeys: ["recurrenceType", "recurrenceInterval"] }
      ),
      { nil: undefined }
    ),
    description: fc.option(fc.string({ minLength: 0, maxLength: 1000 }), {
      nil: undefined,
    }),
    detectedLanguage: fc.option(fc.string({ minLength: 2, maxLength: 10 }), {
      nil: undefined,
    }),
    duplicates: fc.option(
      fc.array(
        fc.record({
          taskId: fc.uuid(),
          similarity: fc.double({ min: 0, max: 1, noNaN: true }),
          title: fc.string({ minLength: 1, maxLength: 200 }),
        }),
        { minLength: 0, maxLength: 5 }
      ),
      { nil: undefined }
    ),
  },
  { requiredKeys: ["title", "confidence"] }
);
