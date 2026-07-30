/**
 * ConversationManager — Thread-safe conversation processing with sequential message ordering.
 *
 * Handles conversation state management using Firestore transactions to enforce
 * sequential processing within a single conversation. Messages arriving while
 * a prior message is being processed are queued via a Firestore queue subcollection
 * (simulating Cloud Tasks behavior).
 *
 * Data model:
 * - Thread: `v2_conversations/{uid}/threads/{conversationId}`
 * - Messages: `v2_conversations/{uid}/threads/{conversationId}/messages/{messageId}`
 * - Queue: `v2_conversations/{uid}/threads/{conversationId}/queue/{queueId}`
 *
 * Uses the named Firestore database "preamble".
 *
 * Requirements: 22.1, 22.2, 22.3, 9.1, 9.2
 */

import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { ConversationMessage } from "../models/types";

// ─── Firestore References ──────────────────────────────────────────────────────

/**
 * Returns the Firestore instance for the "preamble" named database.
 */
function getDb() {
  return getFirestore("preamble");
}

/**
 * Returns the document reference for a conversation thread.
 */
function getThreadDoc(uid: string, conversationId: string) {
  return getDb()
    .collection("v2_conversations")
    .doc(uid)
    .collection("threads")
    .doc(conversationId);
}

/**
 * Returns the messages subcollection reference for a conversation.
 */
function getMessagesCollection(uid: string, conversationId: string) {
  return getThreadDoc(uid, conversationId).collection("messages");
}

/**
 * Returns the queue subcollection reference for a conversation.
 * Used to queue messages when the conversation is currently being processed.
 */
function getQueueCollection(uid: string, conversationId: string) {
  return getThreadDoc(uid, conversationId).collection("queue");
}

// ─── Types ─────────────────────────────────────────────────────────────────────

/**
 * Thread document structure stored in Firestore.
 */
interface ThreadDocument {
  createdAt: number;
  updatedAt: number;
  messageCount: number;
  summarizedUpTo: number;
  summaryText: string;
  isProcessing: boolean;
  lastBriefingDate: string | null;
}

/**
 * Message document structure stored in Firestore.
 */
interface MessageDocument {
  role: "user" | "assistant" | "system";
  content: string;
  toolCalls: ConversationMessage["toolCalls"] | null;
  toolResults: ConversationMessage["toolResults"] | null;
  renderBlocks: unknown[] | null;
  inputTokens: number;
  outputTokens: number;
  model: string | null;
  traceId: string | null;
  createdAt: number;
}

/**
 * Queued message structure.
 */
interface QueuedMessage {
  message: string;
  queuedAt: number;
  status: "pending" | "processing" | "completed";
}

// ─── Public API ────────────────────────────────────────────────────────────────

/**
 * Processes a message within a conversation, enforcing sequential processing.
 *
 * Uses a Firestore transaction to atomically check and set `isProcessing=true`
 * on the thread document. If the thread is already processing, the message is
 * queued in a subcollection for later processing.
 *
 * - Req 22.1: Sequential processing within a single conversation
 * - Req 22.2: Multiple concurrent conversations from the same user without interference
 * - Req 22.3: Queue messages arriving during active processing
 *
 * @param uid - The authenticated user's UID
 * @param conversationId - The conversation thread identifier
 * @param message - The user's message content
 */
export async function processMessage(
  uid: string,
  conversationId: string,
  message: string
): Promise<void> {
  const db = getDb();
  const threadRef = getThreadDoc(uid, conversationId);

  const acquired = await db.runTransaction(async (transaction) => {
    const threadSnap = await transaction.get(threadRef);

    if (!threadSnap.exists) {
      // First message in this conversation — create thread and acquire lock
      const newThread: ThreadDocument = {
        createdAt: Date.now(),
        updatedAt: Date.now(),
        messageCount: 0,
        summarizedUpTo: 0,
        summaryText: "",
        isProcessing: true,
        lastBriefingDate: null,
      };
      transaction.set(threadRef, newThread);
      return true;
    }

    const threadData = threadSnap.data() as ThreadDocument;

    if (threadData.isProcessing) {
      // Thread is currently processing — cannot acquire lock
      return false;
    }

    // Acquire the processing lock
    transaction.update(threadRef, {
      isProcessing: true,
      updatedAt: Date.now(),
    });
    return true;
  });

  if (!acquired) {
    // Queue the message for later processing (Req 22.3)
    await queueMessage(uid, conversationId, message);
    return;
  }

  try {
    // Store the user message in the messages subcollection
    await storeMessage(uid, conversationId, {
      role: "user",
      content: message,
      toolCalls: null,
      toolResults: null,
      renderBlocks: null,
      inputTokens: 0,
      outputTokens: 0,
      model: null,
      traceId: null,
      createdAt: Date.now(),
    });

    // Increment message count and update timestamp
    await threadRef.update({
      messageCount: FieldValue.increment(1),
      updatedAt: Date.now(),
    });
  } finally {
    // Release the processing lock
    await threadRef.update({
      isProcessing: false,
      updatedAt: Date.now(),
    });

    // Process any queued messages
    await processQueuedMessages(uid, conversationId);
  }
}

/**
 * Retrieves conversation history with an optional limit.
 *
 * If the thread has a summary (summarizedUpTo > 0), the summary is prepended
 * as a "system" message before the remaining messages.
 *
 * - Req 9.1: Full conversation history up to context window limit
 * - Req 9.2: Summarization strategy for older messages
 *
 * @param uid - The authenticated user's UID
 * @param conversationId - The conversation thread identifier
 * @param limit - Maximum number of messages to return (default 50)
 * @returns Array of ConversationMessage ordered by createdAt ascending
 */
export async function getHistory(
  uid: string,
  conversationId: string,
  limit: number = 50
): Promise<ConversationMessage[]> {
  const threadRef = getThreadDoc(uid, conversationId);
  const threadSnap = await threadRef.get();

  const messages: ConversationMessage[] = [];

  // If thread has a summary, prepend it as a system message
  if (threadSnap.exists) {
    const threadData = threadSnap.data() as ThreadDocument;
    if (threadData.summarizedUpTo > 0 && threadData.summaryText) {
      messages.push({
        role: "system",
        content: threadData.summaryText,
        createdAt: 0, // Summary is always the first "message"
      });
    }
  }

  // Fetch messages ordered by createdAt ascending with limit
  const messagesCollection = getMessagesCollection(uid, conversationId);
  const querySnap = await messagesCollection
    .orderBy("createdAt", "asc")
    .limitToLast(limit)
    .get();

  for (const doc of querySnap.docs) {
    const data = doc.data() as MessageDocument;
    const msg: ConversationMessage = {
      role: data.role,
      content: data.content,
      createdAt: data.createdAt,
    };

    if (data.toolCalls) {
      msg.toolCalls = data.toolCalls;
    }
    if (data.toolResults) {
      msg.toolResults = data.toolResults;
    }

    messages.push(msg);
  }

  return messages;
}

/**
 * Summarizes older messages in a conversation, keeping only recent messages intact.
 *
 * Given `keepRecent` messages to preserve, fetches all older messages and produces
 * a condensed summary. For now, concatenates the first 200 characters of each
 * older message — actual AI summarization will be wired in later.
 *
 * Updates the thread's `summaryText` and `summarizedUpTo` fields.
 *
 * - Req 9.2: Compresses older messages preserving facts, decisions, and outcomes
 *
 * @param uid - The authenticated user's UID
 * @param conversationId - The conversation thread identifier
 * @param keepRecent - Number of recent messages to keep unsummarized
 * @returns The generated summary text
 */
export async function summarizeOlderMessages(
  uid: string,
  conversationId: string,
  keepRecent: number
): Promise<string> {
  const threadRef = getThreadDoc(uid, conversationId);
  const threadSnap = await threadRef.get();

  if (!threadSnap.exists) {
    return "";
  }

  const threadData = threadSnap.data() as ThreadDocument;
  const totalMessages = threadData.messageCount;

  if (totalMessages <= keepRecent) {
    // Nothing to summarize — all messages are "recent"
    return threadData.summaryText || "";
  }

  // Determine how many messages to summarize
  const messagesToSummarize = totalMessages - keepRecent;

  // Fetch the older messages (those not in the "keep recent" window)
  const messagesCollection = getMessagesCollection(uid, conversationId);
  const olderMessagesSnap = await messagesCollection
    .orderBy("createdAt", "asc")
    .limit(messagesToSummarize)
    .get();

  if (olderMessagesSnap.empty) {
    return threadData.summaryText || "";
  }

  // Build summary — concatenate first 200 chars of each message
  // Preserves key facts, decisions, and outcomes in condensed form
  // TODO: Wire in actual AI summarization later
  const summaryParts: string[] = [];

  // Include existing summary if available
  if (threadData.summaryText && threadData.summarizedUpTo > 0) {
    summaryParts.push(threadData.summaryText);
  }

  for (const doc of olderMessagesSnap.docs) {
    const data = doc.data() as MessageDocument;
    const prefix = data.role === "user" ? "User" : data.role === "assistant" ? "AI" : "System";
    const truncatedContent = data.content.length > 200
      ? data.content.substring(0, 200) + "..."
      : data.content;
    summaryParts.push(`[${prefix}]: ${truncatedContent}`);
  }

  const summaryText = summaryParts.join("\n");

  // Update thread with new summary
  await threadRef.update({
    summaryText,
    summarizedUpTo: messagesToSummarize,
    updatedAt: Date.now(),
  });

  return summaryText;
}

// ─── Internal Helpers ──────────────────────────────────────────────────────────

/**
 * Stores a message document in the messages subcollection.
 */
async function storeMessage(
  uid: string,
  conversationId: string,
  message: MessageDocument
): Promise<string> {
  const messagesCollection = getMessagesCollection(uid, conversationId);
  const docRef = await messagesCollection.add(message);
  return docRef.id;
}

/**
 * Queues a message for later processing when the conversation is currently busy.
 * Uses a Firestore subcollection to simulate Cloud Tasks queue behavior.
 */
async function queueMessage(
  uid: string,
  conversationId: string,
  message: string
): Promise<void> {
  const queueCollection = getQueueCollection(uid, conversationId);
  const queuedMessage: QueuedMessage = {
    message,
    queuedAt: Date.now(),
    status: "pending",
  };
  await queueCollection.add(queuedMessage);
}

/**
 * Processes any queued messages after the current message completes.
 * Picks up the oldest pending message and processes it recursively.
 */
async function processQueuedMessages(
  uid: string,
  conversationId: string
): Promise<void> {
  const queueCollection = getQueueCollection(uid, conversationId);

  // Get the oldest pending message
  const pendingSnap = await queueCollection
    .where("status", "==", "pending")
    .orderBy("queuedAt", "asc")
    .limit(1)
    .get();

  if (pendingSnap.empty) {
    return;
  }

  const queueDoc = pendingSnap.docs[0];
  const queuedData = queueDoc.data() as QueuedMessage;

  // Mark as processing
  await queueDoc.ref.update({ status: "processing" });

  try {
    // Process the queued message (recursive call)
    await processMessage(uid, conversationId, queuedData.message);

    // Mark as completed
    await queueDoc.ref.update({ status: "completed" });
  } catch (error) {
    // If processing fails, mark back to pending for retry
    await queueDoc.ref.update({ status: "pending" });
    throw error;
  }
}
