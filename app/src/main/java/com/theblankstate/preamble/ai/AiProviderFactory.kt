package com.theblankstate.preamble.ai

/**
 * Single source of truth for constructing AiProvider instances.
 *
 * Behavior:
 *   - Provider + model come from [AiConfigService] (Firebase Remote Config, hot-swappable).
 *   - API keys come from BuildConfig (baked into APK, never sent over the wire).
 *   - When a remote-controlled kill switch is active, returns null so all callers
 *     gracefully degrade to local-only behavior.
 *
 * Tiers:
 *   - [main]    — chat, voice, task input, notification edits.
 *   - [memory]  — extraction, planner, summarizer, consolidator.
 */
object AiProviderFactory {

    fun main(): AiProvider? {
        if (AiConfigService.killSwitch()) return null
        val providerType = parseProvider(AiConfigService.chatProvider()) ?: return null
        val key = AiConfigService.keyFor(providerType).takeIf { it.isNotBlank() } ?: return null
        return build(providerType, key, AiConfigService.chatModel())
    }

    /** Build provider for a specific model ID, inferring the provider type from the model name. */
    fun mainWithModelOverride(modelId: String): AiProvider? {
        if (modelId.isBlank()) return main()
        if (AiConfigService.killSwitch()) return null
        val providerType = when {
            "gemini" in modelId -> AiProviderType.GEMINI
            "mistral" in modelId || "mixtral" in modelId || "devstral" in modelId -> AiProviderType.MISTRAL
            "gpt" in modelId || "o1" in modelId || "o3" in modelId -> AiProviderType.OPENAI
            "claude" in modelId -> AiProviderType.CLAUDE
            else -> parseProvider(AiConfigService.chatProvider()) ?: return null
        }
        val key = AiConfigService.keyFor(providerType).takeIf { it.isNotBlank() } ?: return null
        return build(providerType, key, modelId)
    }

    fun memory(): AiProvider? {
        if (AiConfigService.killSwitch()) return null
        val providerType = parseProvider(AiConfigService.memoryProvider()) ?: return null
        val key = AiConfigService.keyFor(providerType).takeIf { it.isNotBlank() }
            ?: parseProvider(AiConfigService.chatProvider())?.let { AiConfigService.keyFor(it) }?.takeIf { it.isNotBlank() }
            ?: return null
        return build(providerType, key, AiConfigService.memoryModel())
    }

    private fun parseProvider(raw: String): AiProviderType? = runCatching {
        AiProviderType.valueOf(raw.trim().uppercase())
    }.getOrNull()

    private fun build(providerType: AiProviderType, key: String, model: String): AiProvider? {
        val cleanModel = model.takeIf { it.isNotBlank() }
        return runCatching {
            when (providerType) {
                AiProviderType.MISTRAL -> MistralProvider(key, cleanModel)
                AiProviderType.OPENAI -> OpenAiProvider(key, cleanModel)
                AiProviderType.GEMINI -> GeminiProvider(key, cleanModel)
                AiProviderType.CLAUDE -> ClaudeProvider(key, cleanModel)
            }
        }.getOrNull()
    }
}
