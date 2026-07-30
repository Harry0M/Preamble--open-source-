/**
 * MemoryCategorizer — Classification, relationship linking, and retry utilities for Memory_V2.
 *
 * Provides:
 * - classifyMemory(): Keyword-based heuristic classification into exactly one MemoryCategory
 * - detectAndLinkEntities(): Detects shared entities across entries and creates bidirectional links
 * - withRetry(): Exponential backoff retry utility for memory extraction calls
 *
 * Requirements: 11.1, 11.2, 11.3, 11.4, 13.2, 13.3
 */

import { getFirestore } from "firebase-admin/firestore";
import { MemoryCategory, MemoryEntry } from "../models/types";

// ─── Constants ─────────────────────────────────────────────────────────────────

/** Maximum number of relationship links per memory entry (Req 11.4). */
const MAX_LINKS_PER_ENTRY = 20;

/** Base delay in milliseconds for exponential backoff retry. */
const BASE_RETRY_DELAY_MS = 100;

/** Default maximum retry attempts for extraction (Req 13.2). */
const DEFAULT_MAX_ATTEMPTS = 3;

// ─── Keyword Maps ──────────────────────────────────────────────────────────────

/**
 * Keyword-to-category mapping for heuristic classification.
 * Each category has a list of keywords/phrases that signal membership.
 * Order matters: earlier entries in the array have higher priority when multiple match.
 */
const CATEGORY_KEYWORDS: Record<MemoryCategory, string[]> = {
  identity: ["my name", "i am a", "i am an", "name is", "\\bage\\b", "school", "college", "\\bjob\\b", "work at", "i'm a ", "i'm an "],
  preference: ["prefer", "\\blike\\b", "don't like", "dont like", "favorite", "favourite", "\\bhate\\b", "\\blove\\b"],
  goal: ["want to", "\\bgoal\\b", "\\baim\\b", "plan to", "aspire", "planning to", "hoping to", "\\bdream\\b"],
  relationship: ["\\bfriend\\b", "\\bbrother\\b", "\\bsister\\b", "\\bmother\\b", "\\bfather\\b", "\\bcolleague\\b", "\\bpartner\\b", "\\bhusband\\b", "\\bwife\\b", "\\bson\\b", "\\bdaughter\\b", "\\bboss\\b", "\\broommate\\b"],
  habit: ["\\balways\\b", "\\busually\\b", "every day", "everyday", "\\broutine\\b", "\\bhabit\\b", "tend to", "regularly"],
  schedule: ["\\bmeeting\\b", "\\bclass\\b", "\\bappointment\\b", "\\bdeadline\\b", "\\bscheduled\\b", "at \\d", "on monday", "on tuesday", "on wednesday", "on thursday", "on friday", "on saturday", "on sunday"],
  location: ["live in", "\\bfrom\\b", "\\btimezone\\b", "\\bcity\\b", "\\baddress\\b", "located", "based in", "moved to"],
  important_date: ["\\bbirthday\\b", "\\banniversary\\b", "\\bexam\\b", "event on", "born on", "due date"],
  project: ["\\bproject\\b", "working on", "\\bbuilding\\b", "\\bdeveloping\\b", "\\bcreating\\b", "side project"],
  productivity_pattern: ["\\bcomplete\\b", "\\bfinish\\b", "time spent", "\\bproductive\\b", "\\bproductivity\\b", "took me", "hours to"],
  interest: ["interested in", "\\bhobby\\b", "\\benjoy\\b", "\\bpassion\\b", "passionate", "fascinated", "curious about"],
  context: [], // Default fallback — no specific keywords
};

// ─── Firestore References ──────────────────────────────────────────────────────

/**
 * Returns the Firestore instance for the "preamble" named database.
 */
function getDb() {
  return getFirestore("preamble");
}

/**
 * Returns the collection reference for a user's memory entries.
 */
function getEntriesCollection(uid: string) {
  return getDb().collection("v2_memory").doc(uid).collection("entries");
}

// ─── Classification ────────────────────────────────────────────────────────────

/**
 * Classifies text into exactly one MemoryCategory using keyword-based heuristics.
 *
 * Scans the input text (case-insensitive) against known keyword patterns for each category.
 * Returns the category with the most keyword matches. Falls back to "context" if no
 * category achieves any match (insufficient confidence).
 *
 * @param text - The memory text to classify
 * @returns Exactly one MemoryCategory from the allowed set
 *
 * Requirements: 11.1, 11.2
 */
export function classifyMemory(text: string): MemoryCategory {
  const lowerText = text.toLowerCase();

  let bestCategory: MemoryCategory = "context";
  let bestScore = 0;

  for (const [category, keywords] of Object.entries(CATEGORY_KEYWORDS) as [MemoryCategory, string[]][]) {
    if (keywords.length === 0) continue;

    let score = 0;
    for (const keyword of keywords) {
      // Support regex patterns in keywords (e.g., "at \\d" for schedule)
      const pattern = new RegExp(keyword, "i");
      if (pattern.test(lowerText)) {
        score++;
      }
    }

    if (score > bestScore) {
      bestScore = score;
      bestCategory = category;
    }
  }

  // Default to "context" when classification confidence is insufficient (no matches)
  return bestCategory;
}

// ─── Entity Detection & Relationship Linking ───────────────────────────────────

/**
 * Extracts simple entities (capitalized words, likely names/places/projects) from text.
 * Returns a set of normalized entity strings for comparison.
 *
 * @param text - The text to extract entities from
 * @returns A Set of lowercase entity strings
 */
function extractEntities(text: string): Set<string> {
  const entities = new Set<string>();

  // Extract capitalized words (potential proper nouns: names, places, projects)
  // Matches sequences of capitalized words (e.g., "John Smith", "IIT Delhi")
  const capitalizedPattern = /\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*/g;
  const capitalizedMatches = text.match(capitalizedPattern) || [];
  for (const match of capitalizedMatches) {
    entities.add(match.toLowerCase());
  }

  // Extract quoted strings as potential entity names
  const quotedPattern = /"([^"]+)"|'([^']+)'/g;
  let quotedMatch;
  while ((quotedMatch = quotedPattern.exec(text)) !== null) {
    const value = quotedMatch[1] || quotedMatch[2];
    if (value && value.length > 1) {
      entities.add(value.toLowerCase());
    }
  }

  // Extract date-like patterns (YYYY-MM-DD, DD/MM/YYYY)
  const datePattern = /\b\d{4}-\d{2}-\d{2}\b|\b\d{1,2}\/\d{1,2}\/\d{2,4}\b/g;
  const dateMatches = text.match(datePattern) || [];
  for (const match of dateMatches) {
    entities.add(match);
  }

  return entities;
}

/**
 * Detects shared entities across memory entries and creates bidirectional relationship links.
 *
 * After a new entry is stored, this function:
 * 1. Extracts entities from the new entry's text
 * 2. Searches existing entries for shared entities (person names, project names, locations, dates)
 * 3. Creates bidirectional links (max 20 per entry) by updating `linkedEntryIds` on both entries
 *
 * @param uid - The authenticated user's UID
 * @param newEntryId - The document ID of the newly stored entry
 * @param text - The text content of the new entry (used for entity extraction)
 *
 * Requirements: 11.3, 11.4
 */
export async function detectAndLinkEntities(
  uid: string,
  newEntryId: string,
  text: string
): Promise<void> {
  const newEntities = extractEntities(text);

  // If no entities detected, nothing to link
  if (newEntities.size === 0) {
    return;
  }

  const collection = getEntriesCollection(uid);

  // Fetch recent entries to check for shared entities (limit scan for performance)
  const snapshot = await collection
    .orderBy("lastAccessedAt", "desc")
    .limit(100)
    .get();

  if (snapshot.empty) {
    return;
  }

  const matchedEntryIds: string[] = [];

  for (const doc of snapshot.docs) {
    // Skip the new entry itself
    if (doc.id === newEntryId) continue;

    const existingData = doc.data() as Omit<MemoryEntry, "id">;
    const existingEntities = extractEntities(existingData.text);

    // Check for shared entities
    let hasSharedEntity = false;
    for (const entity of newEntities) {
      if (existingEntities.has(entity)) {
        hasSharedEntity = true;
        break;
      }
    }

    if (hasSharedEntity) {
      matchedEntryIds.push(doc.id);
    }

    // Stop if we've reached the max links limit
    if (matchedEntryIds.length >= MAX_LINKS_PER_ENTRY) {
      break;
    }
  }

  if (matchedEntryIds.length === 0) {
    return;
  }

  // Update the new entry with links to matched entries
  const newEntryRef = collection.doc(newEntryId);
  const newEntryDoc = await newEntryRef.get();

  if (newEntryDoc.exists) {
    const currentLinks: string[] = (newEntryDoc.data() as Omit<MemoryEntry, "id">).linkedEntryIds || [];
    const mergedNewLinks = [...new Set([...currentLinks, ...matchedEntryIds])].slice(0, MAX_LINKS_PER_ENTRY);
    await newEntryRef.update({ linkedEntryIds: mergedNewLinks });
  }

  // Update each matched entry with a backlink to the new entry (bidirectional)
  const batch = getDb().batch();
  let batchCount = 0;

  for (const matchedId of matchedEntryIds) {
    const matchedRef = collection.doc(matchedId);
    const matchedDoc = await matchedRef.get();

    if (matchedDoc.exists) {
      const matchedData = matchedDoc.data() as Omit<MemoryEntry, "id">;
      const existingLinks: string[] = matchedData.linkedEntryIds || [];

      // Only add backlink if under the max limit and not already linked
      if (existingLinks.length < MAX_LINKS_PER_ENTRY && !existingLinks.includes(newEntryId)) {
        batch.update(matchedRef, {
          linkedEntryIds: [...existingLinks, newEntryId],
        });
        batchCount++;
      }
    }

    // Firestore batch limit is 500, but we won't exceed 20 updates here
    if (batchCount >= MAX_LINKS_PER_ENTRY) break;
  }

  if (batchCount > 0) {
    await batch.commit();
  }
}

// ─── Retry Utility ─────────────────────────────────────────────────────────────

/**
 * Retries an async function with exponential backoff.
 *
 * Delay pattern: 100ms, 200ms, 400ms (doubling each attempt).
 * Used for memory extraction calls to handle transient failures.
 *
 * @param fn - The async function to retry
 * @param maxAttempts - Maximum number of attempts (default: 3)
 * @returns The result of the function on success
 * @throws The last error encountered after all attempts are exhausted
 *
 * Requirements: 13.2, 13.3
 */
export async function withRetry<T>(
  fn: () => Promise<T>,
  maxAttempts: number = DEFAULT_MAX_ATTEMPTS
): Promise<T> {
  let lastError: Error | unknown;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return await fn();
    } catch (error) {
      lastError = error;

      // If we've exhausted all attempts, throw the last error
      if (attempt >= maxAttempts) {
        break;
      }

      // Exponential backoff: 100ms, 200ms, 400ms...
      const delay = BASE_RETRY_DELAY_MS * Math.pow(2, attempt - 1);
      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }

  throw lastError;
}
