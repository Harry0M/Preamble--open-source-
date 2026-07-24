package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.viewmodel.StatsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * V2 · Capsule grid — bento-style tiles, everything rounded.
 */
@Composable
fun StatsCapsuleScreen(
    statsState: StatsState,
    tweaks: StatsTweaks,
    onOpenTweaks: () -> Unit,
    onOpenRecap: (() -> Unit)? = null,
    recapDayLabel: String = "Sun",
    isRecapDay: Boolean = false,
    range: StatsRange,
    onRangeChange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = tweaks.theme == StatsTheme.DARK
    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f)
    val hair = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    val tile = if (dark) Color(0xFF1A1A1A) else Color(0xFFF4F4F3)
    val surface = if (dark) Color(0xFF0E0E0E) else Color.White
    val accent = tweaks.accent

    val pad: Dp = when (tweaks.density) {
        StatsDensity.COMPACT -> 14.dp
        StatsDensity.COZY -> 18.dp
        StatsDensity.AIRY -> 22.dp
    }
    val gap: Dp = when (tweaks.density) {
        StatsDensity.COMPACT -> 8.dp
        StatsDensity.COZY -> 10.dp
        StatsDensity.AIRY -> 14.dp
    }

    val openCat = LocalStatsDeepDive.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        monthYearLabel(),
                        color = fgMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        "Your month",
                        color = fg,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.8).sp,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onOpenRecap != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(tile)
                                .clickable { onOpenRecap() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Recap · $recapDayLabel",
                                color = if (isRecapDay) fg else fgMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            )
                        }
                    }
                    MiniRangePill(
                        range = range,
                        onChange = onRangeChange,
                        tile = tile,
                        fg = fg,
                        fgMuted = fgMuted,
                        surface = surface
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, hair, RoundedCornerShape(999.dp))
                            .clickable { onOpenTweaks() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Settings, "Settings", tint = fg, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = (pad.value - 4f).dp.coerceAtLeast(8.dp), bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                HeroScoreTile(
                    score = statsState.productivityScore,
                    delta = (statsState.monthOverMonthGrowth * 100f).roundToInt(),
                    best = isBestThisYear(statsState),
                    fg = fg,
                    fgMuted = fgMuted,
                    tile = tile,
                    accent = accent,
                    hair = hair,
                    history = statsState.productivityScoreHistory,
                    onClick = openCat?.let { { it(StatsCategory.SCORE) } }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    StreakAccentTile(
                        streak = statsState.streak,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = openCat?.let { { it(StatsCategory.STREAK) } }
                    )
                    CompletedFgTile(
                        completed = statsState.totalCompleted,
                        total = statsState.totalTasks,
                        fg = fg,
                        dark = dark,
                        surface = surface,
                        modifier = Modifier.weight(1f),
                        onClick = openCat?.let { { it(StatsCategory.SCORE) } }
                    )
                }
                WeekBarsTile(
                    weeklyStats = statsState.weeklyStats,
                    thisWeek = statsState.thisWeekCompleted,
                    fg = fg,
                    fgMuted = fgMuted,
                    tile = tile,
                    dark = dark,
                    onClick = openCat?.let { { it(StatsCategory.WEEKLY) } }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    FocusDonutTile(
                        pct = focusCompletionPct(statsState),
                        hours = statsState.totalFocusHours,
                        sessions = focusSessionCount(statsState),
                        fg = fg,
                        fgMuted = fgMuted,
                        tile = tile,
                        hair = hair,
                        modifier = Modifier.weight(1f),
                        onClick = openCat?.let { { it(StatsCategory.FOCUS) } }
                    )
                    LongestFocusTile(
                        longest = longestSessionValue(statsState),
                        when_ = statsState.bestFocusDay ?: "—",
                        fg = fg,
                        fgMuted = fgMuted,
                        tile = tile,
                        modifier = Modifier.weight(1f),
                        onClick = openCat?.let { { it(StatsCategory.FOCUS) } }
                    )
                }
                HeatmapTile(
                    data = statsState.yearlyHeatmap,
                    dark = dark,
                    accent = accent,
                    surface = surface,
                    fg = fg,
                    fgMuted = fgMuted,
                    tile = tile,
                    onClick = openCat?.let { { it(StatsCategory.CONSISTENCY) } }
                )
                PeakHoursTile(
                    bars = hourlyBars(statsState),
                    fg = fg,
                    fgMuted = fgMuted,
                    tile = tile,
                    dark = dark,
                    insight = peakInsight(statsState, hourlyBars(statsState)),
                    onClick = openCat?.let { { it(StatsCategory.PEAK_HOURS) } }
                )
                TopTagsTile(
                    stats = statsState,
                    fg = fg,
                    dark = dark,
                    tile = tile,
                    surface = surface,
                    onClick = openCat?.let { { it(StatsCategory.TAGS) } }
                )
                MilestonesTile(
                    stats = statsState,
                    fg = fg,
                    dark = dark,
                    tile = tile,
                    accent = accent,
                    fgMuted = fgMuted,
                    onClick = openCat?.let { { it(StatsCategory.BESTS) } }
                )
                WeekReviewTile(
                    stats = statsState,
                    fg = fg,
                    fgMuted = fgMuted,
                    hair = hair,
                    onClick = openCat?.let { { it(StatsCategory.WEEKLY) } }
                )
            }
        }
    }
}

/* ───────── components ───────── */

@Composable
private fun MiniRangePill(
    range: StatsRange,
    onChange: (StatsRange) -> Unit,
    tile: Color,
    fg: Color,
    fgMuted: Color,
    surface: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tile)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        listOf("W" to StatsRange.WEEK, "M" to StatsRange.MONTH, "Y" to StatsRange.YEAR).forEach { (label, r) ->
            val active = r == range
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) fg else Color.Transparent)
                    .clickable { onChange(r) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) surface else fgMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CapsuleTile(
    modifier: Modifier = Modifier,
    bg: Color,
    border: Color? = null,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(22.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding)
    ) { content() }
}

@Composable
private fun Kicker(text: String, color: Color) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun HeroScoreTile(
    score: Int,
    delta: Int,
    best: Boolean,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    accent: Color,
    hair: Color,
    history: List<Pair<String, Int>>,
    onClick: (() -> Unit)? = null,
) {
    CapsuleTile(bg = tile, padding = 20.dp, onClick = onClick) {
        Kicker("Productivity index", fgMuted)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    score.toString(),
                    color = fg,
                    fontSize = 96.sp,
                    lineHeight = 84.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-4).sp,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "/100",
                    color = fgMuted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Sparkline(
                values = sparkValues(history),
                color = fg,
                modifier = Modifier
                    .width(140.dp)
                    .height(44.dp)
                    .padding(bottom = 8.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                text = (if (delta >= 0) "↑ +" else "↓ ") + "${kotlin.math.abs(delta)} pts",
                bg = accent,
                fg = Color.Black,
                bold = true
            )
            Pill(
                text = if (best) "Best this year" else "Steady",
                bg = Color.Transparent,
                fg = fgMuted,
                border = hair
            )
        }
    }
}

@Composable
private fun StreakAccentTile(
    streak: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    CapsuleTile(bg = accent, modifier = modifier, onClick = onClick) {
        Kicker("Streak", Color.Black.copy(alpha = 0.55f))
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                streak.toString(),
                color = Color.Black,
                fontSize = 54.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-2).sp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "days",
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Next milestone · ${((streak / 10) + 1) * 10}",
            color = Color.Black.copy(alpha = 0.7f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CompletedFgTile(
    completed: Int,
    total: Int,
    fg: Color,
    dark: Boolean,
    surface: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val kicker = if (dark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f)
    val sub = if (dark) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)
    val pct = if (total > 0) ((completed.toFloat() / total) * 100).roundToInt() else 0
    CapsuleTile(bg = fg, modifier = modifier, onClick = onClick) {
        Kicker("Completed", kicker)
        Spacer(Modifier.height(24.dp))
        Text(
            completed.toString(),
            color = surface,
            fontSize = 54.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-2).sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "of $total · $pct% rate",
            color = sub,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun WeekBarsTile(
    weeklyStats: List<Pair<String, Float>>,
    thisWeek: Int,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    dark: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val bars = weekBarValues(weeklyStats)
    val todayIdx = todayWeekdayIndex()
    CapsuleTile(bg = tile, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Kicker("This week", fgMuted)
            Text("$thisWeek tasks", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bars.forEachIndexed { i, v ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    val today = i == todayIdx
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(max(6f, 90f * v).dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (today) fg else if (dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f))
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            labels.forEachIndexed { i, l ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        l,
                        color = if (i == todayIdx) fg else fgMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusDonutTile(
    pct: Float,
    hours: Float,
    sessions: Int,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    hair: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    CapsuleTile(bg = tile, modifier = modifier, onClick = onClick) {
        Kicker("Focus", fgMuted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Donut(pct = pct.coerceIn(0f, 1f), color = fg, trackColor = hair, size = 80.dp, stroke = 10.dp)
                Text(
                    "${(pct * 100).roundToInt()}%",
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "${hours.toInt()}h",
                    color = fg,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    "$sessions sessions",
                    color = fgMuted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun LongestFocusTile(
    longest: String,
    when_: String,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    CapsuleTile(bg = tile, modifier = modifier, onClick = onClick) {
        Kicker("Longest focus", fgMuted)
        Spacer(Modifier.height(20.dp))
        Text(
            longest,
            color = fg,
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(when_, color = fgMuted, fontSize = 11.sp)
    }
}

@Composable
private fun HeatmapTile(
    data: Map<String, Float>,
    dark: Boolean,
    accent: Color,
    surface: Color,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    onClick: (() -> Unit)? = null,
) {
    CapsuleTile(bg = tile, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Kicker("Consistency", fgMuted)
            Text("18 weeks", color = fgMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        val weeks = 18
        val levels = buildHeatLevels(data, weeks)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(7) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(weeks) { col ->
                        val lvl = levels[col * 7 + row]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(heatColor(lvl, dark, accent, surface))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeakHoursTile(
    bars: FloatArray,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    dark: Boolean,
    insight: String,
    onClick: (() -> Unit)? = null,
) {
    CapsuleTile(bg = tile, onClick = onClick) {
        Kicker("Peak hours", fgMuted)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            bars.forEach { v ->
                val peak = v > 0.8f
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(max(4f, 70f * v.coerceIn(0.06f, 1f)).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (peak) fg else if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.09f))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("6 AM", "Noon", "6 PM", "12 AM").forEach {
                Text(it, color = fgMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(insight, color = fg, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TopTagsTile(
    stats: StatsState,
    fg: Color,
    dark: Boolean,
    tile: Color,
    surface: Color,
    onClick: (() -> Unit)? = null,
) {
    val items = stats.tagStats
        .sortedByDescending { it.completedCount }
        .take(7)
        .map { it.tag to it.completedCount }
    CapsuleTile(bg = tile, onClick = onClick) {
        Kicker("Top tags", if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f))
        Spacer(Modifier.height(10.dp))
        if (items.isEmpty()) {
            Text(
                "Tag tasks to see your categories here.",
                color = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f),
                fontSize = 12.sp
            )
        } else {
            FlowTags(items = items, fg = fg, dark = dark, surface = surface)
        }
    }
}

@Composable
private fun FlowTags(
    items: List<Pair<String, Int>>,
    fg: Color,
    dark: Boolean,
    surface: Color
) {
    val mutedBg = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
    val mutedCount = if (dark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var line = mutableListOf<Pair<String, Int>>()
        val lines = mutableListOf<List<Pair<String, Int>>>()
        var budget = 0
        val perLine = 2
        var count = 0
        items.forEach {
            line.add(it)
            count++
            if (count == perLine) {
                lines.add(line.toList()); line = mutableListOf(); count = 0
            }
        }
        if (line.isNotEmpty()) lines.add(line.toList())
        lines.forEachIndexed { idx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { i, (name, c) ->
                    val big = idx == 0 && i == 0
                    val bg = if (big) fg else mutedBg
                    val textColor = if (big) surface else fg
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(bg)
                            .padding(
                                horizontal = if (big) 14.dp else 12.dp,
                                vertical = if (big) 8.dp else 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name,
                            color = textColor,
                            fontSize = if (big) 14.sp else 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            c.toString(),
                            color = if (big) surface.copy(alpha = 0.6f) else mutedCount,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestonesTile(
    stats: StatsState,
    fg: Color,
    dark: Boolean,
    tile: Color,
    accent: Color,
    fgMuted: Color,
    onClick: (() -> Unit)? = null,
) {
    val items = milestoneTiles(stats)
    CapsuleTile(bg = tile, onClick = onClick) {
        Kicker("Personal bests · unlocked", fgMuted)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { i, m ->
                val hot = i == 0
                val mBg = if (hot) accent else if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
                val mFg = if (hot) Color.Black else fg
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(mBg)
                        .padding(12.dp)
                        .width(104.dp)
                ) {
                    Text(m.emphasis, color = mFg, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Text(m.label, color = mFg.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    Text(m.sub, color = mFg.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun WeekReviewTile(
    stats: StatsState,
    fg: Color,
    fgMuted: Color,
    hair: Color,
    onClick: (() -> Unit)? = null,
) {
    CapsuleTile(bg = Color.Transparent, border = hair, onClick = onClick) {
        Kicker("Week in review", fgMuted)
        Spacer(Modifier.height(8.dp))
        Text(
            weekReviewLine(stats),
            color = fg,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.2).sp,
        )
    }
}

@Composable
private fun Pill(
    text: String,
    bg: Color,
    fg: Color,
    border: Color? = null,
    bold: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(999.dp)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun Donut(
    pct: Float,
    color: Color,
    trackColor: Color,
    size: Dp,
    stroke: Dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val strokePx = stroke.toPx()
        val diameter = this.size.minDimension
        val topLeft = Offset((this.size.width - diameter) / 2 + strokePx / 2, (this.size.height - diameter) / 2 + strokePx / 2)
        val arcSize = androidx.compose.ui.geometry.Size(diameter - strokePx, diameter - strokePx)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Butt)
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * pct,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun Sparkline(
    values: FloatArray,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).coerceAtLeast(0.0001f)
        val dotR = 4.dp.toPx()
        val inset = dotR + 1.dp.toPx()
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = inset + (i.toFloat() / (values.size - 1)) * w
            val y = inset + h - ((v - minV) / range) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        val lastX = inset + w
        val lastY = inset + h - ((values.last() - minV) / range) * h
        drawCircle(color = color, radius = dotR, center = Offset(lastX, lastY))
    }
}

/* ───────── helpers ───────── */

private fun monthYearLabel(): String {
    return SimpleDateFormat("MMMM yyyy", Locale.US).format(java.util.Date())
}

private fun weekBarValues(weeklyStats: List<Pair<String, Float>>): FloatArray {
    val defaults = floatArrayOf(0.5f, 0.7f, 0.9f, 0.4f, 0.85f, 0.65f, 0.3f)
    if (weeklyStats.isEmpty()) return defaults
    val vals = weeklyStats.map { it.second }.toFloatArray()
    val maxV = (vals.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
    val norm = FloatArray(vals.size) { vals[it] / maxV }
    return if (norm.size >= 7) norm.copyOfRange(0, 7) else defaults
}

private fun todayWeekdayIndex(): Int {
    val c = Calendar.getInstance()
    return ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) // Mon=0..Sun=6
}

private fun sparkValues(history: List<Pair<String, Int>>): FloatArray {
    if (history.isEmpty()) return floatArrayOf(52f, 58f, 51f, 63f, 68f, 72f, 69f, 75f, 81f, 78f, 84f, 87f)
    return history.takeLast(12).map { it.second.toFloat() }.toFloatArray()
}

private fun isBestThisYear(s: StatsState): Boolean {
    val max = s.productivityScoreHistory.maxOfOrNull { it.second } ?: s.productivityScore
    return s.productivityScore >= max
}

private fun focusCompletionPct(s: StatsState): Float {
    return when {
        s.todayTotal > 0 -> s.todayCompleted.toFloat() / s.todayTotal
        s.totalTasks > 0 -> s.totalCompleted.toFloat() / s.totalTasks
        else -> 0.78f
    }
}

private data class Milestone(val emphasis: String, val label: String, val sub: String)

private fun milestoneTiles(s: StatsState): List<Milestone> {
    val longest = max(s.longestStreak, s.streak)
    val mostTasks = s.dailyCompleted.maxByOrNull { it.second }
    val bestFocus = if (s.bestFocusDayMinutes > 0) formatMinutes(s.bestFocusDayMinutes) else "—"
    val completionRate = ((s.completionRate7Day) * 100).roundToInt()
    return listOf(
        Milestone("$longest", "day streak", "best"),
        Milestone("${mostTasks?.second ?: s.todayCompleted}", "tasks/day", mostTasks?.first ?: "recent"),
        Milestone(bestFocus, "deep focus", s.bestFocusDay ?: "—"),
        Milestone("$completionRate%", "week rate", "7-day"),
    )
}

private fun peakInsight(s: StatsState, bars: FloatArray): String {
    val beforeNoon = (0..2).sumOf { (bars[it] * 100).toDouble() }
    val total = bars.sumOf { (it * 100).toDouble() }.coerceAtLeast(1.0)
    val pct = (beforeNoon / total * 100).roundToInt()
    val style = if (pct >= 55) "Morning bird" else if (pct <= 35) "Afternoon surger" else "Balanced day"
    return "$style — $pct% done before noon."
}

private fun weekReviewLine(s: StatsState): String {
    val tasks = s.thisWeekCompleted
    val peak = s.peakDayOfWeek.ifBlank { "weekdays" }
    val delta = (s.weekOverWeekGrowth * 100).roundToInt()
    val deltaTxt = when {
        delta > 0 -> "up $delta% vs last week"
        delta < 0 -> "down ${kotlin.math.abs(delta)}% vs last week"
        else -> "steady vs last week"
    }
    return "$tasks tasks shipped. $peak was your strongest — $deltaTxt."
}
