package com.theblankstate.preamble.collab

/**
 * Pure, framework-free model for the Recipient_Picker (Requirement 30).
 *
 * A unified [Recipient] lets the picker hold Friends and Circles together in a
 * single list and reuse [SocialSearch] for filtering. The picker maps the
 * repository's `Friend` to [FriendRef] and `Circle` to [CircleRef] at the call
 * site, so this module stays free of Android, Firebase, and serialization
 * dependencies and can be exercised directly by JVM property-based tests.
 */

/**
 * Lightweight, framework-free view of a friend so the pure module does not depend
 * on the repository's `Friend` type. The picker maps `Friend -> FriendRef`.
 */
data class FriendRef(
    val uid: String,
    val name: String,
    val preambleId: String,
    val photoUrl: String? = null,
)

/**
 * Lightweight, framework-free view of a circle so the pure module does not depend
 * on the repository's `Circle` type. The picker maps `Circle -> CircleRef`.
 */
data class CircleRef(
    val id: String,
    val name: String,
    val memberUids: List<String>,
)

/**
 * A selectable target in the Recipient_Picker: exactly one of a Friend or a
 * Circle (Requirement 30.1). Each variant exposes a stable selection [key]
 * (`"f:<uid>"` for a friend, `"c:<id>"` for a circle) used for selection
 * tracking and de-duplication.
 */
sealed interface Recipient {
    /** Stable selection key: `"f:<uid>"` for a friend or `"c:<id>"` for a circle. */
    val key: String

    data class FriendRecipient(val friend: FriendRef) : Recipient {
        override val key: String get() = "f:${friend.uid}"
    }

    data class CircleRecipient(val circle: CircleRef) : Recipient {
        override val key: String get() = "c:${circle.id}"
    }
}

/**
 * Adapts a [Recipient] to [SocialSearch.Searchable] so the existing
 * case-insensitive [SocialSearch.filter] matches a Friend on display name AND
 * Preamble_ID and a Circle on Circle_Name (Requirement 30.3). A Circle has no
 * Preamble_ID, so [SocialSearch.Searchable.preambleId] is empty for circles.
 */
fun Recipient.asSearchable(): SocialSearch.Searchable = object : SocialSearch.Searchable {
    override val preambleId: String = when (this@asSearchable) {
        is Recipient.FriendRecipient -> friend.preambleId
        is Recipient.CircleRecipient -> ""
    }
    override val displayName: String = when (this@asSearchable) {
        is Recipient.FriendRecipient -> friend.name
        is Recipient.CircleRecipient -> circle.name
    }
}

/**
 * Thin wrapper that holds a [Recipient] and IS a [SocialSearch.Searchable] by
 * delegating to [asSearchable]. This lets callers reuse the existing
 * `filter`-then-`visible` composition directly:
 *
 * ```
 * val results = SocialSearch.filter(query, recipients.map { SearchableRecipient(it) })
 * val chosen = results.map { it.recipient }
 * ```
 *
 * Because [filter] preserves input order and never fabricates entries, the
 * [recipient] of each result maps back to the original [Recipient] one-to-one.
 */
class SearchableRecipient(val recipient: Recipient) :
    SocialSearch.Searchable by recipient.asSearchable()
