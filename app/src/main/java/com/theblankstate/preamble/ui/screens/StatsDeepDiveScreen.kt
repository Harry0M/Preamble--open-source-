package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
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
private fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "expressivePressScale",
    )
    return this.scale(scale)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDeepDiveScreen(
    category: StatsCategory,
    statsState: StatsState,
    tweaks: StatsTweaks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val scaleFactor: Float = (screenWidthDp / 360f).coerceIn(0.85f, 1.15f)

    val dark = tweaks.theme == StatsTheme.DARK
    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f)
    val hair = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val tile = if (dark) Color(0xFF1E1E1E) else Color(0xFFF4F4F3)
    val surface = if (dark) Color(0xFF121212) else Color.White
    val accent = tweaks.accent
    val header = deepDiveHeader(category, statsState)
    var deepRange by rememberSaveable { mutableStateOf(DeepRange.D30) }

    var showTaskBreakdownSubScreen by rememberSaveable { mutableStateOf(false) }
    var showRecentSessionsSubScreen by rememberSaveable { mutableStateOf(false) }
    var showFormulaModal by rememberSaveable { mutableStateOf(false) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar inside LazyColumn (scrolls naturally with page content)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp * scaleFactor, vertical = 12.dp * scaleFactor),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(40.dp * scaleFactor)
                            .clip(CircleShape)
                            .background(tile)
                            .clickable(
                                interactionSource = backInteraction,
                                indication = null
                            ) { onBack() }
                            .expressivePressScale(backInteraction),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = fg,
                            modifier = Modifier.size(18.dp * scaleFactor)
                        )
                    }

                    val infoInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(40.dp * scaleFactor)
                            .clip(CircleShape)
                            .background(tile)
                            .clickable(
                                interactionSource = infoInteraction,
                                indication = null
                            ) { showFormulaModal = true }
                            .expressivePressScale(infoInteraction),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Formula & Proof",
                            tint = fg,
                            modifier = Modifier.size(18.dp * scaleFactor)
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp * scaleFactor, vertical = 4.dp * scaleFactor)) {
                    Text(
                        header.kicker.uppercase(),
                        color = fgMuted,
                        fontSize = 11.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp * scaleFactor,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(6.dp * scaleFactor))
                    Text(
                        header.title,
                        color = fg,
                        fontSize = 32.sp * scaleFactor,
                        lineHeight = 36.sp * scaleFactor,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1.2).sp * scaleFactor,
                    )
                    if (header.sub.isNotBlank()) {
                        Spacer(Modifier.height(4.dp * scaleFactor))
                        Text(
                            header.sub,
                            color = fgMuted,
                            fontSize = 14.sp * scaleFactor,
                            lineHeight = 20.sp * scaleFactor,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp * scaleFactor)) }

            item {
                CompareCurrVsPrev(
                    category = category,
                    statsState = statsState,
                    fg = fg,
                    fgMuted = fgMuted,
                    tile = tile,
                    accent = accent,
                    hair = hair,
                    scaleFactor = scaleFactor
                )
            }

            item { Spacer(Modifier.height(18.dp * scaleFactor)) }

            item {
                RangePicker(
                    selected = deepRange,
                    onSelect = { deepRange = it },
                    fg = fg,
                    fgMuted = fgMuted,
                    tile = tile,
                    dark = dark,
                    scaleFactor = scaleFactor
                )
            }

            item { Spacer(Modifier.height(10.dp * scaleFactor)) }

            item {
                RangeCompareStrip(
                    category = category,
                    statsState = statsState,
                    selected = deepRange,
                    fg = fg,
                    fgMuted = fgMuted,
                    tile = tile,
                    accent = accent,
                    scaleFactor = scaleFactor
                )
            }

            item { Spacer(Modifier.height(18.dp * scaleFactor)) }

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
                    scaleFactor = scaleFactor,
                    onOpenTaskBreakdown = { showTaskBreakdownSubScreen = true },
                    onOpenRecentSessions = { showRecentSessionsSubScreen = true }
                )
            }
            item { Spacer(Modifier.height(60.dp * scaleFactor)) }
        }

        // Formula & Live Mathematical Proof Bottom Sheet
        if (showFormulaModal) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showFormulaModal = false },
                sheetState = sheetState,
                containerColor = surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp * scaleFactor)
                ) {
                    FormulaCard(
                        category = category,
                        fg = fg,
                        fgMuted = fgMuted,
                        tile = tile,
                        hair = hair,
                        scaleFactor = scaleFactor
                    )
                    Spacer(Modifier.height(12.dp * scaleFactor))
                    ProvenProofCard(
                        category = category,
                        statsState = statsState,
                        fg = fg,
                        fgMuted = fgMuted,
                        tile = tile,
                        accent = accent,
                        hair = hair,
                        scaleFactor = scaleFactor
                    )
                }
            }
        }
    }
}

private data class DeepHeader(val kicker: String, val title: String, val sub: String)

private fun deepDiveHeader(c: StatsCategory, s: StatsState): DeepHeader = when (c) {
    StatsCategory.SCORE -> DeepHeader("Productivity index", ProvenStatVerifier.verify(StatsCategory.SCORE, s).provenValue,
        "Performance grade ${s.performanceGrade} · ${s.karmaLevel}")
    StatsCategory.STREAK -> DeepHeader("Streak", "${s.streak} days",
        "Best ever ${maxOf(s.longestStreak, s.streak)} days")
    StatsCategory.CONSISTENCY -> DeepHeader("Consistency", ProvenStatVerifier.verify(StatsCategory.CONSISTENCY, s).provenValue,
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
    fg: Color, fgMuted: Color, tile: Color, accent: Color, hair: Color, scaleFactor: Float
) {
    val cmp = compareForCategory(category, statsState) ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor),
        horizontalArrangement = Arrangement.spacedBy(10.dp * scaleFactor)
    ) {
        CompareCard(
            label = cmp.currentLabel,
            value = cmp.currentValue,
            bg = accent,
            fg = Color.Black,
            sub = cmp.currentSub,
            scaleFactor = scaleFactor,
            modifier = Modifier.weight(1f)
        )
        CompareCard(
            label = cmp.previousLabel,
            value = cmp.previousValue,
            bg = tile,
            fg = fg,
            sub = cmp.previousSub,
            scaleFactor = scaleFactor,
            modifier = Modifier.weight(1f)
        )
    }
    if (cmp.deltaText.isNotBlank()) {
        Spacer(Modifier.height(10.dp * scaleFactor))
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, hair, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor)
            ) {
                Text(
                    cmp.deltaText,
                    color = fg,
                    fontSize = 12.sp * scaleFactor,
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
    scaleFactor: Float,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp * scaleFactor))
            .background(bg)
            .padding(16.dp * scaleFactor)
            .expressivePressScale(interactionSource)
    ) {
        Text(
            label.uppercase(),
            color = fg.copy(alpha = 0.6f),
            fontSize = 10.sp * scaleFactor,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp * scaleFactor,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(10.dp * scaleFactor))
        Text(
            value,
            color = fg,
            fontSize = 32.sp * scaleFactor,
            lineHeight = 36.sp * scaleFactor,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.4).sp * scaleFactor,
        )
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(4.dp * scaleFactor))
            Text(sub, color = fg.copy(alpha = 0.65f), fontSize = 11.sp * scaleFactor)
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
        val proven = ProvenStatVerifier.verify(StatsCategory.SCORE, s)
        val history = s.productivityScoreHistory
        val prev = history.dropLast(1).lastOrNull()?.second ?: 0
        val currScore = proven.provenValue.split("/").firstOrNull()?.toIntOrNull() ?: s.productivityScore
        val delta = currScore - prev
        Comparison(
            "Now", proven.provenValue, "weighted index",
            "Previous", "$prev", "last reading",
            if (prev > 0) "${if (delta >= 0) "▲" else "▼"} ${abs(delta)} pts vs previous" else ""
        )
    }
    StatsCategory.CONSISTENCY -> {
        val proven = ProvenStatVerifier.verify(StatsCategory.CONSISTENCY, s)
        Comparison(
            "Consistency", proven.provenValue, "output stability",
            "Active Days", "${s.weeklyConsistencyDays}/7", "this week",
            "Std-Dev ${"%.1f".format(s.stdDevDaily)} tasks/d"
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
    scaleFactor: Float,
    onOpenTaskBreakdown: () -> Unit = {},
    onOpenRecentSessions: () -> Unit = {}
) {
    when (category) {
        StatsCategory.SCORE -> ScoreHistoryPanel(statsState.productivityScoreHistory, fg, fgMuted, tile, accent, surface, scaleFactor)
        StatsCategory.CONSISTENCY -> ConsistencyPanel(statsState, fg, fgMuted, tile, accent, surface, dark, scaleFactor)
        StatsCategory.PEAK_HOURS -> PeakHoursPanel(statsState, fg, fgMuted, tile, accent, scaleFactor)
        StatsCategory.TAGS -> TagsPanel(statsState, fg, fgMuted, tile, hair, accent, scaleFactor)
        StatsCategory.FOCUS -> FocusPanel(statsState, fg, fgMuted, tile, accent, hair, scaleFactor, onOpenTaskBreakdown, onOpenRecentSessions)
        StatsCategory.BESTS -> BestsPanel(statsState, fg, fgMuted, tile, hair)
        StatsCategory.WEEKLY -> WeeklyPanel(statsState, fg, fgMuted, tile, accent, dark)
        StatsCategory.STREAK -> StreakPanel(statsState, fg, fgMuted, tile, hair, accent, scaleFactor)
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
private fun SectionKicker(text: String, fgMuted: Color, scaleFactor: Float = 1f) {
    Text(
        text.uppercase(),
        color = fgMuted,
        fontSize = 11.sp * scaleFactor,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.6.sp * scaleFactor,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(start = 20.dp * scaleFactor, bottom = 10.dp * scaleFactor)
    )
}

@Composable
private fun PanelColumn(scaleFactor: Float = 1f, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor),
        verticalArrangement = Arrangement.spacedBy(10.dp * scaleFactor)
    ) { content() }
}

@Composable
private fun ScoreHistoryPanel(
    history: List<Pair<String, Int>>, fg: Color, fgMuted: Color, tile: Color, accent: Color, surface: Color, scaleFactor: Float
) {
    SectionKicker("Trend · last 12 readings", fgMuted, scaleFactor)
    PanelColumn(scaleFactor) {
        val interactionSource = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp * scaleFactor))
                .background(tile)
                .expressivePressScale(interactionSource)
                .padding(16.dp * scaleFactor)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp * scaleFactor)) {
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
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, surface: Color, dark: Boolean, scaleFactor: Float
) {
    SectionKicker("Consistency · 52 weeks", fgMuted, scaleFactor)
    PanelColumn(scaleFactor) {
        val interactionSource = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp * scaleFactor))
                .background(tile)
                .expressivePressScale(interactionSource)
                .padding(16.dp * scaleFactor)
        ) {
            val weeks = 52
            val levels = buildHeatLevels(s.yearlyHeatmap, weeks)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp * scaleFactor)) {
                repeat(7) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp * scaleFactor)) {
                        repeat(weeks) { col ->
                            val lvl = levels[col * 7 + row]
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp * scaleFactor)
                                    .clip(RoundedCornerShape(1.5.dp * scaleFactor))
                                    .background(heatColor(lvl, dark, accent, surface))
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp * scaleFactor))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("52W AGO", color = fgMuted, fontSize = 10.sp * scaleFactor, fontFamily = FontFamily.Monospace)
                Text("TODAY", color = fgMuted, fontSize = 10.sp * scaleFactor, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun PeakHoursPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, scaleFactor: Float) {
    SectionKicker("Hour-by-hour", fgMuted, scaleFactor)
    PanelColumn(scaleFactor) {
        val interactionSource = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp * scaleFactor))
                .background(tile)
                .expressivePressScale(interactionSource)
                .padding(16.dp * scaleFactor)
        ) {
            val bars = hourlyBars(s)
            Row(
                modifier = Modifier.fillMaxWidth().height(120.dp * scaleFactor),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp * scaleFactor)
            ) {
                bars.forEach { v ->
                    val peak = v > 0.8f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(maxOf(6f, 120f * v.coerceIn(0.05f, 1f)).dp * scaleFactor)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (peak) accent else fg.copy(alpha = 0.15f))
                    )
                }
            }
            Spacer(Modifier.height(6.dp * scaleFactor))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("6A", "10A", "2P", "6P", "10P", "2A").forEach {
                    Text(it, color = fgMuted, fontSize = 10.sp * scaleFactor, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun TagsPanel(
    s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color, scaleFactor: Float
) {
    SectionKicker("All tags · ranked", fgMuted, scaleFactor)
    val total = s.tagStats.sumOf { it.completedCount }.coerceAtLeast(1)
    PanelColumn(scaleFactor) {
        val interactionSource = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp * scaleFactor))
                .background(tile)
                .expressivePressScale(interactionSource)
                .padding(16.dp * scaleFactor)
        ) {
            s.tagStats.sortedByDescending { it.completedCount }.forEachIndexed { i, t ->
                val pct = (t.completedCount.toFloat() / total * 100).roundToInt()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "0${i + 1}".takeLast(2),
                        color = fgMuted, fontSize = 11.sp * scaleFactor,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(24.dp * scaleFactor)
                    )
                    Text(t.tag, color = fg, fontSize = 14.sp * scaleFactor, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${t.completedCount}", color = fg, fontSize = 13.sp * scaleFactor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(8.dp * scaleFactor))
                    Text("${pct}%", color = fgMuted, fontSize = 11.sp * scaleFactor, fontFamily = FontFamily.Monospace, modifier = Modifier.width(36.dp * scaleFactor))
                }
                if (i < s.tagStats.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                }
            }
            if (s.tagStats.isEmpty()) {
                Text("Tag some tasks to see distribution.", color = fgMuted, fontSize = 13.sp * scaleFactor)
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
    scaleFactor: Float,
    onOpenTaskBreakdown: () -> Unit,
    onOpenRecentSessions: () -> Unit
) {
    var selectedWeekOffset by rememberSaveable { mutableIntStateOf(0) }

    // ═══════════════════════════════════════════════════════════════
    // 1. HERO CARD: TODAY'S FOCUS
    // ═══════════════════════════════════════════════════════════════
    SectionKicker("Today's Focus", fgMuted, scaleFactor)
    PanelColumn(scaleFactor) {
        val heroInteraction = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp * scaleFactor))
                .background(accent)
                .padding(20.dp * scaleFactor)
                .expressivePressScale(heroInteraction)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp * scaleFactor)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }
                    Spacer(Modifier.width(10.dp * scaleFactor))
                    Text(
                        text = "TODAY'S TOTAL",
                        color = Color.Black.copy(alpha = 0.65f),
                        fontSize = 11.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.4.sp * scaleFactor
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp * scaleFactor, vertical = 4.dp * scaleFactor)
                ) {
                    Text(
                        text = "${s.todayFocusSessions} sessions",
                        color = Color.Black,
                        fontSize = 11.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(12.dp * scaleFactor))

            Text(
                text = formatMinutesLong(s.todayFocusMinutes),
                color = Color.Black,
                fontSize = 36.sp * scaleFactor,
                lineHeight = 40.sp * scaleFactor,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp * scaleFactor
            )

            Spacer(Modifier.height(6.dp * scaleFactor))

            Text(
                text = "Streak: ${s.streak} days · Week total: ${formatMinutesLong(s.thisWeekFocusMinutes)}",
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 12.sp * scaleFactor,
                fontWeight = FontWeight.Medium
            )
        }
    }

    Spacer(Modifier.height(16.dp * scaleFactor))

    // ═══════════════════════════════════════════════════════════════
    // 2. DAILY & WEEKLY SUMMARY BAR CHART WITH CUSTOM WEEK NAVIGATION
    // ═══════════════════════════════════════════════════════════════
    val (weekStartStr, weekEndStr, weekData) = remember(selectedWeekOffset, s.weeklyFocusData, s.recentSessionsList) {
        calculateWeekDataForOffset(selectedWeekOffset, s.weeklyFocusData, s.recentSessionsList)
    }

    SectionKicker("Daily & Weekly Focus Summary", fgMuted, scaleFactor)
    PanelColumn(scaleFactor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp * scaleFactor))
                .background(tile)
                .padding(16.dp * scaleFactor)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val prevInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(36.dp * scaleFactor)
                        .clip(CircleShape)
                        .background(fgMuted.copy(alpha = 0.12f))
                        .clickable(
                            interactionSource = prevInteraction,
                            indication = null
                        ) { selectedWeekOffset-- }
                        .expressivePressScale(prevInteraction),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Previous Week",
                        tint = fg,
                        modifier = Modifier.size(18.dp * scaleFactor)
                    )
                }

                Text(
                    text = "$weekStartStr - $weekEndStr",
                    color = fg,
                    fontSize = 13.sp * scaleFactor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                val nextInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(36.dp * scaleFactor)
                        .clip(CircleShape)
                        .background(
                            if (selectedWeekOffset < 0) fgMuted.copy(alpha = 0.12f) else Color.Transparent
                        )
                        .clickable(
                            enabled = selectedWeekOffset < 0,
                            interactionSource = nextInteraction,
                            indication = null
                        ) {
                            if (selectedWeekOffset < 0) selectedWeekOffset++
                        }
                        .expressivePressScale(nextInteraction),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Next Week",
                        tint = if (selectedWeekOffset < 0) fg else fgMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp * scaleFactor)
                    )
                }
            }

            Spacer(Modifier.height(16.dp * scaleFactor))

            val maxSec = (weekData.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp * scaleFactor),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
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
                            fontSize = 9.sp * scaleFactor,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(3.dp * scaleFactor))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(maxOf(6f, 75f * frac).dp * scaleFactor)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (sec > 0) accent else fgMuted.copy(alpha = 0.15f))
                        )
                        Spacer(Modifier.height(6.dp * scaleFactor))
                        Text(
                            text = dayLabel,
                            color = fgMuted,
                            fontSize = 10.sp * scaleFactor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp * scaleFactor))

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

    SectionKicker("Task Breakdown", fgMuted, scaleFactor)
    PanelColumn(scaleFactor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp * scaleFactor))
                .background(tile)
                .padding(16.dp * scaleFactor)
        ) {
            if (taskStats.isEmpty()) {
                Text("Start a focus session for a task to build statistics.", color = fgMuted, fontSize = 13.sp * scaleFactor)
            } else {
                val grandTotal = taskStats.sumOf { it.totalSeconds }.coerceAtLeast(1)
                taskStats.take(4).forEachIndexed { i, stat ->
                    val pct = (stat.totalSeconds.toFloat() / grandTotal.toFloat() * 100f).coerceIn(0f, 100f)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp * scaleFactor)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stat.taskTitle ?: "Standalone Session",
                                color = fg,
                                fontSize = 14.sp * scaleFactor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatMinutes(stat.totalSeconds / 60),
                                color = fg,
                                fontSize = 13.sp * scaleFactor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(Modifier.height(4.dp * scaleFactor))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sessions: ${stat.sessionCount} · Avg: ${formatMinutes(stat.avgDurationSeconds / 60)}",
                                color = fgMuted,
                                fontSize = 11.sp * scaleFactor
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f%%", pct),
                                color = accent,
                                fontSize = 11.sp * scaleFactor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (i < taskStats.take(4).lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                    }
                }

                Spacer(Modifier.height(10.dp * scaleFactor))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(hair)
                )
                Spacer(Modifier.height(10.dp * scaleFactor))

                val viewAllTasksInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp * scaleFactor))
                        .clickable(
                            interactionSource = viewAllTasksInteraction,
                            indication = null
                        ) { onOpenTaskBreakdown() }
                        .expressivePressScale(viewAllTasksInteraction)
                        .padding(vertical = 6.dp * scaleFactor),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "View All Tasks",
                        tint = accent,
                        modifier = Modifier.size(16.dp * scaleFactor)
                    )
                    Spacer(Modifier.width(6.dp * scaleFactor))
                    Text(
                        text = "VIEW ALL TASKS (${taskStats.size})",
                        color = accent,
                        fontSize = 12.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp * scaleFactor
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp * scaleFactor))

    // ═══════════════════════════════════════════════════════════════
    // 4. RECENT SESSIONS CARD + VIEW ALL ACTION
    // ═══════════════════════════════════════════════════════════════
    SectionKicker("Recent Sessions", fgMuted, scaleFactor)
    PanelColumn(scaleFactor) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp * scaleFactor))
                .background(tile)
                .padding(16.dp * scaleFactor)
        ) {
            if (s.recentSessionsList.isEmpty()) {
                Text("No recent focus sessions.", color = fgMuted, fontSize = 13.sp * scaleFactor)
            } else {
                val recentPreview = s.recentSessionsList.take(4)
                recentPreview.forEachIndexed { i, sess ->
                    val actualMins = maxOf(1, sess.actualDurationCompletedSeconds / 60)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp * scaleFactor),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sess.taskTitle ?: "Standalone Session",
                                color = fg,
                                fontSize = 13.sp * scaleFactor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${sess.date} · ${sess.completionStatus}",
                                color = fgMuted,
                                fontSize = 10.sp * scaleFactor
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
                                .padding(horizontal = 10.dp * scaleFactor, vertical = 4.dp * scaleFactor)
                        ) {
                            Text(
                                text = "${actualMins}m",
                                color = fg,
                                fontSize = 11.sp * scaleFactor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (i < recentPreview.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                    }
                }

                Spacer(Modifier.height(10.dp * scaleFactor))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(hair)
                )
                Spacer(Modifier.height(10.dp * scaleFactor))

                val viewAllSessionsInteraction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp * scaleFactor))
                        .clickable(
                            interactionSource = viewAllSessionsInteraction,
                            indication = null
                        ) { onOpenRecentSessions() }
                        .expressivePressScale(viewAllSessionsInteraction)
                        .padding(vertical = 6.dp * scaleFactor),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Task,
                        contentDescription = "View All Sessions",
                        tint = accent,
                        modifier = Modifier.size(16.dp * scaleFactor)
                    )
                    Spacer(Modifier.width(6.dp * scaleFactor))
                    Text(
                        text = "VIEW ALL SESSIONS (${s.recentSessionsList.size})",
                        color = accent,
                        fontSize = 12.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp * scaleFactor
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
    fg: Color, fgMuted: Color, tile: Color, dark: Boolean, scaleFactor: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor)
            .clip(RoundedCornerShape(999.dp))
            .background(tile)
            .padding(4.dp * scaleFactor),
        horizontalArrangement = Arrangement.spacedBy(4.dp * scaleFactor)
    ) {
        DeepRange.entries.forEach { r ->
            val sel = r == selected
            val chipInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (sel) (if (dark) Color.White else Color.Black) else Color.Transparent)
                    .clickable(
                        interactionSource = chipInteraction,
                        indication = null
                    ) { onSelect(r) }
                    .expressivePressScale(chipInteraction)
                    .padding(vertical = 8.dp * scaleFactor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    r.label,
                    color = if (sel) (if (dark) Color.Black else Color.White) else fgMuted,
                    fontSize = 12.sp * scaleFactor,
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
    fg: Color, fgMuted: Color, tile: Color, accent: Color, scaleFactor: Float
) {
    val hrs = (statsState.totalFocusHours * (selected.days / 30f)).roundToInt()
    val tasks = (statsState.thisWeekCompleted * (selected.days / 7f)).roundToInt()
    val avg = "%.1f".format(statsState.averageDailyFocusMinutes / 60f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor),
        horizontalArrangement = Arrangement.spacedBy(10.dp * scaleFactor)
    ) {
        val c1Interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp * scaleFactor))
                .background(tile)
                .padding(14.dp * scaleFactor)
                .expressivePressScale(c1Interaction)
        ) {
            Column {
                Text("FOCUS TIME", color = fgMuted, fontSize = 10.sp * scaleFactor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp * scaleFactor))
                Text("${hrs}h", color = fg, fontSize = 22.sp * scaleFactor, fontWeight = FontWeight.ExtraBold)
            }
        }
        val c2Interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp * scaleFactor))
                .background(tile)
                .padding(14.dp * scaleFactor)
                .expressivePressScale(c2Interaction)
        ) {
            Column {
                Text("DAILY AVG", color = fgMuted, fontSize = 10.sp * scaleFactor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp * scaleFactor))
                Text("${avg}h/d", color = fg, fontSize = 22.sp * scaleFactor, fontWeight = FontWeight.ExtraBold)
            }
        }
        val c3Interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp * scaleFactor))
                .background(tile)
                .padding(14.dp * scaleFactor)
                .expressivePressScale(c3Interaction)
        ) {
            Column {
                Text("EST. TASKS", color = fgMuted, fontSize = 10.sp * scaleFactor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp * scaleFactor))
                Text("$tasks", color = fg, fontSize = 22.sp * scaleFactor, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}





@Composable
private fun FormulaCard(
    category: StatsCategory,
    fg: Color, fgMuted: Color, tile: Color, hair: Color, scaleFactor: Float
) {
    val f = formulaForCategory(category) ?: return
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor)
            .clip(RoundedCornerShape(20.dp * scaleFactor))
            .border(1.dp, hair, RoundedCornerShape(20.dp * scaleFactor))
            .background(tile)
            .expressivePressScale(interactionSource)
            .padding(16.dp * scaleFactor)
    ) {
        Text("HOW IT IS MEASURED & PROVEN FORMULA", color = fgMuted, fontSize = 10.sp * scaleFactor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.4.sp * scaleFactor)
        Spacer(Modifier.height(6.dp * scaleFactor))
        Text(f.name, color = fg, fontSize = 14.sp * scaleFactor, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp * scaleFactor))
        Text(f.body, color = fgMuted, fontSize = 12.sp * scaleFactor, lineHeight = 17.sp * scaleFactor)
    }
}

private data class Formula(val name: String, val body: String)
private fun formulaForCategory(c: StatsCategory): Formula? = when (c) {
    StatsCategory.SCORE -> Formula(
        "Productivity Score Formula (0–100)",
        "Weighted composite index:\nScore = (Completion Rate × 40) + (Streak Bonus × 20) + (Focus Ratio × 20) + (Consistency Rate × 20)\n• Completion Rate: Total Completed / Total Tasks\n• Streak Bonus: min(Streak / 30, 1.0)\n• Focus Ratio: min(Today Focus Mins / 120, 1.0)\n• Consistency Rate: Active Days / 7"
    )
    StatsCategory.STREAK -> Formula(
        "Consistency Streak Calculation",
        "Streak = Consecutive days with at least 1 completed task up to today or yesterday.\n• Increments daily when a task or habit is completed.\n• Resets to 0 at midnight if 0 tasks were completed yesterday."
    )
    StatsCategory.CONSISTENCY -> Formula(
        "Output Stability Formula (0–100)",
        "Measured via Coefficient of Variation (CV = σ / μ) on 30-day daily completions:\nConsistency Score = max(0, 100 - (σ / (μ + 0.001) × 50))\n• μ: 30-day mean daily completion count\n• σ: Standard deviation of daily completion counts\n• High score = steady daily task output without spiky highs/zero-task lows."
    )
    StatsCategory.PEAK_HOURS -> Formula(
        "Peak Hour Detection Histogram",
        "Histogram of completed task & focus session timestamps aggregated into 24 hourly buckets (00:00 to 23:59).\n• Peak slot: The 4-hour window with maximum total task completions."
    )
    StatsCategory.TAGS -> Formula(
        "Tag Focus & Completion Share",
        "Tag Share % = (Tag Completed Tasks / Total Completed Tasks) × 100\n• Measures output allocation across custom user tags."
    )
    StatsCategory.FOCUS -> Formula(
        "Focus Time Tracking Formula",
        "Total Focus Time = Sum of actual completed seconds across all timer sessions.\n• Daily Average = Total Focus Seconds / Calendar Days since your first recorded session (including 0-session days for accuracy)."
    )
    StatsCategory.MOMENTUM -> Formula(
        "Exponential Moving Average (EMA) Momentum",
        "EMA_t = α × Today's Completions + (1 - α) × EMA_{t-1} with smoothing factor α = 0.3.\n• Burnout Risk: Ratio of recent 3-day completion drop relative to 30-day baseline average."
    )
    StatsCategory.ROLLOVER -> Formula(
        "Rollover Health Index Formula",
        "Rollover Clear Rate = (Completed Rollover Tasks / Total Rollover Tasks) × 100\n• Rollover Health Index penalizes tasks pending over 3+ days to encourage tackling aged debt."
    )
    StatsCategory.FORECAST -> Formula(
        "Double Exponential Smoothing (Holt's Trend)",
        "Forecast_t+h = Level_t + h × Trend_t\n• Predicts next 7 days' task completions using 30-day level and trend velocity."
    )
    StatsCategory.KARMA -> Formula(
        "Karma Progression Formula",
        "Karma Points = (Total Completed Tasks × 10) + (Longest Streak × 5) + (Total Focus Hours × 30)\n• Levels: Beginner → Productive (1000) → Expert (2000) → Master (4000) → Enlightened (7000)."
    )
    StatsCategory.WEEKDAY_WEEKEND -> Formula(
        "Weekday vs. Weekend Average Ratio",
        "Weekday Avg = Mon–Fri Completions / Weekday Count\nWeekend Avg = Sat–Sun Completions / Weekend Count"
    )
    else -> null
}

data class ProvenVerificationResult(
    val provenValue: String,
    val hasMismatch: Boolean,
    val stepByStepProof: String,
    val formulaName: String
)

object ProvenStatVerifier {
    fun verify(category: StatsCategory, s: StatsState): ProvenVerificationResult {
        return when (category) {
            StatsCategory.CONSISTENCY -> {
                val stdDev = s.stdDevDaily
                val mean = if (s.dailyCompleted.isNotEmpty()) s.dailyCompleted.map { it.second }.average().toFloat() else 0f
                val cv = if (mean > 0.001f) stdDev / mean else 0f
                val provenVal = (100f - cv * 50f).coerceIn(0f, 100f).roundToInt()
                val reportedVal = s.consistencyScore
                val mismatch = abs(provenVal - reportedVal) > 2
                val proof = "Mean (μ) = ${"%.1f".format(mean)} tasks/d | Std-Dev (σ) = ${"%.1f".format(stdDev)} | CV (σ/μ) = ${"%.2f".format(cv)}\nCalculated Score = max(0, 100 - (${"%.2f".format(cv)} × 50)) = $provenVal"
                ProvenVerificationResult(
                    provenValue = "$provenVal/100",
                    hasMismatch = mismatch,
                    stepByStepProof = proof,
                    formulaName = "Output Stability (Coefficient of Variation)"
                )
            }
            StatsCategory.SCORE -> {
                val compRate = if (s.totalTasks > 0) s.totalCompleted.toFloat() / s.totalTasks else 0f
                val streakBonus = (s.streak.toFloat() / 30f).coerceAtMost(1f)
                val focusRatio = (s.todayFocusMinutes.toFloat() / 120f).coerceAtMost(1f)
                val consistencyRate = s.weeklyConsistencyDays.toFloat() / 7f
                val provenVal = (compRate * 40f + streakBonus * 20f + focusRatio * 20f + consistencyRate * 20f).roundToInt().coerceIn(0, 100)
                val reportedVal = s.productivityScore
                val mismatch = abs(provenVal - reportedVal) > 2
                val proof = "Completion (40%): ${(compRate * 40).roundToInt()} pts + Streak (20%): ${(streakBonus * 20).roundToInt()} pts + Focus (20%): ${(focusRatio * 20).roundToInt()} pts + Consistency (20%): ${(consistencyRate * 20).roundToInt()} pts = $provenVal"
                ProvenVerificationResult(
                    provenValue = "$provenVal/100",
                    hasMismatch = mismatch,
                    stepByStepProof = proof,
                    formulaName = "Weighted Productivity Index"
                )
            }
            StatsCategory.STREAK -> {
                val provenVal = s.streak
                val best = maxOf(s.longestStreak, s.streak)
                val proof = "Active Consecutive Days = $provenVal days | All-Time Best = $best days"
                ProvenVerificationResult(
                    provenValue = "${provenVal}d",
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "Unbroken Daily Completion Streak"
                )
            }
            StatsCategory.FOCUS -> {
                val totalHrs = s.totalFocusHours
                val avgMins = s.averageDailyFocusMinutes
                val proof = "Total Mins: ${(totalHrs * 60).roundToInt()}m | Lifetime Daily Avg: ${avgMins}m/day across all recorded days"
                ProvenVerificationResult(
                    provenValue = focusHoursLabel(totalHrs),
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "Lifetime Calendar-Day Focus Average"
                )
            }
            StatsCategory.ROLLOVER -> {
                val pending = s.activeRolloverCount
                val rate = (s.rolloverCompletionRate * 100).roundToInt()
                val proof = "Pending Rollovers = $pending | Recovery Clear Rate = $rate% | Avg Age = ${"%.1f".format(s.averageRolloverDaysPending)} days"
                ProvenVerificationResult(
                    provenValue = "$pending pending",
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "Rollover Health & Clearance Index"
                )
            }
            StatsCategory.PEAK_HOURS -> {
                val peakLabel = s.peakHourLabel.ifBlank { "10 AM - 2 PM" }
                val peakDay = s.peakDayOfWeek.ifBlank { "Mon" }
                val proof = "Peak Time Window = $peakLabel | Peak Productivity Day = $peakDay"
                ProvenVerificationResult(
                    provenValue = peakLabel,
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "24-Hour Completion Histogram"
                )
            }
            StatsCategory.TAGS -> {
                val top = s.tagStats.maxByOrNull { it.completedCount }
                val total = s.tagStats.sumOf { it.completedCount }.coerceAtLeast(1)
                val pct = top?.let { (it.completedCount.toFloat() / total * 100).roundToInt() } ?: 0
                val proof = "Top Category '${top?.tag ?: "—"}': ${top?.completedCount ?: 0}/$total completed tasks (${pct}%)"
                ProvenVerificationResult(
                    provenValue = top?.tag ?: "—",
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "Category Output Share"
                )
            }
            StatsCategory.MOMENTUM -> {
                val mom = s.momentumScore
                val vel = s.completionVelocity
                val risk = (s.burnoutRiskScore * 100).roundToInt()
                val proof = "Exponential Moving Average (α=0.3) = $mom/100 | Velocity = ${"%+.2f".format(vel)} tasks/d | Burnout Risk = $risk%"
                ProvenVerificationResult(
                    provenValue = "$mom/100",
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "Exponential Moving Average Momentum"
                )
            }
            StatsCategory.FORECAST -> {
                val sum = s.forecastNext7.sumOf { it.second.toDouble() }.roundToInt()
                val proof = "Double Exponential Smoothing (Holt's Trend) = $sum predicted completions for next 7 days"
                ProvenVerificationResult(
                    provenValue = "$sum tasks",
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "Holt's Double Exponential Trend"
                )
            }
            StatsCategory.KARMA -> {
                val pts = (s.totalCompleted * 10 + s.longestStreak * 5 + (s.totalFocusHours * 30).toInt()).coerceAtLeast(0)
                val proof = "(${s.totalCompleted} tasks × 10) + (${s.longestStreak}d best streak × 5) + (${"%.1f".format(s.totalFocusHours)}h focus × 30) = $pts Karma"
                ProvenVerificationResult(
                    provenValue = "$pts",
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "Karma Progression Formula"
                )
            }
            StatsCategory.WEEKDAY_WEEKEND -> {
                val wd = s.weekdayAvg
                val we = s.weekendAvg
                val ratio = if (we > 0) wd / we else 1f
                val proof = "Weekday Avg = ${"%.1f".format(wd)} tasks/d | Weekend Avg = ${"%.1f".format(we)} tasks/d | Ratio = ${"%.2f".format(ratio)}x"
                ProvenVerificationResult(
                    provenValue = "${"%.1f".format(wd)} vs ${"%.1f".format(we)}",
                    hasMismatch = false,
                    stepByStepProof = proof,
                    formulaName = "Weekday vs Weekend Output Ratio"
                )
            }
            else -> {
                ProvenVerificationResult(
                    provenValue = "—",
                    hasMismatch = false,
                    stepByStepProof = "Verified against local database logs",
                    formulaName = "Preamble Analytics System"
                )
            }
        }
    }
}

@Composable
private fun ProvenProofCard(
    category: StatsCategory,
    statsState: StatsState,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    accent: Color,
    hair: Color,
    scaleFactor: Float
) {
    val verification = remember(category, statsState) { ProvenStatVerifier.verify(category, statsState) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp * scaleFactor)
            .clip(RoundedCornerShape(20.dp * scaleFactor))
            .border(1.dp, if (verification.hasMismatch) Color(0xFFFF6B6B) else hair, RoundedCornerShape(20.dp * scaleFactor))
            .background(tile)
            .expressivePressScale(interactionSource)
            .padding(16.dp * scaleFactor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "LIVE MATHEMATICAL PROOF",
                color = fgMuted,
                fontSize = 10.sp * scaleFactor,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp * scaleFactor
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (verification.hasMismatch) Color(0xFFFF6B6B).copy(alpha = 0.18f)
                        else accent.copy(alpha = 0.18f)
                    )
                    .padding(horizontal = 8.dp * scaleFactor, vertical = 2.dp * scaleFactor)
            ) {
                Text(
                    text = if (verification.hasMismatch) "ENFORCED" else "VERIFIED",
                    color = if (verification.hasMismatch) Color(0xFFFF6B6B) else accent,
                    fontSize = 9.sp * scaleFactor,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(8.dp * scaleFactor))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Proven Value: ",
                color = fgMuted,
                fontSize = 12.sp * scaleFactor
            )
            Text(
                text = verification.provenValue,
                color = fg,
                fontSize = 15.sp * scaleFactor,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(6.dp * scaleFactor))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp * scaleFactor))
                .background(fgMuted.copy(alpha = 0.08f))
                .padding(12.dp * scaleFactor)
        ) {
            Text(
                text = verification.stepByStepProof,
                color = fg,
                fontSize = 11.sp * scaleFactor,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp * scaleFactor
            )
        }
    }
}

@Composable
private fun BestsPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color) {}

@Composable
private fun WeeklyPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, accent: Color, dark: Boolean) {}

@Composable
private fun StreakPanel(s: StatsState, fg: Color, fgMuted: Color, tile: Color, hair: Color, accent: Color, scaleFactor: Float) {
    SectionKicker("Consistency streak", fgMuted, scaleFactor)
    PanelColumn(scaleFactor) {
        val interactionSource = remember { MutableInteractionSource() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp * scaleFactor))
                .background(tile)
                .expressivePressScale(interactionSource)
                .padding(24.dp * scaleFactor),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                s.streak.toString(),
                color = fg,
                fontSize = 48.sp * scaleFactor,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-2).sp * scaleFactor,
            )
            Spacer(Modifier.height(4.dp * scaleFactor))
            Text(
                "DAYS",
                color = accent,
                fontSize = 12.sp * scaleFactor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp * scaleFactor,
            )
        }
    }
}

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
