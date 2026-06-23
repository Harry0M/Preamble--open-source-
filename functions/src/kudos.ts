/**
 * onCollaborativeTaskReaction — Firestore onUpdate trigger (social-engagement).
 *
 * Triggered on writes to /collaborativeTasks/{taskId} in the "preamble" database.
 * It diffs before.reactions vs after.reactions: for an added or changed reaction
 * (never a removal — Req 6.3), it reads after.memberStates, selects every member
 * other than the reactor whose status == "completed" (Req 6.1, 6.4), and sends each
 * a data-only FCM push carrying the reactor's display name, the emoji, and the task
 * title (Req 6.2). Removals and the no-completed-members case send nothing. A
 * per-recipient FCM failure is logged and swallowed without affecting the stored
 * reaction (Req 6.5).
 */
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

const DEFAULT_NAME = "Someone";

function asStringMap(value: unknown): Record<string, any> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, any>)
    : {};
}

/** Reactor uids whose reaction was added or changed (NOT removed) between two maps. */
function addedOrChangedReactors(
  before: Record<string, any>,
  after: Record<string, any>
): string[] {
  const changed: string[] = [];
  for (const [reactorUid, afterEntry] of Object.entries(after)) {
    const afterReaction = asStringMap(afterEntry);
    const afterEmoji =
      typeof afterReaction.emoji === "string" ? afterReaction.emoji : null;
    if (!afterEmoji) continue; // not a valid reaction record

    const beforeReaction = asStringMap(before[reactorUid]);
    const beforeEmoji =
      typeof beforeReaction.emoji === "string" ? beforeReaction.emoji : null;

    // ADDED: not present before. CHANGED: present before with a different emoji.
    if (beforeEmoji === null || beforeEmoji !== afterEmoji) {
      changed.push(reactorUid);
    }
  }
  return changed;
}

export const onCollaborativeTaskReaction = onDocumentUpdated(
  {
    database: "preamble",
    document: "collaborativeTasks/{taskId}",
  },
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after) return;

    const beforeReactions = asStringMap(before.reactions);
    const afterReactions = asStringMap(after.reactions);

    // Detect added/changed reactions only; removals send nothing (Req 6.3).
    const reactors = addedOrChangedReactors(beforeReactions, afterReactions);
    if (reactors.length === 0) return;

    const memberStates = asStringMap(after.memberStates);
    const taskPayload = asStringMap(after.task);
    const taskTitle =
      (typeof taskPayload.title === "string" && taskPayload.title.trim()) ||
      "your shared task";

    const db = getFirestore("preamble");

    for (const reactorUid of reactors) {
      const reactorState = asStringMap(memberStates[reactorUid]);
      const reactorName =
        (typeof reactorState.name === "string" && reactorState.name.trim()) ||
        DEFAULT_NAME;
      const emoji =
        typeof asStringMap(afterReactions[reactorUid]).emoji === "string"
          ? (afterReactions[reactorUid].emoji as string)
          : "";

      // Recipients: every member other than the reactor whose status == "completed"
      // (Req 6.1, 6.4). No completed members → send nothing.
      const recipients = Object.entries(memberStates)
        .filter(([uid, state]) => {
          if (uid === reactorUid) return false;
          return asStringMap(state).status === "completed";
        })
        .map(([uid]) => uid);

      if (recipients.length === 0) continue;

      const body = `${reactorName} reacted ${emoji} to '${taskTitle}'`;

      for (const recipientUid of recipients) {
        try {
          const userSnap = await db.collection("users").doc(recipientUid).get();
          const fcmToken = userSnap.data()?.fcmToken;
          if (typeof fcmToken !== "string" || fcmToken.length === 0) continue;

          await getMessaging().send({
            token: fcmToken,
            data: {
              title: `Kudos from ${reactorName}`,
              body,
              deepLink: `preamble://task/${event.params.taskId}`,
              type: "kudos",
              channelType: "broadcast",
              taskId: event.params.taskId,
              emoji,
            },
            android: { priority: "high" },
          });
        } catch (err: any) {
          // Per-recipient failure is logged and swallowed (Req 6.5); the stored
          // reaction is unaffected and other recipients are still attempted.
          console.error("onCollaborativeTaskReaction: FCM delivery failed", {
            taskId: event.params.taskId,
            reactorUid,
            recipientUid,
            error: err?.message,
          });
        }
      }
    }
  }
);
