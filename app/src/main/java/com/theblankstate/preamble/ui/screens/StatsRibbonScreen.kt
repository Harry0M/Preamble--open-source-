package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.viewmodel.StatsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * V3 · Ribbon story — chapters down a dotted spine.
 */
@Composable
fun StatsRibbonScreen(
    statsState: StatsState,
    tweaks: StatsTweaks,
    onOpenTweaks: () -> Unit,
    onOpenRecap: (() -> Unit)? = null,
    recapDayLabel: String = "Sun",
    isRecapDay: Boolean = false,
    range: StatsRange = StatsRange.MONTH,
    onRangeChange: (StatsRange) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val dark = tweaks.theme == StatsTheme.DARK
    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f)
    val hair = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val tile = if (dark) Color(0xFF1A1A1A) else Color(0xFFF4F4F3)
    val surface = if (dark) Color(0xFF0E0E0E) else Color.White
    val accent = tweaks.accent

    val pad: Dp = when (tweaks.density) {
        StatsDensity.COMPACT -> 16.dp
        StatsDensity.COZY -> 20.dp
        StatsDensity.AIRY -> 24.dp
    }
    val gapChapter: Dp = when (tweaks.density) {
        StatsDensity.COMPACT -> 22.dp
        StatsDensity.COZY -> 32.dp
        StatsDensity.AIRY -> 40.dp
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
                    .padding(start = pad, end = pad, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Stats", color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rangeDateSpanLabel(),
                        color = fgMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    if (onOpenRecap != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(tile)
                                .clickable { onOpenRecap() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Recap · $recapDayLabel",
                                color = if (isRecapDay) fg else fgMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, hair, RoundedCornerShape(999.dp))
                            .clickable { onOpenTweaks() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Settings, null, tint = fg, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = pad, vertical = 4.dp)) {
                Text(
                    "THE STORY SO FAR",
                    color = fgMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    heroLine1(statsState),
                    color = fg,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp,
                )
                Text(
                    heroLine2(statsState),
                    color = fgMuted,
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.2).sp,
                )
            }
        }



        item {
            SpineColumn(hair = hair, pad = pad, gap = gapChapter) {
                Chapter(
                    num = 1,
                    title = chapter1Title(statsState),
                    fg = fg, fgMuted = fgMuted, surface = surface,
                    onClick = openCat?.let { { it(StatsCategory.SCORE) } }
                ) {
                    ChapterScoreTile(
                        score = statsState.productivityScore,
                        delta = (statsState.monthOverMonthGrowth * 100f).roundToInt(),
                        history = statsState.productivityScoreHistory,
                        fg = fg,
                        fgMuted = fgMuted,
                        accent = accent,
                        tile = tile,
                        surface = surface,
                    )
                }
                Chapter(
                    num = 2,
                    title = "${statsState.streak}-day streak, unbroken",
                    fg = fg, fgMuted = fgMuted, surface = surface,
                    onClick = openCat?.let { { it(StatsCategory.STREAK) } }
                ) {
                    ChapterStreakTile(
                        streak = statsState.streak,
                        fg = fg,
                        fgMuted = fgMuted,
                        hair = hair,
                        accent = accent,
                    )
                }
                Chapter(
                    num = 3,
                    title = "Consistency",
                    fg = fg, fgMuted = fgMuted, surface = surface,
                    onClick = openCat?.let { { it(StatsCategory.CONSISTENCY) } }
                ) {
                    ChapterHeatmap(
                        data = statsState.yearlyHeatmap,
                        dark = dark,
                        accent = accent,
                        surface = surface,
                        fgMuted = fgMuted,
                    )
                }
                Chapter(
                    num = 4,
                    title = "Your shape of a day",
                    fg = fg, fgMuted = fgMuted, surface = surface,
                    onClick = openCat?.let { { it(StatsCategory.PEAK_HOURS) } }
                ) {
                    ChapterDayShape(
                        bars = hourlyBars(statsState),
                        fg = fg,
                        fgMuted = fgMuted,
                        accent = accent,
                        surface = surface,
                        peakLabel = statsState.peakHourLabel.ifBlank { peakFromBars(hourlyBars(statsState)) }
                    )
                }
                Chapter(
                    num = 5,
                    title = "Where the hours went",
                    fg = fg, fgMuted = fgMuted, surface = surface,
                    onClick = openCat?.let { { it(StatsCategory.TAGS) } }
                ) {
                    ChapterTagList(stats = statsState, fg = fg, fgMuted = fgMuted, hair = hair, surface = surface, dark = dark)
                }
                Chapter(
                    num = 6,
                    title = "Focus · ${focusHoursLabel(statsState.totalFocusHours)}",
                    fg = fg, fgMuted = fgMuted, surface = surface,
                    onClick = openCat?.let { { it(StatsCategory.FOCUS) } }
                ) {
                    ChapterFocus(
                        sessions = focusSessionCount(statsState),
                        longest = longestSessionValue(statsState),
                        fg = fg, fgMuted = fgMuted, tile = tile
                    )
                }
                Chapter(
                    num = 7,
                    title = "Personal bests",
                    fg = fg, fgMuted = fgMuted, surface = surface,
                    onClick = null
                ) {
                    ChapterBests(stats = statsState, fg = fg, fgMuted = fgMuted, hair = hair)
                }
                Chapter(
                    num = 8,
                    title = "The week in one line",
                    fg = fg, fgMuted = fgMuted, surface = surface,
                    onClick = null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(accent)
                            .padding(16.dp)
                    ) {
                        Text(
                            ribbonWeekLine(statsState),
                            color = Color.Black,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.2).sp,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SpineColumn(
    hair: Color,
    pad: Dp,
    gap: Dp,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = pad + 15.dp, top = 0.dp, bottom = 0.dp)
        ) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
            drawLine(
                color = hair,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dash
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = pad),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            content()
        }
    }
}

@Composable
private fun Chapter(
    num: Int,
    title: String,
    fg: Color,
    fgMuted: Color,
    surface: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.TopStart) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(surface)
                    .border(2.dp, fg, RoundedCornerShape(999.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(fg)
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "CH.${"0$num".takeLast(2)}",
                color = fgMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    title,
                    color = fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (onClick != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "→",
                        color = fgMuted,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ChapterScoreTile(
    score: Int,
    delta: Int,
    history: List<Pair<String, Int>>,
    fg: Color,
    fgMuted: Color,
    accent: Color,
    tile: Color,
    surface: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tile)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                score.toString(),
                color = fg,
                fontSize = 72.sp,
                lineHeight = 64.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-3).sp,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .padding(bottom = 4.dp)
            ) {
                Text(
                    (if (delta >= 0) "↑ " else "↓ ") + "${kotlin.math.abs(delta)} PTS",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        AreaChart(
            values = scoreAreaValues(history),
            line = fg,
            fill = fg,
            surface = surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            areaTicks().forEach {
                Text(it, color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun AreaChart(
    values: FloatArray,
    line: Color,
    fill: Color,
    surface: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val dotR = 5.dp.toPx()
        val inset = dotR + 2.dp.toPx()
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        val dataMin = values.min()
        val dataMax = values.max()
        val pad = ((dataMax - dataMin) * 0.15f).coerceAtLeast(4f)
        val minV = (dataMin - pad).coerceAtLeast(0f)
        val maxV = dataMax + pad
        val range = (maxV - minV).coerceAtLeast(0.0001f)
        val path = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = inset + (i.toFloat() / (values.size - 1)) * w
            val y = inset + h - ((v - minV) / range) * h
            if (i == 0) {
                path.moveTo(x, y); area.moveTo(x, y)
            } else {
                path.lineTo(x, y); area.lineTo(x, y)
            }
        }
        val bottom = inset + h
        area.lineTo(inset + w, bottom); area.lineTo(inset, bottom); area.close()
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                0f to fill.copy(alpha = 0.14f),
                1f to fill.copy(alpha = 0f)
            )
        )
        drawPath(path = path, color = line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        val lastX = inset + w
        val lastY = inset + h - ((values.last() - minV) / range) * h
        drawCircle(color = surface, radius = dotR, center = Offset(lastX, lastY))
        drawCircle(color = line, radius = dotR, center = Offset(lastX, lastY), style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun ChapterStreakTile(
    streak: Int,
    fg: Color,
    fgMuted: Color,
    hair: Color,
    accent: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                streak.toString(),
                color = Color.Black,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                lineHeight = 32.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "DAYS",
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            val startDate = streakStartLabel(streak)
            val nextIn = ((streak / 10) + 1) * 10 - streak
            Text(
                "Started $startDate. Next milestone in $nextIn days.",
                color = fgMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(30) { i ->
                    val done = i < streak.coerceAtMost(30)
                    val today = i == streak.coerceAtMost(30)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    done -> fg
                                    today -> fgMuted
                                    else -> hair
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterHeatmap(
    data: Map<String, Float>,
    dark: Boolean,
    accent: Color,
    surface: Color,
    fgMuted: Color
) {
    val weeks = 18
    val levels = buildHeatLevels(data, weeks)
    val active = levels.count { it > 0 }
    val pct = ((active.toFloat() / levels.size) * 100).roundToInt()
    Column {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(7) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(weeks) { col ->
                        val lvl = levels[col * 7 + row]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(heatColor(lvl, dark, accent, surface))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("18W AGO", color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("$pct% ACTIVE DAYS", color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text("TODAY", color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ChapterDayShape(
    bars: FloatArray,
    fg: Color,
    fgMuted: Color,
    accent: Color,
    surface: Color,
    peakLabel: String
) {
    val peakIdx = bars.indices.maxByOrNull { bars[it] } ?: 0
    Column {
        BoxWithConstraints(modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)) {
            val totalW = constraints.maxWidth.toFloat()
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (bars.size < 2) return@Canvas
                val dotR = 5.dp.toPx()
                val inset = dotR + 2.dp.toPx()
                val w = size.width - inset * 2
                val h = size.height - inset * 2
                val line = Path()
                val area = Path()
                bars.forEachIndexed { i, v ->
                    val x = inset + (i.toFloat() / (bars.size - 1)) * w
                    val y = inset + h - v.coerceIn(0.04f, 1f) * h
                    if (i == 0) {
                        line.moveTo(x, y); area.moveTo(x, y)
                    } else {
                        line.lineTo(x, y); area.lineTo(x, y)
                    }
                }
                val bottom = inset + h
                area.lineTo(inset + w, bottom); area.lineTo(inset, bottom); area.close()
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(0f to accent.copy(alpha = 1f), 1f to accent.copy(alpha = 0.2f))
                )
                drawPath(path = line, color = fg, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                val px = inset + (peakIdx.toFloat() / (bars.size - 1)) * w
                val py = inset + h - bars[peakIdx].coerceIn(0.04f, 1f) * h
                drawCircle(color = fg, radius = dotR, center = Offset(px, py))
                val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()), 0f)
                drawLine(
                    color = fg,
                    start = Offset(px, inset),
                    end = Offset(px, py - dotR),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("6A", "10A", "2P", "6P", "10P", "2A").forEach {
                Text(it, color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Peak at $peakLabel, with a second push after dinner. Flat afternoons — consider a reset walk.",
            color = fgMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun ChapterTagList(
    stats: StatsState,
    fg: Color,
    fgMuted: Color,
    hair: Color,
    surface: Color,
    dark: Boolean
) {
    val items = stats.tagStats
        .sortedByDescending { it.completedCount }
        .take(5)
    Column {
        items.forEachIndexed { i, t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "0${i + 1}".takeLast(2),
                    color = fgMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.width(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    t.tag,
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${t.completedCount} tasks",
                    color = fgMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(8.dp))
                val chipBg = if (i == 0) fg else if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
                val chipFg = if (i == 0) surface else fg
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(chipBg)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        estimateTagHours(t.completedCount),
                        color = chipFg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            if (i < items.lastIndex) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
            }
        }
        if (items.isEmpty()) {
            Text("Tag tasks to see time allocation.", color = fgMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ChapterFocus(
    sessions: Int,
    longest: String,
    fg: Color,
    fgMuted: Color,
    tile: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(tile)
                .padding(14.dp)
        ) {
            Text(
                "FOCUS SESSIONS",
                color = fgMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                sessions.toString(),
                color = fg,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(tile)
                .padding(14.dp)
        ) {
            Text(
                "LONGEST",
                color = fgMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                longest,
                color = fg,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
            )
        }
    }
}

@Composable
private fun ChapterBests(
    stats: StatsState,
    fg: Color,
    fgMuted: Color,
    hair: Color
) {
    val items = ribbonBests(stats)
    Column {
        items.forEachIndexed { i, m ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(m.label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        m.when_.uppercase(),
                        color = fgMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        m.value,
                        color = fg,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(m.unit, color = fgMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
        }
    }
}

/* ───────── data & helpers ───────── */

private data class RibbonBest(val label: String, val value: String, val unit: String, val when_: String)

private fun ribbonBests(s: StatsState): List<RibbonBest> {
    val longest = max(s.longestStreak, s.streak)
    val mostTasks = s.dailyCompleted.maxByOrNull { it.second }
    val bestFocusMin = s.bestFocusDayMinutes
    val focusLabel = if (bestFocusMin > 0) formatMinutes(bestFocusMin) else "—"
    return listOf(
        RibbonBest("Longest streak", "$longest", "days", "all time"),
        RibbonBest("Most in one day", "${mostTasks?.second ?: s.todayCompleted}", "tasks", mostTasks?.first ?: "recent"),
        RibbonBest("Best focus block", focusLabel, "min", s.bestFocusDay ?: "—"),
        RibbonBest("Weekly consistency", "${s.weeklyConsistencyDays}/7", "days", "this week"),
    )
}

private fun rangeDateSpanLabel(): String {
    val cal = Calendar.getInstance()
    val end = SimpleDateFormat("MMM dd", Locale.US).format(cal.time).uppercase()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val start = SimpleDateFormat("MMM dd", Locale.US).format(cal.time).uppercase()
    return "$start → $end"
}

private fun heroLine1(s: StatsState): String {
    return when {
        s.productivityScore >= 85 -> "A strong month."
        s.productivityScore >= 70 -> "A steady month."
        s.productivityScore >= 50 -> "Finding your rhythm."
        else -> "A fresh start."
    }
}

private fun heroLine2(s: StatsState): String {
    return when {
        s.productivityScore >= 85 -> "You did the work — and it added up."
        s.productivityScore >= 70 -> "You did the work — in smaller, tighter pieces."
        s.productivityScore >= 50 -> "Momentum is starting to build."
        else -> "Every day from here counts."
    }
}

private fun chapter1Title(s: StatsState): String = "Productivity · ${s.productivityScore}/100"

private fun scoreAreaValues(history: List<Pair<String, Int>>): FloatArray {
    if (history.isEmpty()) return floatArrayOf(52f, 58f, 51f, 63f, 68f, 72f, 69f, 75f, 81f, 78f, 84f, 87f)
    return history.takeLast(12).map { it.second.toFloat() }.toFloatArray()
}

private fun areaTicks(): List<String> {
    val fmt = SimpleDateFormat("MMM ''yy", Locale.US)
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, -11)
    val a = fmt.format(cal.time).uppercase()
    cal.add(Calendar.MONTH, 5)
    val b = fmt.format(cal.time).uppercase()
    cal.add(Calendar.MONTH, 6)
    val c = fmt.format(cal.time).uppercase()
    return listOf(a, b, c)
}

private fun streakStartLabel(streak: Int): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -(streak - 1).coerceAtLeast(0))
    return SimpleDateFormat("MMM d", Locale.US).format(cal.time)
}

private fun estimateTagHours(count: Int): String {
    val minutes = count * 13
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${"%02d".format(m)}m" else "${m}m"
}

private fun ribbonWeekLine(s: StatsState): String {
    val tasks = s.thisWeekCompleted
    val streak = s.streak
    val fh = focusHoursLabel(s.totalFocusHours)
    return "Good week. $tasks tasks, $streak days in a row, $fh of focus — and mornings clearly working for you."
}
