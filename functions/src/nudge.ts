/**
 * sendNudge — callable Cloud Function (social-engagement).
 *
 * Authoritative enforcement for nudges, since a client cannot push directly to
 * another specific user. Using the Admin SDK it:
 *   1. authenticates the caller (deny when absent),
 *   2. reads /collaborativeTasks/{taskId} and verifies the caller is a member
 *      (Req 11.1) and is not the target (Req 11.3),
 *   3. verifies memberStates[targetUid].status == "pending" (Req 11.2/11.4),
 *   4. reads /nudges/{taskId}_{senderUid}_{targetUid} and enforces the 60-minute
 *      rolling window server-side (Req 12),
 *   5. on success writes lastSentAt = now to that nudge doc and sends a data-only
 *      FCM message to users/{targetUid}.fcmToken rendered as
 *      "<sender> nudged you about '<task>'" (Req 10.2, 10.3),
 *   6. returns { ok: true } or throws an HttpsError('failed-precondition', reason).
 *
 * The /nudges collection is written only through this Admin-SDK path; its
 * Security_Rules deny all client access.
 */
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

/** Nudge_Rate_Limit window: 60 minutes (Req 12). */
const NUDGE_WINDOW_MILLIS = 60 * 60 * 1000;

const DEFAULT_NAME = "Someone";

function asStringMap(value: unknown): Record<string, any> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, any>)
    : {};
}

function cleanId(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

export const sendNudge = onCall(async (request) => {
  // (1) Authenticate the caller — deny when absent.
  const senderUid = request.auth?.uid;
  if (!senderUid) {
    throw new HttpsError("unauthenticated", "You must be signed in to send a nudge.");
  }

  const taskId = cleanId(request.data?.taskId);
  const targetUid = cleanId(request.data?.targetUid);
  if (!taskId || !targetUid) {
    throw new HttpsError("invalid-argument", "taskId and targetUid are required.");
  }

  // (3 pre-check) A member cannot nudge themselves (Req 11.3).
  if (targetUid === senderUid) {
    throw new HttpsError("failed-precondition", "You cannot nudge yourself.");
  }

  const db = getFirestore("preamble");

  // (2) Read the canonical collaborative task document.
  const taskRef = db.collection("collaborativeTasks").doc(taskId);
  const taskSnap = await taskRef.get();
  if (!taskSnap.exists) {
    throw new HttpsError("failed-precondition", "This shared task no longer exists.");
  }
  const taskData = taskSnap.data() || {};

  // (2) Verify the caller is a member of the task (Req 11.1).
  const memberUids = Array.isArray(taskData.memberUids)
    ? (taskData.memberUids as unknown[]).filter((u): u is string => typeof u === "string")
    : [];
  if (!memberUids.includes(senderUid)) {
    throw new HttpsError("failed-precondition", "Only members of this task can send nudges.");
  }

  // (3) Verify the target's member status is "pending" (Req 11.2/11.4). Because the
  // admin is itself a member, the admin path of Req 11.4 is covered by the membership
  // check above plus this pending check.
  const memberStates = asStringMap(taskData.memberStates);
  const targetState = asStringMap(memberStates[targetUid]);
  const targetStatus = typeof targetState.status === "string" ? targetState.status : null;
  if (targetStatus !== "pending") {
    throw new HttpsError("failed-precondition", "Only pending members can be nudged.");
  }

  // (4) Enforce the 60-minute rolling rate limit per (sender, target, task) (Req 12).
  const now = Date.now();
  const nudgeId = `${taskId}_${senderUid}_${targetUid}`;
  const nudgeRef = db.collection("nudges").doc(nudgeId);
  const nudgeSnap = await nudgeRef.get();
  const lastSentAt = nudgeSnap.exists
    ? Number(nudgeSnap.data()?.lastSentAt) || 0
    : 0;
  if (lastSentAt > 0 && now - lastSentAt < NUDGE_WINDOW_MILLIS) {
    throw new HttpsError(
      "failed-precondition",
      "You nudged this member recently. You can nudge them again later."
    );
  }

  // Resolve the sender's display name (prefer the member entry recorded on the task).
  const senderState = asStringMap(memberStates[senderUid]);
  let senderName =
    (typeof senderState.name === "string" && senderState.name.trim()) || "";
  if (!senderName) {
    const senderUserSnap = await db.collection("users").doc(senderUid).get();
    const senderUser = senderUserSnap.data() || {};
    senderName =
      (typeof senderUser.displayName === "string" && senderUser.displayName.trim()) ||
      (typeof senderUser.name === "string" && senderUser.name.trim()) ||
      DEFAULT_NAME;
  }

  // Resolve the task title from the shared task payload.
  const taskPayload = asStringMap(taskData.task);
  const taskTitle =
    (typeof taskPayload.title === "string" && taskPayload.title.trim()) || "your shared task";

  // (5) Record the send time before pushing so the rate limit is durable even if the
  // push fails. The reaction/nudge write is the source of truth for rate limiting.
  await nudgeRef.set(
    {
      taskId,
      senderUid,
      targetUid,
      lastSentAt: now,
    },
    { merge: true }
  );

  // (5) Send the data-only FCM message to the target's stored token, rendered by
  // PreambleFcmService (title/body/deepLink/type conventions).
  const targetUserSnap = await db.collection("users").doc(targetUid).get();
  const fcmToken = targetUserSnap.data()?.fcmToken;
  if (typeof fcmToken === "string" && fcmToken.length > 0) {
    const body = `${senderName} nudged you about '${taskTitle}'`;
    try {
      await getMessaging().send({
        token: fcmToken,
        data: {
          title: `Nudge from ${senderName}`,
          body,
          deepLink: `preamble://task/${taskId}`,
          type: "nudge",
          channelType: "social_kudos",
          taskId,
        },
        android: { priority: "high" },
      });
    } catch (err: any) {
      // Delivery failure is logged but does not fail the nudge — the lastSentAt
      // write already committed (Req 10.x: the operation succeeded server-side).
      console.error("sendNudge: FCM delivery failed", {
        taskId,
        senderUid,
        targetUid,
        error: err?.message,
      });
    }
  }

  return { ok: true };
});
