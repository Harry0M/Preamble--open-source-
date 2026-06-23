package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free toggle/change/remove + validation logic for kudos reactions
 * on the schema-v2 canonical collaborative document.
 *
 * Feature: social-engagement. This object is the single source of truth for the
 * reaction transforms described in design.md (Reaction_Service / Pure logic) and
 * Requirements 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 2.5:
 *
 *  - [EMOJI_SET] / [isValidEmoji] : the fixed six-emoji Reaction_Emoji_Set (Req 1.1, 1.3).
 *  - [apply] : toggle/change/remove semantics over a reactions map keyed by reactor uid —
 *      no existing reaction + valid emoji -> ADD with a UTC creation timestamp (Req 1.2);
 *      existing reaction with a different emoji -> CHANGE in place (Req 2.2);
 *      existing reaction with the same emoji -> REMOVE (Req 2.3);
 *      an emoji outside [EMOJI_SET] -> [ReactionResult.Rejected] with an unchanged map (Req 1.3).
 *  - [remove] : explicit own-entry-only removal; a no-op when the reactor has no entry (Req 2.4).
 *
 * Every transform keeps at most one entry per reactor uid (the map key is the reactor uid,
 * Req 2.1) and leaves every other reactor's entry byte-for-byte identical (Req 2.5).
 *
 * Like the rest of this package, the file is deliberately free of Android, Firestore, and
 * serialization dependencies so these transforms are fast/JVM property-testable. The caller
 * supplies `now` (no clock access), so the logic is deterministic. The Firestore gateway
 * (`WorkspaceRepository`) applies the resulting map (or the equivalent field-level update)
 * inside a transaction.
 */
object Reactions {

    /** Reaction_Emoji_Set (Req 1.1, 3.6 glossary): exactly six emoji, in fixed order. */
    val EMOJI_SET: List<String> = listOf("👍", "🎉", "🔥", "👏", "❤️", "💪")

    /** Message returned when an emoji outside [EMOJI_SET] is supplied (Req 1.3). */
    const val REASON_INVALID_EMOJI = "That emoji is not an allowed reaction."

    /** Field names stored under reactions[reactorUid]. */
    private const val FIELD_EMOJI = "emoji"
    private const val FIELD_TARGET_UID = "targetUid"
    private const val FIELD_CREATED_AT = "createdAt"

    /** True iff [emoji] is a member of the fixed Reaction_Emoji_Set (Req 1.3). */
    fun isValidEmoji(emoji: String): Boolean = emoji in EMOJI_SET

    /**
     * Outcome of an [apply] transform.
     *
     * [Updated] carries the new reactions map and the [Effect] that was applied, letting the
     * caller (and the server-side trigger) decide messaging / whether to push.
     * [Rejected] carries the original, unchanged map together with a [reason] message (Req 1.3).
     */
    sealed interface ReactionResult {
        data class Updated(val reactions: Map<String, Any?>, val effect: Effect) : ReactionResult
        data class Rejected(val reactions: Map<String, Any?>, val reason: String) : ReactionResult
    }

    /** The kind of change [apply] performed; reproduced server-side to decide kudos pushes. */
    enum class Effect { ADDED, CHANGED, REMOVED }

    /**
     * Toggle/change/remove a single reactor's reaction over [reactions] (keyed by reactor uid):
     *
     *  - no existing entry + valid [emoji]  -> ADD a new entry storing [emoji], [targetUid], and
     *    [now] as the UTC `createdAt`; reports [Effect.ADDED] (Req 1.2).
     *  - existing entry with a different emoji -> CHANGE the emoji (and [targetUid]) in place,
     *    preserving the original `createdAt`, without creating an additional entry;
     *    reports [Effect.CHANGED] (Req 2.2).
     *  - existing entry with the same emoji -> REMOVE the reactor's entry; reports
     *    [Effect.REMOVED] (Req 2.3).
     *
     * If [emoji] is not in [EMOJI_SET], returns [ReactionResult.Rejected] with the map unchanged
     * (Req 1.3). In every case at most one entry exists per reactor (Req 2.1) and every other
     * reactor's entry is byte-for-byte identical (Req 2.5).
     */
    fun apply(
        reactions: Map<String, Any?>,
        reactorUid: String,
        emoji: String,
        targetUid: String?,
        now: Long
    ): ReactionResult {
        if (!isValidEmoji(emoji)) {
            return ReactionResult.Rejected(reactions, REASON_INVALID_EMOJI)
        }

        val existing = reactions[reactorUid].asStringMap()
        val existingEmoji = existing?.get(FIELD_EMOJI) as? String

        // Same emoji as the current reaction -> toggle off / remove (Req 2.3).
        if (existing != null && existingEmoji == emoji) {
            return ReactionResult.Updated(
                removeEntry(reactions, reactorUid),
                Effect.REMOVED
            )
        }

        val result = LinkedHashMap<String, Any?>(reactions)
        if (existing != null) {
            // Existing, different emoji -> change in place, preserving createdAt (Req 2.2).
            val updatedEntry = LinkedHashMap<String, Any?>(existing).apply {
                this[FIELD_EMOJI] = emoji
                this[FIELD_TARGET_UID] = targetUid
            }
            result[reactorUid] = updatedEntry
            return ReactionResult.Updated(result, Effect.CHANGED)
        }

        // No existing entry -> add a new reaction with a UTC creation timestamp (Req 1.2).
        val newEntry = linkedMapOf<String, Any?>(
            FIELD_EMOJI to emoji,
            FIELD_TARGET_UID to targetUid,
            FIELD_CREATED_AT to now
        )
        result[reactorUid] = newEntry
        return ReactionResult.Updated(result, Effect.ADDED)
    }

    /**
     * Explicit remove control (Req 2.4): returns a copy of [reactions] with only [reactorUid]'s
     * entry removed, leaving every other reactor's entry unchanged (Req 2.5). When [reactorUid]
     * has no entry, the map is returned unchanged (a no-op).
     */
    fun remove(reactions: Map<String, Any?>, reactorUid: String): Map<String, Any?> =
        if (reactions.containsKey(reactorUid)) removeEntry(reactions, reactorUid) else reactions

    private fun removeEntry(reactions: Map<String, Any?>, reactorUid: String): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>(reactions)
        result.remove(reactorUid)
        return result
    }

    /** Safe accessor for the loosely-typed canonical document map. */
    private fun Any?.asStringMap(): Map<String, Any?>? {
        val map = this as? Map<*, *> ?: return null
        val result = LinkedHashMap<String, Any?>(map.size)
        for ((key, value) in map) {
            if (key is String) result[key] = value
        }
        return result
    }
}
