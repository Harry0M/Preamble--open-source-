/**
 * v2DailyBriefing — Cloud Function endpoint for generating a daily morning briefing.
 *
 * Generates a daily briefing containing:
 * - Today's scheduled tasks
 * - Deadlines within the next 7 calendar days
 * - Relevant memory context from Memory_V2
 * - Proactive insights when ≥14 days of task history available
 *
 * Guards:
 * - At most once per calendar day (checks thread's `lastBriefingDate`)
 * - Only triggers when current local time is between 04:00 and 12:00
 * - Requires Firebase Auth bearer token
 * - Token budget enforcement
 *
 * Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7
 */

import { onRequest } from "firebase-functions/v2/https";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import {
  search,
  checkBudget,
  recordUsage,
  buildBriefingPrompt,
  generate,
  resolveModel,
  estimateTokens,
} from "../services";
import {
  startTrace,
  logMemoryRetrieval,
  logModelCall,
  logFailure,
  endTrace,
} from "../services/trace-logger";
import { conversationThreadPath } from "../utils/collection-paths";
import { enforceRateLimit } from "../utils/rate-limiter";
import type { TaskSnapshot, MemoryEntry } from "../models/types";

// ─── Constants ─────────────────────────────────────────────────────────────────

/** Briefing time window: earliest local hour (inclusive) */
const BRIEFING_WINDOW_START_HOUR = 4;

/** Briefing time window: latest local hour (exclusive) */
const BRIEFING_WINDOW_END_HOUR = 12;

/** Default conversation thread ID used for briefing state tracking */
const BRIEFING_THREAD_ID = "daily_briefing";

/** Number of days of upcoming deadlines to include */
const UPCOMING_DEADLINE_DAYS = 7;

/** Minimum task history days required for proactive insights */
const MIN_HISTORY_DAYS_FOR_INSIGHTS = 14;

// ─── Auth Helper ───────────────────────────────────────────────────────────────

/**
 * Verifies the Firebase Auth token from the Authorization header.
 * Returns the UID on success, or null if invalid/missing.
 */
async function verifyAuth(
  authHeader: string | undefined
): Promise<string | null> {
  if (!authHeader?.startsWith("Bearer ")) return null;
  try {
    const decoded = await getAuth().verifyIdToken(authHeader.slice(7));
    return decoded.uid;
  } catch {
    return null;
  }
}

// ─── Time Helpers ──────────────────────────────────────────────────────────────

/**
 * Gets the current local hour (0-23) in the user's timezone.
 */
function getCurrentLocalHour(timezone: string): number {
  const now = new Date();
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: timezone,
    hour: "numeric",
    hour12: false,
  });
  return parseInt(formatter.format(now), 10);
}

/**
 * Gets today's date string (YYYY-MM-DD) in the user's timezone.
 */
function getTodayInTimezone(timezone: string): string {
  const now = new Date();
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(now);
}

/**
 * Gets a date string N days from now in the user's timezone.
 */
function getDateOffsetInTimezone(timezone: string, days: number): string {
  const now = new Date();
  now.setDate(now.getDate() + days);
  const formatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(now);
}

/**
 * Gets the current ISO 8601 datetime string in the user's timezone.
 */
function getCurrentDateTimeInTimezone(timezone: string): string {
  const now = new Date();
  const dateFormatter = new Intl.DateTimeFormat("en-CA", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  const timeFormatter = new Intl.DateTimeFormat("en-GB", {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
  return `${dateFormatter.format(now)}T${timeFormatter.format(now)}`;
}

// ─── Task Helpers ──────────────────────────────────────────────────────────────

/**
 * Returns the Firestore instance for the "preamble" named database.
 */
function getDb() {
  return getFirestore("preamble");
}

/**
 * Queries tasks scheduled for today for the given user.
 */
async function queryTodayTasks(uid: string, today: string): Promise<TaskSnapshot[]> {
  const db = getDb();
  const snap = await db
    .collection("tasks")
    .where("uid", "==", uid)
    .where("createdDate", "==", today)
    .where("isCompleted", "==", false)
    .get();

  return snap.docs.map((doc) => {
    const data = doc.data();
    return {
      id: doc.id,
      title: data.title || "",
      date: data.createdDate || undefined,
      time: data.deadlineTime || undefined,
      priority: data.priority || undefined,
      tags: data.tags || undefined,
      completed: data.isCompleted || false,
    };
  });
}

/**
 * Queries tasks with deadlines within the next N days for the given user.
 */
async function queryUpcomingDeadlines(
  uid: string,
  today: string,
  endDate: string
): Promise<TaskSnapshot[]> {
  const db = getDb();
  const snap = await db
    .collection("tasks")
    .where("uid", "==", uid)
    .where("createdDate", ">", today)
    .where("createdDate", "<=", endDate)
    .where("isCompleted", "==", false)
    .get();

  return snap.docs.map((doc) => {
    const data = doc.data();
    return {
      id: doc.id,
      title: data.title || "",
      date: data.createdDate || undefined,
      time: data.deadlineTime || undefined,
      priority: data.priority || undefined,
      tags: data.tags || undefined,
      completed: data.isCompleted || false,
    };
  });
}

/**
 * Counts the number of days of task history for the user.
 * Returns the number of days from the earliest task's createdAt to today.
 */
async function countTaskHistoryDays(uid: string): Promise<number> {
  const db = getDb();
  const snap = await db
    .collection("tasks")
    .where("uid", "==", uid)
    .orderBy("createdAt", "asc")
    .limit(1)
    .get();

  if (snap.empty) return 0;

  const earliestTask = snap.docs[0].data();
  const createdAt = earliestTask.createdAt;

  if (!createdAt) return 0;

  // Handle Firestore Timestamp or numeric milliseconds
  const earliestMs = typeof createdAt === "number"
    ? createdAt
    : createdAt.toMillis?.() ?? createdAt._seconds * 1000;

  const now = Date.now();
  const diffMs = now - earliestMs;
  return Math.floor(diffMs / (1000 * 60 * 60 * 24));
}

// ─── Main Endpoint ─────────────────────────────────────────────────────────────

/**
 * POST /v2DailyBriefing
 *
 * Request body: { timezone: string, conversationId?: string }
 *
 * Response on success: { available: true, briefing: string, proactiveInsights: boolean }
 * Response when unavailable: { available: false, reason: string }
 */
export const v2DailyBriefing = onRequest(
  { cors: true, timeoutSeconds: 60 },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    // 1. Verify auth token
    const uid = await verifyAuth(req.headers.authorization);
    if (!uid) {
      res.status(401).json({ error: "Unauthorized" });
      return;
    }

    // 1b. Rate limiting (max 10 req/min per user, Req 26.3)
    if (await enforceRateLimit(uid, res)) return;

    // Extract timezone and conversationId from body
    const { timezone, conversationId } = req.body as {
      timezone?: string;
      conversationId?: string;
    };

    if (!timezone) {
      res.status(400).json({ error: "timezone is required" });
      return;
    }

    const threadId = conversationId || BRIEFING_THREAD_ID;

    // 2. Check time window: current local time must be between 04:00 and 12:00
    const currentHour = getCurrentLocalHour(timezone);
    if (currentHour < BRIEFING_WINDOW_START_HOUR || currentHour >= BRIEFING_WINDOW_END_HOUR) {
      res.json({ available: false, reason: "outside_time_window" });
      return;
    }

    // 3. Check if briefing already delivered today
    const today = getTodayInTimezone(timezone);
    const db = getDb();
    const threadPath = conversationThreadPath(uid, threadId);
    const threadDoc = await db.doc(threadPath).get();

    if (threadDoc.exists) {
      const threadData = threadDoc.data();
      if (threadData?.lastBriefingDate === today) {
        res.json({ available: false, reason: "already_delivered" });
        return;
      }
    }

    // 4. Start trace and check budget
    const trace = startTrace(uid, "briefing");

    const budgetCheck = await checkBudget(uid, 500); // Estimate ~500 normalized tokens for a briefing
    if (!budgetCheck.allowed) {
      logFailure(trace, "budget_exceeded", Date.now() - trace.startTime);
      endTrace(trace, "error");
      res.status(429).json({
        error: "Daily token budget exceeded",
        resetTime: budgetCheck.resetTime,
      });
      return;
    }

    try {
      // 5. Query today's tasks
      const todayTasks = await queryTodayTasks(uid, today);

      // 6. Query upcoming deadlines (next 7 days)
      const endDate = getDateOffsetInTimezone(timezone, UPCOMING_DEADLINE_DAYS);
      const upcomingDeadlines = await queryUpcomingDeadlines(uid, today, endDate);

      // 7. Retrieve relevant memories via MemoryEngine.search()
      let memories: MemoryEntry[] = [];
      try {
        const searchQuery = buildMemorySearchQuery(todayTasks, upcomingDeadlines);
        memories = await search(uid, searchQuery, {
          limit: 10,
          memoryType: "all",
        });
        logMemoryRetrieval(
          trace,
          memories.length,
          memories.length > 0 ? memories[0].confidence : 0
        );
      } catch {
        // Requirement 1.7 / graceful degradation: proceed without memory if unavailable
        logMemoryRetrieval(trace, 0, 0);
      }

      // 8. Count task history days (for proactive insights)
      const taskHistoryDays = await countTaskHistoryDays(uid);
      const includeInsights = taskHistoryDays >= MIN_HISTORY_DAYS_FOR_INSIGHTS;

      // 9. Build briefing prompt via PromptFactory.buildBriefingPrompt()
      const currentDateTime = getCurrentDateTimeInTimezone(timezone);
      const promptPayload = buildBriefingPrompt({
        uid,
        timezone,
        currentDateTime,
        memories,
        todayTasks,
        upcomingDeadlines,
        taskHistoryDays: includeInsights ? taskHistoryDays : undefined,
      });

      // 10. Generate via ModelRouter.generate()
      const modelConfig = await resolveModel();
      const estimatedTokens = estimateTokens(promptPayload.systemPrompt, modelConfig);
      const modelRequest = {
        systemPrompt: promptPayload.systemPrompt,
        messages: promptPayload.messages,
        temperature: promptPayload.temperature,
        maxOutputTokens: promptPayload.maxOutputTokens,
      };

      const startMs = Date.now();
      const response = await generate(modelRequest, modelConfig);
      const latencyMs = Date.now() - startMs;

      logModelCall(
        trace,
        modelConfig.modelId,
        response.inputTokens,
        response.outputTokens,
        latencyMs
      );

      // 11. Validate response and record usage
      if (!response.text) {
        logFailure(trace, "empty_response", Date.now() - trace.startTime);
        endTrace(trace, "error");
        res.status(500).json({ error: "Failed to generate briefing" });
        return;
      }

      await recordUsage(uid, {
        inputTokens: response.inputTokens,
        outputTokens: response.outputTokens,
        model: modelConfig.modelId,
        costPerMillionTokens: modelConfig.costPerMillionTokens,
        normalizedCost:
          ((response.inputTokens + response.outputTokens) *
            modelConfig.costPerMillionTokens) /
          1_000_000,
        traceId: trace.traceId,
      });

      // 12. Update the thread's lastBriefingDate to today
      await db.doc(threadPath).set(
        { lastBriefingDate: today, updatedAt: Date.now() },
        { merge: true }
      );

      // 13. Return the briefing content
      endTrace(trace, "success");
      res.json({
        available: true,
        briefing: response.text,
        proactiveInsights: includeInsights,
        todayTaskCount: todayTasks.length,
        upcomingDeadlineCount: upcomingDeadlines.length,
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : "Internal server error";
      logFailure(trace, message, Date.now() - trace.startTime);
      endTrace(trace, "error");
      res.status(500).json({ error: message });
    }
  }
);

// ─── Internal Helpers ──────────────────────────────────────────────────────────

/**
 * Builds a semantic search query string from the user's tasks for memory retrieval.
 * Combines task titles to find relevant memories contextual to today's agenda.
 */
function buildMemorySearchQuery(
  todayTasks: TaskSnapshot[],
  upcomingDeadlines: TaskSnapshot[]
): string {
  const allTasks = [...todayTasks, ...upcomingDeadlines];
  if (allTasks.length === 0) {
    return "daily routine productivity schedule";
  }

  const titles = allTasks
    .slice(0, 5)
    .map((t) => t.title)
    .join(", ");

  return `tasks and context for: ${titles}`;
}
