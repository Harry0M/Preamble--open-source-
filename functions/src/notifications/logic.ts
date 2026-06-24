/**
 * Pure notification logic for WS7 social/collaborative notifications.
 *
 * This module is intentionally dependency-free (NO firebase imports) so that
 * the diff/recipient/payload logic can be exercised by property-based tests
 * without a Firestore emulator. All side-effecting concerns (token lookup,
 * FCM send, idempotency ledger) live in `social-notifications.ts`.
 */

/** Member status as stored in `memberStates[uid].status`. */
export type Status =
  | "pending"
  | "accepted"
  | "completed"
  | "declined"
  | "left"
  | "removed";

/** Terminal statuses whose members are dropped from member-facing recipients. */
const TERMINAL_STATUSES: ReadonlySet<Status> = new Set<Status>([
  "declined",
  "left",
  "removed",
]);

export interface MemberState {
  status?: Status;
  name?: string;
  completedTimestamp?: number;
}

export interface TaskDoc {
  adminUid?: string;
  memberUids?: string[];
  assigneeUids?: string[];
  memberStates?: Record<string, MemberState>;
  task?: Record<string, unknown>; // payload incl. title
  createdAt?: number;
  updatedAt?: number;
}

/** Member-relevant task fields whose change constitutes a Req-4 "change". */
export const MEMBER_RELEVANT_TASK_KEYS = [
  "title",
  "description",
  "dueDate",
  "deadlineTime",
  "date",
  "priority",
  "subtasks",
  "tags",
  "isEvent",
  "eventIcon",
  "eventColor",
] as const;

export interface TaskUpdateClassification {
  contentChanged: boolean; // Req 4.1 / 4.5
  completedMemberUids: string[]; // members who crossed into "completed" this write (Req 5.1)
}

/**
 * Pure before/after diff — the heart of Req 8.1/8.2. No I/O.
 *
 * - `contentChanged` is true iff the JSON of any MEMBER_RELEVANT_TASK_KEYS
 *   field differs between `before.task` and `after.task`. Member-state-only
 *   writes leave `task` untouched ⇒ false (Req 4.5).
 * - `completedMemberUids` lists every uid whose status was NOT "completed" in
 *   `before` and IS "completed" in `after` (a genuine crossing). An already-
 *   completed status yields nothing (Req 8.2, 8.3).
 */
export function classifyTaskUpdate(
  before: TaskDoc,
  after: TaskDoc
): TaskUpdateClassification {
  const beforeTask = before.task ?? {};
  const afterTask = after.task ?? {};

  let contentChanged = false;
  for (const key of MEMBER_RELEVANT_TASK_KEYS) {
    if (JSON.stringify(beforeTask[key]) !== JSON.stringify(afterTask[key])) {
      contentChanged = true;
      break;
    }
  }

  const beforeStates = before.memberStates ?? {};
  const afterStates = after.memberStates ?? {};
  const completedMemberUids: string[] = [];
  for (const uid of Object.keys(afterStates)) {
    const wasCompleted = beforeStates[uid]?.status === "completed";
    const isCompleted = afterStates[uid]?.status === "completed";
    if (!wasCompleted && isCompleted) {
      completedMemberUids.push(uid);
    }
  }

  return { contentChanged, completedMemberUids };
}

export type EventKind =
  | { kind: "assigned" }
  | { kind: "changed"; actorUid: string | null }
  | { kind: "completed"; completingUid: string };

/** True when the member's current status is terminal (declined/left/removed). */
function isTerminal(doc: TaskDoc, uid: string): boolean {
  const status = doc.memberStates?.[uid]?.status;
  return status !== undefined && TERMINAL_STATUSES.has(status);
}

/** De-duplicate while preserving order. */
function dedupe(uids: string[]): string[] {
  return Array.from(new Set(uids));
}

/**
 * Pure recipient selection — the heart of Req 7 (anti-self-notify + targeting).
 *
 * - `assigned` → `doc.assigneeUids` (members − admin by construction), with
 *   terminal-status members dropped.
 * - `changed` → `memberUids − adminUid − {actorUid}`, with terminal-status
 *   members dropped.
 * - `completed` → `{ adminUid }` minus `completingUid` (empty when the admin
 *   is the completer, Req 5.2 / 7.3).
 */
export function selectRecipients(doc: TaskDoc, event: EventKind): string[] {
  switch (event.kind) {
    case "assigned": {
      const assignees = doc.assigneeUids ?? [];
      return dedupe(
        assignees.filter(
          (uid) => uid !== doc.adminUid && !isTerminal(doc, uid)
        )
      );
    }
    case "changed": {
      const members = doc.memberUids ?? [];
      return dedupe(
        members.filter(
          (uid) =>
            uid !== doc.adminUid &&
            uid !== event.actorUid &&
            !isTerminal(doc, uid)
        )
      );
    }
    case "completed": {
      const adminUid = doc.adminUid;
      if (!adminUid || adminUid === event.completingUid) {
        return [];
      }
      return [adminUid];
    }
  }
}

export type PayloadType =
  | "invite_accepted"
  | "task_assigned"
  | "task_changed"
  | "task_completed";

export interface NotificationPayload {
  title: string;
  body: string;
  deepLink: string;
  type: PayloadType;
  channelType: string;
  [k: string]: string; // FCM data values are all strings
}

/**
 * Pure payload builder (plain copy, no reward language — Req 1.6/2.4).
 *
 * deepLink:
 *  - `invite_accepted` → the Social_Hub link `preamble://social`
 *  - task types        → `preamble://task/{taskId}`
 * channelType:
 *  - `invite_accepted` → `social_invites`
 *  - task types        → `social_collab`
 * All FCM data values are strings.
 */
export function buildPayload(input: {
  type: PayloadType;
  taskId?: string;
  title?: string;
  actorName?: string;
}): NotificationPayload {
  const { type, taskId, title, actorName } = input;
  const taskTitle = title ?? "your shared task";
  const who = actorName ?? "Someone";

  switch (type) {
    case "invite_accepted":
      return {
        title: "Invite accepted",
        body: `${who} accepted your invite`,
        deepLink: "preamble://social",
        type,
        channelType: "social_invites",
      };
    case "task_assigned":
      return {
        title: "New shared task",
        body: `You were added to '${taskTitle}'`,
        deepLink: `preamble://task/${taskId ?? ""}`,
        type,
        channelType: "social_collab",
      };
    case "task_changed":
      return {
        title: "Shared task updated",
        body: `'${taskTitle}' was updated`,
        deepLink: `preamble://task/${taskId ?? ""}`,
        type,
        channelType: "social_collab",
      };
    case "task_completed":
      return {
        title: "Shared task progress",
        body: `${who} completed their part of '${taskTitle}'`,
        deepLink: `preamble://task/${taskId ?? ""}`,
        type,
        channelType: "social_collab",
      };
  }
}
