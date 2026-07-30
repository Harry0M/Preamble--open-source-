/**
 * Unit tests for V2 collection paths and isolation guard.
 *
 * Validates that:
 * - V2 collection constants are correctly defined
 * - assertV2Collection() rejects V1 paths
 * - isV2Collection() correctly identifies V2 paths
 * - Path builder helpers produce correct paths
 *
 * Requirements: 26.1, 26.2, 26.4, 26.5, 26.6
 */
import { describe, it, expect } from "vitest";
import {
  V2_COLLECTIONS,
  V2_COLLECTION_LIST,
  V1_FORBIDDEN_COLLECTIONS,
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

describe("V2 Collection Paths", () => {
  describe("V2_COLLECTIONS constants", () => {
    it("defines all four V2 collections", () => {
      expect(V2_COLLECTIONS.MEMORY).toBe("v2_memory");
      expect(V2_COLLECTIONS.CONVERSATIONS).toBe("v2_conversations");
      expect(V2_COLLECTIONS.TOKEN_USAGE).toBe("v2_token_usage");
      expect(V2_COLLECTIONS.CONFIG).toBe("v2_config");
    });

    it("V2_COLLECTION_LIST contains all collection values", () => {
      expect(V2_COLLECTION_LIST).toContain("v2_memory");
      expect(V2_COLLECTION_LIST).toContain("v2_conversations");
      expect(V2_COLLECTION_LIST).toContain("v2_token_usage");
      expect(V2_COLLECTION_LIST).toContain("v2_config");
      expect(V2_COLLECTION_LIST).toHaveLength(4);
    });
  });

  describe("V1_FORBIDDEN_COLLECTIONS", () => {
    it("lists known V1 collection paths", () => {
      expect(V1_FORBIDDEN_COLLECTIONS).toContain("ai_memory");
      expect(V1_FORBIDDEN_COLLECTIONS).toContain("config/ai");
    });
  });

  describe("assertV2Collection()", () => {
    it("allows valid V2 collection paths without throwing", () => {
      expect(() => assertV2Collection("v2_memory")).not.toThrow();
      expect(() => assertV2Collection("v2_memory/uid123/entries")).not.toThrow();
      expect(() => assertV2Collection("v2_conversations/uid123/threads")).not.toThrow();
      expect(() => assertV2Collection("v2_token_usage/uid123")).not.toThrow();
      expect(() => assertV2Collection("v2_config/models")).not.toThrow();
    });

    it("throws V2IsolationViolationError for V1 ai_memory collection", () => {
      expect(() => assertV2Collection("ai_memory")).toThrow(V2IsolationViolationError);
      expect(() => assertV2Collection("ai_memory/some_doc")).toThrow(V2IsolationViolationError);
    });

    it("throws V2IsolationViolationError for V1 config/ai path", () => {
      expect(() => assertV2Collection("config/ai")).toThrow(V2IsolationViolationError);
      expect(() => assertV2Collection("config/ai/subcollection")).toThrow(V2IsolationViolationError);
    });

    it("throws for V1 subcollection patterns in user paths", () => {
      expect(() => assertV2Collection("users/uid123/ai_memory")).toThrow(V2IsolationViolationError);
      expect(() => assertV2Collection("users/uid123/ai_memory/entries")).toThrow(V2IsolationViolationError);
      expect(() => assertV2Collection("users/uid123/ai_history")).toThrow(V2IsolationViolationError);
    });

    it("includes the violating path in the error", () => {
      try {
        assertV2Collection("config/ai");
        expect.fail("Should have thrown");
      } catch (err) {
        expect(err).toBeInstanceOf(V2IsolationViolationError);
        expect((err as V2IsolationViolationError).collectionPath).toBe("config/ai");
      }
    });

    it("is case-insensitive for safety", () => {
      expect(() => assertV2Collection("AI_MEMORY")).toThrow(V2IsolationViolationError);
      expect(() => assertV2Collection("Config/AI")).toThrow(V2IsolationViolationError);
    });

    it("allows unrelated non-V1 non-V2 collection paths", () => {
      // Paths like "tasks" or "users" are not forbidden (V2 tools read tasks)
      expect(() => assertV2Collection("tasks")).not.toThrow();
      expect(() => assertV2Collection("users/uid123")).not.toThrow();
    });
  });

  describe("isV2Collection()", () => {
    it("returns true for V2 collection roots", () => {
      expect(isV2Collection("v2_memory")).toBe(true);
      expect(isV2Collection("v2_conversations")).toBe(true);
      expect(isV2Collection("v2_token_usage")).toBe(true);
      expect(isV2Collection("v2_config")).toBe(true);
    });

    it("returns true for V2 collection subpaths", () => {
      expect(isV2Collection("v2_memory/uid123/entries/e1")).toBe(true);
      expect(isV2Collection("v2_conversations/uid/threads/t1")).toBe(true);
      expect(isV2Collection("v2_config/models")).toBe(true);
    });

    it("returns false for V1 collections", () => {
      expect(isV2Collection("ai_memory")).toBe(false);
      expect(isV2Collection("config/ai")).toBe(false);
      expect(isV2Collection("users/uid123/ai_memory")).toBe(false);
    });

    it("returns false for unrelated collections", () => {
      expect(isV2Collection("tasks")).toBe(false);
      expect(isV2Collection("users/uid123")).toBe(false);
    });
  });

  describe("Path builder helpers", () => {
    const testUid = "user_abc123";
    const testEntryId = "entry_xyz";
    const testConversationId = "conv_001";

    it("memoryEntriesPath returns correct path", () => {
      expect(memoryEntriesPath(testUid)).toBe("v2_memory/user_abc123/entries");
    });

    it("memoryEntryPath returns correct document path", () => {
      expect(memoryEntryPath(testUid, testEntryId)).toBe(
        "v2_memory/user_abc123/entries/entry_xyz"
      );
    });

    it("conversationThreadsPath returns correct path", () => {
      expect(conversationThreadsPath(testUid)).toBe(
        "v2_conversations/user_abc123/threads"
      );
    });

    it("conversationThreadPath returns correct document path", () => {
      expect(conversationThreadPath(testUid, testConversationId)).toBe(
        "v2_conversations/user_abc123/threads/conv_001"
      );
    });

    it("tokenUsagePath returns correct path", () => {
      expect(tokenUsagePath(testUid)).toBe("v2_token_usage/user_abc123");
    });

    it("modelConfigPath returns correct path", () => {
      expect(modelConfigPath()).toBe("v2_config/models");
    });
  });
});
