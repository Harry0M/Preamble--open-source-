/**
 * Property 15: V2 Firestore collection isolation
 *
 * For arbitrary V2 operations, verify all collection paths use V2-prefixed
 * collections (v2_memory, v2_conversations, v2_token_usage, v2_config) and
 * never read/write V1 collections.
 *
 * **Validates: Requirements 26.4**
 */
import { describe, it, expect } from "vitest";
import * as fc from "fast-check";

import {
  V2_COLLECTIONS,
  V2_COLLECTION_LIST,
  V1_FORBIDDEN_COLLECTIONS,
  V1_FORBIDDEN_SUBCOLLECTION_PATTERNS,
  V2IsolationViolationError,
  assertV2Collection,
  isV2Collection,
  memoryEntriesPath,
  memoryEntryPath,
  conversationThreadsPath,
  conversationThreadPath,
  tokenUsagePath,
  modelConfigPath,
} from "../../src/v2/utils/collection-paths";

// ─── Arbitraries ───────────────────────────────────────────────────────────────

/** UID character set */
const UID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-".split("");

/** Generates a valid UID (6-40 chars) */
const arbUid = fc
  .array(fc.constantFrom(...UID_CHARS), { minLength: 6, maxLength: 40 })
  .map((chars) => chars.join(""));

/** Generates a valid Firestore document ID (no slashes, 1-30 chars) */
const arbDocId = fc
  .array(fc.constantFrom(...UID_CHARS), { minLength: 1, maxLength: 30 })
  .map((chars) => chars.join(""));

/** Generates arbitrary V1 collection paths that should be rejected */
const arbV1Path = fc.oneof(
  // Direct V1 forbidden collections
  fc.constantFrom(...V1_FORBIDDEN_COLLECTIONS),
  // V1 forbidden collections with sub-paths
  fc.constantFrom(...V1_FORBIDDEN_COLLECTIONS).chain((col) =>
    arbDocId.map((doc) => `${col}/${doc}`)
  ),
  // V1 subcollection patterns under user documents
  arbUid.chain((uid) =>
    fc.constantFrom(
      `users/${uid}/ai_memory`,
      `users/${uid}/ai_memory/entry1`,
      `users/${uid}/ai_history`,
      `users/${uid}/ai_history/msg1`
    )
  )
);

/** Generates arbitrary valid V2 paths from the path helper functions */
const arbV2PathFromHelpers = fc.oneof(
  arbUid.map((uid) => memoryEntriesPath(uid)),
  fc.tuple(arbUid, arbDocId).map(([uid, entryId]) => memoryEntryPath(uid, entryId)),
  arbUid.map((uid) => conversationThreadsPath(uid)),
  fc.tuple(arbUid, arbDocId).map(([uid, convId]) => conversationThreadPath(uid, convId)),
  arbUid.map((uid) => tokenUsagePath(uid)),
  fc.constant(modelConfigPath())
);

/** Generates arbitrary strings that are NOT valid V2 paths and NOT V1 paths either */
const arbArbitraryNonV2Path = fc
  .string({ minLength: 1, maxLength: 100 })
  .filter((s) => {
    const lower = s.trim().toLowerCase();
    // Exclude paths that happen to start with V2 prefixes
    const isV2 = V2_COLLECTION_LIST.some(
      (col) => lower === col || lower.startsWith(`${col}/`)
    );
    // Exclude paths that are obviously V1
    const isV1 = V1_FORBIDDEN_COLLECTIONS.some(
      (col) => lower === col || lower.startsWith(`${col}/`)
    );
    const hasV1SubPattern = V1_FORBIDDEN_SUBCOLLECTION_PATTERNS.some(
      (pattern) => lower.includes(pattern)
    );
    return !isV2 && !isV1 && !hasV1SubPattern && s.trim().length > 0;
  });

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 15: V2 Firestore collection isolation", () => {

  describe("All path helper functions produce V2-prefixed collection paths", () => {
    it("memoryEntriesPath always produces a path starting with v2_memory", () => {
      fc.assert(
        fc.property(arbUid, (uid) => {
          const path = memoryEntriesPath(uid);
          expect(path.startsWith(V2_COLLECTIONS.MEMORY)).toBe(true);
          expect(isV2Collection(path)).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("memoryEntryPath always produces a path starting with v2_memory", () => {
      fc.assert(
        fc.property(arbUid, arbDocId, (uid, entryId) => {
          const path = memoryEntryPath(uid, entryId);
          expect(path.startsWith(V2_COLLECTIONS.MEMORY)).toBe(true);
          expect(isV2Collection(path)).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("conversationThreadsPath always produces a path starting with v2_conversations", () => {
      fc.assert(
        fc.property(arbUid, (uid) => {
          const path = conversationThreadsPath(uid);
          expect(path.startsWith(V2_COLLECTIONS.CONVERSATIONS)).toBe(true);
          expect(isV2Collection(path)).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("conversationThreadPath always produces a path starting with v2_conversations", () => {
      fc.assert(
        fc.property(arbUid, arbDocId, (uid, convId) => {
          const path = conversationThreadPath(uid, convId);
          expect(path.startsWith(V2_COLLECTIONS.CONVERSATIONS)).toBe(true);
          expect(isV2Collection(path)).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("tokenUsagePath always produces a path starting with v2_token_usage", () => {
      fc.assert(
        fc.property(arbUid, (uid) => {
          const path = tokenUsagePath(uid);
          expect(path.startsWith(V2_COLLECTIONS.TOKEN_USAGE)).toBe(true);
          expect(isV2Collection(path)).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("modelConfigPath always produces a path starting with v2_config", () => {
      const path = modelConfigPath();
      expect(path.startsWith(V2_COLLECTIONS.CONFIG)).toBe(true);
      expect(isV2Collection(path)).toBe(true);
    });
  });

  describe("assertV2Collection accepts valid V2 paths", () => {
    it("does not throw for any path produced by V2 path helpers", () => {
      fc.assert(
        fc.property(arbV2PathFromHelpers, (path) => {
          // Should not throw — valid V2 paths pass the isolation guard
          expect(() => assertV2Collection(path)).not.toThrow();
        }),
        { numRuns: 300 }
      );
    });

    it("does not throw for raw V2 collection names", () => {
      for (const col of V2_COLLECTION_LIST) {
        expect(() => assertV2Collection(col)).not.toThrow();
      }
    });

    it("does not throw for V2 collection names with arbitrary sub-paths", () => {
      fc.assert(
        fc.property(
          fc.constantFrom(...V2_COLLECTION_LIST),
          arbDocId,
          arbDocId,
          (col, seg1, seg2) => {
            const path = `${col}/${seg1}/${seg2}`;
            expect(() => assertV2Collection(path)).not.toThrow();
          }
        ),
        { numRuns: 200 }
      );
    });
  });

  describe("assertV2Collection rejects V1 paths", () => {
    it("throws V2IsolationViolationError for any V1 collection path", () => {
      fc.assert(
        fc.property(arbV1Path, (path) => {
          expect(() => assertV2Collection(path)).toThrow(V2IsolationViolationError);
        }),
        { numRuns: 200 }
      );
    });

    it("throws for V1 forbidden collections with leading/trailing whitespace", () => {
      fc.assert(
        fc.property(
          fc.constantFrom(...V1_FORBIDDEN_COLLECTIONS),
          fc.constantFrom("", " ", "  ", "\t"),
          fc.constantFrom("", " ", "  ", "\t"),
          (col, prefix, suffix) => {
            const path = `${prefix}${col}${suffix}`;
            expect(() => assertV2Collection(path)).toThrow(V2IsolationViolationError);
          }
        ),
        { numRuns: 100 }
      );
    });

    it("throws for V1 subcollection patterns embedded at any depth", () => {
      fc.assert(
        fc.property(arbUid, arbDocId, (uid, docId) => {
          // Paths matching V1 forbidden subcollection patterns
          const v1SubPaths = [
            `users/${uid}/ai_memory`,
            `users/${uid}/ai_memory/${docId}`,
            `users/${uid}/ai_history`,
            `users/${uid}/ai_history/${docId}`,
            `some/path/ai_memory`,
            `deep/nested/ai_history/doc`,
          ];

          for (const path of v1SubPaths) {
            expect(() => assertV2Collection(path)).toThrow(V2IsolationViolationError);
          }
        }),
        { numRuns: 100 }
      );
    });
  });

  describe("isV2Collection correctly identifies V2 vs non-V2 paths", () => {
    it("returns true for all paths generated by V2 path helpers", () => {
      fc.assert(
        fc.property(arbV2PathFromHelpers, (path) => {
          expect(isV2Collection(path)).toBe(true);
        }),
        { numRuns: 300 }
      );
    });

    it("returns false for arbitrary non-V2 paths", () => {
      fc.assert(
        fc.property(arbArbitraryNonV2Path, (path) => {
          expect(isV2Collection(path)).toBe(false);
        }),
        { numRuns: 200 }
      );
    });

    it("returns false for V1 forbidden collection paths", () => {
      fc.assert(
        fc.property(arbV1Path, (path) => {
          expect(isV2Collection(path)).toBe(false);
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("V2 path helpers never produce paths matching V1 patterns", () => {
    it("no V2 helper path contains V1 forbidden subcollection patterns", () => {
      fc.assert(
        fc.property(arbV2PathFromHelpers, (path) => {
          const lower = path.toLowerCase();
          for (const pattern of V1_FORBIDDEN_SUBCOLLECTION_PATTERNS) {
            expect(lower.includes(pattern)).toBe(false);
          }
        }),
        { numRuns: 300 }
      );
    });

    it("no V2 helper path starts with a V1 forbidden collection", () => {
      fc.assert(
        fc.property(arbV2PathFromHelpers, (path) => {
          const lower = path.trim().toLowerCase();
          for (const forbidden of V1_FORBIDDEN_COLLECTIONS) {
            expect(lower.startsWith(forbidden)).toBe(false);
            expect(lower.startsWith(`${forbidden}/`)).toBe(false);
          }
        }),
        { numRuns: 300 }
      );
    });
  });
});
