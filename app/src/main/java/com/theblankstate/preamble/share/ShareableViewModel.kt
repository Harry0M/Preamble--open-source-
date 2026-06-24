package com.theblankstate.preamble.share

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theblankstate.preamble.analytics.AnalyticsManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** UI state of a Shareable_Moment generation request (Requirements 10.1, 10.2, 10.3). */
sealed interface ShareUiState {
    /** No share in progress. */
    data object Idle : ShareUiState

    /** Image is being generated; the UI shows a progress indication (Requirement 10.2). */
    data object Generating : ShareUiState

    /** Generation/timeout/share failed; the UI shows an error and the sheet is NOT opened (Requirements 10.3, 10.4). */
    data object Error : ShareUiState
}

/**
 * Orchestrates Shareable_Moment generation and sharing (Growth-loops Requirement 10).
 *
 * [requestShare] launches rendering on [renderDispatcher] (default [Dispatchers.Default]) wrapped in
 * `withTimeout` ([timeoutMs], default 10 s — Requirement 10.4), keeping the main thread free so the
 * UI stays responsive (Requirement 10.1). The state flows through [uiState] as
 * `Idle → Generating → (Idle | Error)` so the UI can show progress (Requirement 10.2).
 *
 * On a successful render the caption is built via [ShareCaption.build] (Requirement 11) and the image
 * is handed to [share] (the Share_Sheet); a `moment-shared` event is then tracked with the kind
 * (Requirement 10.5). On any failure or timeout the state becomes [ShareUiState.Error] and the share
 * sheet is never opened (Requirements 10.3, 10.4).
 *
 * The renderer and launcher are passed in per-call as suspend lambdas (the composable owns the
 * `Activity`/`Context` the real [ShareableImageRenderer]/[ShareSheetLauncher] need), which also keeps
 * this ViewModel unit-testable with fakes.
 */
class ShareableViewModel(
    private val renderDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeoutMs: Long = SHARE_TIMEOUT_MS,
    private val trackShared: (ShareKind) -> Unit = { AnalyticsManager.trackMomentShared(it.name) },
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    /**
     * Generates and shares a Shareable_Moment for [content] of the given [kind].
     *
     * @param normalizedPreambleId the sharer's normalized Preamble_ID for the caption invite-link,
     *        or null/blank to omit the link (Requirement 11.3).
     * @param render renders the branded shareable to a [Bitmap]; must never throw (returns [Result]).
     * @param share writes the bitmap and launches the Share_Sheet with the caption.
     */
    fun requestShare(
        kind: ShareKind,
        content: ShareableContent,
        normalizedPreambleId: String?,
        render: suspend (ShareableContent) -> Result<Bitmap>,
        share: suspend (bitmap: Bitmap, caption: String) -> Result<Unit>,
    ) {
        if (_uiState.value == ShareUiState.Generating) return
        _uiState.value = ShareUiState.Generating

        viewModelScope.launch {
            // Render off the main thread, bounded by the 10 s timeout (Req 10.1, 10.4).
            val bitmap = runCatching {
                withTimeout(timeoutMs) {
                    withContext(renderDispatcher) { render(content) }
                }
            }.getOrElse { Result.failure(it) }.getOrNull()

            if (bitmap == null) {
                _uiState.value = ShareUiState.Error  // failure or timeout; no share sheet (Req 10.3, 10.4)
                return@launch
            }

            val caption = ShareCaption.build(kind, normalizedPreambleId)  // Req 11
            val shared = share(bitmap, caption)
            if (shared.isSuccess) {
                trackShared(kind)  // Req 10.5
                _uiState.value = ShareUiState.Idle
            } else {
                _uiState.value = ShareUiState.Error  // Req 10.3
            }
        }
    }

    /** Maps a streak milestone via [ShareableContentMapper] and shares it (Requirements 8.x, 10.x). */
    fun shareStreakMilestone(
        days: Int,
        normalizedPreambleId: String?,
        render: suspend (ShareableContent) -> Result<Bitmap>,
        share: suspend (Bitmap, String) -> Result<Unit>,
    ) = requestShare(ShareKind.STREAK_MILESTONE, ShareableContentMapper.fromStreak(days), normalizedPreambleId, render, share)

    /** Maps a perfect day via [ShareableContentMapper] and shares it (Requirements 9.x, 10.x). */
    fun sharePerfectDay(
        tasksCompleted: Int,
        normalizedPreambleId: String?,
        render: suspend (ShareableContent) -> Result<Bitmap>,
        share: suspend (Bitmap, String) -> Result<Unit>,
    ) = requestShare(ShareKind.PERFECT_DAY, ShareableContentMapper.fromPerfectDay(tasksCompleted), normalizedPreambleId, render, share)

    /** Maps a weekly recap via [ShareableContentMapper] and shares it (Requirements 7.x, 10.x). */
    fun shareWeeklyRecap(
        recap: WeeklyRecapSummary,
        normalizedPreambleId: String?,
        render: suspend (ShareableContent) -> Result<Bitmap>,
        share: suspend (Bitmap, String) -> Result<Unit>,
    ) = requestShare(ShareKind.WEEKLY_RECAP, ShareableContentMapper.fromWeeklyRecap(recap), normalizedPreambleId, render, share)

    /** Clears a previously shown error back to [ShareUiState.Idle]. */
    fun dismissError() {
        if (_uiState.value == ShareUiState.Error) _uiState.value = ShareUiState.Idle
    }

    private companion object {
        /** Generation deadline (Requirement 10.4). */
        const val SHARE_TIMEOUT_MS = 10_000L
    }
}
