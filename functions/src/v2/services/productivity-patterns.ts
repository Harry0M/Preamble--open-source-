/**
 * Productivity Pattern Memory Storage
 *
 * Stores task completion duration data as Long_Term_Memory entries in the
 * "productivity_pattern" category. Provides time estimation lookup for Chat_V2
 * based on historical completion patterns.
 *
 * Requirements: 24.3, 24.4
 */

import { store, search } from "./memory-engine";
import { NewMemoryEntry, MemoryEntry } from "../models/types";

// ─── Types ─────────────────────────────────────────────────────────────────────

/**
 * Task data needed to record a completion pattern.
 */
export interface TaskCompletionData {
  taskId: string;
  title: string;
  tags?: string[];
  createdAt: number;
  completedAt: number;
}

/**
 * Time estimate result based on historical patterns.
 */
export interface TimeEstimate {
  averageHours: number;
  sampleCount: number;
  confidence: number;
}

// ─── Constants ─────────────────────────────────────────────────────────────────

/** Minimum duration (ms) to consider valid — filters out instant completions. */
const MIN_VALID_DURATION_MS = 60_000; // 1 minute

/** Maximum entries to retrieve for estimation. */
const PATTERN_SEARCH_LIMIT = 10;

/** Minimum confidence for search results when looking up patterns. */
const PATTERN_MIN_CONFIDENCE = 0.3;

// ─── Functions ─────────────────────────────────────────────────────────────────

/**
 * Records a task completion as a productivity pattern memory entry.
 *
 * Computes the duration from task creation to completion and stores it
 * as a Long_Term_Memory entry in the "productivity_pattern" category.
 *
 * The stored text includes the task title, tags, and duration in hours
 * so that semantic search can match similar task types.
 *
 * @param uid - The authenticated user's UID
 * @param taskData - Task completion data including timestamps and metadata
 * @returns The memory entry ID, or null if duration is invalid
 *
 * Requirements: 24.3
 */
export async function recordTaskCompletion(
  uid: string,
  taskData: TaskCompletionData
): Promise<string | null> {
  const durationMs = taskData.completedAt - taskData.createdAt;

  // Skip recording for instant completions or invalid durations
  if (durationMs < MIN_VALID_DURATION_MS) {
    return null;
  }

  const durationHours = durationMs / (1000 * 60 * 60);
  const durationDisplay = formatDuration(durationMs);

  const tagsStr = taskData.tags && taskData.tags.length > 0
    ? ` (tags: ${taskData.tags.join(", ")})`
    : "";

  const text = `Task '${taskData.title}'${tagsStr} completed in ${durationDisplay}`;

  const entry: NewMemoryEntry = {
    text,
    category: "productivity_pattern",
    memoryType: "long_term",
    confidence: 0.9,
    source: "system",
  };

  try {
    const entryId = await store(uid, entry);
    return entryId;
  } catch {
    // Non-critical: don't fail the task completion if pattern storage fails
    return null;
  }
}

/**
 * Looks up time estimates for a task based on historical productivity patterns.
 *
 * Searches Memory_V2 for "productivity_pattern" entries matching similar tasks
 * (by title and/or tags), computes an average completion time, and returns it.
 *
 * @param uid - The authenticated user's UID
 * @param taskTitle - The title of the task to estimate
 * @param tags - Optional tags to improve matching
 * @returns Time estimate or null if insufficient historical data
 *
 * Requirements: 24.4
 */
export async function getTimeEstimate(
  uid: string,
  taskTitle: string,
  tags?: string[]
): Promise<TimeEstimate | null> {
  // Build a query combining title and tags for semantic matching
  const tagsStr = tags && tags.length > 0 ? ` tags: ${tags.join(", ")}` : "";
  const query = `Task '${taskTitle}'${tagsStr} completion time`;

  try {
    const results = await search(uid, query, {
      limit: PATTERN_SEARCH_LIMIT,
      minConfidence: PATTERN_MIN_CONFIDENCE,
      categories: ["productivity_pattern"],
      memoryType: "long_term",
    });

    if (results.length === 0) {
      return null;
    }

    // Extract durations from pattern entries
    const durations = extractDurationsFromEntries(results);

    if (durations.length === 0) {
      return null;
    }

    const averageHours = durations.reduce((sum, d) => sum + d, 0) / durations.length;

    // Confidence based on number of data points and average similarity
    const avgConfidence = results
      .slice(0, durations.length)
      .reduce((sum, r) => sum + r.confidence, 0) / durations.length;

    return {
      averageHours: Math.round(averageHours * 100) / 100,
      sampleCount: durations.length,
      confidence: Math.round(avgConfidence * 100) / 100,
    };
  } catch {
    // Non-critical: return null if search fails
    return null;
  }
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Formats a duration in milliseconds to a human-readable string.
 */
function formatDuration(durationMs: number): string {
  const hours = durationMs / (1000 * 60 * 60);

  if (hours < 1) {
    const minutes = Math.round(durationMs / (1000 * 60));
    return `${minutes} minutes`;
  } else if (hours < 24) {
    return `${Math.round(hours * 10) / 10} hours`;
  } else {
    const days = Math.round(hours / 24 * 10) / 10;
    return `${days} days`;
  }
}

/**
 * Extracts numeric durations (in hours) from productivity pattern memory entries.
 *
 * Parses the stored text format: "Task '...' completed in X hours/minutes/days"
 */
function extractDurationsFromEntries(entries: MemoryEntry[]): number[] {
  const durations: number[] = [];
  // Matches: "completed in <number> <unit>"
  const durationRegex = /completed in ([\d.]+) (minutes?|hours?|days?)/i;

  for (const entry of entries) {
    const match = entry.text.match(durationRegex);
    if (match) {
      const value = parseFloat(match[1]);
      const unit = match[2].toLowerCase();

      let hours: number;
      if (unit.startsWith("minute")) {
        hours = value / 60;
      } else if (unit.startsWith("hour")) {
        hours = value;
      } else if (unit.startsWith("day")) {
        hours = value * 24;
      } else {
        continue;
      }

      if (hours > 0 && isFinite(hours)) {
        durations.push(hours);
      }
    }
  }

  return durations;
}
