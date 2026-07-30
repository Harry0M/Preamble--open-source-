package com.theblankstate.preamble.ai.v2

/**
 * Service provider for AI V2 — ready for Hilt DI when added to the project.
 *
 * Currently the project uses direct object singletons. This provider interface
 * enables a clean migration to Hilt injection:
 *
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object AiV2Module {
 *     @Provides
 *     @Singleton
 *     fun provideAiV2Service(): AiV2ServiceContract = CloudAiV2Service
 * }
 * ```
 *
 * Until Hilt is wired up, consumers can access [CloudAiV2Service] directly
 * as an object singleton, or use [V2ServiceProvider.service] for a single
 * access point that's easy to swap later.
 */
object V2ServiceProvider {

    /**
     * The active V2 service instance.
     * Replace this with Hilt `@Inject` when DI is set up project-wide.
     */
    val service: AiV2ServiceContract = CloudAiV2Service
}

/**
 * Contract interface for AI V2 operations.
 *
 * Enables testing with fakes/mocks and future Hilt injection.
 */
interface AiV2ServiceContract {
    fun chat(request: V2ChatRequest): kotlinx.coroutines.flow.Flow<V2StreamEvent>
    fun chatContinue(request: V2ToolResultRequest): kotlinx.coroutines.flow.Flow<V2StreamEvent>
    suspend fun parse(request: V2ParseRequest): V2ParseResult
    suspend fun getTokenBalance(): V2TokenBalance
    suspend fun getModelConfig(): List<V2ModelInfo>
    suspend fun getDailyBriefing(): V2BriefingResult
}
