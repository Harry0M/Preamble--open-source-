package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.data.AiMemoryEntity
import com.theblankstate.preamble.data.PreambleDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Maintenance pass over the long-term memory store. Two operations:
 *
 *   1. **Auto-prune** — pure algorithm. Drops memories that look stale + low-confidence.
 *   2. **AI consolidation** — when memory count > threshold, asks the cheap model to
 *      identify {duplicates_to_merge, contradictions_to_replace, low_value_to_drop}.
 *      Output is bounded JSON so we can apply changes safely.
 *
 * Designed to run opportunistically — once on app start, plus on demand from settings.
 * Idempotent: running it twice in a row is harmless.
 */
object MemoryMaintenance {
    private const val TAG = "MemoryMaintenance"

    // Auto-prune thresholds
    private const val UNUSED_DAYS_THRESHOLD = 90L
    private const val LOW_CONFIDENCE_THRESHOLD = 0.7f
    private const val CONSOLIDATION_TRIGGER = 50

    suspend fun run(context: Context): MaintenanceReport = withContext(Dispatchers.IO) {
        val pruned = autoPrune(context)
        val consolidationResult = if (memoryCount(context) > CONSOLIDATION_TRIGGER) {
            aiConsolidate(context)
        } else ConsolidationOutcome(0, 0, 0)
        MaintenanceReport(pruned, consolidationResult)
    }

    data class MaintenanceReport(
        val prunedStale: Int,
        val consolidated: ConsolidationOutcome,
    )

    data class ConsolidationOutcome(
        val merged: Int,
        val replaced: Int,
        val dropped: Int,
    )

    // ── Auto-prune (no LLM) ───────────────────────────────────────────────────

    private suspend fun autoPrune(context: Context): Int {
        val dao = PreambleDatabase.getInstance(context).aiMemoryDao()
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: "local_user"
        val all = dao.snapshot(uid, limit = 1000)
        val now = System.currentTimeMillis()
        val cutoff = now - TimeUnit.DAYS.toMillis(UNUSED_DAYS_THRESHOLD)
        val stale = all.filter { it.lastUsedAt < cutoff && it.confidence < LOW_CONFIDENCE_THRESHOLD }
        val repo = AiMemoryRepository.get(context)
        for (m in stale) repo.delete(m.id)
        if (stale.isNotEmpty()) Log.d(TAG, "Auto-pruned ${stale.size} stale memories")
        return stale.size
    }

    // ── AI consolidation ──────────────────────────────────────────────────────

    private suspend fun aiConsolidate(context: Context): ConsolidationOutcome {
        val provider = buildCheapProvider() ?: return ConsolidationOutcome(0, 0, 0)
        val dao = PreambleDatabase.getInstance(context).aiMemoryDao()
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: "local_user"
        val all = dao.snapshot(uid, limit = 1000)
        if (all.size < CONSOLIDATION_TRIGGER) return ConsolidationOutcome(0, 0, 0)

        val repo = AiMemoryRepository.get(context)
        val logger = AiProcessLogger.get(context)

        val systemPrompt = buildConsolidationPrompt()
        val memoryListJson = all.joinToString(",\n") {
            """{"id":"${it.id}","key":"${it.key.take(40)}","value":"${it.value.take(120).replace("\"", "\\\"")}","category":"${it.category}","conf":${it.confidence}}"""
        }
        val userPrompt = "Memories (JSON list):\n[$memoryListJson]"

        val t0 = System.currentTimeMillis()
        val response = runCatching {
            provider.chat(
                listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userPrompt)),
                emptyList()
            )
        }
        val dt = System.currentTimeMillis() - t0

        val raw = response.getOrNull()?.text?.trim().orEmpty()
        val plan = parseConsolidationPlan(raw) ?: run {
            logger.log(
                op = "memory_consolidate",
                provider = provider.name,
                model = null,
                input = "memories=${all.size}",
                output = raw.take(800),
                toolCalls = null,
                durationMs = dt,
                success = false,
                error = "parse failed",
            )
            return ConsolidationOutcome(0, 0, 0)
        }

        // Apply
        var merged = 0
        var replaced = 0
        var dropped = 0
        val byId = all.associateBy { it.id }

        for (drop in plan.drop) {
            val mem = byId[drop] ?: continue
            repo.delete(mem.id); dropped++
        }
        for (merge in plan.merge) {
            // Keep first id, drop rest, optionally update value
            val keep = byId[merge.keep] ?: continue
            val newValue = merge.newValue ?: keep.value
            repo.save(keep.key, newValue, keep.category, maxOf(keep.confidence, 0.85f), source = "consolidation")
            for (id in merge.dropIds) {
                if (id == keep.id) continue
                byId[id]?.let { repo.delete(it.id); merged++ }
            }
        }
        for (rep in plan.replace) {
            val old = byId[rep.id] ?: continue
            repo.save(old.key, rep.newValue, old.category, 0.95f, source = "consolidation")
            replaced++
        }

        val outcome = ConsolidationOutcome(merged, replaced, dropped)
        logger.log(
            op = "memory_consolidate",
            provider = provider.name,
            model = null,
            input = "memories=${all.size}",
            output = raw.take(800),
            toolCalls = null,
            durationMs = dt,
            success = true,
            thought = "merged=$merged replaced=$replaced dropped=$dropped",
        )
        return outcome
    }

    private fun buildCheapProvider(): AiProvider? = AiProviderFactory.memory()

    private fun buildConsolidationPrompt(): String {
        return """
You consolidate a user's long-term memory list. Each memory has id/key/value/category/conf.

Output ONLY a JSON object with three arrays — no prose, no markdown:
{
  "merge":   [{"keep":<id>, "dropIds":[<id>,...], "newValue":"<combined value or null>"}],
  "replace": [{"id":<id>, "newValue":"<corrected value>"}],
  "drop":    [<id>, ...]
}

Rules:
- "merge": same fact recorded multiple times under similar keys — keep the most informative id, drop the rest.
- "replace": fact is contradicted by a newer one in the list (e.g. job changed). Replace the value of the older entry.
- "drop": low-value or trivially duplicated facts.
- Be conservative — when in doubt, do NOT touch the memory. Empty arrays are valid.
- Never drop identity facts (name, age, gender) unless a clear newer one exists.
- Maximum 20 changes per call.
        """.trimIndent()
    }

    private suspend fun memoryCount(context: Context): Int {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: "local_user"
        return PreambleDatabase.getInstance(context).aiMemoryDao().snapshot(uid, limit = 5000).size
    }

    private data class ConsolidationPlan(
        val merge: List<MergeOp>,
        val replace: List<ReplaceOp>,
        val drop: List<String>,
    )
    private data class MergeOp(val keep: String, val dropIds: List<String>, val newValue: String?)
    private data class ReplaceOp(val id: String, val newValue: String)

    private fun parseConsolidationPlan(raw: String): ConsolidationPlan? {
        if (raw.isBlank()) return null
        val cleaned = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val obj = JsonParser.parseString(cleaned.substring(start, end + 1)).asJsonObject
            val mergeList = obj.getAsJsonArray("merge")?.mapNotNull { el ->
                val o = el.asJsonObject
                val keep = o.get("keep")?.asString ?: return@mapNotNull null
                val drops = o.getAsJsonArray("dropIds")?.map { it.asString } ?: emptyList()
                val newV = o.get("newValue")?.let { if (it.isJsonNull) null else it.asString }
                MergeOp(keep, drops, newV)
            } ?: emptyList()
            val replaceList = obj.getAsJsonArray("replace")?.mapNotNull { el ->
                val o = el.asJsonObject
                val id = o.get("id")?.asString ?: return@mapNotNull null
                val nv = o.get("newValue")?.asString ?: return@mapNotNull null
                ReplaceOp(id, nv)
            } ?: emptyList()
            val dropList = obj.getAsJsonArray("drop")?.map { it.asString } ?: emptyList()
            ConsolidationPlan(mergeList, replaceList, dropList)
        }.onFailure { Log.w(TAG, "Plan parse failed", it) }.getOrNull()
    }
}
