/**
 * Shared Circles — membership propagation + delete cascade trigger.
 *
 * The canonical Circle document at `/circles/{circleId}` is the source of truth for
 * Circle membership. Each `/circleTasks/{taskId}` document carries a DENORMALIZED
 * copy of the parent Circle's `memberUidMap` (Design Decision D2) so membership-gated
 * reads/writes on tasks need no parent `get()`. This trigger is the AUTHORITATIVE
 * keeper of that denormalized copy (Design Decision D3): clients only write the parent
 * Circle's membership and let the trigger fan the change out onto the Circle's tasks.
 *
 * `onCirclesMembershipChanged` (`onDocumentWritten` on `circles/{circleId}`):
 *   - Deletion (after == null): cascade-delete every `/circleTasks` where
 *     `circleId == id` (Req 7.2, 7.3).
 *   - Membership change (sorted keys of before.memberUidMap differ from
 *     after.memberUidMap): batch-set `memberUidMap = after.memberUidMap` on every
 *     `/circleTasks` where `circleId == id`, so a removed member loses task access
 *     within 5 s (Req 5.5).
 *   - Name-only / timestamp-only change: no-op.
 *
 * The trigger is idempotent (re-running it converges to the same state) and logs +
 * swallows per-batch failures so Cloud Functions retries are safe.
 */
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions/v2";
import { getFirestore, WriteBatch } from "firebase-admin/firestore";

/** Firestore hard limit on operations per batched write. */
const MAX_BATCH_OPS = 500;

/** Normalizes a `memberUidMap` field to a sorted list of keys for comparison. */
function sortedMemberKeys(value: unknown): string[] {
  if (!value || typeof value !== "object") return [];
  return Object.keys(value as Record<string, unknown>).sort();
}

/** True when the membership key sets differ (order-insensitive). */
function membershipChanged(before: unknown, after: unknown): boolean {
  const a = sortedMemberKeys(before);
  const b = sortedMemberKeys(after);
  if (a.length !== b.length) return true;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return true;
  }
  return false;
}

/**
 * Commits a list of per-document mutations in batches of at most MAX_BATCH_OPS.
 * Each batch failure is logged and swallowed so a partial failure does not abort the
 * whole fan-out and the trigger stays retry-safe.
 */
async function commitInChunks(
  db: FirebaseFirestore.Firestore,
  refs: FirebaseFirestore.DocumentReference[],
  apply: (batch: WriteBatch, ref: FirebaseFirestore.DocumentReference) => void,
  context: { circleId: string; op: "delete" | "propagate" }
): Promise<void> {
  for (let i = 0; i < refs.length; i += MAX_BATCH_OPS) {
    const chunk = refs.slice(i, i + MAX_BATCH_OPS);
    const batch = db.batch();
    for (const ref of chunk) {
      apply(batch, ref);
    }
    try {
      await batch.commit();
    } catch (err: any) {
      logger.error("onCirclesMembershipChanged: batch commit failed", {
        circleId: context.circleId,
        op: context.op,
        batchStart: i,
        batchSize: chunk.length,
        error: err?.message,
      });
    }
  }
}

/**
 * onCirclesMembershipChanged — `onDocumentWritten` on `circles/{circleId}`.
 *
 * Keeps the denormalized `memberUidMap` on every `/circleTasks` of a Circle in sync
 * with the parent Circle, and cascade-deletes a Circle's tasks when the Circle is
 * deleted. Runs with Admin SDK privileges (the only writer of a task's `memberUidMap`).
 */
export const onCirclesMembershipChanged = onDocumentWritten(
  {
    database: "preamble",
    document: "circles/{circleId}",
  },
  async (event) => {
    const circleId = event.params.circleId;
    if (!circleId) return;

    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    const db = getFirestore("preamble");
    const tasksQuery = db.collection("circleTasks").where("circleId", "==", circleId);

    // (1) Circle deleted → cascade-delete every task belonging to it (Req 7.2, 7.3).
    //     Idempotent: a re-run finds no remaining tasks and is a no-op.
    if (!after) {
      const snap = await tasksQuery.get();
      if (snap.empty) return;
      await commitInChunks(
        db,
        snap.docs.map((d) => d.ref),
        (batch, ref) => batch.delete(ref),
        { circleId, op: "delete" }
      );
      logger.info("onCirclesMembershipChanged: cascaded task deletes", {
        circleId,
        taskCount: snap.size,
      });
      return;
    }

    // (2) Created or non-membership update with no membership change → no-op.
    //     (A name-only / timestamp-only change leaves memberUidMap keys identical.)
    if (!membershipChanged(before?.memberUidMap, after.memberUidMap)) {
      return;
    }

    // (3) Membership changed → propagate the new memberUidMap onto every task (Req 5.5).
    //     Idempotent: re-running sets the same map. Setting the same value is harmless.
    const newMemberUidMap = after.memberUidMap ?? {};
    const snap = await tasksQuery.get();
    if (snap.empty) return;
    await commitInChunks(
      db,
      snap.docs.map((d) => d.ref),
      (batch, ref) => batch.set(ref, { memberUidMap: newMemberUidMap }, { merge: true }),
      { circleId, op: "propagate" }
    );
    logger.info("onCirclesMembershipChanged: propagated membership to tasks", {
      circleId,
      taskCount: snap.size,
      memberCount: Object.keys(newMemberUidMap).length,
    });
  }
);
