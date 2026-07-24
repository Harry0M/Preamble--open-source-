package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.viewmodel.StatsState
import kotlin.math.abs
import kotlin.math.roundToInt

enum class StatsCategory {
    SCORE, STREAK, CONSISTENCY, PEAK_HOURS, TAGS, FOCUS,
    BESTS, WEEKLY, MOMENTUM, ROLLOVER, FORECAST, KARMA,
    INSIGHTS, PRIORITY, TASK_TYPE, WEEKDAY_WEEKEND
}

enum class DeepRange(val days: Int, val label: String) {
    D7(7, "7D"), D30(30, "30D"), D90(90, "90D"), D365(365, "1Y")
}

@Composable
fun StatsDeepDiveScreen(
    category: StatsCategory,
    statsState: StatsState,
    tweaks: StatsTweaks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = tweaks.theme == StatsTheme.DARK
    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f)
    val hair = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val tile = if (dark) Color(0xFF1A1A1A) else Color(0xFFF4F4F3)
    val surface = if (dark) Color(0xFF0E0E0E) else Color.White
    val accent = tweaks.accent
    val header = deepDiveHeader(category, statsState)
    var deepRange by rememberSaveable { mutableStateOf(DeepRange.D30) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = fg)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "Stats",
                    color = fg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    header.kicker.uppercase(),
                    color = fgMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    header.title,
                    color = fg,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.2).sp,
                )
                if (header.sub.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        header.sub,
                        color = fgMuted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(18.dp)) }

        item {
            CompareCurrVsPrev(
                category = category,
                statsState = statsState,
                fg = fg,
                fgMuted = fgMuted,
                tile = tile,
                accent = accent,
                hair = hair,
            )
        }

        item { Spacer(Modifier.height(20.dp)) }

        item {
            RangePicker(
                selected = deepRange,
                onSelect = { deepRange = it },
                fg = fg,
                fgMuted = fgMuted,
                tile = tile,
                dark = dark,
            )
        }

        item { Spacer(Modifier.height(12.dp)) }

        item {
            RangeCompareStrip(
                category = category,
                statsState = statsState,
                selected = deepRange,
                fg = fg,
                fgMuted = fgMuted,
                tile = tile,
                accent = accent,
            )
        }

        item { Spacer(Modifier.height(20.dp)) }

        item {
            FormulaCard(
                category = category,
                fg = fg,
                fgMuted = fgMuted,
                tile = tile,
                hair = hair,
            )
        }

        item { Spacer(Modifier.height(20.dp)) }

        item {
            CategoryBody(
                category = category,
                statsState = statsState,
                fg = fg,
                fgMuted = fgMuted,
                tile = tile,
                accent = accent,
                hair = hair,
                surface = surface,
                dark = dark,
            )
        }
        item { Spacer(Modifier.height(60.dp)) }
    }
}

private data class DeepHeader(val kicker: String, val title: String, val sub: String)

private fun deepDiveHeader(c: StatsCategory, s: StatsState): DeepHeader = when (c) {
    StatsCategory.SCORE -> DeepHeader("Productivity index", "${s.productivityScore}/100",
        "Performance grade ${s.performanceGrade} · ${s.karmaLevel}")
    StatsCategory.STREAK -> DeepHeader("Streak", "${s.streak} days",
        "Best ever ${maxOf(s.longestStreak, s.streak)} days")
    StatsCategory.CONSISTENCY -> DeepHeader("Consistency", "${s.consistencyScore}/100",
        "${s.weeklyConsistencyDays}/7 active days this week")
    StatsCategory.PEAK_HOURS -> DeepHeader("Peak hours", s.peakHourLabel.ifBlank { "—" },
        "When you get things done")
    StatsCategory.TAGS -> DeepHeader("Where the time went", s.mostActiveTag ?: "—",
        "${s.tagStats.size} tags tracked")
    StatsCategory.FOCUS -> DeepHeader("Focus", focusHoursLabel(s.totalFocusHours),
        "${s.averageDailyFocusMinutes}m average daily")
    StatsCategory.BESTS -> DeepHeader("Personal bests", "All-time", "")
    StatsCategory.WEEKLY -> DeepHeader("This week", "${s.thisWeekCompleted} tasks",
        "Last week ${s.lastWeekCompleted}")
    StatsCategory.MOMENTUM -> DeepHeader("Momentum", "${s.momentumScore}/100",
        "Velocity ${"%+.1f".format(s.completionVelocity)} · Burnout risk ${(s.burnoutRiskScore*100).roundToInt()}%")
    StatsCategory.ROLLOVER -> DeepHeader("Rollover health",
        "${s.activeRolloverCount} pending",
        s.oldestRolloverTaskTitle?.let { "Oldest: $it · ${s.oldestRolloverDays}d" } ?: "No rolled-over tasks")
    StatsCategory.FORECAST -> DeepHeader("Forecast · next 7 days",
        "${s.forecastNext7.sumOf { it.second.toDouble() }.roundToInt()} tasks",
        "Projection from recent trend")
    StatsCategory.KARMA -> DeepHeader("Karma", "${s.karmaPoints}", s.karmaLevel)
    StatsCategory.INSIGHTS -> DeepHeader("Smart insights", "${s.smartInsights.size} notes", "")
    StatsCategory.PRIORITY -> DeepHeader("Priority mix", "By priority", "")
    StatsCategory.TASK_TYPE -> DeepHeader("Task types", "By category", "")
    StatsCategory.WEEKDAY_WEEKEND -> DeepHeader("Weekday vs weekend",
        "${"%.1f".format(s.weekdayAvg)} / ${"%.1f".format(s.weekendAvg)}",
        "Average tasks per day")
}

@Composable
private fun CompareCurrVsPrev(
    category: StatsCategory,
    statsState: StatsState,
    fg: Color, fgMuted: Color, tile: Color, accent: Color, hair: Color,
) {
    val cmp = compareForCategory(category, statsState) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompareCard(
            label = cmp.currentLabel,
            value = cmp.currentValue,
            bg = accent,
            fg = Color.Black,
            sub = cmp.currentSub,
            modifier = Modifier.weight(1f)
        )
        CompareCard(
            label = cmp.previousLabel,
            value = cmp.previousValue,
            bg = tile,
            fg = fg,
            sub = cmp.previousSub,
            modifier = Modifier.weight(1f)
        )
    }
    if (cmp.deltaText.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, hair, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    cmp.deltaText,
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun CompareCard(
    label: String,
    value: String,
    bg: Color,
    fg: Color,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(16.dp)
    ) {
        Text(
            label.uppercase(),
            color = fg.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            value,
            color = fg,
            fontSize = 34.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.4).sp,
        )
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(sub, color = fg.copy(alpha = 0.65f), fontSize = 11.sp)
        }
    }
}

private data class Comparison(
    val currentLabel: String, val currentValue: String, val currentSub: String,
    val previousLabel: String, val previousValue: String, val previousSub: String,
    val deltaText: String,
)

private fun compareForCategory(c: StatsCategory, s: StatsState): Comparison? = when (c) {
    StatsCategory.SCORE -> {
        val history = s.productivityScoreHistory
        val prev = history.dropLast(1).lastOrNull()?.second ?: 0
        val delta = s.productivityScore - prev
        Comparison(
            "Now", "${s.productivityScore}", "of 100",
            "Previous", "$prev", "last reading",
            if (prev > 0) "${if (delta >= 0) "▲" else "▼"} ${abs(delta)} pts vs previous" else ""
        )
    }
    StatsCategory.STREAK -> {
        val best = maxOf(s.longestStreak, s.streak)
        val delta = s.streak - best
        Comparison(
            "Current", "${s.streak}d", "days in a row",
            "Best", "${best}d", "all-time",
            if (best > 0) "${delta.let { if (it >= 0) "Matched best" else "${abs(it)}d short of best" }}" else ""
        )
    }
    StatsCategory.WEEKLY -> {
        val delta = s.thisWeekCompleted - s.lastWeekCompleted
        val pct = (s.weekOverWeekGrowth * 100).roundToInt()
        Comparison(
            "This week", "${s.thisWeekCompleted}", "tasks",
            "Last week", "${s.lastWeekCompleted}", "tasks",
            "${if (delta >= 0) "▲ +" else "▼ "}${abs(delta)} tasks · ${if (pct >= 0) "+" else ""}$pct%"
        )
    }
    StatsCategory.TAGS -> {
        val top = s.tagStats.maxByOrNull { it.completedCount }
        val total = s.tagStats.sumOf { it.completedCount }.coerceAtLeast(1)
        val topPct = top?.let { (it.completedCount.toFloat() / total * 100).roundToInt() } ?: 0
        Comparison(
            "Top tag", top?.tag ?: "—", "${top?.completedCount ?: 0} tasks · ${topPct}%",
            "Other tags", "${(s.tagStats.size - 1).coerceAtLeast(0)}", "tracked",
            ""
        )
    }
    StatsCategory.FOCUS -> {
        val hrs = focusHoursLabel(s.totalFocusHours)
        Comparison(
            "Total focus", hrs, "${focusSessionCount(s)} sessions",
            "Avg per day", "${s.averageDailyFocusMinutes}m", "rolling",
            ""
        )
    }
    StatsCategory.MOMENTUM -> {
        val mom = s.momentumScore
        val cons = s.consistencyScore
        Comparison(
            "Momentum", "$mom", "0–100 EMA",
            "Consistency", "$cons", "output stability",
            "Velocity ${"%+.2f".format(s.completionVelocity)} tasks/day"
        )
    }
    StatsCategory.ROLLOVER -> {
        val rate = (s.rolloverCompletionRate * 100).roundToInt()
        Comparison(
            "Pending", "${s.activeRolloverCount}", "rolled-over",
            "Clear rate", "${rate}%", "eventually done",
            "Oldest ${s.oldestRolloverDays}d · avg ${"%.1f".format(s.averageRolloverDaysPending)}d pending"
        )
    }
    StatsCategory.WEEKDAY_WEEKEND -> Comparison(
        "Weekday avg", "%.1f".format(s.weekdayAvg), "Mon–Fri",
        "Weekend avg", "%.1f".format(s.weekendAvg), "Sat–Sun",
        "Peak day: ${s.peakDayOfWeek.ifBlank { "—" }}"
    )
    StatsCategory.KARMA -> Comparison(
        "Karma", "${s.karmaPoints}", s.karmaLevel,
        "Grade", s.performanceGrade, "current",
        ""
    )
    StatsCategory.CONSISTENCY -> Comparison(
        "Consistency", "${s.consistencyScore}", "0–100",
        "Active days", "${s.weeklyConsistencyDays}/7", "this week",
        "Std-dev ${"%.1f".format(s.stdDevDaily)} tasks"
    )
    StatsCategory.PEAK_HOURS -> Comparison(
        "Peak", s.peakHourLabel.ifBlank { "—" }, "",
        "Peak day", s.peakDayOfWeek.ifBlank { "—" }, "",
        ""
    )
    StatsCategory.FORECAST -> {
        val sum = s.forecastNext7.sumOf { it.second.toDouble() }.roundToInt()
        Comparison(
            "Next 7d", "$sum", "projected tasks",
            "Last 7d", "${s.thisWeekCompleted}", "actual",
            ""
        )
    }
    StatsCategory.BESTS, StatsCategory.INSIGHTS,
    StatsCategory.PRIORITY, StatsCategory.TASK_TYPE -> null
}

@Composable
private fun CategoryBody(
    category: StatsCategory,
    statsState: StatsState,
    fg: Color, fgMuted: Color, tile: Color, accent: Color, hair: Color, surface: Color,
    dark: Boolean,
) {
    when (category) {
        StatsCategory.SCORE -> ScoreHistoryPanel(statsState.productivityScoreHistory, fg, fgMuted, tile, accent, surface)
        StatsCategory.CONSISTENCY -> ConsistencyPanel(statsState, fg, fgMuted, tile, accent, surface, dark)
        StatsCategory.PEAK_HOURS -> PeakHoursPanel(statsState, fg, fgMuted, tile, accent)
        StatsCategory.TAGS -> TagsPanel(statsState, fg, fgMuted, tile, hair, accent)
        StatsCategory.FOCUS -> FocusPanel(statsState, fg, fgMuted, tile, accent, hair)
        StatsCategory.BESTS -> BestsPanel(statsState, fg, fgMuted, tile, hair)
        StatsCategory.WEEKLY -> WeeklyPanel(statsState, fg, fgMuted, tile, accent, dark)
        StatsCategory.STREAK -> StreakPanel(statsState, fg, fgMuted, tile, hair, accent)
        StatsCategory.MOMENTUM -> MomentumPanel(statsState, fg, fgMuted, tile, accent, hair)
        StatsCategory.ROLLOVER -> RolloverPanel(statsState, fg, fgMuted, tile, accent)
        StatsCategory.FORECAST -> ForecastPanel(statsState, fg, fgMuted, tile, accent, surface)
        StatsCategory.KARMA -> KarmaPanel(statsState, fg, fgMuted, tile, accent)
        StatsCategory.INSIGHTS -> InsightsPanel(statsState, fg, fgMuted, tile, accent)
        StatsCategory.PRIORITY -> PriorityPanel(statsState, fg, fgMuted, tile, hair, accent)
        StatsCategory.TASK_TYPE -> TaskTypePanel(statsState, fg, fgMuted, tile, hair, accent)
        StatsCategory.WEEKDAY_WEEKEND -> WeekdayWeekendPanel(statsState, fg, fgMuted, tile, accent)
    }
}

/* ─────────────── panels ─────────────── */

@Composable
private fun SectionKicker(text: String, fgMuted: Color) {
    Text(
        text.uppercase(),
        color = fgMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun PanelColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
private fun ScoreHistoryPanel(
    history: List<Pair<String, Int>>, fg: Color, fgMuted: Color, tile: Color, accent: Color, surface: Color
) {
    SectionKicker("Trend · last 12 readings", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tile)
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val vals = if (history.isEmpty())
                    floatArrayOf(52f, 58f, 51f, 63f, 68f, 72f, 69f, 75f, 81f, 78f, 84f, 87f)
                else history.takeLast(12).map { it.second.toFloat() }.toFloatArray()
                if (vals.size < 2) return@Canvas
                val dotR = 5.dp.toPx()
                val inset = dotR + 2.dp.toPx()
                val w = size.width - inset * 2
                val h = size.height - inset * 2
                val dataMin = vals.min()
                val dataMax = vals.max()
                val pad = ((dataMax - dataMin) * 0.2f).coerceAtLeast(4f)
                val min = (dataMin - pad).coerceAtLeast(0f)
                val max = (dataMax + pad).coerceAtMost(100f)
                val range = (max - min).coerceAtLeast(0.0001f)
                val path = Path(); val area = Path()
                vals.forEachIndexed { i, v ->
                    val x = inset + i / (vals.size - 1f) * w
                    val y = inset + h - ((v - min) / range) * h
                    if (i == 0) { path.moveTo(x, y); area.moveTo(x, y) }
                    else { path.lineTo(x, y); area.lineTo(x, y) }
                }
                val bottom = inset + h
                area.lineTo(inset + w, bottom); area.lineTo(inset, bottom); area.close()
                drawPath(area, Brush.verticalGradient(0f to accent.copy(alpha = 0.6f), 1f to accent.copy(alpha = 0f)))
                drawPath(path, color = fg, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                val lx = inset + w
                val ly = inset + h - ((vals.last() - min) / range) * h
                drawCircle(surface, dotR, Offset(lx, ly))
                drawCircle(fg, dotR, Offset(lx, ly), style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun ConsistencyPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, surface: Color, dark: Boolean
) {
    SectionKicker("Consistency · 52 weeks", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tile)
                .padding(16.dp)
        ) {
            val weeks = 52
            val levels = buildHeatLevels(s.yearlyHeatmap, weeks)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(weeks) { col ->
                            val lvl = levels[col * 7 + row]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(heatColor(lvl, dark, accent, surface))
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("52W AGO", color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("TODAY", color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun PeakHoursPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color) {
    SectionKicker("Hour-by-hour", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(tile).padding(16.dp)
        ) {
            val bars = hourlyBars(s)
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                bars.forEach { v ->
                    val peak = v > 0.8f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(maxOf(6f, 120f * v.coerceIn(0.05f, 1f)).dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (peak) accent else fg.copy(alpha = 0.15f))
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("6A", "10A", "2P", "6P", "10P", "2A").forEach {
                    Text(it, color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun TagsPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color
) {
    SectionKicker("All tags · ranked", fgMuted)
    val total = s.tagStats.sumOf { it.completedCount }.coerceAtLeast(1)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(tile).padding(16.dp)
        ) {
            s.tagStats.sortedByDescending { it.completedCount }.forEachIndexed { i, t ->
                val pct = (t.completedCount.toFloat() / total * 100).roundToInt()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "0${i + 1}".takeLast(2),
                        color = fgMuted, fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(t.tag, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${t.completedCount}", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(8.dp))
                    Text("${pct}%", color = fgMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(36.dp))
                }
                if (i < s.tagStats.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                }
            }
            if (s.tagStats.isEmpty()) {
                Text("Tag some tasks to see distribution.", color = fgMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FocusPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, hair: Color
) {
    // ═══════════════════════════════════════════════════════════════
    // 1. OVERVIEW CARDS
    // ═══════════════════════════════════════════════════════════════
    SectionKicker("Focus Overview", fgMuted)
    PanelColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tile)
                    .padding(14.dp)
            ) {
                Text("TODAY", color = fgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text(formatMinutes(s.todayFocusMinutes), color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tile)
                    .padding(14.dp)
            ) {
                Text("THIS WEEK", color = fgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text(formatMinutes(s.thisWeekFocusMinutes), color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tile)
                    .padding(14.dp)
            ) {
                Text("SESSIONS", color = fgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text("${s.totalCompletedSessions}", color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // ═══════════════════════════════════════════════════════════════
    // 2. DAILY & WEEKLY SUMMARY BAR CHART
    // ═══════════════════════════════════════════════════════════════
    SectionKicker("Daily & Weekly Focus Summary", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tile)
                .padding(16.dp)
        ) {
            val data = s.weeklyFocusData
            if (data.isEmpty()) {
                Text("No focus data recorded yet.", color = fgMuted, fontSize = 13.sp)
            } else {
                val maxV = (data.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data.takeLast(7).forEach { (dayLabel, sec) ->
                        val frac = sec.toFloat() / maxV
                        val mins = sec / 60
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (mins > 0) "${mins}m" else "",
                                color = fgMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(maxOf(6f, 65f * frac).dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(accent)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                dayLabel,
                                color = fgMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // ═══════════════════════════════════════════════════════════════
    // 3. PER-TASK BREAKDOWN
    // ═══════════════════════════════════════════════════════════════
    SectionKicker("Task Breakdown", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tile)
                .padding(16.dp)
        ) {
            if (s.perTaskStatsList.isEmpty() && s.topFocusedTasks.isEmpty()) {
                Text("Start a focus session for a task to build statistics.", color = fgMuted, fontSize = 13.sp)
            } else {
                val taskStats = s.perTaskStatsList.ifEmpty {
                    s.topFocusedTasks.map {
                        com.theblankstate.preamble.data.PerTaskTimerStats(
                            taskId = it.taskId,
                            taskTitle = it.taskTitle,
                            totalSeconds = it.totalSeconds,
                            sessionCount = it.sessionCount,
                            avgDurationSeconds = if (it.sessionCount > 0) it.totalSeconds / it.sessionCount else 0,
                            lastSessionDate = null,
                            longestSessionSeconds = it.totalSeconds
                        )
                    }
                }
                taskStats.forEachIndexed { i, stat ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stat.taskTitle ?: "Standalone Session",
                                color = fg,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatMinutes(stat.totalSeconds / 60),
                                color = fg,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sessions: ${stat.sessionCount} · Avg: ${formatMinutes(stat.avgDurationSeconds / 60)}",
                                color = fgMuted,
                                fontSize = 11.sp
                            )
                            if (stat.lastSessionDate != null) {
                                Text(
                                    text = "Last: ${stat.lastSessionDate}",
                                    color = fgMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (i < taskStats.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // ═══════════════════════════════════════════════════════════════
    // 4. RECENT SESSIONS
    // ═══════════════════════════════════════════════════════════════
    SectionKicker("Recent Sessions", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tile)
                .padding(16.dp)
        ) {
            if (s.recentSessionsList.isEmpty()) {
                Text("No recent focus sessions.", color = fgMuted, fontSize = 13.sp)
            } else {
                s.recentSessionsList.take(10).forEachIndexed { i, sess ->
                    val actualMins = maxOf(1, sess.actualDurationCompletedSeconds / 60)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sess.taskTitle ?: "Standalone Session",
                                color = fg,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${sess.date} · Status: ${sess.completionStatus}",
                                color = fgMuted,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (sess.completionStatus == "COMPLETED")
                                        accent.copy(alpha = 0.2f)
                                    else
                                        fgMuted.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${actualMins}m",
                                color = fg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (i < s.recentSessionsList.take(10).lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                    }
                }
            }
        }
    }
}

@Composable
private fun BestsPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color) {
    SectionKicker("Records", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(tile).padding(16.dp)
        ) {
            val items = listOf(
                Triple("Longest streak", "${maxOf(s.longestStreak, s.streak)}", "days"),
                Triple("Most tasks / day", "${s.dailyCompleted.maxOfOrNull { it.second } ?: s.todayCompleted}", "tasks"),
                Triple("Longest focus", if (s.bestFocusDayMinutes > 0) formatMinutes(s.bestFocusDayMinutes) else "—", s.bestFocusDay ?: ""),
                Triple("Karma peak", "${s.karmaPoints}", s.karmaLevel),
                Triple("Grade", s.performanceGrade, "current"),
            )
            items.forEachIndexed { i, (l, v, u) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(l, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(v, color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                        Spacer(Modifier.width(4.dp))
                        Text(u, color = fgMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                if (i < items.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
            }
        }
    }
}

@Composable
private fun WeeklyPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, dark: Boolean
) {
    SectionKicker("This week, day by day", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(tile).padding(16.dp)
        ) {
            val vals = s.weeklyStats
            val maxV = (vals.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(0.0001f)
            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                vals.take(7).forEach { (_, v) ->
                    val frac = v / maxV
                    Box(
                        modifier = Modifier.weight(1f)
                            .height(maxOf(6f, 100f * frac).dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("M","T","W","T","F","S","S").forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(it, color = fgMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color) {
    SectionKicker("Streak ladder · 60 days", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(tile).padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(60) { i ->
                    val done = i < s.streak.coerceAtMost(60)
                    Box(
                        modifier = Modifier.weight(1f).height(24.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (done) accent else hair)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("60D AGO", color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Text("TODAY", color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MomentumPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, hair: Color
) {
    SectionKicker("Momentum & risk", fgMuted)
    PanelColumn {
        MetricRow("Momentum score", "${s.momentumScore}/100", tile, fg, fgMuted)
        MetricRow("Consistency score", "${s.consistencyScore}/100", tile, fg, fgMuted)
        MetricRow("Completion velocity", "%+.2f".format(s.completionVelocity) + " tasks/day", tile, fg, fgMuted)
        MetricRow("Burnout risk", "${(s.burnoutRiskScore * 100).roundToInt()}%", tile, fg, fgMuted)
        MetricRow("Moving avg (7d)", "%.1f".format(s.movingAvg7Day), tile, fg, fgMuted)
        MetricRow("Std-dev (daily)", "%.1f".format(s.stdDevDaily), tile, fg, fgMuted)
    }
}

@Composable
private fun RolloverPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color) {
    SectionKicker("Rollover details", fgMuted)
    PanelColumn {
        MetricRow("Active rolled-over", "${s.activeRolloverCount}", tile, fg, fgMuted)
        MetricRow("Average days pending", "%.1f".format(s.averageRolloverDaysPending), tile, fg, fgMuted)
        MetricRow("Rollover completion", "${(s.rolloverCompletionRate * 100).roundToInt()}%", tile, fg, fgMuted)
        s.oldestRolloverTaskTitle?.let {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(accent).padding(14.dp)
            ) {
                Text("OLDEST", color = Color.Black.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                Text(it, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("${s.oldestRolloverDays} days pending", color = Color.Black.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ForecastPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, surface: Color
) {
    SectionKicker("Next 7 days · projection", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(tile).padding(16.dp)
        ) {
            if (s.forecastNext7.isEmpty()) {
                Text("Not enough data yet.", color = fgMuted, fontSize = 13.sp)
            } else {
                val maxV = (s.forecastNext7.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(0.0001f)
                Row(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    s.forecastNext7.forEach { (_, v) ->
                        val frac = v / maxV
                        Box(
                            modifier = Modifier.weight(1f)
                                .height(maxOf(6f, 100f * frac).dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(accent)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.forecastNext7.forEach { (label, _) ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(label.takeLast(2), color = fgMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KarmaPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color) {
    SectionKicker("Karma system", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(accent).padding(20.dp)
        ) {
            Text("LEVEL", color = Color.Black.copy(alpha = 0.55f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text(s.karmaLevel, color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            Spacer(Modifier.height(6.dp))
            Text("${s.karmaPoints} karma points", color = Color.Black.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        MetricRow("Performance grade", s.performanceGrade, tile, fg, fgMuted)
        MetricRow("Completion 7d avg", "${(s.completionRate7Day * 100).roundToInt()}%", tile, fg, fgMuted)
        MetricRow("Completion 30d avg", "${(s.completionRate30Day * 100).roundToInt()}%", tile, fg, fgMuted)
    }
}

@Composable
private fun InsightsPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color) {
    SectionKicker("Smart insights", fgMuted)
    PanelColumn {
        if (s.smartInsights.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(tile).padding(16.dp)
            ) {
                Text("Log a few more days and insights will show up here.", color = fgMuted, fontSize = 13.sp, lineHeight = 20.sp)
            }
        } else {
            s.smartInsights.forEachIndexed { i, text ->
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (i == 0) accent else tile).padding(14.dp)
                ) {
                    Text(
                        "INSIGHT ${(i + 1).toString().padStart(2, '0')}",
                        color = if (i == 0) Color.Black.copy(alpha = 0.55f) else fgMuted,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp, fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text,
                        color = if (i == 0) Color.Black else fg,
                        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color
) {
    SectionKicker("Priority distribution", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(tile).padding(16.dp)
        ) {
            if (s.priorityDistribution.isEmpty()) {
                Text("Add priorities to tasks to see distribution.", color = fgMuted, fontSize = 13.sp)
            } else {
                s.priorityDistribution.forEachIndexed { i, (label, pct) ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("${pct.roundToInt()}%", color = fgMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(hair)) {
                            Box(
                                Modifier.fillMaxWidth((pct / 100f).coerceIn(0f, 1f)).height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (i == 0) accent else fg.copy(alpha = 0.7f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskTypePanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color
) {
    SectionKicker("By task type", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(tile).padding(16.dp)
        ) {
            if (s.taskTypeBreakdown.isEmpty()) {
                Text("Categorise tasks to unlock this view.", color = fgMuted, fontSize = 13.sp)
            } else {
                val total = s.taskTypeBreakdown.sumOf { it.count }.coerceAtLeast(1)
                s.taskTypeBreakdown.sortedByDescending { it.count }.forEachIndexed { i, t ->
                    val pct = (t.count.toFloat() / total * 100).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("0${i+1}".takeLast(2), color = fgMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(22.dp))
                        Text(t.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${t.count}", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(8.dp))
                        Text("${pct}%", color = fgMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(36.dp))
                    }
                    if (i < s.taskTypeBreakdown.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayWeekendPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color
) {
    SectionKicker("Habits by day", fgMuted)
    PanelColumn {
        MetricRow("Peak day of week", s.peakDayOfWeek.ifBlank { "—" }, tile, fg, fgMuted)
        MetricRow("Avg task age", "%.1f days".format(s.avgTaskAgeDays), tile, fg, fgMuted)
        MetricRow("Procrastination index", "${(s.procrastinationIndex * 100).roundToInt()}%", tile, fg, fgMuted)
        MetricRow("Yesterday done / total", "${s.yesterdayCompleted} / ${s.yesterdayTotal}", tile, fg, fgMuted)
        MetricRow("This month vs last", "${s.thisMonthCompleted} vs ${s.lastMonthCompleted}", tile, fg, fgMuted)
    }
}

@Composable
private fun MetricRow(label: String, value: String, tile: Color, fg: Color, fgMuted: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tile)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = fgMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(value, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

/* ─────────────── compare-across-ranges ─────────────── */

@Composable
private fun RangePicker(
    selected: DeepRange,
    onSelect: (DeepRange) -> Unit,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    dark: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            "COMPARE WINDOW",
            color = fgMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(tile)
                .padding(3.dp)
        ) {
            DeepRange.values().forEach { r ->
                val active = r == selected
                val bg = if (active) (if (dark) Color.White else Color.Black) else Color.Transparent
                val tx = if (active) (if (dark) Color.Black else Color.White) else fgMuted
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(bg)
                        .clickable { onSelect(r) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        r.label,
                        color = tx,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

private data class RangeMetric(
    val primary: String,
    val primaryUnit: String,
    val secondary: String,
)

private fun metricForRange(
    category: StatsCategory,
    s: StatsState,
    days: Int,
): RangeMetric {
    val daily = s.dailyStatsWithDates.takeLast(days)
    val done = daily.sumOf { it.second }
    val total = daily.sumOf { it.third }
    val active = daily.count { it.second > 0 }
    val rate = if (total > 0) (done.toFloat() / total * 100).roundToInt() else 0
    val avgDay = if (daily.isNotEmpty()) done.toFloat() / daily.size else 0f

    return when (category) {
        StatsCategory.SCORE -> {
            val hist = s.productivityScoreHistory.takeLast(days)
            val avg = if (hist.isNotEmpty()) hist.map { it.second }.average().roundToInt() else 0
            val peak = hist.maxOfOrNull { it.second } ?: 0
            RangeMetric("$avg", "avg", "peak $peak")
        }
        StatsCategory.FOCUS -> {
            val focus = s.weeklyFocusData.takeLast(days).sumOf { it.second }
            val hrs = focus / 60
            val mins = focus % 60
            RangeMetric(if (hrs > 0) "${hrs}h${if (mins > 0) " ${mins}m" else ""}" else "${mins}m", "focus", "${days}d window")
        }
        StatsCategory.CONSISTENCY, StatsCategory.WEEKDAY_WEEKEND -> {
            val pct = if (daily.isNotEmpty()) (active.toFloat() / daily.size * 100).roundToInt() else 0
            RangeMetric("$active", "active days", "${pct}% of window")
        }
        StatsCategory.STREAK, StatsCategory.MOMENTUM -> {
            RangeMetric("%.1f".format(avgDay), "tasks/day", "$active active days")
        }
        else -> RangeMetric("$done", "done", "${rate}% rate · $active days")
    }
}

@Composable
private fun RangeCompareStrip(
    category: StatsCategory,
    statsState: StatsState,
    selected: DeepRange,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DeepRange.values().forEach { r ->
            val m = metricForRange(category, statsState, r.days)
            val active = r == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) accent else tile)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                Text(
                    r.label,
                    color = if (active) Color.Black.copy(alpha = 0.6f) else fgMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    m.primary,
                    color = if (active) Color.Black else fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    m.primaryUnit,
                    color = if (active) Color.Black.copy(alpha = 0.7f) else fgMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    m.secondary,
                    color = if (active) Color.Black.copy(alpha = 0.7f) else fgMuted,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}

/* ─────────────── formula / info card ─────────────── */

private fun formulaForCategory(c: StatsCategory): Pair<String, String> = when (c) {
    StatsCategory.SCORE -> "How this is calculated" to
        "Productivity index = completionRate·40 + streakBonus·20 + focusRatio·20 + consistencyRate·20. Clamped 0–100. Updated every time a task finishes."
    StatsCategory.STREAK -> "How this is calculated" to
        "Streak = consecutive days with at least one completed task ending today. Breaks if any day is skipped. Longest-ever is tracked separately."
    StatsCategory.CONSISTENCY -> "How this is calculated" to
        "Consistency = 100 − (stdDev ÷ mean · 100). Active-days-per-7 is the raw count of days this week with at least one completion."
    StatsCategory.PEAK_HOURS -> "How this is calculated" to
        "Each completion is bucketed by its hour-of-day over the last 30 days. Peak hour is the hour with the largest completion count."
    StatsCategory.TAGS -> "How this is calculated" to
        "Each completed task contributes 1 to every tag it carries. Percentages are share of total completions in the lifetime window."
    StatsCategory.FOCUS -> "How this is calculated" to
        "Total focus = sum of all focus session minutes (completed only). Averages are over the rolling 7-day window. Per-task rank sums minutes per taskId."
    StatsCategory.BESTS -> "How this is calculated" to
        "All-time peaks across the whole task history. Longest streak, single-day task max, best focus-day, highest karma and current grade."
    StatsCategory.WEEKLY -> "How this is calculated" to
        "This week = Monday 00:00 through now. Last week = preceding Mon–Sun. Growth = (this − last) ÷ max(last, 1) · 100."
    StatsCategory.MOMENTUM -> "How this is calculated" to
        "Momentum = EMA of daily completions (half-life ~7d), scaled 0–100. Burnout risk rises when velocity spikes but completion-rate drops."
    StatsCategory.ROLLOVER -> "How this is calculated" to
        "Rolled-over = tasks whose creation date is earlier than today and are still open. Clear rate = done ÷ (done + still-pending) over rollovers."
    StatsCategory.FORECAST -> "How this is calculated" to
        "Simple projection from the recent 14-day trend line extrapolated forward 7 days. Weekday seasonality (Mon-Sun averages) applied on top."
    StatsCategory.KARMA -> "How this is calculated" to
        "Karma = +points for each completion and streak-day, −points for overdue rollovers. Level thresholds step up every set number of points."
    StatsCategory.INSIGHTS -> "How this is calculated" to
        "Rule-based readings of the other stats: streak momentum, consistency drops, focus-to-tasks ratio, weekday skew, peak hour vs your active window."
    StatsCategory.PRIORITY -> "How this is calculated" to
        "Each completed task's priority is bucketed (High/Medium/Low/none). Percentages are share of completions over the window."
    StatsCategory.TASK_TYPE -> "How this is calculated" to
        "Each completed task's category label bucketed and counted. Shows raw count and share-of-total."
    StatsCategory.WEEKDAY_WEEKEND -> "How this is calculated" to
        "Weekday avg = mean tasks/day Mon–Fri over the last 30 days. Weekend avg = Sat–Sun. Peak day = the weekday with highest mean."
}

@Composable
private fun FormulaCard(
    category: StatsCategory,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    hair: Color,
) {
    val (title, body) = formulaForCategory(category)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(tile)
                .border(1.dp, hair, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(
                "FORMULA",
                color = fgMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                color = fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                color = fgMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}
