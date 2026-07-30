/**
 * Confidence-based field omission filter for parse results.
 *
 * Implements the requirement that temporal (date, time) and recurrence fields
 * are omitted from parse output when their field-level confidence is below 0.5.
 *
 * This is the enforcement layer for:
 * - Requirement 2.6: Omit date/time when Parser cannot resolve
 * - Requirement 3.5: Omit time when it doesn't resolve to valid HH:mm
 * - Requirement 3.6: Omit date/time when Parse_Confidence for temporal field < 0.5
 * - Requirement 4.5: Omit recurrence when confidence < 0.5
 */

/**
 * Confidence scores for individual temporal/recurrence fields.
 * Each score is in the range [0.0, 1.0].
 */
export interface FieldConfidences {
  date: number;
  time: number;
  recurrence: number;
}

/**
 * Raw parse result from the AI model, before confidence-based filtering.
 * Temporal and recurrence fields may be present regardless of confidence.
 */
export interface RawParseResult {
  title: string;
  confidence: number;
  date?: string;
  time?: string;
  recurrence?: {
    recurrenceType: "daily" | "weekly" | "monthly" | "yearly";
    recurrenceInterval: number;
    recurrenceDays?: number[];
    recurrenceEndDate?: string;
  };
  description?: string;
  detectedLanguage?: string;
  priority?: number;
  tags?: string[];
  duplicates?: Array<{
    taskId: string;
    similarity: number;
    title: string;
  }>;
}

/**
 * The confidence threshold below which temporal/recurrence fields are omitted.
 * Fields with confidence >= this value are preserved.
 * Fields with confidence < this value are omitted.
 */
export const CONFIDENCE_THRESHOLD = 0.5;

/**
 * Applies low-confidence field omission to a raw parse result.
 *
 * For each temporal/recurrence field (date, time, recurrence), if the
 * corresponding field confidence is below 0.5, the field is removed from the
 * output. Non-temporal fields (title, description, tags, priority, detectedLanguage)
 * are never affected by this filter.
 *
 * @param rawResult - The raw parse result from the AI model
 * @param fieldConfidences - Per-field confidence scores for temporal/recurrence fields
 * @returns A new parse result with low-confidence fields omitted
 */
export function applyLowConfidenceOmission(
  rawResult: RawParseResult,
  fieldConfidences: FieldConfidences
): RawParseResult {
  const result: RawParseResult = {
    title: rawResult.title,
    confidence: rawResult.confidence,
  };

  // Copy non-temporal fields as-is (never affected by confidence filter)
  if (rawResult.description !== undefined) {
    result.description = rawResult.description;
  }
  if (rawResult.detectedLanguage !== undefined) {
    result.detectedLanguage = rawResult.detectedLanguage;
  }
  if (rawResult.priority !== undefined) {
    result.priority = rawResult.priority;
  }
  if (rawResult.tags !== undefined) {
    result.tags = rawResult.tags;
  }
  if (rawResult.duplicates !== undefined) {
    result.duplicates = rawResult.duplicates;
  }

  // Apply confidence threshold to temporal/recurrence fields
  // Only include field if confidence >= threshold (Req 2.6, 3.5, 3.6, 4.5)
  if (rawResult.date !== undefined && fieldConfidences.date >= CONFIDENCE_THRESHOLD) {
    result.date = rawResult.date;
  }

  if (rawResult.time !== undefined && fieldConfidences.time >= CONFIDENCE_THRESHOLD) {
    result.time = rawResult.time;
  }

  if (rawResult.recurrence !== undefined && fieldConfidences.recurrence >= CONFIDENCE_THRESHOLD) {
    result.recurrence = rawResult.recurrence;
  }

  return result;
}
