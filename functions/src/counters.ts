import { onDocumentCreated, onDocumentDeleted } from "firebase-functions/v2/firestore";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

// Increment taskCount when a task is created
export const onTaskCreated = onDocumentCreated(
  {
    database: "preamble",
    document: "tasks/{taskId}"
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data();
    if (!data || !data.uid) return;

    const db = getFirestore();
    const userRef = db.collection("users").doc(data.uid);
    
    await userRef.update({
      taskCount: FieldValue.increment(1)
    }).catch(async (err) => {
      // If the document doesn't have taskCount yet or update fails, set it
      if (err.code === 5) { // NOT_FOUND
        // ignore if user doc does not exist
      } else {
        await userRef.set({ taskCount: 1 }, { merge: true }).catch(() => {});
      }
    });
  }
);

// Decrement taskCount when a task is deleted
export const onTaskDeleted = onDocumentDeleted(
  {
    database: "preamble",
    document: "tasks/{taskId}"
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data();
    if (!data || !data.uid) return;

    const db = getFirestore();
    const userRef = db.collection("users").doc(data.uid);

    await userRef.update({
      taskCount: FieldValue.increment(-1)
    }).catch(() => {});

    const preambleDb = getFirestore("preamble");
    const taskId = data.id || event.params.taskId.replace(/%2F/g, "/");
    await preambleDb.collection("deletedTasks").doc(event.params.taskId).set({
      uid: data.uid,
      taskId: taskId,
      deletedAt: Date.now()
    }).catch(() => {});
  }
);
