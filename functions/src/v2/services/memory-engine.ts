/**
 * MemoryEngine — Semantic memory storage and lifecycle management for AI V2.
 *
 * Manages memory entries with vector embeddings for semantic search.
 * Uses Firestore (named database "preamble") with collection `v2_memory/{uid}/entries/`.
 * Generates 768-dimension embeddings via Google GenAI SDK (text-embedding-004).
 *
 * Key features:
 * - store(): generates embedding and stores new memory entry with count cap enforcement
 * - merge(): updates existing entry with new data (semantic deduplication)
 * - elevateToLongTerm(): promotes short-term entry to long-term
 * - expireStaleShortTerm(): removes stale short-term entries past retention
 * - search(): semantic vector search with confidence filtering and category/type filters
 * - getLinkedEntries(): retrieves directly linked entries above confidence threshold
 * - findDuplicates(): vector search for deduplication with configurable threshold
 *
 * Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 11.1, 11.3, 11.4, 11.5, 12.1, 12.2, 12.3, 12.4, 13.1, 13.4, 14.1
 */

import { getFirestore } from "firebase-admin/firestore";
import { GoogleGenAI } from "@google/genai";
import { MemoryEntry, NewMemoryEntry, SearchOptions } from "../models/types";

// ─── Constants ─────────────────────────────────────────────────────────────────

/** Maximum number of memory entries per user (Req 10.1). */
const MAX_ENTRIES_PER_USER = 1000;

/** Embedding model used for vector generation. */
const EMBEDDING_MODEL = "text-embedding-004";

/** Embedding dimension expected from the model. */
const EMBEDDING_DIMENSION = 768;

/** Similarity threshold for semantic deduplication merge (Req 12.2). */
const MERGE_SIMILARITY_THRESHOLD = 0.85;

/** Default minimum confidence threshold for search retrieval (Req 10.4). */
const DEFAULT_MIN_CONFIDENCE = 0.3;

/** Default result limit for search (Req 10.2). */
const DEFAULT_SEARCH_LIMIT = 10;

/** Maximum result limit for search. */
const MAX_SEARCH_LIMIT = 20;

/** Default similarity threshold for duplicate detection (Req 12.2). */
const DEFAULT_DUPLICATE_THRESHOLD = 0.85;

/** Maximum linked entries to return (Req 11.5). */
const MAX_LINKED_ENTRIES = 5;

/** Timeout in milliseconds for retrieval operations (Req 10.5). */
const RETRIEVAL_TIMEOUT_MS = 5000;

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

// ─── Embedding Generation ──────────────────────────────────────────────────────

/**
 * Generates a 768-dimension embedding vector for the given text using Google GenAI SDK.
 *
 * Uses the text-embedding-004 model via the @google/genai SDK's embedContent method.
 *
 * @param text - The text to generate an embedding for
 * @returns A 768-dimension number array representing the text embedding
 */
export async function generateEmbedding(text: string): Promise<number[]> {
  const client = new GoogleGenAI({
    apiKey: process.env.GOOGLE_GENAI_API_KEY || "",
  });

  const response = await client.models.embedContent({
    model: EMBEDDING_MODEL,
    contents: text,
  });

  const embedding = response.embeddings?.[0]?.values;

  if (!embedding || embedding.length === 0) {
    throw new Error("Failed to generate embedding: empty response from model");
  }

  // Ensure we have the expected dimension (pad or truncate if needed)
  if (embedding.length !== EMBEDDING_DIMENSION) {
    // In practice the model should always return 768-dim, but handle gracefully
    if (embedding.length > EMBEDDING_DIMENSION) {
      return embedding.slice(0, EMBEDDING_DIMENSION);
    }
    // Pad with zeros if somehow shorter
    const padded = [...embedding];
    while (padded.length < EMBEDDING_DIMENSION) {
      padded.push(0);
    }
    return padded;
  }

  return embedding;
}

// ─── Similarity Computation ────────────────────────────────────────────────────

/**
 * Computes cosine similarity between two vectors.
 *
 * @param a - First vector
 * @param b - Second vector
 * @returns Cosine similarity in range [-1, 1] (higher = more similar)
 */
export function cosineSimilarity(a: number[], b: number[]): number {
  if (a.length !== b.length || a.length === 0) return 0;

  let dotProduct = 0;
  let normA = 0;
  let normB = 0;

  for (let i = 0; i < a.length; i++) {
    dotProduct += a[i] * b[i];
    normA += a[i] * a[i];
    normB += b[i] * b[i];
  }

  const denominator = Math.sqrt(normA) * Math.sqrt(normB);
  if (denominator === 0) return 0;

  return dotProduct / denominator;
}

// ─── Memory Count Cap & Eviction ───────────────────────────────────────────────

/**
 * Gets the current count of memory entries for a user.
 *
 * @param uid - The user's UID
 * @returns The number of memory entries stored
 */
async function getEntryCount(uid: string): Promise<number> {
  const snapshot = await getEntriesCollection(uid)
    .count()
    .get();
  return snapshot.data().count;
}

/**
 * Evicts the least-recently-accessed memory entry for a user.
 *
 * Finds the entry with the oldest `lastAccessedAt` timestamp and deletes it.
 * Called when the user is at the 1000 entry cap and a new entry needs to be stored.
 *
 * @param uid - The user's UID
 */
async function evictLeastRecentlyAccessed(uid: string): Promise<void> {
  const collection = getEntriesCollection(uid);
  const snapshot = await collection
    .orderBy("lastAccessedAt", "asc")
    .limit(1)
    .get();

  if (!snapshot.empty) {
    await snapshot.docs[0].ref.delete();
  }
}

// ─── Public API ────────────────────────────────────────────────────────────────

/**
 * Stores a new memory entry with a generated vector embedding.
 *
 * Enforces the 1000-entry count cap per user (Req 10.1). If at the limit,
 * evicts the least-recently-accessed entry before storing the new one.
 *
 * Generates a 768-dim embedding via Google GenAI text-embedding-004, then
 * stores the complete MemoryEntry document in Firestore.
 *
 * @param uid - The authenticated user's UID
 * @param entry - New memory entry data (text, category, memoryType, confidence, source)
 * @returns The generated document ID for the new entry
 *
 * Requirements: 10.1, 11.1, 12.1
 */
export async function store(uid: string, entry: NewMemoryEntry): Promise<string> {
  const collection = getEntriesCollection(uid);

  // Check count cap and evict if necessary (Req 10.1)
  const currentCount = await getEntryCount(uid);
  if (currentCount >= MAX_ENTRIES_PER_USER) {
    await evictLeastRecentlyAccessed(uid);
  }

  // Generate embedding for the entry text
  const embedding = await generateEmbedding(entry.text);

  const now = Date.now();

  // Build the full memory entry document
  const memoryDoc: Omit<MemoryEntry, "id"> = {
    uid,
    text: entry.text,
    embedding,
    category: entry.category,
    memoryType: entry.memoryType,
    confidence: entry.confidence,
    linkedEntryIds: entry.linkedEntryIds ?? [],
    source: entry.source,
    createdAt: now,
    lastAccessedAt: now,
    accessCount: 0,
    ...(entry.conversationId ? { conversationId: entry.conversationId } : {}),
  };

  // Store in Firestore — auto-generates document ID
  const docRef = await collection.add(memoryDoc);

  return docRef.id;
}

/**
 * Merges new data into an existing memory entry.
 *
 * Used for semantic deduplication: when a new entry is detected as a duplicate
 * (similarity ≥ 0.85), the existing entry is updated with the new text and
 * a fresh embedding is generated.
 *
 * Updates:
 * - text (if provided)
 * - embedding (regenerated from new text)
 * - confidence (if provided)
 * - linkedEntryIds (merged, max 20)
 * - lastAccessedAt (set to now)
 * - accessCount (incremented)
 *
 * @param uid - The authenticated user's UID
 * @param existingId - The document ID of the existing entry to merge into
 * @param newData - Partial fields to merge into the existing entry
 *
 * Requirements: 12.2, 12.3
 */
export async function merge(
  uid: string,
  existingId: string,
  newData: Partial<MemoryEntry>
): Promise<void> {
  const docRef = getEntriesCollection(uid).doc(existingId);
  const doc = await docRef.get();

  if (!doc.exists) {
    throw new Error(`Memory entry ${existingId} not found for user ${uid}`);
  }

  const existingData = doc.data() as Omit<MemoryEntry, "id">;
  const updateFields: Record<string, unknown> = {
    lastAccessedAt: Date.now(),
    accessCount: (existingData.accessCount ?? 0) + 1,
  };

  // If new text is provided, regenerate embedding
  if (newData.text && newData.text !== existingData.text) {
    updateFields.text = newData.text;
    updateFields.embedding = await generateEmbedding(newData.text);
  }

  // Merge confidence (prefer higher confidence)
  if (newData.confidence !== undefined) {
    updateFields.confidence = Math.max(
      existingData.confidence,
      newData.confidence
    );
  }

  // Merge category if provided
  if (newData.category) {
    updateFields.category = newData.category;
  }

  // Merge linkedEntryIds (union, max 20)
  if (newData.linkedEntryIds && newData.linkedEntryIds.length > 0) {
    const existingLinks = existingData.linkedEntryIds ?? [];
    const merged = [...new Set([...existingLinks, ...newData.linkedEntryIds])];
    updateFields.linkedEntryIds = merged.slice(0, 20);
  }

  await docRef.update(updateFields);
}

/**
 * Promotes a short-term memory entry to long-term.
 *
 * Changes the `memoryType` field from "short_term" to "long_term" and updates
 * the `lastAccessedAt` timestamp.
 *
 * @param uid - The authenticated user's UID
 * @param entryId - The document ID of the entry to elevate
 *
 * Requirements: 12.4
 */
export async function elevateToLongTerm(
  uid: string,
  entryId: string
): Promise<void> {
  const docRef = getEntriesCollection(uid).doc(entryId);
  const doc = await docRef.get();

  if (!doc.exists) {
    throw new Error(`Memory entry ${entryId} not found for user ${uid}`);
  }

  const data = doc.data() as Omit<MemoryEntry, "id">;

  if (data.memoryType === "long_term") {
    // Already long-term, nothing to do
    return;
  }

  await docRef.update({
    memoryType: "long_term",
    lastAccessedAt: Date.now(),
  });
}

/**
 * Removes short-term memory entries that haven't been accessed within the retention period.
 *
 * Finds all entries where:
 * - memoryType === "short_term"
 * - lastAccessedAt < (now - retentionDays * 24h)
 *
 * Deletes them in batches and returns the count of deleted entries.
 *
 * @param uid - The authenticated user's UID
 * @param retentionDays - Number of days before a short-term entry is considered stale
 * @returns The number of entries deleted
 *
 * Requirements: 13.1, 13.4
 */
export async function expireStaleShortTerm(
  uid: string,
  retentionDays: number
): Promise<number> {
  const collection = getEntriesCollection(uid);
  const cutoffTimestamp = Date.now() - retentionDays * 24 * 60 * 60 * 1000;

  // Query for stale short-term entries
  const snapshot = await collection
    .where("memoryType", "==", "short_term")
    .where("lastAccessedAt", "<", cutoffTimestamp)
    .get();

  if (snapshot.empty) {
    return 0;
  }

  // Delete in batches (Firestore batch limit is 500)
  const batchSize = 500;
  let deletedCount = 0;
  const docs = snapshot.docs;

  for (let i = 0; i < docs.length; i += batchSize) {
    const batch = getDb().batch();
    const batchDocs = docs.slice(i, i + batchSize);

    for (const doc of batchDocs) {
      batch.delete(doc.ref);
    }

    await batch.commit();
    deletedCount += batchDocs.length;
  }

  return deletedCount;
}

// ─── Timeout Utility ───────────────────────────────────────────────────────────

/**
 * Wraps a promise with a timeout. Returns empty array if the operation exceeds the timeout.
 *
 * @param operation - The async operation to wrap
 * @param timeoutMs - Timeout in milliseconds (default: RETRIEVAL_TIMEOUT_MS)
 * @returns The operation result or empty array on timeout
 *
 * Requirements: 10.5
 */
async function withTimeout<T>(
  operation: Promise<T[]>,
  timeoutMs: number = RETRIEVAL_TIMEOUT_MS
): Promise<T[]> {
  const timeoutPromise = new Promise<T[]>((resolve) => {
    setTimeout(() => resolve([]), timeoutMs);
  });

  return Promise.race([operation, timeoutPromise]);
}

// ─── Search & Retrieval ────────────────────────────────────────────────────────

/**
 * Performs semantic search across a user's memory entries using Firestore Vector Search.
 *
 * Generates an embedding for the query text, then uses Firestore's `findNearest()` to
 * find the most similar entries by cosine distance. Results are filtered by confidence
 * threshold and optional category/memoryType filters. Updates `lastAccessedAt` and
 * `accessCount` on retrieved entries.
 *
 * Returns empty array if the operation exceeds 5 seconds (Req 10.5).
 *
 * @param uid - The authenticated user's UID
 * @param query - The search query text
 * @param options - Optional search configuration (limit, minConfidence, categories, memoryType)
 * @returns Array of matching MemoryEntry objects ordered by descending similarity
 *
 * Requirements: 10.2, 10.3, 10.4, 10.5, 14.1
 */
export async function search(
  uid: string,
  query: string,
  options?: SearchOptions
): Promise<MemoryEntry[]> {
  const limit = Math.min(options?.limit ?? DEFAULT_SEARCH_LIMIT, MAX_SEARCH_LIMIT);
  const minConfidence = options?.minConfidence ?? DEFAULT_MIN_CONFIDENCE;
  const categories = options?.categories;
  const memoryType = options?.memoryType ?? "all";

  const operation = async (): Promise<MemoryEntry[]> => {
    // Generate embedding for the query
    const queryEmbedding = await generateEmbedding(query);

    const collection = getEntriesCollection(uid);

    // Use Firestore findNearest for vector search with cosine distance
    // Fetch more results than needed to allow post-filtering
    const fetchLimit = Math.min(limit * 3, 60);

    const vectorQuery = collection.findNearest(
      "embedding",
      queryEmbedding,
      {
        limit: fetchLimit,
        distanceMeasure: "COSINE",
      }
    );

    const snapshot = await vectorQuery.get();

    if (snapshot.empty) {
      return [];
    }

    // Convert results to MemoryEntry array with computed similarity scores
    const results: MemoryEntry[] = [];

    for (const doc of snapshot.docs) {
      const data = doc.data() as Omit<MemoryEntry, "id">;

      // Compute cosine similarity between query embedding and stored embedding
      const similarity = cosineSimilarity(queryEmbedding, data.embedding);

      // Apply minimum confidence threshold (Req 10.4)
      if (similarity < minConfidence) {
        continue;
      }

      // Apply category filter
      if (categories && categories.length > 0 && !categories.includes(data.category)) {
        continue;
      }

      // Apply memoryType filter
      if (memoryType !== "all" && data.memoryType !== memoryType) {
        continue;
      }

      results.push({
        ...data,
        id: doc.id,
        confidence: similarity,
      });
    }

    // Sort by descending similarity (Req 10.2)
    results.sort((a, b) => b.confidence - a.confidence);

    // Limit to requested count (Req 10.2 — default 10)
    const limited = results.slice(0, limit);

    // Update lastAccessedAt and accessCount for retrieved entries (non-blocking)
    const updatePromises = limited.map((entry) =>
      getEntriesCollection(uid).doc(entry.id).update({
        lastAccessedAt: Date.now(),
        accessCount: (entry.accessCount ?? 0) + 1,
      }).catch(() => {
        // Non-critical: don't fail search if access tracking fails
      })
    );
    await Promise.all(updatePromises);

    return limited;
  };

  return withTimeout(operation());
}

/**
 * Retrieves directly linked memory entries for a given entry.
 *
 * Fetches entries referenced by `linkedEntryIds` on the source entry,
 * filters by minimum confidence threshold, sorts by confidence descending,
 * and returns up to 5 entries.
 *
 * Returns empty array if the operation exceeds 5 seconds (Req 10.5).
 *
 * @param uid - The authenticated user's UID
 * @param entryId - The document ID of the entry whose links to retrieve
 * @param limit - Maximum number of linked entries to return (default 5, max 5)
 * @returns Array of linked MemoryEntry objects above confidence threshold
 *
 * Requirements: 11.3, 11.4, 11.5
 */
export async function getLinkedEntries(
  uid: string,
  entryId: string,
  limit?: number
): Promise<MemoryEntry[]> {
  const maxResults = Math.min(limit ?? MAX_LINKED_ENTRIES, MAX_LINKED_ENTRIES);

  const operation = async (): Promise<MemoryEntry[]> => {
    const collection = getEntriesCollection(uid);

    // Fetch the source entry to get its linkedEntryIds
    const sourceDoc = await collection.doc(entryId).get();

    if (!sourceDoc.exists) {
      return [];
    }

    const sourceData = sourceDoc.data() as Omit<MemoryEntry, "id">;
    const linkedIds = sourceData.linkedEntryIds ?? [];

    if (linkedIds.length === 0) {
      return [];
    }

    // Fetch linked entries by ID
    const linkedDocs = await Promise.all(
      linkedIds.map((id) => collection.doc(id).get())
    );

    const results: MemoryEntry[] = [];

    for (const doc of linkedDocs) {
      if (!doc.exists) continue;

      const data = doc.data() as Omit<MemoryEntry, "id">;

      // Filter by minimum confidence threshold (Req 11.5)
      if (data.confidence < DEFAULT_MIN_CONFIDENCE) {
        continue;
      }

      results.push({
        ...data,
        id: doc.id,
      });
    }

    // Sort by confidence descending (Req 11.5)
    results.sort((a, b) => b.confidence - a.confidence);

    // Limit to max results
    const limited = results.slice(0, maxResults);

    // Update lastAccessedAt and accessCount for retrieved entries (non-blocking)
    const updatePromises = limited.map((entry) =>
      collection.doc(entry.id).update({
        lastAccessedAt: Date.now(),
        accessCount: (entry.accessCount ?? 0) + 1,
      }).catch(() => {
        // Non-critical: don't fail retrieval if access tracking fails
      })
    );
    await Promise.all(updatePromises);

    return limited;
  };

  return withTimeout(operation());
}

/**
 * Finds potential duplicate memory entries via vector search.
 *
 * Uses semantic similarity search with a configurable threshold (default 0.85)
 * to identify entries that are near-duplicates of the provided text.
 * Useful for deduplication before storing new entries.
 *
 * Returns empty array if the operation exceeds 5 seconds (Req 10.5).
 *
 * @param uid - The authenticated user's UID
 * @param text - The text to check for duplicates against
 * @param threshold - Minimum similarity score to consider a duplicate (default 0.85)
 * @returns Array of matching MemoryEntry objects above the threshold
 *
 * Requirements: 10.2, 10.5, 12.2
 */
export async function findDuplicates(
  uid: string,
  text: string,
  threshold?: number
): Promise<MemoryEntry[]> {
  const similarityThreshold = threshold ?? DEFAULT_DUPLICATE_THRESHOLD;

  const operation = async (): Promise<MemoryEntry[]> => {
    // Generate embedding for the text to check
    const queryEmbedding = await generateEmbedding(text);

    const collection = getEntriesCollection(uid);

    // Use Firestore findNearest with a smaller result set for deduplication
    const vectorQuery = collection.findNearest(
      "embedding",
      queryEmbedding,
      {
        limit: 10,
        distanceMeasure: "COSINE",
      }
    );

    const snapshot = await vectorQuery.get();

    if (snapshot.empty) {
      return [];
    }

    const results: MemoryEntry[] = [];

    for (const doc of snapshot.docs) {
      const data = doc.data() as Omit<MemoryEntry, "id">;

      // Compute cosine similarity
      const similarity = cosineSimilarity(queryEmbedding, data.embedding);

      // Only include entries above the duplicate threshold
      if (similarity < similarityThreshold) {
        continue;
      }

      results.push({
        ...data,
        id: doc.id,
        confidence: similarity,
      });
    }

    // Sort by descending similarity
    results.sort((a, b) => b.confidence - a.confidence);

    return results;
  };

  return withTimeout(operation());
}