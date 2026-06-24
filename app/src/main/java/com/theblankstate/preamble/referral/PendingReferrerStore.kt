package com.theblankstate.preamble.referral

import android.content.Context

/**
 * Thin [android.content.SharedPreferences] wrapper that carries a referrer's
 * Preamble_ID from a pre-account deep link through to first account creation
 * (Requirement 2.1).
 *
 * The pending referrer is stored in the existing `"preamble_prefs"` file (the
 * same file the rest of the app uses) under the key `"pending_referrer"`.
 * [consume] is single-use: it reads and clears the value in one call so a stale
 * referrer cannot attach to a later account.
 */
object PendingReferrerStore {

    private const val PREFS = "preamble_prefs"
    private const val KEY_PENDING_REFERRER = "pending_referrer"

    /** Stores the normalized referrer [preambleId] for use at the next account creation. */
    fun save(context: Context, preambleId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_REFERRER, preambleId)
            .apply()
    }

    /**
     * Reads and clears the pending referrer id, returning it or `null` if none
     * was retained. Single-use by design.
     */
    fun consume(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_PENDING_REFERRER, null)
        if (value != null) {
            prefs.edit().remove(KEY_PENDING_REFERRER).apply()
        }
        return value
    }
}
