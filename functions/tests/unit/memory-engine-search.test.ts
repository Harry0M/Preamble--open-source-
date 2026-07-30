/**
 * Unit tests for MemoryEngine search and retrieval functions.
 *
 * Validates: Requirements 10.2, 10.3, 10.4, 10.5, 11.3, 11.4, 11.5, 14.1
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// ─── Mock Setup ────────────────────────────────────────────────────────────────

const mockUpdate = vi.fn().mockResolvedValue(undefined);
const mockDocGet = vi.fn();
const mockFindNearestGet = vi.fn();

const mockDoc = vi.fn((id: string) => ({
  get: mockDocGet,
  update: mockUpdate,
  id,
}));

const mockFindNearest = vi.fn(() => ({
  get: mockFindNearestGet,
}));

const mockEntriesCollection = vi.fn(() => ({
  doc: mockDoc,
  findNearest: mockFindNearest,
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: vi.fn(() => ({
    collection: vi.fn(() => ({
      doc: vi.fn(() => ({
        collection: mockEntriesCollection,
      })),
    })),
  })),
}));

// Mock the GoogleGenAI embedding generation
vi.mock("@google/genai", () => ({
  GoogleGenAI: class MockGoogleGenAI {
    models = {
      embedContent: vi.fn().mockResolvedValue({
        embeddings: [{ values: new Array(768).fill(0.1) }],
      }),
    };
  },
}));

import {
  search,
  getLinkedEntries,
  findDuplicates,
  cosineSimilarity,
} from "../../src/v2/services/memory-engine";

// ─── Test Helpers ──────────────────────────────────────────────────────────────

function createMockEntry(overrides: Record<string, unknown> = {}) {
  return {
    uid: "user-123",
    text: "Test memory entry",
    embedding: new Array(768).fill(0.1), // Same as query for high similarity
    category: "context",
    memoryType: "long_term",
    confidence: 0.8,
    linkedEntryIds: [],
    source: "chat",
    createdAt: Date.now(),
    lastAccessedAt: Date.now(),
    accessCount: 3,
    ...overrides,
  };
}

function createMockDocSnapshot(id: string, data: Record<string, unknown> | null) {
  return {
    id,
    exists: data !== null,
    data: () => data,
    ref: { id, update: mockUpdate },
  };
}

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("MemoryEngine - Search & Retrieval", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdate.mockResolvedValue(undefined);
  });

  describe("search()", () => {
    it("returns entries ordered by descending similarity (Req 10.2)", async () => {
      const highSimilarityEntry = createMockEntry({
        text: "Very relevant",
        embedding: new Array(768).fill(0.1), // Same as query embedding
      });
      const lowerSimilarityEntry = createMockEntry({
        text: "Less relevant",
        embedding: new Array(768).fill(0.05), // Different from query
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [
          createMockDocSnapshot("entry-1", highSimilarityEntry),
          createMockDocSnapshot("entry-2", lowerSimilarityEntry),
        ],
      });

      const results = await search("user-123", "test query");

      expect(results.length).toBeGreaterThan(0);
      // Verify descending order
      for (let i = 1; i < results.length; i++) {
        expect(results[i - 1].confidence).toBeGreaterThanOrEqual(results[i].confidence);
      }
    });

    it("limits results to 10 by default (Req 10.2)", async () => {
      // Create 15 entries with the same embedding as query (high similarity)
      const docs = Array.from({ length: 15 }, (_, i) =>
        createMockDocSnapshot(`entry-${i}`, createMockEntry({
          embedding: new Array(768).fill(0.1),
        }))
      );

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs,
      });

      const results = await search("user-123", "test query");

      expect(results.length).toBeLessThanOrEqual(10);
    });

    it("respects custom limit option capped at 20 (Req 10.2)", async () => {
      const docs = Array.from({ length: 25 }, (_, i) =>
        createMockDocSnapshot(`entry-${i}`, createMockEntry({
          embedding: new Array(768).fill(0.1),
        }))
      );

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs,
      });

      const results = await search("user-123", "test query", { limit: 25 });

      expect(results.length).toBeLessThanOrEqual(20);
    });

    it("filters entries below minimum confidence threshold of 0.3 (Req 10.4)", async () => {
      // Create an entry with embedding that produces low similarity with the query
      const lowConfidenceEntry = createMockEntry({
        embedding: new Array(768).fill(-0.1), // Negative = low cosine similarity
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [
          createMockDocSnapshot("entry-low", lowConfidenceEntry),
        ],
      });

      const results = await search("user-123", "test query");

      // All results should be above 0.3 threshold
      for (const entry of results) {
        expect(entry.confidence).toBeGreaterThanOrEqual(0.3);
      }
    });

    it("supports custom minConfidence threshold", async () => {
      const entry = createMockEntry({
        embedding: new Array(768).fill(0.1), // High similarity
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [createMockDocSnapshot("entry-1", entry)],
      });

      const results = await search("user-123", "test query", { minConfidence: 0.99 });

      // With threshold 0.99, most entries would be filtered
      for (const result of results) {
        expect(result.confidence).toBeGreaterThanOrEqual(0.99);
      }
    });

    it("filters by category when specified", async () => {
      const identityEntry = createMockEntry({
        category: "identity",
        embedding: new Array(768).fill(0.1),
      });
      const goalEntry = createMockEntry({
        category: "goal",
        embedding: new Array(768).fill(0.1),
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [
          createMockDocSnapshot("entry-identity", identityEntry),
          createMockDocSnapshot("entry-goal", goalEntry),
        ],
      });

      const results = await search("user-123", "test query", {
        categories: ["identity"],
      });

      for (const result of results) {
        expect(result.category).toBe("identity");
      }
    });

    it("filters by memoryType when specified", async () => {
      const shortTermEntry = createMockEntry({
        memoryType: "short_term",
        embedding: new Array(768).fill(0.1),
      });
      const longTermEntry = createMockEntry({
        memoryType: "long_term",
        embedding: new Array(768).fill(0.1),
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [
          createMockDocSnapshot("entry-short", shortTermEntry),
          createMockDocSnapshot("entry-long", longTermEntry),
        ],
      });

      const results = await search("user-123", "test query", {
        memoryType: "long_term",
      });

      for (const result of results) {
        expect(result.memoryType).toBe("long_term");
      }
    });

    it("returns empty array when vector search returns no results", async () => {
      mockFindNearestGet.mockResolvedValue({
        empty: true,
        docs: [],
      });

      const results = await search("user-123", "test query");

      expect(results).toEqual([]);
    });

    it("returns empty array on timeout exceeding 5 seconds (Req 10.5)", async () => {
      // Simulate a slow operation that exceeds 5 seconds
      mockFindNearestGet.mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve({ empty: true, docs: [] }), 10000))
      );

      const results = await search("user-123", "test query");

      expect(results).toEqual([]);
    }, 10000);

    it("assigns computed similarity as confidence score (Req 10.3)", async () => {
      const entry = createMockEntry({
        embedding: new Array(768).fill(0.1), // Identical to query embedding
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [createMockDocSnapshot("entry-1", entry)],
      });

      const results = await search("user-123", "test query");

      if (results.length > 0) {
        // Confidence should be between 0.0 and 1.0 (allowing small floating point error)
        expect(results[0].confidence).toBeGreaterThanOrEqual(0);
        expect(results[0].confidence).toBeCloseTo(1.0, 5);
      }
    });

    it("returns all memory types when memoryType is 'all'", async () => {
      const shortTermEntry = createMockEntry({
        memoryType: "short_term",
        embedding: new Array(768).fill(0.1),
      });
      const longTermEntry = createMockEntry({
        memoryType: "long_term",
        embedding: new Array(768).fill(0.1),
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [
          createMockDocSnapshot("entry-short", shortTermEntry),
          createMockDocSnapshot("entry-long", longTermEntry),
        ],
      });

      const results = await search("user-123", "test query", { memoryType: "all" });

      expect(results.length).toBe(2);
    });
  });

  describe("getLinkedEntries()", () => {
    it("retrieves linked entries above confidence threshold (Req 11.5)", async () => {
      const sourceEntry = createMockEntry({
        linkedEntryIds: ["linked-1", "linked-2"],
      });
      const linkedEntry1 = createMockEntry({ confidence: 0.8 });
      const linkedEntry2 = createMockEntry({ confidence: 0.5 });

      // First call is for the source entry
      let callIndex = 0;
      mockDoc.mockImplementation((id: string) => ({
        get: vi.fn().mockImplementation(() => {
          if (id === "source-entry") {
            return Promise.resolve(createMockDocSnapshot("source-entry", sourceEntry));
          }
          if (id === "linked-1") {
            return Promise.resolve(createMockDocSnapshot("linked-1", linkedEntry1));
          }
          if (id === "linked-2") {
            return Promise.resolve(createMockDocSnapshot("linked-2", linkedEntry2));
          }
          return Promise.resolve(createMockDocSnapshot(id, null));
        }),
        update: mockUpdate,
        id,
      }));

      const results = await getLinkedEntries("user-123", "source-entry");

      // All results should be above 0.3 threshold
      for (const result of results) {
        expect(result.confidence).toBeGreaterThanOrEqual(0.3);
      }
    });

    it("returns at most 5 linked entries (Req 11.5)", async () => {
      const linkedIds = Array.from({ length: 10 }, (_, i) => `linked-${i}`);
      const sourceEntry = createMockEntry({ linkedEntryIds: linkedIds });

      mockDoc.mockImplementation((id: string) => ({
        get: vi.fn().mockImplementation(() => {
          if (id === "source-entry") {
            return Promise.resolve(createMockDocSnapshot("source-entry", sourceEntry));
          }
          // All linked entries are valid
          return Promise.resolve(
            createMockDocSnapshot(id, createMockEntry({ confidence: 0.8 }))
          );
        }),
        update: mockUpdate,
        id,
      }));

      const results = await getLinkedEntries("user-123", "source-entry");

      expect(results.length).toBeLessThanOrEqual(5);
    });

    it("sorts linked entries by confidence descending (Req 11.5)", async () => {
      const sourceEntry = createMockEntry({
        linkedEntryIds: ["linked-1", "linked-2", "linked-3"],
      });

      mockDoc.mockImplementation((id: string) => ({
        get: vi.fn().mockImplementation(() => {
          if (id === "source-entry") {
            return Promise.resolve(createMockDocSnapshot("source-entry", sourceEntry));
          }
          if (id === "linked-1") {
            return Promise.resolve(createMockDocSnapshot("linked-1", createMockEntry({ confidence: 0.5 })));
          }
          if (id === "linked-2") {
            return Promise.resolve(createMockDocSnapshot("linked-2", createMockEntry({ confidence: 0.9 })));
          }
          if (id === "linked-3") {
            return Promise.resolve(createMockDocSnapshot("linked-3", createMockEntry({ confidence: 0.7 })));
          }
          return Promise.resolve(createMockDocSnapshot(id, null));
        }),
        update: mockUpdate,
        id,
      }));

      const results = await getLinkedEntries("user-123", "source-entry");

      for (let i = 1; i < results.length; i++) {
        expect(results[i - 1].confidence).toBeGreaterThanOrEqual(results[i].confidence);
      }
    });

    it("returns empty array when source entry doesn't exist", async () => {
      mockDoc.mockImplementation((id: string) => ({
        get: vi.fn().mockResolvedValue(createMockDocSnapshot(id, null)),
        update: mockUpdate,
        id,
      }));

      const results = await getLinkedEntries("user-123", "nonexistent");

      expect(results).toEqual([]);
    });

    it("returns empty array when source entry has no linked IDs", async () => {
      const sourceEntry = createMockEntry({ linkedEntryIds: [] });

      mockDoc.mockImplementation((id: string) => ({
        get: vi.fn().mockResolvedValue(createMockDocSnapshot(id, sourceEntry)),
        update: mockUpdate,
        id,
      }));

      const results = await getLinkedEntries("user-123", "source-entry");

      expect(results).toEqual([]);
    });

    it("skips linked entries that don't exist", async () => {
      const sourceEntry = createMockEntry({
        linkedEntryIds: ["exists", "missing"],
      });

      mockDoc.mockImplementation((id: string) => ({
        get: vi.fn().mockImplementation(() => {
          if (id === "source-entry") {
            return Promise.resolve(createMockDocSnapshot("source-entry", sourceEntry));
          }
          if (id === "exists") {
            return Promise.resolve(createMockDocSnapshot("exists", createMockEntry({ confidence: 0.8 })));
          }
          return Promise.resolve(createMockDocSnapshot(id, null));
        }),
        update: mockUpdate,
        id,
      }));

      const results = await getLinkedEntries("user-123", "source-entry");

      expect(results.length).toBe(1);
      expect(results[0].id).toBe("exists");
    });

    it("returns empty array on timeout exceeding 5 seconds (Req 10.5)", async () => {
      mockDoc.mockImplementation((id: string) => ({
        get: vi.fn().mockImplementation(
          () => new Promise((resolve) => setTimeout(() => resolve(createMockDocSnapshot(id, null)), 10000))
        ),
        update: mockUpdate,
        id,
      }));

      const results = await getLinkedEntries("user-123", "source-entry");

      expect(results).toEqual([]);
    }, 10000);
  });

  describe("findDuplicates()", () => {
    it("uses default threshold of 0.85", async () => {
      // Entry with identical embedding to query (similarity = 1.0)
      const duplicateEntry = createMockEntry({
        embedding: new Array(768).fill(0.1),
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [createMockDocSnapshot("dup-1", duplicateEntry)],
      });

      const results = await findDuplicates("user-123", "test text");

      // With identical embeddings, similarity should be 1.0 which is >= 0.85
      for (const result of results) {
        expect(result.confidence).toBeGreaterThanOrEqual(0.85);
      }
    });

    it("supports custom threshold", async () => {
      const entry = createMockEntry({
        embedding: new Array(768).fill(0.1),
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [createMockDocSnapshot("entry-1", entry)],
      });

      const results = await findDuplicates("user-123", "test text", 0.99);

      for (const result of results) {
        expect(result.confidence).toBeGreaterThanOrEqual(0.99);
      }
    });

    it("returns entries sorted by descending similarity", async () => {
      const entry1 = createMockEntry({
        embedding: new Array(768).fill(0.1), // High similarity
      });
      const entry2 = createMockEntry({
        embedding: new Array(768).fill(0.09), // Slightly lower
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [
          createMockDocSnapshot("entry-1", entry1),
          createMockDocSnapshot("entry-2", entry2),
        ],
      });

      const results = await findDuplicates("user-123", "test text", 0.5);

      for (let i = 1; i < results.length; i++) {
        expect(results[i - 1].confidence).toBeGreaterThanOrEqual(results[i].confidence);
      }
    });

    it("returns empty array when no results match", async () => {
      mockFindNearestGet.mockResolvedValue({
        empty: true,
        docs: [],
      });

      const results = await findDuplicates("user-123", "unique text");

      expect(results).toEqual([]);
    });

    it("returns empty array on timeout exceeding 5 seconds (Req 10.5)", async () => {
      mockFindNearestGet.mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve({ empty: true, docs: [] }), 10000))
      );

      const results = await findDuplicates("user-123", "test text");

      expect(results).toEqual([]);
    }, 10000);

    it("excludes entries below the duplicate threshold", async () => {
      // Entry with very different embedding (low similarity)
      const lowSimilarityEntry = createMockEntry({
        embedding: new Array(768).fill(-0.1), // Negative = very low cosine sim
      });

      mockFindNearestGet.mockResolvedValue({
        empty: false,
        docs: [createMockDocSnapshot("entry-low", lowSimilarityEntry)],
      });

      const results = await findDuplicates("user-123", "test text");

      // Should not include entries below 0.85 threshold
      for (const result of results) {
        expect(result.confidence).toBeGreaterThanOrEqual(0.85);
      }
    });
  });

  describe("cosineSimilarity() helper", () => {
    it("returns 1.0 for identical vectors", () => {
      const a = [1, 2, 3, 4, 5];
      const similarity = cosineSimilarity(a, a);
      expect(similarity).toBeCloseTo(1.0);
    });

    it("returns 0.0 for orthogonal vectors", () => {
      const a = [1, 0, 0];
      const b = [0, 1, 0];
      const similarity = cosineSimilarity(a, b);
      expect(similarity).toBeCloseTo(0.0);
    });

    it("returns -1.0 for opposite vectors", () => {
      const a = [1, 2, 3];
      const b = [-1, -2, -3];
      const similarity = cosineSimilarity(a, b);
      expect(similarity).toBeCloseTo(-1.0);
    });

    it("returns 0 for empty vectors", () => {
      expect(cosineSimilarity([], [])).toBe(0);
    });

    it("returns 0 for mismatched length vectors", () => {
      expect(cosineSimilarity([1, 2], [1, 2, 3])).toBe(0);
    });
  });
});
