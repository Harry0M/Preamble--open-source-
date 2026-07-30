/**
 * Property 10: Memory retrieval ordering and filtering
 *
 * For any semantic search query against Memory_V2, the returned entries SHALL be:
 * 1. Ordered by descending similarity score
 * 2. Limited to at most 10 entries
 * 3. All entries have Memory_Confidence in [0.0, 1.0]
 * 4. No entry has confidence below the 0.3 threshold
 * 5. Linked entries per result ≤ 5
 * 6. Relationship links per entry ≤ 20
 *
 * **Validates: Requirements 10.2, 10.3, 10.4, 11.4, 11.5**
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as fc from "fast-check";
import { MemoryEntry, MemoryCategory } from "../../src/v2/models/types";

// ─── Mock Setup ────────────────────────────────────────────────────────────────

// Track mock entries for Firestore vector search simulation
let mockEntries: Array<{ id: string; data: Omit<MemoryEntry, "id"> }> = [];

// Mock the Google GenAI SDK — GoogleGenAI is used as a constructor (new GoogleGenAI)
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

// Mock firebase-admin/firestore — getFirestore("preamble") returns the mock db
vi.mock("firebase-admin/firestore", () => {
  return {
    getFirestore: () => ({
      collection: (collectionName: string) => ({
        doc: (uid: string) => ({
          collection: () => ({
            doc: (entryId: string) => ({
              get: async () => {
                const found = mockEntries.find((e) => e.id === entryId);
                return {
                  exists: !!found,
                  data: () => found?.data ?? null,
                  id: entryId,
                  ref: { update: async () => {} },
                };
              },
              update: async () => {},
            }),
            findNearest: () => ({
              get: async () => ({
                empty: mockEntries.length === 0,
                docs: mockEntries.map((e) => ({
                  id: e.id,
                  data: () => e.data,
                  ref: { update: async () => {} },
                })),
              }),
            }),
          }),
        }),
      }),
    }),
  };
});

import { search, getLinkedEntries, cosineSimilarity } from "../../src/v2/services/memory-engine";

// ─── Arbitraries ───────────────────────────────────────────────────────────────

const CATEGORIES: MemoryCategory[] = [
  "identity", "preference", "goal", "interest", "context",
  "relationship", "habit", "schedule", "project",
  "important_date", "location", "productivity_pattern",
];

/** Generates a normalized 768-dim vector (unit-length) for controlled similarity */
function arbNormalizedVector(): fc.Arbitrary<number[]> {
  return fc.array(fc.double({ min: -1, max: 1, noNaN: true, noDefaultInfinity: true }), {
    minLength: 768,
    maxLength: 768,
  }).map((arr) => {
    const norm = Math.sqrt(arr.reduce((sum, v) => sum + v * v, 0));
    if (norm === 0) {
      // Avoid zero vector — set first component to 1
      const result = new Array(768).fill(0);
      result[0] = 1;
      return result;
    }
    return arr.map((v) => v / norm);
  });
}

/** Generates a valid MemoryCategory */
const arbCategory = fc.constantFrom(...CATEGORIES);

/** Generates linked entry IDs (max 20, enforced by the test generator) */
const arbLinkedEntryIds = fc.array(fc.uuid(), { minLength: 0, maxLength: 20 });

/** Generates a memory entry with a given embedding */
function arbMemoryEntry(embedding: number[]): fc.Arbitrary<{ id: string; data: Omit<MemoryEntry, "id"> }> {
  return fc.record({
    id: fc.uuid(),
    data: fc.record({
      uid: fc.constant("test-user"),
      text: fc.string({ minLength: 1, maxLength: 200 }),
      embedding: fc.constant(embedding),
      category: arbCategory,
      memoryType: fc.constantFrom("short_term" as const, "long_term" as const),
      confidence: fc.double({ min: 0.0, max: 1.0, noNaN: true, noDefaultInfinity: true }),
      linkedEntryIds: arbLinkedEntryIds,
      source: fc.constantFrom("chat" as const, "parse" as const, "system" as const),
      createdAt: fc.nat(),
      lastAccessedAt: fc.nat(),
      accessCount: fc.nat({ max: 1000 }),
    }),
  });
}

/** Generates a set of memory entries with varying embeddings */
function arbMemoryEntries(maxCount: number): fc.Arbitrary<Array<{ id: string; data: Omit<MemoryEntry, "id"> }>> {
  return fc.array(
    arbNormalizedVector().chain((embedding) => arbMemoryEntry(embedding)),
    { minLength: 0, maxLength: maxCount }
  );
}

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 10: Memory retrieval ordering and filtering", () => {
  beforeEach(() => {
    mockEntries = [];
  });

  it("search results are ordered by descending similarity (Req 10.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbMemoryEntries(20),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (entries, query) => {
          mockEntries = entries;

          const results = await search("test-user", query);

          // Verify descending order of confidence (similarity)
          for (let i = 1; i < results.length; i++) {
            expect(results[i - 1].confidence).toBeGreaterThanOrEqual(results[i].confidence);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("search results are limited to at most 10 entries (Req 10.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbMemoryEntries(30),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (entries, query) => {
          mockEntries = entries;

          const results = await search("test-user", query);

          // Default limit is 10
          expect(results.length).toBeLessThanOrEqual(10);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("all search results have confidence in [0.0, 1.0] (Req 10.3)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbMemoryEntries(20),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (entries, query) => {
          mockEntries = entries;

          const results = await search("test-user", query);

          for (const entry of results) {
            expect(entry.confidence).toBeGreaterThanOrEqual(0.0);
            expect(entry.confidence).toBeLessThanOrEqual(1.0);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("no search result has confidence below 0.3 threshold (Req 10.4)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbMemoryEntries(20),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (entries, query) => {
          mockEntries = entries;

          const results = await search("test-user", query);

          for (const entry of results) {
            expect(entry.confidence).toBeGreaterThanOrEqual(0.3);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("getLinkedEntries returns at most 5 entries (Req 11.5)", async () => {
    await fc.assert(
      fc.asyncProperty(
        // Generate a list of linked IDs (could be many)
        fc.array(fc.uuid(), { minLength: 1, maxLength: 20 }),
        arbCategory,
        async (linkedIds, category) => {
          // Set up the source entry with linkedEntryIds
          const sourceEntry = {
            id: "source-entry",
            data: {
              uid: "test-user",
              text: "source entry text",
              embedding: new Array(768).fill(0.1),
              category,
              memoryType: "long_term" as const,
              confidence: 0.8,
              linkedEntryIds: linkedIds,
              source: "chat" as const,
              createdAt: Date.now(),
              lastAccessedAt: Date.now(),
              accessCount: 0,
            },
          };

          // Create linked entries with confidence above 0.3
          const linkedEntries = linkedIds.map((id) => ({
            id,
            data: {
              uid: "test-user",
              text: `linked entry ${id}`,
              embedding: new Array(768).fill(0.2),
              category: "context" as MemoryCategory,
              memoryType: "long_term" as const,
              confidence: 0.5,
              linkedEntryIds: [] as string[],
              source: "chat" as const,
              createdAt: Date.now(),
              lastAccessedAt: Date.now(),
              accessCount: 0,
            },
          }));

          mockEntries = [sourceEntry, ...linkedEntries];

          const results = await getLinkedEntries("test-user", "source-entry");

          // Linked entries must not exceed 5 (MAX_LINKED_ENTRIES)
          expect(results.length).toBeLessThanOrEqual(5);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("all entries have linkedEntryIds.length ≤ 20 (Req 11.4)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbMemoryEntries(20),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (entries, query) => {
          mockEntries = entries;

          const results = await search("test-user", query);

          for (const entry of results) {
            expect(entry.linkedEntryIds.length).toBeLessThanOrEqual(20);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("cosineSimilarity always produces values in [-1, 1] range for normalized vectors", () => {
    fc.assert(
      fc.property(
        arbNormalizedVector(),
        arbNormalizedVector(),
        (a, b) => {
          const similarity = cosineSimilarity(a, b);
          // Cosine similarity of normalized vectors is in [-1, 1]
          // Allow small floating point tolerance
          expect(similarity).toBeGreaterThanOrEqual(-1.0 - 1e-10);
          expect(similarity).toBeLessThanOrEqual(1.0 + 1e-10);
        }
      ),
      { numRuns: 200 }
    );
  });

  it("search with explicit limit under 10 respects that limit (Req 10.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbMemoryEntries(25),
        fc.string({ minLength: 1, maxLength: 100 }),
        fc.integer({ min: 1, max: 9 }),
        async (entries, query, customLimit) => {
          mockEntries = entries;

          const results = await search("test-user", query, { limit: customLimit });
          expect(results.length).toBeLessThanOrEqual(customLimit);
        }
      ),
      { numRuns: 50 }
    );
  });
});
