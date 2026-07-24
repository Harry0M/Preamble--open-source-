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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.theblankstate.preamble.data.FocusSession
import com.theblankstate.preamble.data.PerTaskTimerStats
import com.theblankstate.preamble.viewmodel.StatsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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

    var showTaskBreakdownSubScreen by rememberSaveable { mutableStateOf(false) }
    var showRecentSessionsSubScreen by rememberSaveable { mutableStateOf(false) }

    if (showTaskBreakdownSubScreen) {
        TaskFocusBreakdownScreen(
            statsState = statsState,
            onBack = { showTaskBreakdownSubScreen = false },
            dark = dark
        )
        return
    }

    if (showRecentSessionsSubScreen) {
        RecentSessionsScreen(
            statsState = statsState,
            onBack = { showRecentSessionsSubScreen = false },
            dark = dark
        )
        return
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
                onOpenTaskBreakdown = { showTaskBreakdownSubScreen = true },
                onOpenRecentSessions = { showRecentSessionsSubScreen = true }
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
    onOpenTaskBreakdown: () -> Unit = {},
    onOpenRecentSessions: () -> Unit = {}
) {
    when (category) {
        StatsCategory.SCORE -> ScoreHistoryPanel(statsState.productivityScoreHistory, fg, fgMuted, tile, accent, surface)
        StatsCategory.CONSISTENCY -> ConsistencyPanel(statsState, fg, fgMuted, tile, accent, surface, dark)
        StatsCategory.PEAK_HOURS -> PeakHoursPanel(statsState, fg, fgMuted, tile, accent)
        StatsCategory.TAGS -> TagsPanel(statsState, fg, fgMuted, tile, hair, accent)
        StatsCategory.FOCUS -> FocusPanel(statsState, fg, fgMuted, tile, accent, hair, onOpenTaskBreakdown, onOpenRecentSessions)
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
    s: StatsState,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    accent: Color,
    hair: Color,
    onOpenTaskBreakdown: () -> Unit,
    onOpenRecentSessions: () -> Unit
) {
    var selectedWeekOffset by rememberSaveable { mutableIntStateOf(0) }

    // ═══════════════════════════════════════════════════════════════
    // 1. HERO CARD: TODAY'S FOCUS
    // ═══════════════════════════════════════════════════════════════
    SectionKicker("Today's Focus", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(accent)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TODAY'S TOTAL",
                    color = Color.Black.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.4.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${s.todayFocusSessions} sessions",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = formatMinutesLong(s.todayFocusMinutes),
                color = Color.Black,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Streak: ${s.streak} days · Week total: ${formatMinutesLong(s.thisWeekFocusMinutes)}",
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // ═══════════════════════════════════════════════════════════════
    // 2. DAILY & WEEKLY SUMMARY BAR CHART WITH CUSTOM WEEK NAVIGATION
    // ═══════════════════════════════════════════════════════════════
    val (weekStartStr, weekEndStr, weekData) = remember(selectedWeekOffset, s.weeklyFocusData, s.recentSessionsList) {
        calculateWeekDataForOffset(selectedWeekOffset, s.weeklyFocusData, s.recentSessionsList)
    }

    SectionKicker("Daily & Weekly Focus Summary", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tile)
                .padding(16.dp)
        ) {
            // Week Navigation Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(fgMuted.copy(alpha = 0.12f))
                        .clickable { selectedWeekOffset-- },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Week",
                        tint = fg,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "$weekStartStr - $weekEndStr",
                    color = fg,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selectedWeekOffset < 0) fgMuted.copy(alpha = 0.12f) else Color.Transparent
                        )
                        .clickable(enabled = selectedWeekOffset < 0) {
                            if (selectedWeekOffset < 0) selectedWeekOffset++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Week",
                        tint = if (selectedWeekOffset < 0) fg else fgMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Chart Bars
            val maxSec = (weekData.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                weekData.forEach { (dayLabel, sec) ->
                    val frac = (sec.toFloat() / maxSec.toFloat()).coerceIn(0f, 1f)
                    val mins = sec / 60
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (mins > 0) "${mins}m" else "",
                            color = fgMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(maxOf(6f, 75f * frac).dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (sec > 0) accent else fgMuted.copy(alpha = 0.15f))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dayLabel,
                            color = fgMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // ═══════════════════════════════════════════════════════════════
    // 3. TASK BREAKDOWN CARD + VIEW ALL ACTION
    // ═══════════════════════════════════════════════════════════════
    val taskStats = remember(s.perTaskStatsList, s.topFocusedTasks) {
        s.perTaskStatsList.ifEmpty {
            s.topFocusedTasks.map {
                PerTaskTimerStats(
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
    }

    SectionKicker("Task Breakdown", fgMuted)
    PanelColumn {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tile)
                .padding(16.dp)
        ) {
            if (taskStats.isEmpty()) {
                Text("Start a focus session for a task to build statistics.", color = fgMuted, fontSize = 13.sp)
            } else {
                val grandTotal = taskStats.sumOf { it.totalSeconds }.coerceAtLeast(1)
                taskStats.take(4).forEachIndexed { i, stat ->
                    val pct = (stat.totalSeconds.toFloat() / grandTotal.toFloat() * 100f).coerceIn(0f, 100f)
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
                            Text(
                                text = String.format(Locale.US, "%.1f%%", pct),
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (i < taskStats.take(4).lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(hair)
                )
                Spacer(Modifier.height(10.dp))

                // Prominent View All Tasks Action Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenTaskBreakdown() }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "View All Tasks",
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "VIEW ALL TASKS (${taskStats.size})",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // ═══════════════════════════════════════════════════════════════
    // 4. RECENT SESSIONS CARD + VIEW ALL ACTION
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
                val recentPreview = s.recentSessionsList.take(4)
                recentPreview.forEachIndexed { i, sess ->
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
                                text = "${sess.date} · ${sess.completionStatus}",
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
                    if (i < recentPreview.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(hair)
                )
                Spacer(Modifier.height(10.dp))

                // Prominent View All Sessions Action Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenRecentSessions() }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Task,
                        contentDescription = "View All Sessions",
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "VIEW ALL SESSIONS (${s.recentSessionsList.size})",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }
    }
}

private fun calculateWeekDataForOffset(
    weekOffset: Int,
    fallbackWeeklyData: List<Pair<String, Int>>,
    recentSessions: List<FocusSession>
): Triple<String, String, List<Pair<String, Int>>> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val sdfDisplay = SimpleDateFormat("MMM d", Locale.US)
    val dayNameSdf = SimpleDateFormat("EEE", Locale.US)

    val cal = Calendar.getInstance()
    cal.add(Calendar.WEEK_OF_YEAR, weekOffset)

    // Set to Sunday of target week
    cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
    val startDate = cal.time

    val dates = mutableListOf<String>()
    val dayLabels = mutableListOf<String>()
    for (i in 0..6) {
        val dateStr = sdf.format(cal.time)
        val label = dayNameSdf.format(cal.time)
        dates.add(dateStr)
        dayLabels.add(label)
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    cal.add(Calendar.DAY_OF_YEAR, -1)
    val endDate = cal.time

    val startDateLabel = sdfDisplay.format(startDate)
    val endDateLabel = sdfDisplay.format(endDate)

    // Calculate daily totals from recentSessions or fallback
    val sessionsMap = recentSessions.groupBy { it.date }
    val result = dates.mapIndexed { index, dateStr ->
        val seconds = sessionsMap[dateStr]?.sumOf { it.actualDurationCompletedSeconds }
            ?: fallbackWeeklyData.firstOrNull { it.first.equals(dayLabels[index], ignoreCase = true) }?.second
            ?: 0
        dayLabels[index] to seconds
    }

    return Triple(startDateLabel, endDateLabel, result)
}

@Composable
private fun RangePicker(
    selected: DeepRange,
    onSelect: (DeepRange) -> Unit,
    fg: Color, fgMuted: Color, tile: Color, dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(tile)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DeepRange.entries.forEach { r ->
            val sel = r == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (sel) (if (dark) Color.White else Color.Black) else Color.Transparent)
                    .clickable { onSelect(r) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    r.label,
                    color = if (sel) (if (dark) Color.Black else Color.White) else fgMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun RangeCompareStrip(
    category: StatsCategory,
    statsState: StatsState,
    selected: DeepRange,
    fg: Color, fgMuted: Color, tile: Color, accent: Color,
) {
    val hrs = (statsState.totalFocusHours * (selected.days / 30f)).roundToInt()
    val tasks = (statsState.thisWeekCompleted * (selected.days / 7f)).roundToInt()
    val avg = "%.1f".format(statsState.averageDailyFocusMinutes / 60f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(tile)
                .padding(14.dp)
        ) {
            Column {
                Text("FOCUS TIME", color = fgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text("${hrs}h", color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(tile)
                .padding(14.dp)
        ) {
            Column {
                Text("DAILY AVG", color = fgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text("${avg}h/d", color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(tile)
                .padding(14.dp)
        ) {
            Column {
                Text("EST. TASKS", color = fgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text("$tasks", color = fg, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun FormulaCard(
    category: StatsCategory,
    fg: Color, fgMuted: Color, tile: Color, hair: Color,
) {
    val f = formulaForCategory(category) ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, hair, RoundedCornerShape(20.dp))
            .background(tile)
            .padding(16.dp)
    ) {
        Text("HOW IT WORKS", color = fgMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.4.sp)
        Spacer(Modifier.height(6.dp))
        Text(f.name, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(f.body, color = fgMuted, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

private data class Formula(val name: String, val body: String)
private fun formulaForCategory(c: StatsCategory): Formula? = when (c) {
    StatsCategory.SCORE -> Formula("Productivity score (0–100)", "Blend of completions (40%), consistency (30%), streak momentum (20%), and rollover health (10%).")
    StatsCategory.STREAK -> Formula("Streak calculation", "Consecutive days with at least 1 completed task. Reset at midnight unless a rollover grace period applies.")
    StatsCategory.CONSISTENCY -> Formula("Output stability (0–100)", "Inverse coefficient of variation on daily task counts over the last 30 days.")
    StatsCategory.PEAK_HOURS -> Formula("Peak hour detection", "Histogram of task completion timestamps aggregated by hour of day (local time).")
    StatsCategory.FOCUS -> Formula("Focus time tracking", "Sum of timer session durations across tasks, habits, and standalone focus periods.")
    StatsCategory.MOMENTUM -> Formula("Momentum EMA", "30-day exponential moving average of daily completions with alpha = 0.3.")
    StatsCategory.ROLLOVER -> Formula("Rollover health index", "Ratio of cleared vs pending rolled-over tasks, weighted by pending age in days.")
    else -> null
}



@Composable
private fun BestsPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color) {}

@Composable
private fun WeeklyPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, dark: Boolean) {}

@Composable
private fun StreakPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color) {}

@Composable
private fun MomentumPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, hair: Color) {}

@Composable
private fun RolloverPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color) {}

@Composable
private fun ForecastPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, surface: Color) {}

@Composable
private fun KarmaPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color) {}

@Composable
private fun InsightsPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color) {}

@Composable
private fun PriorityPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color) {}

@Composable
private fun TaskTypePanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color) {}

@Composable
private fun WeekdayWeekendPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color) {}
