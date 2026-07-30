/**
 * ToolExecutor — Executes approved tool calls within user data scope.
 *
 * Validates that all Firestore paths are scoped to the authenticated user's UID,
 * then dispatches to the appropriate tool handler (read or write).
 *
 * Read tools: get_today_tasks, get_tasks_by_date_range, get_task_by_name,
 *             get_friends_list, get_social_circles
 * Write tools: create_task, update_task, complete_task, create_circle, add_circle_members
 *
 * Uses the named Firestore database "preamble".
 * - Tasks: top-level `tasks` collection with `uid` field
 * - Friends: `users/{uid}/friends/`
 * - Circles: top-level `circles` collection with `memberUidMap`
 *
 * Requirements: 7.2, 7.4, 7.5, 7.7, 8.1, 8.2, 8.4
 */

import { getFirestore } from "firebase-admin/firestore";
import {
  ToolCall,
  ApprovedToolCall,
  ToolResult,
  ScopeValidation,
  ReadTool,
  WriteTool,
} from "../models/types";
import { recordTaskCompletion } from "./productivity-patterns";

// ─── Firestore References ──────────────────────────────────────────────────────

/**
 * Returns the Firestore instance for the "preamble" named database.
 */
function getDb() {
  return getFirestore("preamble");
}

// ─── Valid Tool Names ──────────────────────────────────────────────────────────

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

// ─── Scope Validation ──────────────────────────────────────────────────────────

/**
 * Validates that a tool call is scoped to the authenticated user's UID.
 *
 * Checks:
 * 1. The `targetData` field does not reference another user's document path.
 * 2. No argument values contain Firestore paths referencing other UIDs.
 *
 * Returns `{valid: false, reason: "SCOPE_VIOLATION"}` if cross-user access is detected.
 *
 * @param uid - The authenticated user's UID
 * @param toolCall - The tool call to validate
 */
export function validateScope(uid: string, toolCall: ToolCall): ScopeValidation {
  // Pattern to detect Firestore user-scoped paths referencing a different UID
  // Matches patterns like "users/{someUid}/..." where someUid != uid
  const userPathPattern = /users\/([a-zA-Z0-9_-]+)/g;

  // Check targetData for cross-user references
  if (toolCall.targetData) {
    const matches = toolCall.targetData.matchAll(userPathPattern);
    for (const match of matches) {
      if (match[1] !== uid) {
        return { valid: false, reason: "SCOPE_VIOLATION" };
      }
    }
  }

  // Check all argument values recursively for cross-user paths
  if (!validateArgsScope(uid, toolCall.args)) {
    return { valid: false, reason: "SCOPE_VIOLATION" };
  }

  return { valid: true };
}

/**
 * Recursively checks argument values for Firestore paths referencing other UIDs.
 */
function validateArgsScope(uid: string, args: Record<string, unknown>): boolean {
  const userPathPattern = /users\/([a-zA-Z0-9_-]+)/g;

  for (const value of Object.values(args)) {
    if (typeof value === "string") {
      const matches = value.matchAll(userPathPattern);
      for (const match of matches) {
        if (match[1] !== uid) {
          return false;
        }
      }
    } else if (value !== null && typeof value === "object") {
      if (Array.isArray(value)) {
        for (const item of value) {
          if (typeof item === "string") {
            const matches = item.matchAll(userPathPattern);
            for (const match of matches) {
              if (match[1] !== uid) {
                return false;
              }
            }
          } else if (item !== null && typeof item === "object") {
            if (!validateArgsScope(uid, item as Record<string, unknown>)) {
              return false;
            }
          }
        }
      } else {
        if (!validateArgsScope(uid, value as Record<string, unknown>)) {
          return false;
        }
      }
    }
  }
  return true;
}


// ─── Tool Execution ────────────────────────────────────────────────────────────

/**
 * Executes an approved tool call against the user's data.
 *
 * Flow:
 * 1. Validate scope — reject if cross-user access detected.
 * 2. Dispatch to the correct tool handler based on `toolCall.name`.
 * 3. Wrap in try/catch — on failure return `{success: false, error: ...}`.
 * 4. On success return `{success: true, data: ...}`.
 *
 * @param uid - The authenticated user's UID
 * @param toolCall - The approved tool call to execute
 * @returns ToolResult with success/failure and data or error
 */
export async function execute(
  uid: string,
  toolCall: ApprovedToolCall
): Promise<ToolResult> {
  const baseResult = {
    toolName: toolCall.name,
    traceId: toolCall.traceId,
    executedAt: Date.now(),
  };

  // Step 1: Validate scope
  const scopeCheck = validateScope(uid, toolCall);
  if (!scopeCheck.valid) {
    return {
      ...baseResult,
      success: false,
      error: `SCOPE_VIOLATION: Tool call rejected — cannot access data outside authenticated user's scope.`,
    };
  }

  // Step 2: Validate tool name
  const allTools = [...READ_TOOLS, ...WRITE_TOOLS] as string[];
  if (!allTools.includes(toolCall.name)) {
    return {
      ...baseResult,
      success: false,
      error: `Unknown tool: ${toolCall.name}`,
    };
  }

  // Step 3: Dispatch to handler
  try {
    const data = await dispatchTool(uid, toolCall);
    return { ...baseResult, success: true, data };
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : "Tool execution failed";
    return { ...baseResult, success: false, error: message };
  }
}

/**
 * Dispatches to the appropriate tool handler based on tool name.
 */
async function dispatchTool(
  uid: string,
  toolCall: ApprovedToolCall
): Promise<unknown> {
  switch (toolCall.name) {
    // Read tools
    case "get_today_tasks":
      return getTodayTasks(uid);
    case "get_tasks_by_date_range":
      return getTasksByDateRange(uid, toolCall.args);
    case "get_task_by_name":
      return getTaskByName(uid, toolCall.args);
    case "get_friends_list":
      return getFriendsList(uid);
    case "get_social_circles":
      return getSocialCircles(uid);
    // Write tools
    case "create_task":
      return createTask(uid, toolCall.args);
    case "update_task":
      return updateTask(uid, toolCall.args);
    case "complete_task":
      return completeTask(uid, toolCall.args);
    case "create_circle":
      return createCircle(uid, toolCall.args);
    case "add_circle_members":
      return addCircleMembers(uid, toolCall.args);
    default:
      throw new Error(`Unhandled tool: ${toolCall.name}`);
  }
}


// ─── Read Tool Handlers ────────────────────────────────────────────────────────

/**
 * Retrieves today's incomplete tasks for the user.
 * Queries tasks where `createdDate` equals today's date and `isCompleted` is false.
 */
async function getTodayTasks(uid: string): Promise<unknown> {
  const db = getDb();
  const today = getTodayDateString();

  const snap = await db
    .collection("tasks")
    .where("uid", "==", uid)
    .where("createdDate", "==", today)
    .where("isCompleted", "==", false)
    .get();

  return snap.docs.map((doc) => ({
    id: doc.id,
    ...sanitizeTaskData(doc.data()),
  }));
}

/**
 * Retrieves tasks within a date range for the user.
 * Args: { startDate: string, endDate: string } in YYYY-MM-DD format.
 */
async function getTasksByDateRange(
  uid: string,
  args: Record<string, unknown>
): Promise<unknown> {
  const db = getDb();
  const startDate = args.startDate as string;
  const endDate = args.endDate as string;

  if (!startDate || !endDate) {
    throw new Error("startDate and endDate are required");
  }

  const snap = await db
    .collection("tasks")
    .where("uid", "==", uid)
    .where("createdDate", ">=", startDate)
    .where("createdDate", "<=", endDate)
    .get();

  return snap.docs.map((doc) => ({
    id: doc.id,
    ...sanitizeTaskData(doc.data()),
  }));
}

/**
 * Searches for tasks by title matching a search term (case-insensitive).
 * Args: { searchTerm: string }
 *
 * Note: Firestore doesn't natively support case-insensitive substring search,
 * so we fetch the user's tasks and filter client-side.
 */
async function getTaskByName(
  uid: string,
  args: Record<string, unknown>
): Promise<unknown> {
  const db = getDb();
  const searchTerm = (args.searchTerm as string || "").toLowerCase();

  if (!searchTerm) {
    throw new Error("searchTerm is required");
  }

  const snap = await db
    .collection("tasks")
    .where("uid", "==", uid)
    .limit(100)
    .get();

  const matches = snap.docs
    .filter((doc) => {
      const title = (doc.data().title || "").toLowerCase();
      return title.includes(searchTerm);
    })
    .map((doc) => ({
      id: doc.id,
      ...sanitizeTaskData(doc.data()),
    }));

  return matches;
}

/**
 * Retrieves the user's friends list.
 * Friends are stored at `users/{uid}/friends/{friendUid}`.
 */
async function getFriendsList(uid: string): Promise<unknown> {
  const db = getDb();

  const snap = await db
    .collection(`users/${uid}/friends`)
    .get();

  return snap.docs.map((doc) => ({
    uid: doc.id,
    ...sanitizeFriendData(doc.data()),
  }));
}

/**
 * Retrieves circles that the user is a member of.
 * Circles are stored at top-level `circles` collection with `memberUidMap`.
 */
async function getSocialCircles(uid: string): Promise<unknown> {
  const db = getDb();

  const snap = await db
    .collection("circles")
    .where(`memberUidMap.${uid}`, "!=", null)
    .get();

  return snap.docs.map((doc) => ({
    id: doc.id,
    ...sanitizeCircleData(doc.data()),
  }));
}


// ─── Write Tool Handlers ───────────────────────────────────────────────────────

/**
 * Creates a new task for the user.
 * Args: { title: string, date?: string, time?: string, priority?: number, tags?: string[], description?: string }
 */
async function createTask(
  uid: string,
  args: Record<string, unknown>
): Promise<unknown> {
  const db = getDb();
  const title = args.title as string;

  if (!title) {
    throw new Error("title is required to create a task");
  }

  const now = Date.now();
  const taskData: Record<string, unknown> = {
    uid,
    title,
    createdDate: (args.date as string) || getTodayDateString(),
    priority: (args.priority as number) || 0,
    isCompleted: false,
    createdAt: now,
    updatedAt: now,
  };

  if (args.time) {
    taskData.deadlineTime = args.time;
  }
  if (args.tags) {
    taskData.tags = args.tags;
  }
  if (args.description) {
    taskData.description = args.description;
  }

  const docRef = await db.collection("tasks").add(taskData);
  return { id: docRef.id, ...taskData };
}

/**
 * Updates fields on a specific task document.
 * Args: { taskId: string, ...fieldsToUpdate }
 */
async function updateTask(
  uid: string,
  args: Record<string, unknown>
): Promise<unknown> {
  const db = getDb();
  const taskId = args.taskId as string;

  if (!taskId) {
    throw new Error("taskId is required to update a task");
  }

  // Verify task belongs to user
  const taskRef = db.collection("tasks").doc(taskId);
  const taskDoc = await taskRef.get();

  if (!taskDoc.exists) {
    throw new Error(`Task not found: ${taskId}`);
  }
  if (taskDoc.data()?.uid !== uid) {
    throw new Error("SCOPE_VIOLATION: Task does not belong to authenticated user");
  }

  // Build update payload (exclude taskId from update fields)
  const updateFields: Record<string, unknown> = {};
  const allowedFields = ["title", "date", "time", "priority", "tags", "description"];

  for (const field of allowedFields) {
    if (args[field] !== undefined) {
      // Map V2 field names to V1 Firestore field names
      if (field === "date") {
        updateFields.createdDate = args[field];
      } else if (field === "time") {
        updateFields.deadlineTime = args[field];
      } else {
        updateFields[field] = args[field];
      }
    }
  }

  updateFields.updatedAt = Date.now();

  await taskRef.update(updateFields);
  return { id: taskId, updated: Object.keys(updateFields) };
}

/**
 * Marks a task as complete.
 * Args: { taskId: string }
 * Sets `isCompleted=true` and `completedAt=Date.now()`.
 */
async function completeTask(
  uid: string,
  args: Record<string, unknown>
): Promise<unknown> {
  const db = getDb();
  const taskId = args.taskId as string;

  if (!taskId) {
    throw new Error("taskId is required to complete a task");
  }

  // Verify task belongs to user
  const taskRef = db.collection("tasks").doc(taskId);
  const taskDoc = await taskRef.get();

  if (!taskDoc.exists) {
    throw new Error(`Task not found: ${taskId}`);
  }
  if (taskDoc.data()?.uid !== uid) {
    throw new Error("SCOPE_VIOLATION: Task does not belong to authenticated user");
  }

  const now = Date.now();
  await taskRef.update({
    isCompleted: true,
    completedAt: now,
    updatedAt: now,
  });

  // Record productivity pattern for time estimation (Req 24.3)
  const taskData = taskDoc.data()!;
  recordTaskCompletion(uid, {
    taskId,
    title: taskData.title ?? "Untitled",
    tags: taskData.tags ?? [],
    createdAt: taskData.createdAt ?? now,
    completedAt: now,
  }).catch(() => {
    // Non-blocking: don't fail task completion if pattern storage fails
  });

  return { id: taskId, isCompleted: true, completedAt: now };
}

/**
 * Creates a new social circle.
 * Args: { name: string, description?: string }
 */
async function createCircle(
  uid: string,
  args: Record<string, unknown>
): Promise<unknown> {
  const db = getDb();
  const name = args.name as string;

  if (!name) {
    throw new Error("name is required to create a circle");
  }

  const now = Date.now();
  const circleData: Record<string, unknown> = {
    name,
    createdBy: uid,
    memberUidMap: { [uid]: true },
    createdAt: now,
    updatedAt: now,
  };

  if (args.description) {
    circleData.description = args.description;
  }

  const docRef = await db.collection("circles").add(circleData);
  return { id: docRef.id, ...circleData };
}

/**
 * Adds members to an existing circle.
 * Args: { circleId: string, memberUids: string[] }
 *
 * Validates that the authenticated user is already a member of the circle.
 */
async function addCircleMembers(
  uid: string,
  args: Record<string, unknown>
): Promise<unknown> {
  const db = getDb();
  const circleId = args.circleId as string;
  const memberUids = args.memberUids as string[];

  if (!circleId) {
    throw new Error("circleId is required");
  }
  if (!memberUids || !Array.isArray(memberUids) || memberUids.length === 0) {
    throw new Error("memberUids array is required and must not be empty");
  }

  // Verify circle exists and user is a member
  const circleRef = db.collection("circles").doc(circleId);
  const circleDoc = await circleRef.get();

  if (!circleDoc.exists) {
    throw new Error(`Circle not found: ${circleId}`);
  }

  const circleData = circleDoc.data()!;
  if (!circleData.memberUidMap?.[uid]) {
    throw new Error("SCOPE_VIOLATION: User is not a member of this circle");
  }

  // Add new members to memberUidMap
  const updatedMap = { ...circleData.memberUidMap };
  for (const memberUid of memberUids) {
    updatedMap[memberUid] = true;
  }

  await circleRef.update({
    memberUidMap: updatedMap,
    updatedAt: Date.now(),
  });

  return { circleId, addedMembers: memberUids, totalMembers: Object.keys(updatedMap).length };
}


// ─── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Returns today's date as YYYY-MM-DD string.
 */
function getTodayDateString(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Sanitizes task data for return — strips sensitive fields and internal metadata.
 */
function sanitizeTaskData(data: FirebaseFirestore.DocumentData): Record<string, unknown> {
  return {
    title: data.title || "",
    date: data.createdDate || "",
    time: data.deadlineTime || undefined,
    priority: data.priority || 0,
    tags: data.tags || [],
    description: data.description || undefined,
    completed: data.isCompleted || false,
    completedAt: data.completedAt || undefined,
  };
}

/**
 * Sanitizes friend data for return.
 */
function sanitizeFriendData(data: FirebaseFirestore.DocumentData): Record<string, unknown> {
  return {
    name: data.name || data.displayName || "",
    addedAt: data.addedAt || data.createdAt || undefined,
  };
}

/**
 * Sanitizes circle data for return — excludes internal fields.
 */
function sanitizeCircleData(data: FirebaseFirestore.DocumentData): Record<string, unknown> {
  return {
    name: data.name || "",
    description: data.description || undefined,
    memberCount: data.memberUidMap ? Object.keys(data.memberUidMap).length : 0,
    createdAt: data.createdAt || undefined,
  };
}
