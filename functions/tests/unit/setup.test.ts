/**
 * Sanity test to verify vitest + fast-check infrastructure works.
 */
import { describe, it, expect } from "vitest";
import * as fc from "fast-check";
import {
  arbMemoryEntry,
  arbModelConfig,
  arbParseInput,
  arbInvalidParseText,
  arbValidParseResponse,
  arbTokenUsage,
  MEMORY_CATEGORIES,
} from "../helpers/arbitraries";

describe("Test infrastructure sanity check", () => {
  it("vitest runs successfully", () => {
    expect(1 + 1).toBe(2);
  });

  it("fast-check generates memory entries with correct structure", () => {
    fc.assert(
      fc.property(arbMemoryEntry, (entry) => {
        expect(entry.id).toBeDefined();
        expect(entry.uid).toBeDefined();
        expect(entry.text).toBeDefined();
        expect(entry.embedding).toHaveLength(768);
        expect(MEMORY_CATEGORIES).toContain(entry.category);
        expect(["short_term", "long_term"]).toContain(entry.memoryType);
        expect(entry.confidence).toBeGreaterThanOrEqual(0);
        expect(entry.confidence).toBeLessThanOrEqual(1);
        expect(entry.linkedEntryIds.length).toBeLessThanOrEqual(20);
        expect(["chat", "parse", "system"]).toContain(entry.source);
        expect(entry.accessCount).toBeGreaterThanOrEqual(0);
      }),
      { numRuns: 20 }
    );
  });

  it("fast-check generates model configs with correct structure", () => {
    fc.assert(
      fc.property(arbModelConfig, (config) => {
        expect(["google", "anthropic", "openai", "mistral"]).toContain(config.provider);
        expect(config.modelId.length).toBeGreaterThanOrEqual(1);
        expect(config.modelId.length).toBeLessThanOrEqual(200);
        expect(config.displayName.length).toBeGreaterThanOrEqual(1);
        expect(config.displayName.length).toBeLessThanOrEqual(60);
        expect(config.costPerMillionTokens).toBeGreaterThan(0);
        expect(config.maxContextWindow).toBeGreaterThanOrEqual(1024);
        expect(typeof config.supportsReasoning).toBe("boolean");
        expect(typeof config.enabled).toBe("boolean");
        expect(typeof config.isDefault).toBe("boolean");
      }),
      { numRuns: 20 }
    );
  });

  it("fast-check generates parse inputs with correct structure", () => {
    fc.assert(
      fc.property(arbParseInput, (input) => {
        expect(input.text.length).toBeGreaterThanOrEqual(1);
        expect(input.text.length).toBeLessThanOrEqual(500);
        expect(input.text.trim().length).toBeGreaterThan(0);
        expect(input.timezone).toBeDefined();
        expect(input.currentDateTime).toMatch(
          /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/
        );
        expect(input.memories.length).toBeLessThanOrEqual(10);
      }),
      { numRuns: 20 }
    );
  });

  it("fast-check generates invalid parse text correctly", () => {
    fc.assert(
      fc.property(arbInvalidParseText, (text) => {
        const isEmptyOrWhitespace = text.trim().length === 0;
        const isTooLong = text.length > 500;
        expect(isEmptyOrWhitespace || isTooLong).toBe(true);
      }),
      { numRuns: 50 }
    );
  });

  it("fast-check generates valid parse responses", () => {
    fc.assert(
      fc.property(arbValidParseResponse, (response) => {
        expect(response.title).toBeDefined();
        expect(response.title.length).toBeLessThanOrEqual(200);
        expect(response.confidence).toBeGreaterThanOrEqual(0);
        expect(response.confidence).toBeLessThanOrEqual(1);
        if (response.priority !== undefined) {
          expect(response.priority).toBeGreaterThanOrEqual(1);
          expect(response.priority).toBeLessThanOrEqual(4);
        }
        if (response.tags !== undefined) {
          expect(response.tags.length).toBeLessThanOrEqual(10);
          for (const tag of response.tags) {
            expect(tag.length).toBeLessThanOrEqual(50);
          }
        }
      }),
      { numRuns: 20 }
    );
  });

  it("fast-check generates token usage with correct normalization", () => {
    fc.assert(
      fc.property(arbTokenUsage, (usage) => {
        const expectedCost =
          ((usage.inputTokens + usage.outputTokens) * usage.costPerMillionTokens) /
          1_000_000;
        expect(usage.normalizedCost).toBeCloseTo(expectedCost, 10);
        expect(usage.normalizedCost).toBeGreaterThanOrEqual(0);
      }),
      { numRuns: 50 }
    );
  });
});
