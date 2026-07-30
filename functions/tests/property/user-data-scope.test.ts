/**
 * Property 9: User data scope enforcement
 *
 * For arbitrary requests, verify all Firestore paths scoped to authenticated UID,
 * tool calls referencing other users rejected, unauthenticated requests rejected
 * before data access, memory searches return only authenticated user's entries.
 *
 * **Validates: Requirements 8.1, 8.2, 8.5, 8.6**
 */
import { describe, it, expect } from "vitest";
import * as fc from "fast-check";

import { validateScope } from "../../src/v2/services/tool-executor";
import {
  memoryEntriesPath,
  memoryEntryPath,
  conversationThreadsPath,
  conversationThreadPath,
  tokenUsagePath,
  isV2Collection,
  assertV2Collection,
  V2IsolationViolationError,
} from "../../src/v2/utils/collection-paths";
import { ToolCall, ReadTool, WriteTool } from "../../src/v2/models/types";

// ─── Arbitraries ───────────────────────────────────────────────────────────────

/** UID character set */
const UID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-".split("");

/** Generates a valid UID (alphanumeric with dashes/underscores, 6-40 chars) */
const arbUid = fc
  .array(fc.constantFrom(...UID_CHARS), { minLength: 6, maxLength: 40 })
  .map((chars) => chars.join(""));

/** Generates a pair of distinct UIDs (authenticated user vs. other user) */
const arbDistinctUids = fc.tuple(arbUid, arbUid).filter(([a, b]) => a !== b);

/** Generates a valid read tool name */
const arbReadTool: fc.Arbitrary<ReadTool> = fc.constantFrom(
  "get_today_tasks",
  "get_tasks_by_date_range",
  "get_task_by_name",
  "get_friends_list",
  "get_social_circles"
);

/** Generates a valid write tool name */
const arbWriteTool: fc.Arbitrary<WriteTool> = fc.constantFrom(
  "create_task",
  "update_task",
  "complete_task",
  "create_circle",
  "add_circle_members"
);

/** Generates any valid tool name */
const arbToolName = fc.oneof(arbReadTool, arbWriteTool);

/** Generates a valid tool category */
const arbCategory = fc.constantFrom<"read" | "write">("read", "write");

/** Generates a tool call scoped to the authenticated user (no cross-user paths) */
function arbScopedToolCall(uid: string): fc.Arbitrary<ToolCall> {
  return fc.record({
    name: arbToolName,
    category: arbCategory,
    description: fc.string({ minLength: 1, maxLength: 100 }),
    targetData: fc.constantFrom(
      `users/${uid}/tasks`,
      `users/${uid}/friends`,
      `tasks`,
      `circles`
    ),
    args: fc.oneof(
      fc.constant({ searchTerm: "homework" }),
      fc.constant({ startDate: "2025-01-01", endDate: "2025-12-31" }),
      fc.constant({ title: "New task", priority: 1 }),
      fc.constant({ taskId: "task123" }),
      fc.constant({})
    ),
  });
}

/** Generates a tool call that references another user's path (cross-user violation) */
function arbCrossUserToolCall(authUid: string, otherUid: string): fc.Arbitrary<ToolCall> {
  return fc.record({
    name: arbToolName,
    category: arbCategory,
    description: fc.string({ minLength: 1, maxLength: 100 }),
    targetData: fc.constantFrom(
      `users/${otherUid}/tasks`,
      `users/${otherUid}/friends`,
      `users/${otherUid}/memory`
    ),
    args: fc.oneof(
      fc.constant({ uid: otherUid }),
      fc.constant({ path: `users/${otherUid}/tasks/task1` }),
      fc.constant({})
    ),
  });
}

/** Generates a tool call with cross-user paths embedded in args only */
function arbCrossUserInArgs(authUid: string, otherUid: string): fc.Arbitrary<ToolCall> {
  return fc.record({
    name: arbToolName,
    category: arbCategory,
    description: fc.string({ minLength: 1, maxLength: 100 }),
    targetData: fc.constant("tasks"), // targetData is fine
    args: fc.constantFrom(
      { taskPath: `users/${otherUid}/tasks/task1` },
      { nested: { ref: `users/${otherUid}/data` } },
      { items: [`users/${otherUid}/friends/f1`, "safe-value"] }
    ),
  });
}

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 9: User data scope enforcement", () => {

  describe("Firestore paths scoped to authenticated UID (Req 8.1)", () => {
    it("all V2 collection path helpers produce paths containing the authenticated UID", () => {
      fc.assert(
        fc.property(arbUid, (uid) => {
          // All path builders embed the uid in the correct position
          const memEntries = memoryEntriesPath(uid);
          const memEntry = memoryEntryPath(uid, "entry123");
          const convThreads = conversationThreadsPath(uid);
          const convThread = conversationThreadPath(uid, "conv456");
          const tokenPath = tokenUsagePath(uid);

          // Each path must contain the UID
          expect(memEntries).toContain(uid);
          expect(memEntry).toContain(uid);
          expect(convThreads).toContain(uid);
          expect(convThread).toContain(uid);
          expect(tokenPath).toContain(uid);

          // Each path must start with a V2 collection prefix
          expect(isV2Collection(memEntries)).toBe(true);
          expect(isV2Collection(memEntry)).toBe(true);
          expect(isV2Collection(convThreads)).toBe(true);
          expect(isV2Collection(convThread)).toBe(true);
          expect(isV2Collection(tokenPath)).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("V2 path helpers for different UIDs produce disjoint paths", () => {
      fc.assert(
        fc.property(arbDistinctUids, ([uidA, uidB]) => {
          // Memory paths for user A must NOT contain user B's UID in the UID segment
          const pathA = memoryEntriesPath(uidA);
          const pathB = memoryEntriesPath(uidB);

          expect(pathA).not.toBe(pathB);
          // Path A contains uidA but the UID segment does not match uidB
          expect(pathA).toContain(uidA);
          expect(pathB).toContain(uidB);
          // Structural check: the path segments are distinct
          const segmentsA = pathA.split("/");
          const segmentsB = pathB.split("/");
          expect(segmentsA[1]).toBe(uidA);
          expect(segmentsB[1]).toBe(uidB);
          expect(segmentsA[1]).not.toBe(segmentsB[1]);
        }),
        { numRuns: 200 }
      );
    });
  });

  describe("Tool calls referencing other users rejected (Req 8.2)", () => {
    it("validateScope rejects tool calls with cross-user targetData", () => {
      fc.assert(
        fc.property(
          arbDistinctUids.chain(([authUid, otherUid]) =>
            arbCrossUserToolCall(authUid, otherUid).map((tc) => ({
              authUid,
              otherUid,
              toolCall: tc,
            }))
          ),
          ({ authUid, toolCall }) => {
            const result = validateScope(authUid, toolCall);
            expect(result.valid).toBe(false);
            expect(result.reason).toBe("SCOPE_VIOLATION");
          }
        ),
        { numRuns: 200 }
      );
    });

    it("validateScope rejects tool calls with cross-user paths in args", () => {
      fc.assert(
        fc.property(
          arbDistinctUids.chain(([authUid, otherUid]) =>
            arbCrossUserInArgs(authUid, otherUid).map((tc) => ({
              authUid,
              otherUid,
              toolCall: tc,
            }))
          ),
          ({ authUid, toolCall }) => {
            const result = validateScope(authUid, toolCall);
            expect(result.valid).toBe(false);
            expect(result.reason).toBe("SCOPE_VIOLATION");
          }
        ),
        { numRuns: 200 }
      );
    });

    it("validateScope accepts tool calls properly scoped to authenticated UID", () => {
      fc.assert(
        fc.property(
          arbUid.chain((uid) =>
            arbScopedToolCall(uid).map((tc) => ({ uid, toolCall: tc }))
          ),
          ({ uid, toolCall }) => {
            const result = validateScope(uid, toolCall);
            expect(result.valid).toBe(true);
            expect(result.reason).toBeUndefined();
          }
        ),
        { numRuns: 200 }
      );
    });
  });

  describe("Unauthenticated requests rejected before data access (Req 8.5)", () => {
    it("empty or invalid UID causes scope validation failure for any tool call", () => {
      fc.assert(
        fc.property(
          fc.constantFrom("", " ", "\t", "\n"),
          arbToolName,
          arbCategory,
          (invalidUid, toolName, category) => {
            const toolCall: ToolCall = {
              name: toolName,
              category,
              description: "some action",
              targetData: `users/someOtherUser123/tasks`,
              args: {},
            };

            // An empty/whitespace UID will never match any path's UID segment,
            // so cross-user paths will always be detected as violations
            const result = validateScope(invalidUid, toolCall);
            expect(result.valid).toBe(false);
          }
        ),
        { numRuns: 100 }
      );
    });

    it("V2 isolation guard rejects V1 collection access regardless of UID", () => {
      fc.assert(
        fc.property(arbUid, (uid) => {
          // Any attempt to access V1 collections should throw
          const v1Paths = [
            `users/${uid}/ai_memory`,
            `users/${uid}/ai_history`,
            "config/ai",
            `config/ai/settings`,
          ];

          for (const path of v1Paths) {
            expect(() => assertV2Collection(path)).toThrow(V2IsolationViolationError);
          }
        }),
        { numRuns: 100 }
      );
    });
  });

  describe("Memory searches scoped to authenticated user's entries (Req 8.6)", () => {
    /** Generates a valid Firestore document ID (no slashes, non-empty) */
    const arbEntryId = fc
      .array(fc.constantFrom(...UID_CHARS), { minLength: 1, maxLength: 30 })
      .map((chars) => chars.join(""));

    it("memory path helpers always produce paths under the specific user's UID", () => {
      fc.assert(
        fc.property(arbUid, arbEntryId, (uid, entryId) => {
          const path = memoryEntryPath(uid, entryId);

          // Path format: v2_memory/{uid}/entries/{entryId}
          const segments = path.split("/");
          expect(segments[0]).toBe("v2_memory");
          expect(segments[1]).toBe(uid);
          expect(segments[2]).toBe("entries");
          expect(segments[3]).toBe(entryId);

          // The path is a valid V2 collection path
          expect(isV2Collection(path)).toBe(true);
        }),
        { numRuns: 200 }
      );
    });

    it("memory paths for user A never match memory paths for user B", () => {
      fc.assert(
        fc.property(
          arbDistinctUids,
          fc.string({ minLength: 1, maxLength: 30 }),
          ([uidA, uidB], entryId) => {
            const pathA = memoryEntryPath(uidA, entryId);
            const pathB = memoryEntryPath(uidB, entryId);

            // Even with the same entryId, different UIDs yield different paths
            expect(pathA).not.toBe(pathB);

            // Neither path contains the other's UID in the UID position
            const segA = pathA.split("/");
            const segB = pathB.split("/");
            expect(segA[1]).toBe(uidA);
            expect(segB[1]).toBe(uidB);
            expect(segA[1]).not.toBe(segB[1]);
          }
        ),
        { numRuns: 200 }
      );
    });

    it("V2 memory collection paths never overlap with non-memory V2 collections", () => {
      fc.assert(
        fc.property(arbUid, (uid) => {
          const memPath = memoryEntriesPath(uid);
          const convPath = conversationThreadsPath(uid);
          const tokPath = tokenUsagePath(uid);

          // All are valid V2 paths
          expect(isV2Collection(memPath)).toBe(true);
          expect(isV2Collection(convPath)).toBe(true);
          expect(isV2Collection(tokPath)).toBe(true);

          // But they are structurally distinct (different collection prefixes)
          expect(memPath.startsWith("v2_memory/")).toBe(true);
          expect(convPath.startsWith("v2_conversations/")).toBe(true);
          expect(tokPath.startsWith("v2_token_usage/")).toBe(true);
        }),
        { numRuns: 100 }
      );
    });
  });
});
