package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.data.ChatMessageEntity
import com.theblankstate.preamble.data.PreambleDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Compresses old chat history into a single rolling summary row so that
 * very long conversations stay token-cheap on every turn.
 *
 * Strategy:
 *   - When called, finds the oldest [count] user/assistant rows in the conversation.
 *   - Asks the cheap model to summarize them into ≤ 600 chars covering:
 *       facts the user stated, requests, decisions, what AI did.
 *   - Merges with any pre-existing summary row (so summaries accumulate, not replace).
 *   - Deletes the summarized rows, leaves the new summary as a single row=summary.
 *
 * If the model isn't reachable, no rows are touched — the conversation just
 * stays bigger until next attempt. Idempotent.
 */
object ChatSummarizer {
    private const val TAG = "ChatSummarizer"
    private const val MAX_SUMMARY_CHARS = 600

    suspend fun summarizeOldest(context: Context, cid: String, count: Int) = withContext(Dispatchers.IO) {
        if (count <= 0) return@withContext
        val provider = buildCheapProvider() ?: return@withContext
        val dao = PreambleDatabase.getInstance(context).chatMessageDao()
        val all = dao.snapshot(cid)

        val existingSummary = all.firstOrNull { it.role == "summary" }
        val visible = all.filter { it.role == "user" || it.role == "assistant" }
        if (visible.size < count) return@withContext
        val oldest = visible.take(count)

        val systemPrompt = """
You compress a chat history into one paragraph for long-term context.
Output ONLY the summary — no headers, no markdown, no explanation.
Cap at $MAX_SUMMARY_CHARS characters.
Capture: facts the USER stated about themselves, requests they made, decisions reached,
notable tasks added/changed/deleted by the AI, and any agreements about future behavior.
DO NOT include greetings, filler, or your own commentary.
DO write in third person (e.g. "User mentioned... AI added X task...").
        """.trimIndent()

        val previousBlock = existingSummary?.let { "Previous summary so far:\n${it.content}\n\n" } ?: ""
        val transcript = oldest.joinToString("\n") { "${it.role.uppercase()}: ${it.content.take(200)}" }
        val userPrompt = "${previousBlock}New chunk to merge into summary:\n$transcript"

        val logger = AiProcessLogger.get(context)
        val t0 = System.currentTimeMillis()
        val resp = runCatching {
            provider.chat(
                listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userPrompt)),
                emptyList()
            )
        }
        val dt = System.currentTimeMillis() - t0
        val text = resp.getOrNull()?.text?.trim().orEmpty().take(MAX_SUMMARY_CHARS)
        if (text.isBlank()) {
            logger.log(
                op = "chat_summarize",
                provider = provider.name,
                model = null,
                input = "rows=$count",
                output = null,
                toolCalls = null,
                durationMs = dt,
                success = false,
                error = "empty response",
            )
            return@withContext
        }

        // Replace existing summary or insert a new one
        val newSummary = ChatMessageEntity(
            id = existingSummary?.id ?: UUID.randomUUID().toString(),
            conversationId = cid,
            userId = visible.firstOrNull()?.userId ?: "anonymous",
            role = "summary",
            content = text,
            timestamp = oldest.first().timestamp - 1, // anchored before the earliest msg
            syncPending = 1,
        )
        dao.upsert(newSummary)

        // Drop the absorbed rows
        for (m in oldest) dao.delete(m.id)

        logger.log(
            op = "chat_summarize",
            provider = provider.name,
            model = null,
            input = "rows=$count",
            output = text,
            toolCalls = null,
            durationMs = dt,
            success = true,
            thought = "absorbed=$count summaryLen=${text.length}",
        )
        Log.d(TAG, "Summarized $count rows, summary now ${text.length} chars")
    }

    private fun buildCheapProvider(): AiProvider? = AiProviderFactory.memory()
}
