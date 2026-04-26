package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.theblankstate.preamble.data.AiProcessLogDao
import com.theblankstate.preamble.data.AiProcessLogEntity
import com.theblankstate.preamble.data.PreambleDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Fire-and-forget writer for AI activity rows.
 * Every provider call, extraction run, etc. should log here so the user can
 * see in Settings what the AI is doing on their behalf.
 */
class AiProcessLogger private constructor(
    private val dao: AiProcessLogDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observe(op: String? = null, limit: Int = 200): Flow<List<AiProcessLogEntity>> =
        if (op == null) dao.observe(limit) else dao.observeByOp(op, limit)

    /**
     * Log one operation. Truncates input/output to keep the table small.
     */
    fun log(
        op: String,
        provider: String,
        model: String?,
        input: String,
        output: String?,
        toolCalls: String?,
        durationMs: Long,
        success: Boolean,
        error: String? = null,
        thought: String? = null,
    ) {
        val row = AiProcessLogEntity(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            op = op,
            provider = provider,
            model = model,
            input = input.take(MAX_FIELD),
            output = output?.take(MAX_FIELD),
            toolCalls = toolCalls?.take(MAX_FIELD),
            durationMs = durationMs,
            success = success,
            error = error?.take(MAX_FIELD),
            thought = thought?.take(MAX_FIELD),
        )
        scope.launch {
            runCatching { dao.upsert(row) }.onFailure { Log.w(TAG, "log failed", it) }
        }
    }

    /**
     * Delete entries older than [days] days. Run opportunistically.
     */
    fun pruneAsync(days: Int = 30) {
        scope.launch {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
            runCatching { dao.prune(cutoff) }
        }
    }

    suspend fun clear() = dao.clear()

    companion object {
        private const val TAG = "AiProcessLog"
        private const val MAX_FIELD = 4000

        const val OP_CHAT = "chat"
        const val OP_VOICE = "voice"
        const val OP_TASK_INPUT = "task_input"
        const val OP_PREVIEW = "preview"
        const val OP_EXTRACT_MEMORY = "extract_memory"
        const val OP_NOTIF_EDIT = "notif_edit"

        @Volatile private var INSTANCE: AiProcessLogger? = null

        fun get(context: Context): AiProcessLogger {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiProcessLogger(
                    PreambleDatabase.getInstance(context).aiProcessLogDao()
                ).also { INSTANCE = it }
            }
        }
    }
}
