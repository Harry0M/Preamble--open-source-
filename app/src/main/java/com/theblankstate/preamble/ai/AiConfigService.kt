package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.theblankstate.preamble.BuildConfig
import kotlinx.coroutines.tasks.await

/**
 * Live-tunable AI configuration backed by Firebase Remote Config.
 *
 * Why this exists: keeps you in control after the app ships. You can rotate
 * providers/models from the Firebase console without forcing an app update —
 * useful when a provider goes down, prices change, or you want to A/B test.
 *
 * Security model:
 *   - API keys stay in BuildConfig (baked into the APK at compile time).
 *   - Remote Config decides ONLY which provider/model is active.
 *   - This means no keys travel over the wire and the rotation surface is small.
 *
 * Remote Config keys (set these in the Firebase console → Remote Config):
 *   ai_chat_provider     → MISTRAL | GEMINI | OPENAI | CLAUDE   (default: MISTRAL)
 *   ai_chat_model        → e.g. "mistral-medium-latest"          (default: "")
 *   ai_memory_provider   → MISTRAL | GEMINI | OPENAI | CLAUDE   (default: GEMINI)
 *   ai_memory_model      → e.g. "gemini-2.0-flash"               (default: "")
 *   ai_kill_switch       → true to disable all AI calls          (default: false)
 *   ai_smart_mode_default → true to enable memory pipeline       (default: true)
 *
 * Empty string → "use BuildConfig default" → "use provider default model".
 */
object AiConfigService {

    private const val TAG = "AiConfigService"

    // Remote Config parameter keys
    private const val K_CHAT_PROVIDER = "ai_chat_provider"
    private const val K_CHAT_MODEL = "ai_chat_model"
    private const val K_MEMORY_PROVIDER = "ai_memory_provider"
    private const val K_MEMORY_MODEL = "ai_memory_model"
    private const val K_KILL_SWITCH = "ai_kill_switch"
    private const val K_SMART_DEFAULT = "ai_smart_mode_default"
    private const val K_PREMIUM_GATING = "premium_gating_enabled"

    @Volatile private var initialized = false

    /**
     * Initialize Remote Config once on app start. Sets in-app defaults so first launch
     * works offline, then triggers an async fetch+activate.
     *
     * Safe to call repeatedly — second call is a no-op.
     */
    suspend fun initialize() {
        if (initialized) return
        initialized = true
        try {
            val rc = FirebaseRemoteConfig.getInstance()
            rc.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0L else 3600L)
                    .build()
            )
            rc.setDefaultsAsync(
                mapOf(
                    K_CHAT_PROVIDER to BuildConfig.AI_PROVIDER,
                    K_CHAT_MODEL to BuildConfig.AI_MODEL,
                    K_MEMORY_PROVIDER to BuildConfig.AI_MEMORY_PROVIDER,
                    K_MEMORY_MODEL to BuildConfig.AI_MEMORY_MODEL,
                    K_KILL_SWITCH to false,
                    K_SMART_DEFAULT to true,
                    K_PREMIUM_GATING to false,
                )
            ).await()
            rc.fetchAndActivate().await()
            Log.d(TAG, "Remote Config activated. chat=${chatProvider()}/${chatModel()}, memory=${memoryProvider()}/${memoryModel()}")
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config init failed — falling back to BuildConfig", e)
        }
    }

    /** Force-refresh from server (for a settings "Refresh AI config" button). */
    suspend fun refresh(): Boolean {
        return runCatching {
            FirebaseRemoteConfig.getInstance().fetchAndActivate().await()
        }.onFailure { Log.w(TAG, "refresh failed", it) }.getOrDefault(false)
    }

    // ── Resolved config getters ───────────────────────────────────────────────

    fun chatProvider(): String =
        readString(K_CHAT_PROVIDER, BuildConfig.AI_PROVIDER).ifBlank { "MISTRAL" }

    fun chatModel(): String =
        readString(K_CHAT_MODEL, BuildConfig.AI_MODEL)

    fun memoryProvider(): String =
        readString(K_MEMORY_PROVIDER, BuildConfig.AI_MEMORY_PROVIDER).ifBlank { "GEMINI" }

    fun memoryModel(): String =
        readString(K_MEMORY_MODEL, BuildConfig.AI_MEMORY_MODEL)

    fun killSwitch(): Boolean = runCatching {
        FirebaseRemoteConfig.getInstance().getBoolean(K_KILL_SWITCH)
    }.getOrDefault(false)

    fun smartModeDefault(): Boolean = runCatching {
        FirebaseRemoteConfig.getInstance().getBoolean(K_SMART_DEFAULT)
    }.getOrDefault(true)

    /**
     * Master switch for premium feature gating (Track B). Mirrors [killSwitch] exactly:
     * reads the BOOLEAN via Remote Config and falls back to OFF when the value has not
     * been fetched, is absent, or cannot be read. Gating only turns ON when the fetched
     * value is `true` (Req 7.1, 7.2, 7.4).
     */
    fun premiumGatingEnabled(): Boolean = runCatching {
        FirebaseRemoteConfig.getInstance().getBoolean(K_PREMIUM_GATING)
    }.getOrDefault(false)

    /**
     * Resolve the API key for a given provider. Pulls from BuildConfig — never from
     * Remote Config — so keys can't leak through the wire.
     */
    fun keyFor(provider: AiProviderType): String = when (provider) {
        AiProviderType.MISTRAL -> BuildConfig.AI_KEY_MISTRAL
        AiProviderType.GEMINI -> BuildConfig.AI_KEY_GEMINI
        AiProviderType.OPENAI -> BuildConfig.AI_KEY_OPENAI
        AiProviderType.CLAUDE -> BuildConfig.AI_KEY_CLAUDE
    }

    private fun readString(key: String, fallback: String): String = runCatching {
        val v = FirebaseRemoteConfig.getInstance().getString(key)
        if (v.isBlank()) fallback else v
    }.getOrDefault(fallback)
}
