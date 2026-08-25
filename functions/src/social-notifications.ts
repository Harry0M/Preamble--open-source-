/**
 * social-notifications.ts — WS7 social/collaborative notification triggers.
 *
 * This module hosts the Admin-SDK Firestore triggers that turn social and
 * collaborative events into data-only FCM pushes against the named "preamble"
 * database. The pure diff/recipient/payload logic lives in
 * `./notifications/logic.ts`; this file owns the side-effecting concerns:
 * the idempotency ledger (`claimEvent`) and the shared per-recipient send
 * helper (`sendToRecipients`).
 *
 * Discipline mirrors `kudos.ts`: read `users/{uid}.fcmToken`, skip when absent,
 * `getMessaging().send(...)` wrapped in a per-recipient try/catch that logs and
 * swallows — a delivery failure never blocks the data operation or other
 * recipients, and handlers never re-throw.
 *
 * NOTE: the three trigger exports (onInviteAccepted, onCollaborativeTaskCreated,
 * onCollaborativeTaskUpdated) are added in tasks 3.2/3.3/3.4.
 */
import {
  onDocumentCreated,
  onDocumentUpdated,
} from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import {
  buildPayload,
  classifyTaskUpdate,
  NotificationPayload,
  selectRecipients,
  TaskDoc,
} from "./notifications/logic";

/**
 * Defensive guard mirroring kudos.ts: coerce an unknown value to a plain string
 * map, returning `{}` for null/non-object/array inputs. Used when reading nested
 * maps (`memberStates`, `task`) off a Firestore document whose shape we do not
 * fully trust.
 */
function asStringMap(value: unknown): Record<string, any> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, any>)
    : {};
}

/** Coerce an unknown value to a string[] (dropping non-string entries). */
function asStringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((v): v is string => typeof v === "string")
    : [];
}

/**
 * Idempotency ledger — claim a CloudEvent id exactly once.
 *
 * `event.id` is stable across Cloud Functions retries of the SAME delivery, so
 * `create()` succeeds the first time the id is seen and fails with
 * ALREADY_EXISTS on retries (Req 8.3/8.4). A genuine re-completion is a
 * different write with a different `event.id`, so it is correctly allowed to
 * notify again.
 *
 * Returns:
 *  - `true`  on the first create (proceed with notification),
 *  - `false` on ALREADY_EXISTS (a retry — skip),
 *  - `true`  on ANY OTHER failure: we treat the event as "not claimed" and
 *    proceed; the worst case is a rare duplicate, never a blocked operation.
 */
async function claimEvent(
  db: FirebaseFirestore.Firestore,
  eventId: string
): Promise<boolean> {
  const ref = db.collection("notificationEvents").doc(eventId);
  try {
    await ref.create({ at: Date.now() }); // create() fails if it already exists
    return true;
  } catch (err: any) {
    // ALREADY_EXISTS → this delivery was already processed; skip.
    if (err?.code === 6 || err?.code === "already-exists") {
      return false;
    }
    // Any other (e.g. transient) failure: treat as not claimed and proceed.
    console.error("claimEvent: unexpected ledger failure, proceeding", {
      eventId,
      error: err?.message,
    });
    return true;
  }
}

/**
 * Shared per-recipient send helper.
 *
 * For each recipient uid: read `users/{uid}.fcmToken`, skip silently when it is
 * absent/empty, then send the data-only payload with high Android priority.
 * Each send is wrapped in its own try/catch that logs `{ ...ctx, recipientUid,
 * error }` and continues to the next recipient. Never re-throws (Req 9.1/9.2/9.3).
 */
async function sendToRecipients(
  db: FirebaseFirestore.Firestore,
  recipientUids: string[],
  payload: NotificationPayload,
  ctx: Record<string, unknown>
): Promise<void> {
  if (recipientUids.length === 0) return;
  
  // Batch-fetch all FCM tokens in one round-trip (replaces N individual reads)
  const refs = recipientUids.map(uid => db.collection("users").doc(uid));
  const snaps = await db.getAll(...refs);
  const tokenMap = new Map<string, string>();
  for (const snap of snaps) {
    const token = snap.data()?.fcmToken;
    if (typeof token === "string" && token.length > 0) {
      tokenMap.set(snap.id, token);
    }
  }
  
  for (const recipientUid of recipientUids) {
    try {
      const fcmToken = tokenMap.get(recipientUid);
      if (!fcmToken) continue;
      await getMessaging().send({
        token: fcmToken,
        data: payload,
        android: { priority: "high" },
      });
    } catch (err: any) {
      console.error("sendToRecipients: FCM delivery failed", {
        ...ctx,
        recipientUid,
        error: err?.message,
      });
    }
  }
}

// ---------------------------------------------------------------------------
// onInviteAccepted — Inviter notification on invite acceptance (D5)
// ---------------------------------------------------------------------------

/**
 * onInviteAccepted — `onDocumentCreated` trigger on a friend-doc creation.
 *
 * `acceptInvite` writes BOTH `users/{accepterUid}/friends/{senderUid}` and
 * `users/{senderUid}/friends/{accepterUid}` in one batch and LEAVES the sender's
 * `users/{senderUid}/outgoingInvites/{accepterUid}` mirror in place, so this
 * trigger fires twice — once per friend doc. We must notify ONLY the Inviter and
 * NEVER the Actor/accepter (Req 7.1).
 *
 * Disambiguation (Decision D5): the Inviter is the `ownerUid` for whom the mirror
 * `users/{ownerUid}/outgoingInvites/{friendUid}` exists. The reciprocal firing
 * (the accepter's side) finds no such mirror and returns without notifying.
 *
 * The friend doc created under the Inviter carries the ACCEPTER's display `name`,
 * which we use for the notification copy (falling back to a `users/{friendUid}`
 * lookup, then "Someone").
 *
 * Post-commit discipline (Req 9.4): all failures are logged-and-swallowed; the
 * handler never re-throws.
 */
export const onInviteAccepted = onDocumentCreated(
  {
    database: "preamble",
    document: "users/{ownerUid}/friends/{friendUid}",
  },
  async (event) => {
    try {
      const ownerUid = event.params?.ownerUid;
      const friendUid = event.params?.friendUid;
      // Defensive: missing data/params ⇒ nothing to do (Req 9.4).
      if (!event.data || !ownerUid || !friendUid) return;

      const db = getFirestore("preamble");

      // Disambiguate Inviter vs Actor (Decision D5). The Inviter is the owner who
      // holds the outgoingInvites mirror for this friend. The accepter's side has
      // no such mirror, so it returns and is never notified (Req 7.1).
      const mirrorSnap = await db
        .collection("users")
        .doc(ownerUid)
        .collection("outgoingInvites")
        .doc(friendUid)
        .get();
      if (!mirrorSnap.exists) return;

      // Idempotency: skip retries of the same CloudEvent delivery (Req 8.3/8.4).
      const claimed = await claimEvent(db, event.id);
      if (!claimed) return;

      // Resolve the accepter's display name: prefer the just-created friend doc's
      // `name`, then a users/{friendUid} lookup, else "Someone".
      let accepterName: string | undefined;
      const friendData = event.data.data();
      if (typeof friendData?.name === "string" && friendData.name.length > 0) {
        accepterName = friendData.name;
      } else {
        try {
          const accepterSnap = await db.collection("users").doc(friendUid).get();
          const accepterData = accepterSnap.data();
          const displayName = accepterData?.displayName ?? accepterData?.name;
          if (typeof displayName === "string" && displayName.length > 0) {
            accepterName = displayName;
          }
        } catch (err: any) {
          console.error("onInviteAccepted: accepter name lookup failed", {
            ownerUid,
            friendUid,
            error: err?.message,
          });
        }
      }

      const payload = buildPayload({
        type: "invite_accepted",
        actorName: accepterName ?? "Someone",
      });

      // Notify only the Inviter. sendToRecipients skips a missing token (Req 1.5).
      await sendToRecipients(db, [ownerUid], payload, {
        function: "onInviteAccepted",
        ownerUid,
        friendUid,
      });
    } catch (err: any) {
      // Post-commit trigger: never re-throw (Req 9.4).
      console.error("onInviteAccepted: unexpected failure", {
        eventId: event.id,
        error: err?.message,
      });
    }
  }
);

// ---------------------------------------------------------------------------
// onCollaborativeTaskCreated — Assignment notification on task creation (D1)
// ---------------------------------------------------------------------------

/**
 * onCollaborativeTaskCreated — `onDocumentCreated` trigger on a canonical
 * collaborative-task doc creation (`/collaborativeTasks/{taskId}`).
 *
 * Decision D1: a brand-new canonical doc *is* the assignment. `assigneeUids` are
 * precisely the new assignees and exclude the admin by construction
 * (`normalizeAssignees` drops it), so this is cleaner than detecting "new
 * memberUids" inside an update trigger and also catches the late-create path
 * from `finalizeCollaborativeTaskDetails`.
 *
 * Handler: claim the CloudEvent id (Req 8.3/8.4), select assignee recipients via
 * the pure `selectRecipients(doc, { kind: "assigned" })` (assignees − admin −
 * terminal-status members), build the `task_assigned` payload, and fan out via
 * `sendToRecipients` — which skips tokenless assignees and continues to the rest
 * (Req 3.6). When there is no eligible recipient, nothing is sent (Req 7.3).
 *
 * Post-commit discipline (Req 9.4): all failures are logged-and-swallowed; the
 * handler never re-throws.
 */
export const onCollaborativeTaskCreated = onDocumentCreated(
  {
    database: "preamble",
    document: "collaborativeTasks/{taskId}",
  },
  async (event) => {
    try {
      // Defensive: missing snapshot ⇒ nothing to do (Req 9.4).
      if (!event.data) return;

      const data = event.data.data() ?? {};

      // Read the created doc into a TaskDoc-shaped object using the same
      // defensive guards as kudos.ts for nested maps/arrays.
      const doc: TaskDoc = {
        adminUid:
          typeof data.adminUid === "string" ? data.adminUid : undefined,
        memberUids: asStringArray(data.memberUids),
        assigneeUids: asStringArray(data.assigneeUids),
        memberStates: asStringMap(data.memberStates),
        task: asStringMap(data.task),
      };

      const db = getFirestore("preamble");

      // Idempotency: skip retries of the same CloudEvent delivery (Req 8.3/8.4).
      const claimed = await claimEvent(db, event.id);
      if (!claimed) return;

      // Assignees minus admin minus terminal-status members (pure, Req 3.2/7.2).
      const recipients = selectRecipients(doc, { kind: "assigned" });
      // No eligible recipient ⇒ send nothing (Req 7.3).
      if (recipients.length === 0) return;

      const taskId = event.params.taskId;
      const title =
        typeof doc.task?.title === "string" ? (doc.task.title as string) : undefined;

      const payload = buildPayload({ type: "task_assigned", taskId, title });

      // sendToRecipients skips tokenless assignees and continues (Req 3.6).
      await sendToRecipients(db, recipients, payload, {
        function: "onCollaborativeTaskCreated",
        taskId,
      });
    } catch (err: any) {
      // Post-commit trigger: never re-throw (Req 9.4).
      console.error("onCollaborativeTaskCreated: unexpected failure", {
        eventId: event.id,
        error: err?.message,
      });
    }
  }
);

// ---------------------------------------------------------------------------
// onCollaborativeTaskUpdated — Change + Completion notifications (D3, D7)
// ---------------------------------------------------------------------------

/**
 * Grace window (ms) after `createdAt` during which a content change is treated
 * as part of assignment finalization rather than a standalone edit (Decision
 * D7). The AI-refinement `finalizeCollaborativeTaskDetails` write can alter
 * `task` content shortly after creation; suppressing a `changed` push inside
 * this window avoids an assignment-immediately-followed-by-change pair.
 */
const GRACE_MS = 60_000;

/**
 * onCollaborativeTaskUpdated — `onDocumentUpdated` trigger on a canonical
 * collaborative-task doc (`/collaborativeTasks/{taskId}`), mirroring the
 * before/after read style of `kudos.ts` on the same document (Decision D3).
 *
 * A single before/after read drives both classifications via the pure
 * `classifyTaskUpdate`:
 *  - **Change (Req 4):** when member-relevant `task` content differs, notify
 *    `memberUids − adminUid − {actorUid}` (non-terminal). The D7 grace-window
 *    guard suppresses the push when the write lands within `GRACE_MS` of
 *    `createdAt` (early AI-finalize content writes are part of assignment).
 *    Member-state-only writes leave `task` untouched ⇒ `contentChanged` is
 *    false, so no change push fires (Req 4.5).
 *  - **Completion (Req 5):** for each member who crossed into `completed` this
 *    write, notify the admin only (never the completer; empty when the admin
 *    completed their own slice — Req 5.2/7.3).
 *
 * We classify BEFORE claiming the event so irrelevant writes (e.g. reaction-
 * only or marker writes) do not burn an idempotency-ledger entry.
 *
 * Post-commit discipline (Req 9.4): all failures are logged-and-swallowed; the
 * handler never re-throws.
 */
export const onCollaborativeTaskUpdated = onDocumentUpdated(
  {
    database: "preamble",
    document: "collaborativeTasks/{taskId}",
  },
  async (event) => {
    try {
      // Defensive: missing before/after snapshot ⇒ nothing to do (Req 9.4).
      if (!event.data?.before || !event.data?.after) return;

      const before = event.data.before.data() ?? {};
      const after = event.data.after.data() ?? {};

      // Read both snapshots into TaskDoc-shaped objects using the same
      // defensive guards as kudos.ts for nested maps/arrays.
      const beforeDoc: TaskDoc = {
        adminUid:
          typeof before.adminUid === "string" ? before.adminUid : undefined,
        memberUids: asStringArray(before.memberUids),
        assigneeUids: asStringArray(before.assigneeUids),
        memberStates: asStringMap(before.memberStates),
        task: asStringMap(before.task),
        createdAt:
          typeof before.createdAt === "number" ? before.createdAt : undefined,
      };
      const afterDoc: TaskDoc = {
        adminUid:
          typeof after.adminUid === "string" ? after.adminUid : undefined,
        memberUids: asStringArray(after.memberUids),
        assigneeUids: asStringArray(after.assigneeUids),
        memberStates: asStringMap(after.memberStates),
        task: asStringMap(after.task),
        createdAt:
          typeof after.createdAt === "number" ? after.createdAt : undefined,
      };

      const db = getFirestore("preamble");

      // Pure diff: what (if anything) changed (Req 4.1/4.5, 8.1/8.2).
      const { contentChanged, completedMemberUids } = classifyTaskUpdate(
        beforeDoc,
        afterDoc
      );

      // Nothing relevant happened ⇒ return BEFORE claiming the event so we do
      // not burn a ledger entry on irrelevant writes (reactions, markers, etc.).
      if (!contentChanged && completedMemberUids.length === 0) return;

      // Idempotency: skip retries of the same CloudEvent delivery (Req 8.3/8.4).
      const claimed = await claimEvent(db, event.id);
      if (!claimed) return;

      const taskId = event.params.taskId;
      const title =
        typeof afterDoc.task?.title === "string"
          ? (afterDoc.task.title as string)
          : undefined;

      // --- CHANGE push (Req 4) ------------------------------------------------
      if (contentChanged) {
        // D7 grace-window guard: an early content write (e.g. AI-finalize) within
        // GRACE_MS of createdAt is part of assignment finalization — suppress it.
        const withinGrace =
          Date.now() - (afterDoc.createdAt ?? 0) < GRACE_MS;
        if (!withinGrace) {
          const recipients = selectRecipients(afterDoc, {
            kind: "changed",
            actorUid: afterDoc.adminUid ?? null,
          });
          if (recipients.length > 0) {
            const payload = buildPayload({ type: "task_changed", taskId, title });
            await sendToRecipients(db, recipients, payload, {
              function: "onCollaborativeTaskUpdated",
              event: "changed",
              taskId,
            });
          }
        }
      }

      // --- COMPLETION push (Req 5) -------------------------------------------
      for (const uid of completedMemberUids) {
        // Admin only; never the completer; empty if admin completed own slice.
        const recipients = selectRecipients(afterDoc, {
          kind: "completed",
          completingUid: uid,
        });
        if (recipients.length === 0) continue;

        const completerName =
          typeof afterDoc.memberStates?.[uid]?.name === "string" &&
          (afterDoc.memberStates[uid].name as string).length > 0
            ? (afterDoc.memberStates[uid].name as string)
            : "Someone";

        const payload = buildPayload({
          type: "task_completed",
          taskId,
          title,
          actorName: completerName,
        });
        await sendToRecipients(db, recipients, payload, {
          function: "onCollaborativeTaskUpdated",
          event: "completed",
          taskId,
          completingUid: uid,
        });
      }
    } catch (err: any) {
      // Post-commit trigger: never re-throw (Req 9.4).
      console.error("onCollaborativeTaskUpdated: unexpected failure", {
        eventId: event.id,
        error: err?.message,
      });
    }
  }
);
