/**
 * Property 17: Memory entry count cap
 *
 * Verify total entries never exceed 1000 per user and new entries trigger
 * eviction of least-recently-accessed when at the cap.
 *
 * **Validates: Requirements 10.1**
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as fc from "fast-check";
import { NewMemoryEntry, MemoryCategory } from "../../src/v2/models/types";

// ─── Mock State ────────────────────────────────────────────────────────────────

let mockEntryCount = 0;
let mockEvicted = false;
let mockStored = false;
let mockOrderByField: string | null = null;
let mockOrderByDirection: string | null = null;

const mockDelete = vi.fn(async () => {
  mockEvicted = true;
});
const mockAdd = vi.fn(async () => ({ id: "new-entry-id" }));

// ─── Mock Google GenAI SDK ─────────────────────────────────────────────────────

vi.mock("@google/genai", () => {
  return {
    GoogleGenAI: class MockGoogleGenAI {
      models = {
        embedContent: async () => ({
          embeddings: [{ values: new Array(768).fill(0.1) }],
        }),
      };
    },
  };
});

// ─── Mock Firestore ────────────────────────────────────────────────────────────

vi.mock("firebase-admin/firestore", () => {
  return {
    getFirestore: () => ({
      collection: (collectionName: string) => ({
        doc: (uid: string) => ({
          collection: () => ({
            count: () => ({
              get: async () => ({
                data: () => ({ count: mockEntryCount }),
              }),
            }),
            orderBy: (field: string, direction: string) => {
              mockOrderByField = field;
              mockOrderByDirection = direction;
              return {
                limit: (n: number) => ({
                  get: async () => ({
                    empty: mockEntryCount === 0,
                    docs: mockEntryCount > 0
                      ? [{ ref: { delete: mockDelete }, id: "oldest-entry" }]
                      : [],
                  }),
                }),
              };
            },
            add: async (doc: any) => {
              mockStored = true;
              return mockAdd(doc);
            },
          }),
        }),
      }),
    }),
  };
});

import { store } from "../../src/v2/services/memory-engine";

// ─── Constants ─────────────────────────────────────────────────────────────────

const MAX_ENTRIES = 1000;

const MEMORY_CATEGORIES: MemoryCategory[] = [
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
];

// ─── Arbitraries ───────────────────────────────────────────────────────────────

/** Generates a valid memory category */
const arbCategory = fc.constantFrom(...MEMORY_CATEGORIES);

/** Generates a valid memory type */
const arbMemoryType = fc.constantFrom("short_term" as const, "long_term" as const);

/** Generates a valid source */
const arbSource = fc.constantFrom("chat" as const, "parse" as const, "system" as const);

/** Generates a valid confidence score */
const arbConfidence = fc.double({ min: 0.0, max: 1.0, noNaN: true, noDefaultInfinity: true });

/** Generates a non-empty text string for a memory entry */
const arbText = fc.string({ minLength: 1, maxLength: 200 });

/** Generates a valid NewMemoryEntry */
const arbNewMemoryEntry: fc.Arbitrary<NewMemoryEntry> = fc.record({
  text: arbText,
  category: arbCategory,
  memoryType: arbMemoryType,
  confidence: arbConfidence,
  source: arbSource,
});

/** Generates a count at the cap (exactly 1000) */
const arbAtCapCount = fc.constant(MAX_ENTRIES);

/** Generates a count below the cap (0 to 999) */
const arbBelowCapCount = fc.integer({ min: 0, max: MAX_ENTRIES - 1 });

/** Generates a count at or above cap (1000 to 1100) */
const arbAtOrAboveCapCount = fc.integer({ min: MAX_ENTRIES, max: MAX_ENTRIES + 100 });

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 17: Memory entry count cap", () => {
  beforeEach(() => {
    mockEntryCount = 0;
    mockEvicted = false;
    mockStored = false;
    mockOrderByField = null;
    mockOrderByDirection = null;
    mockDelete.mockClear();
    mockAdd.mockClear();
  });

  it("evicts least-recently-accessed entry when at 1000 cap before storing new entry (Req 10.1)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbNewMemoryEntry,
        arbAtCapCount,
        async (newEntry, count) => {
          mockEntryCount = count;
          mockEvicted = false;
          mockStored = false;

          await store("test-user", newEntry);

          // When at cap (1000), eviction MUST happen before storing
          expect(mockEvicted).toBe(true);
          // New entry is still stored after eviction
          expect(mockStored).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("does NOT evict any entry when below the 1000 cap (Req 10.1)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbNewMemoryEntry,
        arbBelowCapCount,
        async (newEntry, count) => {
          mockEntryCount = count;
          mockEvicted = false;
          mockStored = false;

          await store("test-user", newEntry);

          // When below cap, no eviction should occur
          expect(mockEvicted).toBe(false);
          // New entry is stored directly
          expect(mockStored).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("after store at or above cap, eviction is always triggered (Req 10.1)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbNewMemoryEntry,
        arbAtOrAboveCapCount,
        async (newEntry, count) => {
          mockEntryCount = count;
          mockEvicted = false;
          mockStored = false;

          await store("test-user", newEntry);

          // When at or above cap, eviction is triggered ensuring total never exceeds 1000
          expect(mockEvicted).toBe(true);
          // One entry deleted + one added = net no increase
          expect(mockStored).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("eviction targets the entry with the oldest lastAccessedAt timestamp (Req 10.1)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbNewMemoryEntry,
        async (newEntry) => {
          mockEntryCount = MAX_ENTRIES;
          mockOrderByField = null;
          mockOrderByDirection = null;

          await store("test-user", newEntry);

          // Verify orderBy was called with "lastAccessedAt" ascending to find the oldest
          expect(mockOrderByField).toBe("lastAccessedAt");
          expect(mockOrderByDirection).toBe("asc");
        }
      ),
      { numRuns: 100 }
    );
  });

  it("store always adds exactly one new entry regardless of cap state (Req 10.1)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbNewMemoryEntry,
        fc.integer({ min: 0, max: MAX_ENTRIES + 50 }),
        async (newEntry, count) => {
          mockEntryCount = count;
          mockStored = false;
          mockAdd.mockClear();

          await store("test-user", newEntry);

          // store() always successfully adds exactly one new entry
          expect(mockAdd).toHaveBeenCalledTimes(1);
          expect(mockStored).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });
});
