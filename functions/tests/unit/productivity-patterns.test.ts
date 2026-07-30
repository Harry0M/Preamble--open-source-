/**
 * Unit tests for Productivity Pattern Memory Storage service.
 *
 * Validates: Requirements 24.3, 24.4
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// ─── Mock memory-engine ────────────────────────────────────────────────────────

const mockStore = vi.fn();
const mockSearch = vi.fn();

vi.mock("../../src/v2/services/memory-engine", () => ({
  store: (...args: unknown[]) => mockStore(...args),
  search: (...args: unknown[]) => mockSearch(...args),
}));

import {
  recordTaskCompletion,
  getTimeEstimate,
  TaskCompletionData,
} from "../../src/v2/services/productivity-patterns";

// ─── Tests ─────────────────────────────────────────────────────────────────────

describe("Productivity Patterns", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockStore.mockResolvedValue("entry-123");
    mockSearch.mockResolvedValue([]);
  });

  describe("recordTaskCompletion", () => {
    it("stores a productivity pattern when task has valid duration (Req 24.3)", async () => {
      const taskData: TaskCompletionData = {
        taskId: "task-1",
        title: "Write report",
        tags: ["work", "writing"],
        createdAt: Date.now() - 3 * 60 * 60 * 1000, // 3 hours ago
        completedAt: Date.now(),
      };

      const result = await recordTaskCompletion("user-123", taskData);

      expect(result).toBe("entry-123");
      expect(mockStore).toHaveBeenCalledOnce();

      const [uid, entry] = mockStore.mock.calls[0];
      expect(uid).toBe("user-123");
      expect(entry.category).toBe("productivity_pattern");
      expect(entry.memoryType).toBe("long_term");
      expect(entry.source).toBe("system");
      expect(entry.text).toContain("Write report");
      expect(entry.text).toContain("tags: work, writing");
      expect(entry.text).toContain("hours");
    });

    it("stores duration in minutes for short tasks", async () => {
      const taskData: TaskCompletionData = {
        taskId: "task-2",
        title: "Quick email",
        tags: [],
        createdAt: Date.now() - 15 * 60 * 1000, // 15 minutes ago
        completedAt: Date.now(),
      };

      await recordTaskCompletion("user-123", taskData);

      const [, entry] = mockStore.mock.calls[0];
      expect(entry.text).toContain("15 minutes");
    });

    it("stores duration in days for long tasks", async () => {
      const taskData: TaskCompletionData = {
        taskId: "task-3",
        title: "Prepare exam",
        tags: ["study"],
        createdAt: Date.now() - 3 * 24 * 60 * 60 * 1000, // 3 days ago
        completedAt: Date.now(),
      };

      await recordTaskCompletion("user-123", taskData);

      const [, entry] = mockStore.mock.calls[0];
      expect(entry.text).toContain("days");
    });

    it("skips recording for instant completions (< 1 minute)", async () => {
      const taskData: TaskCompletionData = {
        taskId: "task-4",
        title: "Quick toggle",
        tags: [],
        createdAt: Date.now() - 30 * 1000, // 30 seconds ago
        completedAt: Date.now(),
      };

      const result = await recordTaskCompletion("user-123", taskData);

      expect(result).toBeNull();
      expect(mockStore).not.toHaveBeenCalled();
    });

    it("handles tasks with no tags gracefully", async () => {
      const taskData: TaskCompletionData = {
        taskId: "task-5",
        title: "No tags task",
        createdAt: Date.now() - 2 * 60 * 60 * 1000,
        completedAt: Date.now(),
      };

      await recordTaskCompletion("user-123", taskData);

      const [, entry] = mockStore.mock.calls[0];
      expect(entry.text).toContain("No tags task");
      expect(entry.text).not.toContain("tags:");
    });

    it("returns null if store fails (non-blocking)", async () => {
      mockStore.mockRejectedValue(new Error("Firestore unavailable"));

      const taskData: TaskCompletionData = {
        taskId: "task-6",
        title: "Failing task",
        tags: ["test"],
        createdAt: Date.now() - 60 * 60 * 1000,
        completedAt: Date.now(),
      };

      const result = await recordTaskCompletion("user-123", taskData);

      expect(result).toBeNull();
    });

    it("includes confidence 0.9 in the stored entry", async () => {
      const taskData: TaskCompletionData = {
        taskId: "task-7",
        title: "Confidence check",
        tags: [],
        createdAt: Date.now() - 2 * 60 * 60 * 1000,
        completedAt: Date.now(),
      };

      await recordTaskCompletion("user-123", taskData);

      const [, entry] = mockStore.mock.calls[0];
      expect(entry.confidence).toBe(0.9);
    });
  });

  describe("getTimeEstimate", () => {
    it("returns average duration from matching patterns (Req 24.4)", async () => {
      mockSearch.mockResolvedValue([
        {
          id: "mem-1",
          text: "Task 'Write report' (tags: work) completed in 3 hours",
          confidence: 0.85,
          category: "productivity_pattern",
        },
        {
          id: "mem-2",
          text: "Task 'Write summary' (tags: work) completed in 5 hours",
          confidence: 0.75,
          category: "productivity_pattern",
        },
      ]);

      const result = await getTimeEstimate("user-123", "Write report", ["work"]);

      expect(result).not.toBeNull();
      expect(result!.averageHours).toBe(4); // (3 + 5) / 2
      expect(result!.sampleCount).toBe(2);
      expect(result!.confidence).toBeGreaterThan(0);
    });

    it("returns null when no matching patterns exist", async () => {
      mockSearch.mockResolvedValue([]);

      const result = await getTimeEstimate("user-123", "Unknown task");

      expect(result).toBeNull();
    });

    it("searches with productivity_pattern category filter", async () => {
      mockSearch.mockResolvedValue([]);

      await getTimeEstimate("user-123", "Study math", ["study"]);

      expect(mockSearch).toHaveBeenCalledWith(
        "user-123",
        expect.stringContaining("Study math"),
        expect.objectContaining({
          categories: ["productivity_pattern"],
          memoryType: "long_term",
        })
      );
    });

    it("parses durations in minutes correctly", async () => {
      mockSearch.mockResolvedValue([
        {
          id: "mem-1",
          text: "Task 'Quick email' completed in 15 minutes",
          confidence: 0.9,
          category: "productivity_pattern",
        },
        {
          id: "mem-2",
          text: "Task 'Quick call' completed in 30 minutes",
          confidence: 0.8,
          category: "productivity_pattern",
        },
      ]);

      const result = await getTimeEstimate("user-123", "Quick email");

      expect(result).not.toBeNull();
      // (15/60 + 30/60) / 2 = 0.375 hours
      expect(result!.averageHours).toBeCloseTo(0.38, 1);
    });

    it("parses durations in days correctly", async () => {
      mockSearch.mockResolvedValue([
        {
          id: "mem-1",
          text: "Task 'Big project' completed in 5 days",
          confidence: 0.8,
          category: "productivity_pattern",
        },
      ]);

      const result = await getTimeEstimate("user-123", "Big project");

      expect(result).not.toBeNull();
      expect(result!.averageHours).toBe(120); // 5 * 24
      expect(result!.sampleCount).toBe(1);
    });

    it("returns null when search fails (non-blocking)", async () => {
      mockSearch.mockRejectedValue(new Error("Search failed"));

      const result = await getTimeEstimate("user-123", "Failing search");

      expect(result).toBeNull();
    });

    it("skips entries with unparseable duration text", async () => {
      mockSearch.mockResolvedValue([
        {
          id: "mem-1",
          text: "Some random memory entry without duration",
          confidence: 0.7,
          category: "productivity_pattern",
        },
        {
          id: "mem-2",
          text: "Task 'Real task' completed in 2 hours",
          confidence: 0.65,
          category: "productivity_pattern",
        },
      ]);

      const result = await getTimeEstimate("user-123", "Some task");

      expect(result).not.toBeNull();
      expect(result!.sampleCount).toBe(1);
      expect(result!.averageHours).toBe(2);
    });

    it("includes tags in the search query when provided", async () => {
      mockSearch.mockResolvedValue([]);

      await getTimeEstimate("user-123", "Gym session", ["fitness", "health"]);

      const query = mockSearch.mock.calls[0][1] as string;
      expect(query).toContain("fitness");
      expect(query).toContain("health");
    });
  });
});
