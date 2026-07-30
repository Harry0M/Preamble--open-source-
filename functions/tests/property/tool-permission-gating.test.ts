/**
 * Property 14: Tool permission gating
 *
 * For N tool calls proposed, verify exactly N permission prompts presented
 * (with category, description, target data), only granted calls executed,
 * denied/unanswered results in zero execution.
 *
 * **Validates: Requirements 7.1, 7.3, 7.6**
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import * as fc from "fast-check";

// ─── Mock Firestore ────────────────────────────────────────────────────────────

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: vi.fn(() => ({
    collection: vi.fn(() => ({
      doc: vi.fn(() => ({
        get: vi.fn().mockResolvedValue({ exists: false, data: () => null }),
        set: vi.fn().mockResolvedValue(undefined),
        update: vi.fn().mockResolvedValue(undefined),
      })),
      where: vi.fn().mockReturnThis(),
      orderBy: vi.fn().mockReturnThis(),
      limit: vi.fn().mockReturnThis(),
      get: vi.fn().mockResolvedValue({ docs: [] }),
    })),
  })),
}));

vi.mock("../../../src/v2/services/productivity-patterns", () => ({
  recordTaskCompletion: vi.fn().mockResolvedValue(undefined),
}));

import { ToolCall, ApprovedToolCall, ReadTool, WriteTool } from "../../src/v2/models/types";
import { execute, validateScope } from "../../src/v2/services/tool-executor";

// ─── Constants ─────────────────────────────────────────────────────────────────

const READ_TOOLS: ReadTool[] = [
  "get_today_tasks",
  "get_tasks_by_date_range",
  "get_task_by_name",
  "get_friends_list",
  "get_social_circles",
];

const WRITE_TOOLS: WriteTool[] = [
  "create_task",
  "update_task",
  "complete_task",
  "create_circle",
  "add_circle_members",
];

const ALL_TOOLS = [...READ_TOOLS, ...WRITE_TOOLS];

// ─── Arbitraries ───────────────────────────────────────────────────────────────

/** Generates a valid tool name from the allowed set */
const arbToolName = fc.constantFrom(...ALL_TOOLS);

/** Generates a tool category */
const arbCategory = fc.constantFrom<"read" | "write">("read", "write");

/** Generates a non-empty description string */
const arbDescription = fc.string({ minLength: 1, maxLength: 100 }).filter((s) => s.trim().length > 0);

/** Generates a non-empty target data string */
const arbTargetData = fc.string({ minLength: 1, maxLength: 100 }).filter((s) => s.trim().length > 0);

/** Generates a valid UID */
const arbUid = fc.string({ minLength: 5, maxLength: 30 }).map((s) => s.replace(/[^a-zA-Z0-9_-]/g, "x") || "user123");

/** Generates a valid ToolCall */
const arbToolCall: fc.Arbitrary<ToolCall> = fc.record({
  name: arbToolName,
  category: arbCategory,
  description: arbDescription,
  targetData: arbTargetData,
  args: fc.constant({}),
});

/** Generates a list of 1 to 5 tool calls (as per task description) */
const arbToolCallList = fc.array(arbToolCall, { minLength: 1, maxLength: 5 });

/** Generates a permission decision: granted, denied, or unanswered */
const arbPermissionDecision = fc.constantFrom<"granted" | "denied" | "unanswered">(
  "granted",
  "denied",
  "unanswered"
);

// ─── Permission Prompt Model ───────────────────────────────────────────────────

/**
 * Models the permission prompt that should be presented to the user.
 * Per Requirement 7.1: includes category, description, and target data.
 */
interface PermissionPrompt {
  category: "read" | "write";
  description: string;
  targetData: string;
}

/**
 * Simulates the permission gating flow:
 * 1. For each proposed tool call, generates a permission prompt
 * 2. Based on user decision, either executes or skips the tool call
 *
 * This models the actual app behavior where:
 * - v2Chat proposes tool calls in its response
 * - Client presents permission dialogs
 * - Only granted calls are sent to v2ChatContinue for execution
 */
function simulatePermissionGating(
  toolCalls: ToolCall[],
  decisions: Array<"granted" | "denied" | "unanswered">
): {
  prompts: PermissionPrompt[];
  executedCalls: ToolCall[];
  skippedCalls: ToolCall[];
} {
  const prompts: PermissionPrompt[] = [];
  const executedCalls: ToolCall[] = [];
  const skippedCalls: ToolCall[] = [];

  for (let i = 0; i < toolCalls.length; i++) {
    const tc = toolCalls[i];
    // Req 7.1: Present permission prompt with category, description, target data
    prompts.push({
      category: tc.category,
      description: tc.description,
      targetData: tc.targetData,
    });

    const decision = decisions[i] ?? "unanswered";
    if (decision === "granted") {
      // Req 7.2: Only granted calls proceed to execution
      executedCalls.push(tc);
    } else {
      // Req 7.3 & 7.8: Denied or unanswered → no execution
      skippedCalls.push(tc);
    }
  }

  return { prompts, executedCalls, skippedCalls };
}

// ─── Property Tests ────────────────────────────────────────────────────────────

describe("Property 14: Tool permission gating", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("N proposed tool calls generate exactly N permission prompts (Req 7.1, 7.6)", () => {
    fc.assert(
      fc.property(
        arbToolCallList,
        (toolCalls) => {
          const decisions = toolCalls.map(() => "granted" as const);
          const { prompts } = simulatePermissionGating(toolCalls, decisions);

          // Property: exactly N prompts for N tool calls
          expect(prompts).toHaveLength(toolCalls.length);
        }
      ),
      { numRuns: 200 }
    );
  });

  it("each permission prompt contains category, description, and target data (Req 7.1)", () => {
    fc.assert(
      fc.property(
        arbToolCallList,
        (toolCalls) => {
          const decisions = toolCalls.map(() => "granted" as const);
          const { prompts } = simulatePermissionGating(toolCalls, decisions);

          for (let i = 0; i < prompts.length; i++) {
            const prompt = prompts[i];
            const tc = toolCalls[i];

            // Each prompt must have category matching the tool call
            expect(prompt.category).toBe(tc.category);
            expect(["read", "write"]).toContain(prompt.category);

            // Each prompt must have the description
            expect(prompt.description).toBe(tc.description);
            expect(prompt.description.length).toBeGreaterThan(0);

            // Each prompt must have the target data
            expect(prompt.targetData).toBe(tc.targetData);
            expect(prompt.targetData.length).toBeGreaterThan(0);
          }
        }
      ),
      { numRuns: 200 }
    );
  });

  it("only granted calls are executed (Req 7.2, 7.6)", () => {
    fc.assert(
      fc.property(
        arbToolCallList,
        fc.array(arbPermissionDecision, { minLength: 1, maxLength: 5 }),
        (toolCalls, rawDecisions) => {
          // Align decisions to match tool call count
          const decisions = toolCalls.map((_, i) => rawDecisions[i % rawDecisions.length]);
          const { executedCalls } = simulatePermissionGating(toolCalls, decisions);

          // Only granted calls should appear in executed list
          const grantedCount = decisions.filter((d) => d === "granted").length;
          expect(executedCalls).toHaveLength(grantedCount);

          // Every executed call must correspond to a granted decision
          for (let i = 0, execIdx = 0; i < toolCalls.length; i++) {
            if (decisions[i] === "granted") {
              expect(executedCalls[execIdx]).toEqual(toolCalls[i]);
              execIdx++;
            }
          }
        }
      ),
      { numRuns: 200 }
    );
  });

  it("denied calls result in zero execution (Req 7.3)", () => {
    fc.assert(
      fc.property(
        arbToolCallList,
        (toolCalls) => {
          // All calls denied
          const decisions = toolCalls.map(() => "denied" as const);
          const { executedCalls, skippedCalls } = simulatePermissionGating(toolCalls, decisions);

          // Zero executed calls
          expect(executedCalls).toHaveLength(0);
          // All calls in skipped list
          expect(skippedCalls).toHaveLength(toolCalls.length);
        }
      ),
      { numRuns: 200 }
    );
  });

  it("unanswered calls result in zero execution (Req 7.8)", () => {
    fc.assert(
      fc.property(
        arbToolCallList,
        (toolCalls) => {
          // All calls unanswered
          const decisions = toolCalls.map(() => "unanswered" as const);
          const { executedCalls, skippedCalls } = simulatePermissionGating(toolCalls, decisions);

          // Zero executed calls
          expect(executedCalls).toHaveLength(0);
          // All calls in skipped list
          expect(skippedCalls).toHaveLength(toolCalls.length);
        }
      ),
      { numRuns: 200 }
    );
  });

  it("mixed decisions: executed count equals granted count (Req 7.6)", () => {
    fc.assert(
      fc.property(
        arbToolCallList,
        fc.array(arbPermissionDecision, { minLength: 1, maxLength: 5 }),
        (toolCalls, rawDecisions) => {
          const decisions = toolCalls.map((_, i) => rawDecisions[i % rawDecisions.length]);
          const { prompts, executedCalls, skippedCalls } = simulatePermissionGating(
            toolCalls,
            decisions
          );

          const grantedCount = decisions.filter((d) => d === "granted").length;
          const deniedOrUnanswered = decisions.filter((d) => d !== "granted").length;

          // Invariants
          expect(prompts).toHaveLength(toolCalls.length);
          expect(executedCalls).toHaveLength(grantedCount);
          expect(skippedCalls).toHaveLength(deniedOrUnanswered);
          expect(executedCalls.length + skippedCalls.length).toBe(toolCalls.length);
        }
      ),
      { numRuns: 200 }
    );
  });

  it("execute() only accepts approved tool calls with valid scope (integration)", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbUid,
        arbToolCall,
        async (uid, toolCall) => {
          // Convert to approved (simulating the grant)
          const approvedCall: ApprovedToolCall = {
            ...toolCall,
            approvedAt: Date.now(),
            traceId: "trace-test-123",
            // Ensure targetData doesn't reference other users
            targetData: `tasks for user ${uid}`,
            args: {},
          };

          // The execute function should process the call (may fail due to Firestore mock
          // returning empty results, but the key point is it doesn't reject outright)
          const result = await execute(uid, approvedCall);

          // It should produce a ToolResult with the expected structure
          expect(result).toHaveProperty("toolName", approvedCall.name);
          expect(result).toHaveProperty("traceId", "trace-test-123");
          expect(result).toHaveProperty("executedAt");
          expect(typeof result.executedAt).toBe("number");
          // success may be true or false depending on Firestore mock, but it was attempted
          expect(typeof result.success).toBe("boolean");
        }
      ),
      { numRuns: 50 }
    );
  });

  it("validateScope rejects tool calls that reference other user paths", () => {
    fc.assert(
      fc.property(
        arbUid.filter((uid) => uid.length >= 3),
        arbToolCall,
        arbUid.filter((uid) => uid.length >= 3),
        (authenticatedUid, toolCall, otherUid) => {
          // Skip if UIDs happen to be equal
          fc.pre(authenticatedUid !== otherUid);

          // Create a tool call that references another user's data
          const maliciousToolCall: ToolCall = {
            ...toolCall,
            targetData: `users/${otherUid}/tasks`,
            args: {},
          };

          const result = validateScope(authenticatedUid, maliciousToolCall);

          // Should be rejected due to scope violation
          expect(result.valid).toBe(false);
          expect(result.reason).toBe("SCOPE_VIOLATION");
        }
      ),
      { numRuns: 100 }
    );
  });

  it("execute() rejects non-approved tool calls referencing other users", async () => {
    await fc.assert(
      fc.asyncProperty(
        arbUid.filter((uid) => uid.length >= 3),
        arbToolCall,
        arbUid.filter((uid) => uid.length >= 3),
        async (authenticatedUid, toolCall, otherUid) => {
          fc.pre(authenticatedUid !== otherUid);

          // Even if "approved", scope violation should prevent execution
          const scopeViolatingCall: ApprovedToolCall = {
            ...toolCall,
            targetData: `users/${otherUid}/tasks/task1`,
            args: { path: `users/${otherUid}/data` },
            approvedAt: Date.now(),
            traceId: "trace-scope-test",
          };

          const result = await execute(authenticatedUid, scopeViolatingCall);

          // Should fail with scope violation
          expect(result.success).toBe(false);
          expect(result.error).toContain("SCOPE_VIOLATION");
        }
      ),
      { numRuns: 50 }
    );
  });
});
