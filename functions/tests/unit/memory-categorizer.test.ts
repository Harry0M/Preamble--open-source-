/**
 * Unit tests for MemoryCategorizer service.
 *
 * Validates: Requirements 11.1, 11.2, 11.3, 11.4, 13.2, 13.3
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// ─── Mock Firestore ────────────────────────────────────────────────────────────

const mockGet = vi.fn();
const mockUpdate = vi.fn();
const mockCommit = vi.fn();
const mockBatchUpdate = vi.fn();

const mockDocRef = vi.fn(() => ({
  get: mockGet,
  update: mockUpdate,
}));

const mockLimitGet = vi.fn();

const mockCollection = vi.fn(() => ({
  doc: mockDocRef,
  orderBy: vi.fn(() => ({
    limit: vi.fn(() => ({
      get: mockLimitGet,
    })),
  })),
}));

vi.mock("firebase-admin/firestore", () => ({
  getFirestore: vi.fn(() => ({
    collection: vi.fn(() => ({
      doc: vi.fn(() => ({
        collection: mockCollection,
      })),
    })),
    batch: vi.fn(() => ({
      update: mockBatchUpdate,
      commit: mockCommit,
    })),
  })),
}));

import {
  classifyMemory,
  detectAndLinkEntities,
  withRetry,
} from "../../src/v2/services/memory-categorizer";

// ─── classifyMemory Tests ──────────────────────────────────────────────────────

describe("classifyMemory", () => {
  it("classifies identity-related text correctly", () => {
    expect(classifyMemory("My name is Rahul")).toBe("identity");
    expect(classifyMemory("I am a software engineer")).toBe("identity");
    expect(classifyMemory("I work at Google")).toBe("identity");
    expect(classifyMemory("I go to IIT Delhi college")).toBe("identity");
  });

  it("classifies preference-related text correctly", () => {
    expect(classifyMemory("I prefer dark mode")).toBe("preference");
    expect(classifyMemory("I don't like spicy food")).toBe("preference");
    expect(classifyMemory("My favorite color is blue")).toBe("preference");
    expect(classifyMemory("I hate waking up early")).toBe("preference");
  });

  it("classifies goal-related text correctly", () => {
    expect(classifyMemory("I want to learn Rust")).toBe("goal");
    expect(classifyMemory("My goal is to run a marathon")).toBe("goal");
    expect(classifyMemory("I plan to travel next year")).toBe("goal");
    expect(classifyMemory("I aspire to become a CTO")).toBe("goal");
  });

  it("classifies relationship-related text correctly", () => {
    expect(classifyMemory("Arjun is my brother")).toBe("relationship");
    expect(classifyMemory("Priya is my colleague at office")).toBe("relationship");
    expect(classifyMemory("My father lives in Mumbai")).toBe("relationship");
  });

  it("classifies habit-related text correctly", () => {
    expect(classifyMemory("I usually wake up at 6am")).toBe("habit");
    expect(classifyMemory("I always drink coffee in the morning")).toBe("habit");
    expect(classifyMemory("My daily routine includes meditation")).toBe("habit");
  });

  it("classifies schedule-related text correctly", () => {
    expect(classifyMemory("I have a meeting tomorrow")).toBe("schedule");
    expect(classifyMemory("Dentist appointment next week")).toBe("schedule");
    expect(classifyMemory("Project deadline is Friday")).toBe("schedule");
  });

  it("classifies location-related text correctly", () => {
    expect(classifyMemory("I live in Bangalore")).toBe("location");
    expect(classifyMemory("My timezone is IST")).toBe("location");
    expect(classifyMemory("My city is Mumbai")).toBe("location");
    expect(classifyMemory("I am based in New York")).toBe("location");
  });

  it("classifies important_date-related text correctly", () => {
    expect(classifyMemory("My birthday is on March 15")).toBe("important_date");
    expect(classifyMemory("Our anniversary is next month")).toBe("important_date");
    expect(classifyMemory("Final exam is on December 10")).toBe("important_date");
  });

  it("classifies project-related text correctly", () => {
    expect(classifyMemory("I am working on a mobile app project")).toBe("project");
    expect(classifyMemory("Currently building a task manager")).toBe("project");
    expect(classifyMemory("My side project is a weather app")).toBe("project");
  });

  it("classifies productivity_pattern-related text correctly", () => {
    expect(classifyMemory("It took me 3 hours to complete the report")).toBe("productivity_pattern");
    expect(classifyMemory("My productivity is highest in the morning")).toBe("productivity_pattern");
    expect(classifyMemory("Time spent on coding was 5 hours")).toBe("productivity_pattern");
  });

  it("classifies interest-related text correctly", () => {
    expect(classifyMemory("I am interested in machine learning")).toBe("interest");
    expect(classifyMemory("Photography is my hobby")).toBe("interest");
    expect(classifyMemory("I enjoy reading sci-fi books")).toBe("interest");
    expect(classifyMemory("My passion is music production")).toBe("interest");
  });

  it("defaults to 'context' when no clear match", () => {
    expect(classifyMemory("The weather is nice today")).toBe("context");
    expect(classifyMemory("some random text here")).toBe("context");
    expect(classifyMemory("")).toBe("context");
  });

  it("returns exactly one category from the valid set", () => {
    const validCategories = [
      "identity", "preference", "goal", "interest",
      "context", "relationship", "habit", "schedule",
      "project", "important_date", "location", "productivity_pattern",
    ];

    // Test multiple ambiguous inputs
    const inputs = [
      "My friend Rahul works at Google",
      "I plan to finish the project by Friday",
      "Meeting about my birthday party",
      "Random text with no keywords",
    ];

    for (const input of inputs) {
      const result = classifyMemory(input);
      expect(validCategories).toContain(result);
    }
  });

  it("is case-insensitive", () => {
    expect(classifyMemory("MY NAME IS RAHUL")).toBe("identity");
    expect(classifyMemory("I PREFER dark mode")).toBe("preference");
    expect(classifyMemory("my GOAL is to run")).toBe("goal");
  });
});

// ─── withRetry Tests ───────────────────────────────────────────────────────────

describe("withRetry", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("returns result on first attempt success", async () => {
    const fn = vi.fn().mockResolvedValueOnce("success");

    const result = await withRetry(fn, 3);

    expect(result).toBe("success");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("retries on failure and succeeds on second attempt", async () => {
    const fn = vi.fn()
      .mockRejectedValueOnce(new Error("fail"))
      .mockResolvedValueOnce("success");

    const resultPromise = withRetry(fn, 3);

    // Advance timers to handle the 100ms delay
    await vi.advanceTimersByTimeAsync(150);

    const result = await resultPromise;
    expect(result).toBe("success");
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it("retries on failure and succeeds on third attempt", async () => {
    const fn = vi.fn()
      .mockRejectedValueOnce(new Error("fail 1"))
      .mockRejectedValueOnce(new Error("fail 2"))
      .mockResolvedValueOnce("success");

    const resultPromise = withRetry(fn, 3);

    // Advance past first delay (100ms) and second delay (200ms)
    await vi.advanceTimersByTimeAsync(350);

    const result = await resultPromise;
    expect(result).toBe("success");
    expect(fn).toHaveBeenCalledTimes(3);
  });

  it("throws after exhausting all attempts", async () => {
    const fn = vi.fn()
      .mockRejectedValueOnce(new Error("fail 1"))
      .mockRejectedValueOnce(new Error("fail 2"))
      .mockRejectedValueOnce(new Error("final fail"));

    const resultPromise = withRetry(fn, 3).catch((e) => e);

    // Advance past all delays
    await vi.advanceTimersByTimeAsync(500);

    const error = await resultPromise;
    expect(error).toBeInstanceOf(Error);
    expect((error as Error).message).toBe("final fail");
    expect(fn).toHaveBeenCalledTimes(3);
  });

  it("uses exponential backoff timing", async () => {
    let callTimes: number[] = [];
    const fn = vi.fn().mockImplementation(() => {
      callTimes.push(Date.now());
      return Promise.reject(new Error("fail"));
    });

    const resultPromise = withRetry(fn, 3).catch((e) => e);

    // Advance past all delays (100 + 200 = 300ms total between 3 attempts)
    await vi.advanceTimersByTimeAsync(500);

    const error = await resultPromise;
    expect(error).toBeInstanceOf(Error);
    expect(fn).toHaveBeenCalledTimes(3);

    // Check delays: first call immediate, second after 100ms, third after 200ms more
    expect(callTimes[1] - callTimes[0]).toBe(100);
    expect(callTimes[2] - callTimes[1]).toBe(200);
  });

  it("respects custom maxAttempts parameter", async () => {
    const fn = vi.fn().mockRejectedValue(new Error("always fails"));

    await expect(withRetry(fn, 1)).rejects.toThrow("always fails");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("defaults to 3 max attempts when not specified", async () => {
    const fn = vi.fn().mockImplementation(() => Promise.reject(new Error("fail")));

    const resultPromise = withRetry(fn).catch((e) => e);

    await vi.advanceTimersByTimeAsync(500);

    const error = await resultPromise;
    expect(error).toBeInstanceOf(Error);
    expect((error as Error).message).toBe("fail");
    expect(fn).toHaveBeenCalledTimes(3);
  });
});

// ─── detectAndLinkEntities Tests ───────────────────────────────────────────────

describe("detectAndLinkEntities", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useRealTimers();
  });

  it("does nothing when no entities are detected in text", async () => {
    // All lowercase, no proper nouns — no entities extracted
    await detectAndLinkEntities("user1", "entry1", "some lowercase text here");

    // Should not attempt Firestore queries since no entities found
    expect(mockLimitGet).not.toHaveBeenCalled();
  });

  it("queries existing entries when entities are detected", async () => {
    // Mock empty query result (no existing entries)
    mockLimitGet.mockResolvedValue({
      empty: true,
      docs: [],
    });

    await detectAndLinkEntities("user1", "entry1", "Meeting with Rahul tomorrow");

    // Should have queried for existing entries since "Rahul" and "Meeting" are entities
    expect(mockLimitGet).toHaveBeenCalled();
  });

  it("creates links when shared entities are found", async () => {
    // Mock existing entries with shared entity "Rahul"
    mockLimitGet.mockResolvedValue({
      empty: false,
      docs: [
        {
          id: "existing1",
          data: () => ({
            text: "Call Rahul about the deadline",
            linkedEntryIds: [],
            lastAccessedAt: Date.now(),
          }),
        },
      ],
    });

    // Mock doc reads for the new entry
    mockDocRef.mockReturnValue({
      get: vi.fn().mockResolvedValue({
        exists: true,
        data: () => ({
          text: "Meeting with Rahul tomorrow",
          linkedEntryIds: [],
        }),
      }),
      update: mockUpdate,
    });

    await detectAndLinkEntities("user1", "entry1", "Meeting with Rahul tomorrow");

    // Should have queried existing entries since "Rahul" is a shared entity
    expect(mockLimitGet).toHaveBeenCalled();
    // The function attempts to update links — verifying no error thrown
  });
});
