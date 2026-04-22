package com.theblankstate.preamble.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.viewmodel.StatsState
import kotlinx.coroutines.launch
import java.util.Calendar

private const val SLIDE_MS = 3800

private data class WrappedSlide(
    val kicker: String,
    val big: String,
    val sub: String,
)

private fun buildSlides(
    stats: StatsState,
    percentile: Int,
    name: String?,
): List<WrappedSlide> {
    val weekNum = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    val greeting = name?.takeIf { it.isNotBlank() }?.let { "$it," } ?: "Your"

    val tasksThisWeek = stats.thisWeekCompleted
    val growthPct = (stats.weekOverWeekGrowth * 100).toInt()
    val growthLabel = when {
        growthPct > 0 -> "+$growthPct% vs last week"
        growthPct < 0 -> "$growthPct% vs last week"
        else -> "Steady as last week"
    }

    val bestDay = stats.peakDayOfWeek.ifBlank { "—" }
    val topTag = stats.mostActiveTag ?: "uncategorized"

    val weekFocus = stats.weeklyFocusData.sumOf { it.second }
    val focusHours = weekFocus / 60
    val focusMins = weekFocus % 60
    val focusBig = if (focusHours > 0) "${focusHours}h ${focusMins}m" else "${focusMins}m"

    val streak = stats.streak
    val streakLongest = stats.longestStreak

    val score = if (stats.productivityScore > 0) stats.productivityScore else 70
    val pct = percentile.coerceIn(1, 99)

    return listOf(
        WrappedSlide(
            kicker = "WEEK $weekNum",
            big = "$greeting\nwrapped.",
            sub = "Tap anywhere to advance.",
        ),
        WrappedSlide(
            kicker = "TASKS CRUSHED",
            big = tasksThisWeek.toString(),
            sub = growthLabel,
        ),
        WrappedSlide(
            kicker = "YOUR PEAK",
            big = bestDay,
            sub = "Most active tag: #$topTag",
        ),
        WrappedSlide(
            kicker = "DEEP FOCUS",
            big = focusBig,
            sub = if (weekFocus > 0) "of pomodoro this week" else "No focus sessions yet.",
        ),
        WrappedSlide(
            kicker = "STREAK",
            big = "$streak days",
            sub = if (streakLongest > streak) "Personal best: $streakLongest" else "Longest yet. Keep going.",
        ),
        WrappedSlide(
            kicker = "PRODUCTIVITY SCORE",
            big = score.toString(),
            sub = "Top $pct% of planners.",
        ),
    )
}

@Composable
fun WrappedScreen(
    statsState: StatsState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val profile = remember { UserProfileStore.load(ctx) }
    val slides = remember(statsState) { buildSlides(statsState, profile.percentile, profile.name) }

    var index by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    LaunchedEffect(index) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(SLIDE_MS, easing = LinearEasing)
        )
        if (index < slides.lastIndex) {
            index++
        } else {
            onDismiss()
        }
    }

    val slide = slides[index]
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.background
    val fg = scheme.onBackground
    val fgMuted = fg.copy(alpha = 0.55f)
    val trackBg = fg.copy(alpha = 0.12f)
    val chipBg = fg.copy(alpha = 0.06f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .pointerInput(slides.size) {
                detectTapGestures(
                    onTap = { pos ->
                        val w = size.width.toFloat()
                        if (pos.x < w * 0.35f) {
                            if (index > 0) index -= 1
                            else scope.launch { progress.snapTo(0f) }
                        } else {
                            if (index < slides.lastIndex) index += 1 else onDismiss()
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                slides.forEachIndexed { i, _ ->
                    val fill = when {
                        i < index -> 1f
                        i == index -> progress.value
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(trackBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fill)
                                .height(2.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(fg)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    slide.kicker,
                    color = fgMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(chipBg)
                        .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(420)) togetherWith fadeOut(tween(220)))
                },
                label = "slide",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) { i ->
                val s = slides[i]
                Column {
                    Text(
                        (i + 1).toString().padStart(2, '0'),
                        color = fgMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        s.big,
                        color = fg,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 66.sp,
                        letterSpacing = (-2.2).sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        s.sub,
                        color = fgMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (index == slides.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(14.dp))
                            .background(fg)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    val text = buildShareText(slides)
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    ctx.startActivity(
                                        Intent.createChooser(send, "Share your week").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                })
                            }
                            .padding(horizontal = 26.dp, vertical = 14.dp)
                    ) {
                        Text(
                            "Share my week",
                            color = surface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }
            }

            Text(
                "PREAMBLE · WEEK WRAPPED",
                color = fgMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun buildShareText(slides: List<WrappedSlide>): String {
    val tasks = slides.getOrNull(1)?.big ?: "—"
    val peak = slides.getOrNull(2)?.big ?: "—"
    val focus = slides.getOrNull(3)?.big ?: "—"
    val streak = slides.getOrNull(4)?.big ?: "—"
    val score = slides.getOrNull(5)?.big ?: "—"
    return buildString {
        appendLine("My week on Preamble:")
        appendLine("• $tasks tasks crushed")
        appendLine("• Peak day: $peak")
        appendLine("• Focus: $focus")
        appendLine("• Streak: $streak")
        appendLine("• Productivity score: $score")
        append("Get Preamble.")
    }
}
