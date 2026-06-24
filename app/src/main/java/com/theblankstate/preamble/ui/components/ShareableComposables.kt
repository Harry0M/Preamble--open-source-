package com.theblankstate.preamble.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.preamble.share.ShareUiState
import com.theblankstate.preamble.share.ShareableContent
import com.theblankstate.preamble.share.ShareableContentMapper
import com.theblankstate.preamble.share.ShareableImageRenderer
import com.theblankstate.preamble.share.ShareableViewModel
import com.theblankstate.preamble.share.ShareSheetLauncher
import com.theblankstate.preamble.share.WeeklyRecapSummary
import com.theblankstate.preamble.ui.theme.LocalAccentColor
import com.theblankstate.preamble.ui.theme.PreambleTheme

/**
 * Branded Compose shareables for the three Shareable_Moment kinds
 * (Growth-loops Requirements 7, 8, 9).
 *
 * [WeeklyRecapShareable], [StreakMilestoneShareable] and [PerfectDayShareable]
 * each consume a [ShareableContent] and render the branded card that is captured
 * to a bitmap by [ShareableImageRenderer], plus a Share control that triggers the
 * non-blocking generate-and-share flow through [ShareableViewModel] (Requirement
 * 10). The caption always carries the sharer's Invite_Link built from
 * [normalizedPreambleId] (Requirement 11).
 *
 * The same branded card ([BrandedShareCard]) is both the on-screen preview and
 * the off-screen captured visual, so what the user previews is exactly what they
 * share.
 */

/* ─────────────────────── activity lookup ─────────────────────── */

/** Unwraps the host [Activity] from a Compose [Context] (decor view is needed for capture). */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/* ─────────────────────── branded card ─────────────────────── */

/**
 * The branded visual for a Shareable_Moment. Rendered both on-screen (preview)
 * and off-screen (captured to a bitmap). Typography scales from the available
 * width so it looks right at the small preview size and at the full 1080-px
 * capture size alike. Depicts the defining metric supplied in [content]
 * (Requirements 7.3, 8.2, 9.2).
 */
@Composable
fun BrandedShareCard(
    content: ShareableContent,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccentColor.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val unit = maxWidth.value
        fun scaled(factor: Float): Dp = (unit * factor).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaled(0.07f)),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Brand wordmark
            Text(
                text = "PREAMBLE",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = (unit * 0.040f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (unit * 0.006f).sp,
                fontFamily = FontFamily.Monospace,
            )

            // Headline + metric + subtitle
            Column(verticalArrangement = Arrangement.spacedBy(scaled(0.02f))) {
                Text(
                    text = content.headline.uppercase(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontSize = (unit * 0.045f).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (unit * 0.004f).sp,
                    fontFamily = FontFamily.Monospace,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(scaled(0.05f)))
                        .background(accent)
                        .padding(horizontal = scaled(0.045f), vertical = scaled(0.035f)),
                ) {
                    Text(
                        text = content.metricLabel,
                        color = Color.Black,
                        fontSize = (unit * 0.13f).sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = (unit * 0.14f).sp,
                        letterSpacing = (-unit * 0.004f).sp,
                    )
                }
                Text(
                    text = content.subtitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = (unit * 0.055f).sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (unit * 0.07f).sp,
                )
            }

            // Footer
            Text(
                text = "preamble.theblankstate.com",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontSize = (unit * 0.035f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (unit * 0.004f).sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* ─────────────────────── share trigger ─────────────────────── */

/**
 * Builds a callback that renders [BrandedShareCard] off-screen and shares it via
 * [ShareableViewModel.requestShare]. The branded card is wrapped in
 * [PreambleTheme] so its colors resolve in the detached capture view.
 */
@Composable
private fun rememberShareTrigger(
    normalizedPreambleId: String?,
    viewModel: ShareableViewModel,
): (ShareableContent) -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    return remember(activity, normalizedPreambleId, viewModel) {
        { content ->
            if (activity != null) {
                viewModel.requestShare(
                    kind = content.kind,
                    content = content,
                    normalizedPreambleId = normalizedPreambleId,
                    render = { c ->
                        ShareableImageRenderer.render(activity) {
                            PreambleTheme { BrandedShareCard(c) }
                        }
                    },
                    share = { bitmap, caption -> ShareSheetLauncher.share(context, bitmap, caption) },
                )
            }
        }
    }
}

/**
 * A Share button that reflects the [ShareableViewModel] state: a spinner while
 * generating (Requirement 10.2) and an inline error line on failure/timeout
 * (Requirements 10.3, 10.4). The share sheet is launched by the ViewModel only
 * on success.
 */
@Composable
private fun ShareControl(
    state: ShareUiState,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onShare,
            enabled = state != ShareUiState.Generating,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
        ) {
            if (state == ShareUiState.Generating) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text("Share", color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }
        if (state == ShareUiState.Error) {
            Text(
                text = "Couldn't create the image. Try again.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Card preview + Share control shared by the three public shareables. */
@Composable
private fun ShareableMomentSurface(
    content: ShareableContent,
    normalizedPreambleId: String?,
    viewModel: ShareableViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val trigger = rememberShareTrigger(normalizedPreambleId, viewModel)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(0.8f),
        ) {
            BrandedShareCard(content)
        }
        ShareControl(state = state, onShare = { trigger(content) })
    }
}

/* ─────────────────────── public shareables ─────────────────────── */

/**
 * Weekly recap shareable (Requirement 7). Builds [ShareableContent] from the
 * supplied [recap] summary projected from `RecapScreen`/`StatsState` data.
 */
@Composable
fun WeeklyRecapShareable(
    recap: WeeklyRecapSummary,
    normalizedPreambleId: String?,
    modifier: Modifier = Modifier,
    viewModel: ShareableViewModel = viewModel(),
) {
    ShareableMomentSurface(
        content = ShareableContentMapper.fromWeeklyRecap(recap),
        normalizedPreambleId = normalizedPreambleId,
        viewModel = viewModel,
        modifier = modifier,
    )
}

/** Streak milestone shareable (Requirement 8); embeds the streak length in days. */
@Composable
fun StreakMilestoneShareable(
    days: Int,
    normalizedPreambleId: String?,
    modifier: Modifier = Modifier,
    viewModel: ShareableViewModel = viewModel(),
) {
    ShareableMomentSurface(
        content = ShareableContentMapper.fromStreak(days),
        normalizedPreambleId = normalizedPreambleId,
        viewModel = viewModel,
        modifier = modifier,
    )
}

/** Perfect day shareable (Requirement 9); embeds the completed-task count. */
@Composable
fun PerfectDayShareable(
    tasksCompleted: Int,
    normalizedPreambleId: String?,
    modifier: Modifier = Modifier,
    viewModel: ShareableViewModel = viewModel(),
) {
    ShareableMomentSurface(
        content = ShareableContentMapper.fromPerfectDay(tasksCompleted),
        normalizedPreambleId = normalizedPreambleId,
        viewModel = viewModel,
        modifier = modifier,
    )
}

/* ─────────────────────── compact control (celebration surface) ─────────────────────── */

/**
 * A compact share chip used where there is no room for the full card preview
 * (e.g. the celebration overlay banner). It renders the branded card off-screen
 * and shares it for the given [content], showing a spinner while generating.
 * Used to offer the Streak/Perfect-day controls on the celebration surface
 * (Requirements 8.1, 9.1).
 */
@Composable
fun ShareMomentChip(
    content: ShareableContent,
    normalizedPreambleId: String?,
    modifier: Modifier = Modifier,
    viewModel: ShareableViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val trigger = rememberShareTrigger(normalizedPreambleId, viewModel)

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(enabled = state != ShareUiState.Generating) { trigger(content) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == ShareUiState.Generating) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                Icons.Default.Share,
                contentDescription = "Share",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = if (state == ShareUiState.Error) "Try again" else "Share",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
