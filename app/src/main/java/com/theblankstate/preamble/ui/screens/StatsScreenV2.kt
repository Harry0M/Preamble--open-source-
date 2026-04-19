package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

enum class StatsRange { WEEK, MONTH, YEAR, ALL }
enum class StatsDensity { COMPACT, COZY, AIRY }
enum class StatsTheme { LIGHT, DARK }
enum class StatsVariant { EDITORIAL, CAPSULE, RIBBON }

data class StatsTweaks(
    val variant: StatsVariant = StatsVariant.EDITORIAL,
    val accent: Color = AccentPalette[0].color,
    val theme: StatsTheme = StatsTheme.LIGHT,
    val density: StatsDensity = StatsDensity.COMPACT,
)

data class AccentSwatch(val id: String, val color: Color, val label: String)

val AccentPalette = listOf(
    AccentSwatch("sand", Color(0xFFF5EBE0), "Warm sand"),
    AccentSwatch("sage", Color(0xFFE8EDDF), "Pale sage"),
    AccentSwatch("rose", Color(0xFFFCE4E4), "Pale rose"),
    AccentSwatch("lilac", Color(0xFFE4E6F1), "Pale lilac"),
    AccentSwatch("butter", Color(0xFFFFF3C4), "Pale butter"),
    AccentSwatch("sky", Color(0xFFD6EAF8), "Pale sky"),
)

@Composable
fun StatsEditorialScreen(
    statsState: StatsState,
    tweaks: StatsTweaks,
    onOpenTweaks: () -> Unit,
    range: StatsRange,
    onRangeChange: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = tweaks.theme == StatsTheme.DARK
    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f)
    val hair = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val chipBg = if (dark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    val surface = if (dark) Color(0xFF0E0E0E) else Color.White
    val accent = tweaks.accent

    val pad: Dp = when (tweaks.density) {
        StatsDensity.COMPACT -> 16.dp
        StatsDensity.COZY -> 20.dp
        StatsDensity.AIRY -> 28.dp
    }
    val gap: Dp = when (tweaks.density) {
        StatsDensity.COMPACT -> 20.dp
        StatsDensity.COZY -> 28.dp
        StatsDensity.AIRY -> 36.dp
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pad, end = pad, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Stats",
                    color = fg,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, hair, RoundedCornerShape(999.dp))
                        .clickable { onOpenTweaks() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Tweaks",
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        item {
            RangeCapsule(
                range = range,
                onChange = onRangeChange,
                chipBg = chipBg,
                dark = dark,
                fgMuted = fgMuted,
                modifier = Modifier.padding(start = pad, end = pad, top = 4.dp)
            )
        }

        item {
            SectionBlock(kicker = heroKicker(range), pad = pad, gap = gap, fgMuted = fgMuted) {
                val animatedScore by animateIntAsState(
                    targetValue = statsState.productivityScore.coerceIn(0, 100),
                    animationSpec = tween(durationMillis = 900),
                    label = "score"
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        animatedScore.toString(),
                        color = fg,
                        fontSize = 132.sp,
                        lineHeight = 112.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-6).sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "/100",
                        color = fgMuted,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.4).sp,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val delta = (statsState.monthOverMonthGrowth * 100f).roundToInt()
                    TrendPill(delta = delta, accent = accent)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        bestMonthBlurb(statsState),
                        color = fgMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        item { Hairline(hair) }

        item {
            SectionBlock(kicker = "Streak", pad = pad, gap = gap, fgMuted = fgMuted) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        statsState.streak.toString(),
                        color = fg,
                        fontSize = 56.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "days in a row",
                        color = fgMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                StreakLadder(streak = statsState.streak, fg = fg, hair = hair)
                Spacer(Modifier.height(8.dp))
                val labels = streakLabels(statsState.streak)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(labels.first, color = fgMuted, fontSize = 11.sp, letterSpacing = 0.3.sp)
                    Text(labels.second, color = fgMuted, fontSize = 11.sp, letterSpacing = 0.3.sp)
                    Text(labels.third, color = fgMuted, fontSize = 11.sp, letterSpacing = 0.3.sp)
                }
            }
        }

        item { Hairline(hair) }

        item {
            SectionBlock(
                kicker = "Consistency · last 18 weeks",
                pad = pad, gap = gap, fgMuted = fgMuted
            ) {
                HeatmapGrid(
                    data = statsState.yearlyHeatmap,
                    dark = dark,
                    accent = accent,
                    surface = surface
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Less", color = fgMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        (0..4).forEach { l ->
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(heatColor(l, dark, accent, surface))
                            )
                        }
                    }
                    Text("More", color = fgMuted, fontSize = 11.sp)
                }
            }
        }

        item { Hairline(hair) }

        item {
            SectionBlock(
                kicker = "When you get things done",
                pad = pad, gap = gap, fgMuted = fgMuted
            ) {
                val bars = hourlyBars(statsState)
                TimeOfDayChart(bars = bars, fg = fg, chipBg = chipBg)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("6", "8", "10", "12", "2", "4", "6", "8", "10", "12", "2", "4").forEach { t ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(t, color = fgMuted, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                InsightCallout(
                    peakLabel = statsState.peakHourLabel.ifBlank { peakFromBars(bars) },
                    accent = accent
                )
            }
        }

        item { Hairline(hair) }

        item {
            SectionBlock(kicker = "Where the time went", pad = pad, gap = gap, fgMuted = fgMuted) {
                val tops = tagLeaderboard(statsState)
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    tops.forEachIndexed { i, t ->
                        TagLeaderboardRow(
                            rank = i + 1,
                            name = t.name,
                            count = t.count,
                            pct = t.pct,
                            fg = fg,
                            fgMuted = fgMuted,
                            chipBg = chipBg,
                            accent = accent,
                            leader = i == 0
                        )
                    }
                    if (tops.isEmpty()) {
                        Text(
                            "Tag some tasks to see where time goes.",
                            color = fgMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item { Hairline(hair) }

        item {
            SectionBlock(kicker = "Focus time", pad = pad, gap = gap, fgMuted = fgMuted) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FocusCard(
                        kicker = "Pomodoros",
                        value = pomodoroCount(statsState).toString(),
                        sub = "${focusHoursLabel(statsState.totalFocusHours)} focused",
                        fg = fg,
                        fgMuted = fgMuted,
                        hair = hair,
                        modifier = Modifier.weight(1f),
                        filled = false,
                        accent = accent
                    )
                    FocusCard(
                        kicker = "Longest session",
                        value = longestSessionValue(statsState),
                        sub = statsState.bestFocusDay ?: "—",
                        fg = fg,
                        fgMuted = fgMuted,
                        hair = hair,
                        modifier = Modifier.weight(1f),
                        filled = true,
                        accent = accent
                    )
                }
            }
        }

        item { Hairline(hair) }

        item {
            SectionBlock(kicker = "Personal bests", pad = pad, gap = gap, fgMuted = fgMuted) {
                val bests = personalBests(statsState)
                Column {
                    bests.forEachIndexed { i, b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(b.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(2.dp))
                                if (b.meta.isNotBlank()) {
                                    Text(b.meta, color = fgMuted, fontSize = 11.sp)
                                }
                            }
                            Text(
                                b.value,
                                color = fg,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                            )
                        }
                        if (i < bests.lastIndex) Hairline(hair)
                    }
                }
            }
        }

        item {
            SectionBlock(kicker = "This week in one line", pad = pad, gap = gap, fgMuted = fgMuted) {
                WeeklyNarrative(
                    tasks = statsState.thisWeekCompleted,
                    streak = statsState.streak,
                    focusHours = statsState.totalFocusHours,
                    fg = fg,
                    accent = accent
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/* ───────── sub-components ───────── */

@Composable
private fun SectionBlock(
    kicker: String?,
    pad: Dp,
    gap: Dp,
    fgMuted: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = pad, vertical = gap)
    ) {
        if (kicker != null) {
            Text(
                kicker.uppercase(),
                color = fgMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(14.dp))
        }
        content()
    }
}

@Composable
private fun Hairline(color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
private fun RangeCapsule(
    range: StatsRange,
    onChange: (StatsRange) -> Unit,
    chipBg: Color,
    dark: Boolean,
    fgMuted: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(chipBg)
            .padding(4.dp),
    ) {
        StatsRange.values().forEach { r ->
            val active = r == range
            val bg by animateColorAsState(
                if (active) (if (dark) Color.White else Color.Black) else Color.Transparent,
                label = "seg-bg"
            )
            val fgColor = if (active) (if (dark) Color.Black else Color.White) else fgMuted
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .clickable { onChange(r) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    r.name.lowercase().replaceFirstChar { it.titlecase() },
                    color = fgColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TrendPill(delta: Int, accent: Color) {
    val sign = if (delta >= 0) "+" else ""
    val label = "$sign$delta vs ${prevMonthShort()}"
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (delta >= 0) "▲" else "▼",
            color = Color.Black,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun StreakLadder(streak: Int, fg: Color, hair: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(30) { i ->
            val done = i < streak.coerceAtMost(30)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (done) Modifier.background(fg)
                        else Modifier.border(1.dp, hair, RoundedCornerShape(6.dp))
                    )
            )
        }
    }
}

@Composable
private fun HeatmapGrid(
    data: Map<String, Float>,
    dark: Boolean,
    accent: Color,
    surface: Color
) {
    val weeks = 18
    val levels = remember(data) { buildHeatLevels(data, weeks) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(7) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(weeks) { col ->
                    val lvl = levels[col * 7 + row]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(heatColor(lvl, dark, accent, surface))
                    )
                }
            }
        }
    }
}

internal fun buildHeatLevels(data: Map<String, Float>, weeks: Int): IntArray {
    val cells = weeks * 7
    val out = IntArray(cells)
    if (data.isEmpty()) {
        for (i in 0 until cells) {
            val v = kotlin.math.sin(i * 0.41) * 0.5 + kotlin.math.cos(i * 0.17) * 0.5
            val n = ((v + 1) / 2).coerceIn(0.0, 1.0)
            out[i] = when {
                n > 0.78 -> 4; n > 0.58 -> 3; n > 0.35 -> 2; n > 0.15 -> 1; else -> 0
            }
        }
        return out
    }
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -(cells - 1))
    for (i in 0 until cells) {
        val key = fmt.format(cal.time)
        val v = data[key] ?: 0f
        out[i] = when {
            v >= 0.78f -> 4
            v >= 0.58f -> 3
            v >= 0.35f -> 2
            v >= 0.15f -> 1
            else -> 0
        }
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return out
}

internal fun heatColor(lvl: Int, dark: Boolean, accent: Color, surface: Color): Color {
    if (lvl == 0) return if (dark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)
    val alphas = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    return lerp(surface, accent, alphas[lvl])
}

@Composable
private fun TimeOfDayChart(bars: FloatArray, fg: Color, chipBg: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        bars.forEach { v ->
            val peak = v > 0.8f
            val heightFrac = v.coerceIn(0.04f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((120f * heightFrac).dp)
                        .clip(
                            if (peak) RoundedCornerShape(
                                topStart = 999.dp,
                                topEnd = 999.dp,
                                bottomStart = 4.dp,
                                bottomEnd = 4.dp
                            ) else RoundedCornerShape(4.dp)
                        )
                        .background(if (peak) fg else chipBg)
                )
            }
        }
    }
}

@Composable
private fun InsightCallout(peakLabel: String, accent: Color) {
    val text = if (peakLabel.isBlank())
        "Keep logging — your rhythm will show up here."
    else
        "You're sharpest around $peakLabel. Most of your top days started with a morning block."
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            color = Color.Black,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private data class TagRow(val name: String, val count: Int, val pct: Int)

private fun tagLeaderboard(s: StatsState): List<TagRow> {
    if (s.tagStats.isEmpty()) return emptyList()
    val total = s.tagStats.sumOf { it.completedCount }.coerceAtLeast(1)
    return s.tagStats
        .sortedByDescending { it.completedCount }
        .take(5)
        .map { TagRow(it.tag, it.completedCount, ((it.completedCount.toFloat() / total) * 100).roundToInt()) }
}

@Composable
private fun TagLeaderboardRow(
    rank: Int,
    name: String,
    count: Int,
    pct: Int,
    fg: Color,
    fgMuted: Color,
    chipBg: Color,
    accent: Color,
    leader: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "0$rank".takeLast(2),
                    color = fgMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.width(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(name, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "$count tasks · $pct%",
                color = fgMuted,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(chipBg)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(min(pct * 2.5f / 100f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (leader) fg else accent)
            )
        }
    }
}

@Composable
private fun FocusCard(
    kicker: String,
    value: String,
    sub: String,
    fg: Color,
    fgMuted: Color,
    hair: Color,
    modifier: Modifier = Modifier,
    filled: Boolean,
    accent: Color
) {
    val bg = if (filled) accent else Color.Transparent
    val textOn = if (filled) Color.Black else fg
    val subText = if (filled) Color.Black.copy(alpha = 0.7f) else fgMuted
    val kickerColor = if (filled) Color.Black.copy(alpha = 0.7f) else fgMuted
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(if (!filled) Modifier.border(1.dp, hair, RoundedCornerShape(18.dp)) else Modifier)
            .padding(16.dp)
    ) {
        Text(
            kicker.uppercase(),
            color = kickerColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            color = textOn,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(sub, color = subText, fontSize = 12.sp)
    }
}

private data class Best(val label: String, val value: String, val meta: String)

private fun personalBests(s: StatsState): List<Best> {
    val longestStreak = max(s.longestStreak, s.streak)
    val bestFocus = if (s.bestFocusDayMinutes > 0)
        formatMinutes(s.bestFocusDayMinutes) else "—"
    val mostTasks = s.dailyCompleted.maxByOrNull { it.second }
    return listOf(
        Best("Longest streak", "$longestStreak days", ""),
        Best("Most tasks in a day", "${mostTasks?.second ?: s.todayCompleted} tasks", mostTasks?.first ?: ""),
        Best("Longest focus", bestFocus, s.bestFocusDay ?: ""),
    )
}

internal fun formatMinutes(m: Int): String {
    val h = m / 60
    val r = m % 60
    return if (h > 0) "${h}h ${"%02d".format(r)}m" else "${r}m"
}

@Composable
private fun WeeklyNarrative(
    tasks: Int,
    streak: Int,
    focusHours: Float,
    fg: Color,
    accent: Color
) {
    val fh = focusHoursLabel(focusHours)
    Column {
        Text(
            "You shipped",
            color = fg,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.4).sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    "$tasks tasks",
                    color = Color.Black,
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                ", kept a",
                color = fg,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$streak-day streak, and focused for $fh — mostly before noon.",
            color = fg,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.4).sp,
        )
    }
}

/* ───────── helpers ───────── */

internal fun focusHoursLabel(h: Float): String {
    val totalMin = (h * 60f).roundToInt()
    val hh = totalMin / 60
    val mm = totalMin % 60
    return if (hh > 0) "${hh}h ${"%02d".format(mm)}m" else "${mm}m"
}

internal fun pomodoroCount(s: StatsState): Int {
    val fromFocus = (s.totalFocusHours * 60f / 25f).roundToInt()
    return max(s.todayPomodoroSessions, fromFocus)
}

internal fun longestSessionValue(s: StatsState): String {
    val m = s.bestFocusDayMinutes
    if (m <= 0) return "—"
    return formatMinutes(m)
}

private fun heroKicker(range: StatsRange): String {
    val monthFmt = SimpleDateFormat("MMMM", Locale.US)
    val now = monthFmt.format(java.util.Date())
    return when (range) {
        StatsRange.WEEK -> "Productivity index · this week"
        StatsRange.MONTH -> "Productivity index · $now"
        StatsRange.YEAR -> "Productivity index · this year"
        StatsRange.ALL -> "Productivity index · all time"
    }
}

internal fun prevMonthShort(): String {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MONTH, -1)
    return SimpleDateFormat("MMM", Locale.US).format(cal.time)
}

private fun streakLabels(streak: Int): Triple<String, String, String> {
    val fmt = SimpleDateFormat("MMM d", Locale.US)
    val cal = Calendar.getInstance()
    val todayLabel = "Today · ${fmt.format(cal.time)}"
    cal.add(Calendar.DAY_OF_YEAR, -(streak - 1).coerceAtLeast(0))
    val startLabel = fmt.format(cal.time)
    val next = ((streak / 10) + 1) * 10
    return Triple(startLabel, todayLabel, "next: $next")
}

private fun bestMonthBlurb(s: StatsState): String {
    val history = s.productivityScoreHistory
    if (history.isEmpty()) return "Steady progress"
    val max = history.maxOfOrNull { it.second } ?: s.productivityScore
    return if (s.productivityScore >= max) "Best month this year" else "Keep climbing"
}

internal fun hourlyBars(s: StatsState): FloatArray {
    val raw = s.hourlyDistribution
    if (raw.isEmpty()) return floatArrayOf(
        0.15f, 0.35f, 0.62f, 0.88f, 0.72f, 0.45f,
        0.58f, 0.82f, 0.55f, 0.28f, 0.18f, 0.12f
    )
    val buckets = IntArray(12)
    raw.forEach { (label, count) ->
        val hour = label.takeWhile { it.isDigit() }.toIntOrNull() ?: return@forEach
        val idx = hourToBucket(hour)
        if (idx in 0..11) buckets[idx] += count
    }
    val maxV = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)
    return FloatArray(12) { buckets[it].toFloat() / maxV }
}

private fun hourToBucket(hour: Int): Int {
    val shifted = (hour - 6 + 24) % 24
    return shifted / 2
}

internal fun peakFromBars(bars: FloatArray): String {
    val idx = bars.indices.maxByOrNull { bars[it] } ?: return ""
    val startHour = (6 + idx * 2) % 24
    val endHour = (startHour + 2) % 24
    return "${formatHour(startHour)} and ${formatHour(endHour)}"
}

internal fun formatHour(h: Int): String {
    val period = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12 $period"
}
