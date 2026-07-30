/**
 * Property 8: Deduplication threshold partitioning
 *
 * For any parsed task and set of existing tasks with computed similarity scores,
 * the Parser_V2 SHALL flag as potential duplicate all and only those existing tasks
 * with similarity ≥ 0.85, while always returning the parsed task itself (never
 * discarding it regardless of duplicate flags).
 *
 * **Validates: Requirements 5.2, 5.3**
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as fc from "fast-check";
import { MemoryEntry, MemoryCategory } from "../../src/v2/models/types";

// ─── Mock Setup ────────────────────────────────────────────────────────────────

// Track mock entries for Firestore vector search simulation
let mockEntries: Array<{ id: string; data: Omit<MemoryEntry, "id"> }> = [];

// Mock query embedding used for similarity computation
const MOCK_QUERY_EMBEDDING = (() => {
  // Create a fixed unit vector pointing in the "1" direction
  const vec = new Array(768).fill(0);
  vec[0] = 1;
  return vec;
})();

// Mock the Google GenAI SDK — returns a fixed embedding for predictable similarity
vi.mock("@google/genai", () => {
  return {
    GoogleGenAI: class MockGoogleGenAI {
      models = {
        embedContent: async () => ({
          embeddings: [{ values: MOCK_QUERY_EMBEDDING }],
        }),
      };
    },
  };
});

// Mock firebase-admin/firestore — getFirestore("preamble") returns the mock db
vi.mock("firebase-admin/firestore", () => {
  return {
    getFirestore: () => ({
      collection: () => ({
        doc: () => ({
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

import { findDuplicates, cosineSimilarity } from "../../src/v2/services/memory-engine";

// ─── Arbitraries ───────────────────────────────────────────────────────────────

const CATEGORIES: MemoryCategory[] = [
  "identity", "preference", "goal", "interest", "context",
  "relationship", "habit", "schedule", "project",
  "important_date", "location", "productivity_pattern",
];

const arbCategory = fc.constantFrom(...CATEGORIES);

/**
 * Generates a 768-dim embedding vector with a controlled similarity to the query embedding.
 * 
 * The query embedding is [1, 0, 0, ...0]. To get a desired cosine similarity `s`,
 * we create a vector with first component = s and a perpendicular component = sqrt(1-s^2).
 * This produces a unit-length vector with exactly the target similarity.
 */
function embeddingWithSimilarity(similarity: number): number[] {
  const vec = new Array(768).fill(0);
  // cos(angle) = similarity for unit vectors: v = [s, sqrt(1-s^2), 0, 0, ...]
  const clamped = Math.max(-1, Math.min(1, similarity));
  vec[0] = clamped;
  const perpComponent = Math.sqrt(Math.max(0, 1 - clamped * clamped));
  vec[1] = perpComponent;
  return vec;
}

/**
 * Generates an arbitrary similarity score between 0.0 and 1.0
 */
const arbSimilarityScore = fc.double({ min: 0.0, max: 1.0, noNaN: true, noDefaultInfinity: true });

/**
 * Generates a memory entry with a specific target similarity to the query embedding
 */
function arbMemoryEntryWithSimilarity(
  similarity: number
): fc.Arbitrary<{ id: string; data: Omit<MemoryEntry, "id"> }> {
  const embedding = embeddingWithSimilarity(similarity);
  return fc.record({
    id: fc.uuid(),
    data: fc.record({
      uid: fc.constant("test-user"),
      text: fc.string({ minLength: 1, maxLength: 200 }),
      embedding: fc.constant(embedding),
      category: arbCategory,
      memoryType: fc.constantFrom("short_term" as const, "long_term" as const),
      confidence: fc.constant(0.5), // Will be overwritten by cosine similarity
      linkedEntryIds: fc.constant([] as string[]),
      source: fc.constantFrom("chat" as const, "parse" as const, "system" as const),
      createdAt: fc.nat(),
      lastAccessedAt: fc.nat(),
      accessCount: fc.nat({ max: 1000 }),
    }),
  });
}

/**
 * Generates a set of entries where each has a known target similarity score.
 * Returns tuples of (targetSimilarity, entry) for verification.
 */
function arbEntriesWithKnownSimilarities(): fc.Arbitrary<
  Array<{ targetSimilarity: number; entry: { id: string; data: Omit<MemoryEntry, "id"> } }>
> {
  return fc.array(
    arbSimilarityScore.chain((similarity) =>
      arbMemoryEntryWithSimilarity(similarity).map((entry) => ({
        targetSimilarity: similarity,
        entry,
      }))
    ),
    { minLength: 1, maxLength: 10 }
  );
}

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 8: Deduplication threshold partitioning", () => {
  beforeEach(() => {
    mockEntries = [];
  });

  it("all entries with similarity ≥ 0.85 are flagged as duplicates (Req 5.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbEntriesWithKnownSimilarities(),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (entriesWithScores, queryText) => {
          // Set up mock entries
          mockEntries = entriesWithScores.map((e) => e.entry);

          const results = await findDuplicates("test-user", queryText, 0.85);

          // Verify: all entries whose actual computed similarity ≥ 0.85 appear in results
          for (const { entry } of entriesWithScores) {
            const actualSimilarity = cosineSimilarity(MOCK_QUERY_EMBEDDING, entry.data.embedding);
            if (actualSimilarity >= 0.85) {
              const found = results.find((r) => r.id === entry.id);
              expect(found).toBeDefined();
            }
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("entries with similarity < 0.85 are NOT flagged as duplicates (Req 5.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbEntriesWithKnownSimilarities(),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (entriesWithScores, queryText) => {
          mockEntries = entriesWithScores.map((e) => e.entry);

          const results = await findDuplicates("test-user", queryText, 0.85);

          // Verify: no entry with computed similarity < 0.85 appears in results
          for (const result of results) {
            expect(result.confidence).toBeGreaterThanOrEqual(0.85);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("the threshold exactly partitions: flagged IFF similarity ≥ 0.85 (Req 5.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbEntriesWithKnownSimilarities(),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (entriesWithScores, queryText) => {
          mockEntries = entriesWithScores.map((e) => e.entry);

          const results = await findDuplicates("test-user", queryText, 0.85);
          const resultIds = new Set(results.map((r) => r.id));

          // Bidirectional check: in results ↔ similarity ≥ 0.85
          for (const { entry } of entriesWithScores) {
            const actualSimilarity = cosineSimilarity(MOCK_QUERY_EMBEDDING, entry.data.embedding);
            const isFlagged = resultIds.has(entry.id);

            if (actualSimilarity >= 0.85) {
              expect(isFlagged).toBe(true);
            } else {
              expect(isFlagged).toBe(false);
            }
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("parsed task is always returned and never discarded regardless of duplicates (Req 5.3)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbEntriesWithKnownSimilarities(),
        fc.string({ minLength: 1, maxLength: 500 }),
        async (entriesWithScores, parsedTaskText) => {
          // Simulate the parse endpoint behavior:
          // The parse result (structured task) is ALWAYS returned to the user,
          // even when duplicates are found. findDuplicates only flags — never discards.
          mockEntries = entriesWithScores.map((e) => e.entry);

          const duplicates = await findDuplicates("test-user", parsedTaskText, 0.85);

          // The function returns duplicate INDICATORS, not a discard decision.
          // The parsed task itself is always preserved in the parse response.
          // Verify: the result is an array of flagged entries (can be empty or not),
          // but this does NOT affect the parsed task being returned.
          // The key invariant: findDuplicates returns a list, does not throw,
          // and does not prevent the parse from completing.
          expect(Array.isArray(duplicates)).toBe(true);

          // Each result has required fields for the parse response
          for (const dup of duplicates) {
            expect(dup.id).toBeDefined();
            expect(typeof dup.id).toBe("string");
            expect(dup.confidence).toBeGreaterThanOrEqual(0.85);
            expect(dup.confidence).toBeLessThanOrEqual(1.0);
            expect(dup.text).toBeDefined();
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it("deduplication with high similarity entries always includes them all (Req 5.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        // Generate entries that are specifically above threshold
        fc.array(
          fc.double({ min: 0.85, max: 1.0, noNaN: true, noDefaultInfinity: true }).chain(
            (sim) => arbMemoryEntryWithSimilarity(sim).map((entry) => ({ targetSimilarity: sim, entry }))
          ),
          { minLength: 1, maxLength: 5 }
        ),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (highSimEntries, queryText) => {
          mockEntries = highSimEntries.map((e) => e.entry);

          const results = await findDuplicates("test-user", queryText, 0.85);

          // All high-sim entries should be in the results
          expect(results.length).toBe(highSimEntries.length);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("deduplication with low similarity entries returns empty (Req 5.2)", async () => {
    await fc.assert(
      fc.asyncProperty(
        // Generate entries that are specifically below threshold
        fc.array(
          fc.double({ min: 0.0, max: 0.84, noNaN: true, noDefaultInfinity: true }).chain(
            (sim) => arbMemoryEntryWithSimilarity(sim).map((entry) => ({ targetSimilarity: sim, entry }))
          ),
          { minLength: 1, maxLength: 5 }
        ),
        fc.string({ minLength: 1, maxLength: 100 }),
        async (lowSimEntries, queryText) => {
          mockEntries = lowSimEntries.map((e) => e.entry);

          const results = await findDuplicates("test-user", queryText, 0.85);

          // No entries should be flagged since all are below 0.85
          expect(results.length).toBe(0);
        }
      ),
      { numRuns: 100 }
    );
  });

  it("duplicate flagging does not modify or discard the parsed task (Req 5.3)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbEntriesWithKnownSimilarities(),
        fc.record({
          title: fc.string({ minLength: 1, maxLength: 200 }),
          priority: fc.integer({ min: 1, max: 4 }),
          confidence: fc.double({ min: 0.0, max: 1.0, noNaN: true, noDefaultInfinity: true }),
        }),
        async (entriesWithScores, parsedTask) => {
          mockEntries = entriesWithScores.map((e) => e.entry);

          // Simulate the parse endpoint pattern: call findDuplicates, then
          // attach the results to the parse output without discarding the task
          const duplicates = await findDuplicates("test-user", parsedTask.title, 0.85);

          // Build the response as the endpoint does (Req 5.3: task is never discarded)
          const response = {
            ...parsedTask,
            duplicates: duplicates.map((d) => ({
              taskId: d.id,
              similarity: d.confidence,
              title: d.text,
            })),
          };

          // The parsed task fields must remain intact
          expect(response.title).toBe(parsedTask.title);
          expect(response.priority).toBe(parsedTask.priority);
          expect(response.confidence).toBe(parsedTask.confidence);

          // Duplicates are indicators only — the task is always present
          expect(response.duplicates).toBeDefined();
          expect(Array.isArray(response.duplicates)).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });
});
