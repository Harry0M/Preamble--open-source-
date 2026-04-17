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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.theblankstate.preamble.ui.components.CustomDateRangeDialog
import com.theblankstate.preamble.ui.components.DAY_MS
import com.theblankstate.preamble.ui.components.RangeComparisonCapsules
import com.theblankstate.preamble.ui.components.RangeStats
import com.theblankstate.preamble.ui.components.computePeriodComparison
import com.theblankstate.preamble.ui.components.formatRangeLabel
import com.theblankstate.preamble.ui.components.rememberRangeStats
import com.theblankstate.preamble.ui.components.sliceByMs
import com.theblankstate.preamble.ui.components.sliceByRange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.ads.FeatureGateManager
import com.theblankstate.preamble.ui.components.AnimatedCircularGauge
import com.theblankstate.preamble.ui.components.BurnoutRiskChip
import com.theblankstate.preamble.ui.components.ComparisonRow
import com.theblankstate.preamble.ui.components.CompletionTrendsDetailSheet
import com.theblankstate.preamble.ui.components.ConsistencyDots
import com.theblankstate.preamble.ui.components.DonutChart
import com.theblankstate.preamble.ui.components.FeatureType
import com.theblankstate.preamble.ui.components.FeatureUnlockSheet
import com.theblankstate.preamble.ui.components.HeatmapCalendar
import com.theblankstate.preamble.ui.components.IntelligenceMetricRow
import com.theblankstate.preamble.ui.components.KeepActiveHealthDetailSheet
import com.theblankstate.preamble.ui.components.PomodoroAnalyticsDetailSheet
import com.theblankstate.preamble.ui.components.ProductivityScoreDetailSheet
import com.theblankstate.preamble.ui.components.StatsSectionCard
import com.theblankstate.preamble.ui.components.StreakConsistencyDetailSheet
import com.theblankstate.preamble.ui.components.TagAnalyticsDetailSheet
import com.theblankstate.preamble.ui.components.TagStatRow
import com.theblankstate.preamble.ui.components.TaskBreakdownDetailSheet
import com.theblankstate.preamble.ui.components.TrendArrow
import com.theblankstate.preamble.ui.components.TrendIntelligenceDetailSheet
import com.theblankstate.preamble.viewmodel.StatsState

private val donutColors = listOf(
    Color(0xFF6366F1), // Local - Indigo
    Color(0xFF22C55E), // Recurring - Green
    Color(0xFFF59E0B), // Keep Active - Amber
    Color(0xFF3B82F6), // Google Calendar - Blue
    Color(0xFFEF4444)  // Google Tasks - Red
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    statsState: StatsState,
    onRefreshStats: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val statsUnlocked by FeatureGateManager.statsUnlocked.collectAsState()
    var showStatsUnlockSheet by remember { mutableStateOf(false) }
    // Detail screen states
    var showScoreDetail by remember { mutableStateOf(false) }
    var showStreakDetail by remember { mutableStateOf(false) }
    var showTrendsDetail by remember { mutableStateOf(false) }
    var showPomodoroDetail by remember { mutableStateOf(false) }
    var showBreakdownDetail by remember { mutableStateOf(false) }
    var showKeepActiveDetail by remember { mutableStateOf(false) }
    var showTagDetail by remember { mutableStateOf(false) }
    var showTrendIntelDetail by remember { mutableStateOf(false) }

    // ── Range selection state ──
    var selectedRange by remember { mutableIntStateOf(14) }
    var periodOffset by remember { mutableIntStateOf(0) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val isRangeActive = customRange != null || periodOffset > 0

    // Range-sliced stats (current + previous period)
    val (rangeStats, prevRangeStats) = rememberRangeStats(
        statsState.dailyStatsWithDates, selectedRange, periodOffset, customRange
    )

    // Auto-refresh stats on screen composition
    LaunchedEffect(Unit) { onRefreshStats?.invoke() }

    // Derive previous period data for chart overlays from range-sliced data
    val prev14DayCompleted = remember(rangeStats.dailyDone, prevRangeStats.dailyDone) {
        if (prevRangeStats.dailyDone.isNotEmpty()) prevRangeStats.dailyDone
        else {
            val all = statsState.dailyStatsWithDates
            if (all.size >= 28) all.takeLast(28).take(14).map { it.second }
            else if (all.size > 14) all.take(all.size - 14).map { it.second }
            else null
        }
    }
    val prev7DayRate = remember(rangeStats.dailyRates, prevRangeStats.dailyRates) {
        if (prevRangeStats.dailyRates.isNotEmpty()) prevRangeStats.dailyRates.map { (it * 100).toInt() }
        else {
            val all = statsState.dailyStatsWithDates
            if (all.size >= 14) {
                all.takeLast(14).take(7).map { (_, done, total) ->
                    if (total > 0) (done.toFloat() / total * 100).toInt() else 0
                }
            } else null
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Statistics", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Date range")
                    }
                    if (onRefreshStats != null) {
                        IconButton(onClick = onRefreshStats) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh stats")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ═══════════════════════════════════════════
            // Range Selector (always visible)
            // ═══════════════════════════════════════════
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Period label + nav arrows
                    val periodLabel = customRange?.let { formatRangeLabel(it.first, it.second) }
                        ?: when (periodOffset) {
                            0 -> "Last $selectedRange days"
                            else -> "${selectedRange * periodOffset + 1}–${selectedRange * (periodOffset + 1)}d ago"
                        }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            periodLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRangeActive) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { periodOffset++ }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.TrendingDown, "Previous", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { if (customRange != null) customRange = null else if (periodOffset > 0) periodOffset-- },
                                enabled = periodOffset > 0 || customRange != null,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, "Next", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    // Range chips
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(7, 14, 30, 60, 90).forEach { range ->
                            FilterChip(
                                selected = selectedRange == range && customRange == null,
                                onClick = { selectedRange = range; periodOffset = 0; customRange = null },
                                label = { Text("${range}D") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Range comparison capsules (when range is active)
            if (rangeStats.days > 0) {
                item {
                    RangeComparisonCapsules(rangeStats, prevRangeStats)
                }
            }

            // ═══════════════════════════════════════════
            // Section 1: Productivity Score (Hero — M3 Expressive)
            // ═══════════════════════════════════════════
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Productivity Score",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Expressive animated gauge — clean, no background
                    AnimatedCircularGauge(
                        score = statsState.productivityScore,
                        primaryColor = primaryColor,
                        modifier = Modifier.size(180.dp)
                    )
                    // Grade + Karma capsules
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val gradeColor = when (statsState.performanceGrade) {
                            "A" -> Color(0xFF4CAF50)
                            "B" -> Color(0xFF8BC34A)
                            "C" -> Color(0xFFFFC107)
                            "D" -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(gradeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Grade ${statsState.performanceGrade}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = gradeColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(primaryColor.copy(alpha = 0.1f))
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${statsState.karmaLevel} · ${statsState.karmaPoints} pts",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                    }
                    TrendArrow(trend = statsState.productivityScoreTrend)
                    // Tap for details
                    if (statsUnlocked) {
                        Text(
                            "Tap for details →",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor.copy(alpha = 0.6f),
                            modifier = Modifier.clickable { showScoreDetail = true }
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 1.5: Smart Insights Feed (M3 Expressive — no bg)
            // ═══════════════════════════════════════════
            if (statsState.smartInsights.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Smart Insights",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        val visibleInsights = if (statsUnlocked) statsState.smartInsights else statsState.smartInsights.take(2)
                        visibleInsights.forEach { insight ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    when {
                                        insight.contains("streak", ignoreCase = true) -> Icons.Filled.LocalFireDepartment
                                        insight.contains("peak", ignoreCase = true) || insight.contains("best", ignoreCase = true) -> Icons.Filled.EmojiEvents
                                        insight.contains("slow", ignoreCase = true) || insight.contains("drop", ignoreCase = true) -> Icons.Filled.Warning
                                        insight.contains("focus", ignoreCase = true) || insight.contains("pomodoro", ignoreCase = true) -> Icons.Filled.Timer
                                        else -> Icons.AutoMirrored.Filled.TrendingUp
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                )
                                Text(
                                    insight,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (!statsUnlocked && statsState.smartInsights.size > 2) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable { showStatsUnlockSheet = true }
                                    .padding(top = 4.dp)
                            ) {
                                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(14.dp), tint = primaryColor)
                                Text(
                                    "${statsState.smartInsights.size - 2} more insights · Unlock Premium",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = primaryColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }


            // ═══════════════════════════════════════════
            // Section 2: Today's Focus Dashboard (capsule grid)
            // ═══════════════════════════════════════════
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatsCapsuleTile(
                            label = "Tasks Done",
                            value = "${statsState.todayCompleted}/${statsState.todayTotal}",
                            icon = Icons.Filled.CheckCircle,
                            tint = Color(0xFF45C9C0),
                            isChecked = statsState.todayCompleted > 0,
                            modifier = Modifier.weight(1f)
                        )
                        StatsCapsuleTile(
                            label = "Focus Sessions",
                            value = if (statsState.todayPomodoroSessions > 0)
                                "${statsState.todayPomodoroSessions}x"
                            else "—",
                            icon = Icons.Filled.Timer,
                            tint = Color(0xFFDDE1E8),
                            isChecked = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatsCapsuleTile(
                            label = "Streak",
                            value = "${statsState.streak} days",
                            icon = Icons.Filled.LocalFireDepartment,
                            tint = Color(0xFF45C9C0),
                            isChecked = statsState.streak > 0,
                            modifier = Modifier.weight(1f)
                        )
                        StatsCapsuleTile(
                            label = "This Week",
                            value = "${statsState.thisWeekCompleted} done",
                            icon = Icons.Filled.Leaderboard,
                            tint = Color(0xFFDDE1E8),
                            isChecked = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 3: Streak & Consistency
            // ═══════════════════════════════════════════
            item {
                StatsSectionCard(
                    title = "Streak & Consistency",
                    onDetailClick = if (statsUnlocked) {{ showStreakDetail = true }} else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current streak — filled circle
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${statsState.streak}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Longest streak — muted circle
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${statsState.longestStreak}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Longest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // This week — capsule
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (statsState.weeklyConsistencyDays >= 5)
                                            primaryColor.copy(alpha = 0.18f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${statsState.weeklyConsistencyDays}/7",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (statsState.weeklyConsistencyDays >= 5)
                                        primaryColor
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("This Week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ConsistencyDots(consistencyDays = statsState.weeklyConsistencyDays, primaryColor = primaryColor)
                    if (statsState.monthlyHeatmap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("This Month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        HeatmapCalendar(heatmap = statsState.monthlyHeatmap, primaryColor = primaryColor)
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 4: Completion Trends (Free)
            // ═══════════════════════════════════════════
            item {
                StatsSectionCard(
                    title = "Completion Trends",
                    onDetailClick = if (statsUnlocked) {{ showTrendsDetail = true }} else null
                ) {
                    // Range-aware comparison rows
                    if (isRangeActive && rangeStats.days > 0) {
                        // Show range vs previous period comparison
                        ComparisonRow(
                            label = "Selected vs Previous",
                            current = rangeStats.totalDone,
                            previous = prevRangeStats.totalDone,
                            currentLabel = "${rangeStats.days}d",
                            previousLabel = "prev ${prevRangeStats.days}d",
                            primaryColor = primaryColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ComparisonRow(
                            label = "Avg / Day",
                            current = rangeStats.avgPerDay.toInt(),
                            previous = prevRangeStats.avgPerDay.toInt(),
                            currentLabel = "selected",
                            previousLabel = "previous",
                            primaryColor = primaryColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ComparisonRow(
                            label = "Completion Rate",
                            current = (rangeStats.completionRate * 100).toInt(),
                            previous = (prevRangeStats.completionRate * 100).toInt(),
                            currentLabel = "selected %",
                            previousLabel = "previous %",
                            primaryColor = primaryColor
                        )
                    } else {
                        // Default: fixed period comparisons
                        ComparisonRow(
                            label = "Today vs Yesterday",
                            current = statsState.todayCompleted,
                            previous = statsState.yesterdayCompleted,
                            currentLabel = "today",
                            previousLabel = "yesterday",
                            primaryColor = primaryColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ComparisonRow(
                            label = "This Week vs Last",
                            current = statsState.thisWeekCompleted,
                            previous = statsState.lastWeekCompleted,
                            currentLabel = "this week",
                            previousLabel = "last week",
                            primaryColor = primaryColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ComparisonRow(
                            label = "This Month vs Last",
                            current = statsState.thisMonthCompleted,
                            previous = statsState.lastMonthCompleted,
                            currentLabel = "this month",
                            previousLabel = "last month",
                            primaryColor = primaryColor
                        )
                    }

                    // Range-aware line chart
                    val chartData = if (isRangeActive && rangeStats.dailyDone.isNotEmpty()) {
                        rangeStats.dailyLabels.zip(rangeStats.dailyDone)
                    } else {
                        statsState.dailyCompleted
                    }
                    if (chartData.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (isRangeActive) "Completions" else "Last 14 Days",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        com.theblankstate.preamble.ui.components.StyledLineChart(
                            data = chartData,
                            primaryColor = primaryColor,
                            modifier = Modifier.fillMaxWidth(),
                            previousData = if (isRangeActive) prevRangeStats.dailyDone.takeIf { it.isNotEmpty() } else prev14DayCompleted,
                            previousLabel = if (isRangeActive) "Prev period" else "Prev 14d"
                        )
                    }

                    // Range-aware bar chart
                    val barData = if (isRangeActive && rangeStats.dailyRates.isNotEmpty()) {
                        rangeStats.dailyLabels.zip(rangeStats.dailyRates.map { (it * 100).toInt() })
                    } else {
                        statsState.weeklyStats.map { (day, rate) -> day to (rate * 100).toInt() }
                    }
                    if (barData.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (isRangeActive) "Completion Rate" else "Weekly Rate",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        com.theblankstate.preamble.ui.components.StyledBarChart(
                            data = barData,
                            primaryColor = primaryColor,
                            modifier = Modifier.fillMaxWidth(),
                            previousData = if (isRangeActive) prevRangeStats.dailyRates.map { (it * 100).toInt() }.takeIf { it.isNotEmpty() } else prev7DayRate,
                            previousLabel = if (isRangeActive) "Prev period" else "Prev week"
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 5: Pomodoro Analytics (PREMIUM)
            // ═══════════════════════════════════════════
            item {
                StatsSectionCard(
                    title = "Pomodoro Analytics",
                    isLocked = !statsUnlocked,
                    onLockedClick = { showStatsUnlockSheet = true },
                    onDetailClick = if (statsUnlocked) {{ showPomodoroDetail = true }} else null
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                String.format("%.1f", statsState.totalFocusHours),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Text("Total Hours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${statsState.averageDailyFocusMinutes}m",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Avg Daily", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (statsState.bestFocusDay != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(primaryColor.copy(alpha = 0.08f)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.EmojiEvents, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Best day: ${statsState.bestFocusDay} (${statsState.bestFocusDayMinutes}m)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    if (statsState.weeklyFocusData.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Weekly Focus", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        com.theblankstate.preamble.ui.components.StyledCapsuleChart(
                            data = statsState.weeklyFocusData,
                            primaryColor = primaryColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (statsState.topFocusedTasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Most Focused Tasks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        statsState.topFocusedTasks.forEachIndexed { index, task ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = primaryColor, modifier = Modifier.width(20.dp))
                                    Text(task.taskTitle ?: "Untitled", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("${task.sessionCount}x  ${task.totalSeconds / 60}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 6: Task Breakdown by Type (PREMIUM)
            // ═══════════════════════════════════════════
            item {
                StatsSectionCard(
                    title = "Task Breakdown",
                    isLocked = !statsUnlocked,
                    onLockedClick = { showStatsUnlockSheet = true },
                    onDetailClick = if (statsUnlocked) {{ showBreakdownDetail = true }} else null
                ) {
                    if (statsState.taskTypeBreakdown.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DonutChart(
                                segments = statsState.taskTypeBreakdown.map { it.label to it.percentage },
                                colors = donutColors,
                                modifier = Modifier.size(100.dp)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                statsState.taskTypeBreakdown.forEachIndexed { index, item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(donutColors[index % donutColors.size]))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(item.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                                        Text("${item.count}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${(item.percentage * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = primaryColor)
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No tasks yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 7: Rollover Task Health (PREMIUM)
            // ═══════════════════════════════════════════
            item {
                StatsSectionCard(
                    title = "Keep Active Health",
                    isLocked = !statsUnlocked,
                    onLockedClick = { showStatsUnlockSheet = true },
                    onDetailClick = if (statsUnlocked) {{ showKeepActiveDetail = true }} else null
                ) {
                    if (statsState.activeRolloverCount > 0 || statsState.rolloverCompletionRate > 0f) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${statsState.activeRolloverCount}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (statsState.activeRolloverCount > 5) Color(0xFFF44336) else primaryColor
                                )
                                Text("Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val avgColor = when {
                                    statsState.averageRolloverDaysPending > 7 -> Color(0xFFF44336)
                                    statsState.averageRolloverDaysPending > 3 -> Color(0xFFF59E0B)
                                    else -> Color(0xFF4CAF50)
                                }
                                Text(
                                    String.format("%.1f", statsState.averageRolloverDaysPending),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = avgColor
                                )
                                Text("Avg Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "${(statsState.rolloverCompletionRate * 100).toInt()}%",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("Done Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (statsState.oldestRolloverTaskTitle != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFFF44336).copy(alpha = 0.08f)).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Warning, null, tint = Color(0xFFF44336), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Oldest: ${statsState.oldestRolloverDays} days", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                    Text(statsState.oldestRolloverTaskTitle ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else {
                        Text("No keep-active tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 8: Tag Analytics (PREMIUM)
            // ═══════════════════════════════════════════
            item {
                StatsSectionCard(
                    title = "Tag Analytics",
                    isLocked = !statsUnlocked,
                    onLockedClick = { showStatsUnlockSheet = true },
                    onDetailClick = if (statsUnlocked) {{ showTagDetail = true }} else null
                ) {
                    if (statsState.tagStats.isNotEmpty()) {
                        statsState.tagStats.take(8).forEachIndexed { index, tag ->
                            TagStatRow(tag = tag.tag, totalCount = tag.totalCount, completionRate = tag.completionRate, primaryColor = primaryColor, isMostActive = index == 0)
                        }
                    } else {
                        Text("Add tags to your tasks to see analytics", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 9: Trend Intelligence (PREMIUM)
            // ═══════════════════════════════════════════
            item {
                StatsSectionCard(
                    title = "Trend Intelligence",
                    isLocked = !statsUnlocked,
                    onLockedClick = { showStatsUnlockSheet = true },
                    onDetailClick = if (statsUnlocked) {{ showTrendIntelDetail = true }} else null
                ) {
                    // Use range-sliced metrics when active, otherwise global stats
                    val displayMomentum = if (isRangeActive) rangeStats.momentum else statsState.momentumScore
                    val displayConsistency = if (isRangeActive) rangeStats.consistencyScore else statsState.consistencyScore
                    val displayVelocity = if (isRangeActive) rangeStats.velocity else statsState.completionVelocity
                    val displayBurnout = if (isRangeActive) rangeStats.burnoutRisk else statsState.burnoutRiskScore
                    val displayPeakDay = if (isRangeActive) rangeStats.peakDay else statsState.peakDayOfWeek
                    val displayMovingAvg = if (isRangeActive) rangeStats.avgPerDay else statsState.movingAvg7Day

                    // Momentum & Consistency bars
                    val momentumColor = when {
                        displayMomentum >= 65 -> Color(0xFF4CAF50)
                        displayMomentum >= 35 -> Color(0xFFF59E0B)
                        else -> Color(0xFFF44336)
                    }
                    val consistencyColor = when {
                        displayConsistency >= 65 -> Color(0xFF4CAF50)
                        displayConsistency >= 35 -> Color(0xFFF59E0B)
                        else -> primaryColor
                    }

                    IntelligenceMetricRow(
                        label = "Momentum",
                        score = displayMomentum,
                        primaryColor = primaryColor,
                        barColor = momentumColor
                    )
                    IntelligenceMetricRow(
                        label = "Consistency",
                        score = displayConsistency,
                        primaryColor = primaryColor,
                        barColor = consistencyColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Velocity row
                    val velocityPositive = displayVelocity >= 0f
                    val velocityColor = if (velocityPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(velocityColor.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Completion Velocity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${if (velocityPositive) "+" else ""}${String.format("%.2f", displayVelocity)} tasks/day",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = velocityColor
                            )
                        }
                        Icon(
                            imageVector = if (velocityPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = velocityColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Burnout risk + Peak day row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Burnout Risk", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            BurnoutRiskChip(risk = displayBurnout)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Peak Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(primaryColor.copy(alpha = 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    displayPeakDay.ifBlank { "—" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 7-day moving average
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isRangeActive) "Avg / Day" else "7-Day Moving Avg", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            String.format("%.1f tasks/day", displayMovingAvg),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }


                }
            }

            // ═══════════════════════════════════════════
            // Global Rank (existing, gated)
            // ═══════════════════════════════════════════
            item {
                val mockRank = remember(statsState.totalCompleted) {
                    val seed = statsState.totalCompleted.toLong()
                    (50000 - (seed * 137 % 49000)).coerceAtLeast(1)
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { if (!statsUnlocked) showStatsUnlockSheet = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Leaderboard, contentDescription = "Global Rank", tint = primaryColor, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Global Rank", style = MaterialTheme.typography.titleMedium)
                            }
                            if (!statsUnlocked) {
                                Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (statsUnlocked) {
                            Text("#$mockRank", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = primaryColor)
                            Text("out of 127,493 users worldwide", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("Watch an ad to see your rank among all Preamble users", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // Motivational message
            item {
                val message = when {
                    statsState.streak >= 7 -> "Amazing! You're on fire!"
                    statsState.streak >= 3 -> "Great consistency! Keep it up!"
                    statsState.todayCompleted == statsState.todayTotal && statsState.todayTotal > 0 -> "All done for today!"
                    statsState.todayTotal > 0 -> "${statsState.todayTotal - statsState.todayCompleted} tasks left. You got this!"
                    else -> "Add some tasks to get started!"
                }
                Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Ad placement
            item {
                val bannerAdView = remember { mutableStateOf<AdView?>(null) }
                val lifecycleOwner = LocalLifecycleOwner.current
                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> bannerAdView.value?.pause()
                            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> bannerAdView.value?.resume()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        bannerAdView.value?.destroy()
                        bannerAdView.value = null
                    }
                }
                val adShape = RoundedCornerShape(16.dp)
                val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                val surface = MaterialTheme.colorScheme.surface
                val borderColor = primaryColor.copy(alpha = 0.15f)
                Box(
                    modifier = Modifier.fillMaxWidth().clip(adShape)
                        .background(Brush.verticalGradient(listOf(surfaceVariant, primaryColor.copy(alpha = 0.06f), surface)))
                        .border(1.dp, Brush.verticalGradient(listOf(borderColor, primaryColor.copy(alpha = 0.08f), borderColor)), adShape)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            AdView(ctx).apply {
                                setAdSize(AdSize.MEDIUM_RECTANGLE)
                                adUnitId = BuildConfig.AD_BANNER_UNIT_ID
                                loadAd(AdRequest.Builder().build())
                                bannerAdView.value = this
                            }
                        },
                        onRelease = { adView -> adView.destroy() },
                        modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showStatsUnlockSheet && activity != null) {
        FeatureUnlockSheet(featureType = FeatureType.STATS, activity = activity, onDismiss = { showStatsUnlockSheet = false })
    }


    // ═══ Detail Sheets ═══
    if (showScoreDetail) {
        ProductivityScoreDetailSheet(stats = statsState, onDismiss = { showScoreDetail = false })
    }
    if (showStreakDetail) {
        StreakConsistencyDetailSheet(stats = statsState, onDismiss = { showStreakDetail = false })
    }
    if (showTrendsDetail) {
        CompletionTrendsDetailSheet(stats = statsState, onDismiss = { showTrendsDetail = false })
    }
    if (showPomodoroDetail) {
        PomodoroAnalyticsDetailSheet(stats = statsState, onDismiss = { showPomodoroDetail = false })
    }
    if (showBreakdownDetail) {
        TaskBreakdownDetailSheet(stats = statsState, onDismiss = { showBreakdownDetail = false })
    }
    if (showKeepActiveDetail) {
        KeepActiveHealthDetailSheet(stats = statsState, onDismiss = { showKeepActiveDetail = false })
    }
    if (showTagDetail) {
        TagAnalyticsDetailSheet(stats = statsState, onDismiss = { showTagDetail = false })
    }
    if (showTrendIntelDetail) {
        TrendIntelligenceDetailSheet(stats = statsState, onDismiss = { showTrendIntelDetail = false })
    }

    // ═══ Date Range Picker Dialog ═══
    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs -> customRange = startMs to endMs }
        )
    }
}

@Composable
private fun StatsCapsuleTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    isChecked: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.95f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (isChecked) Color(0xFF0D7772)
                    else Color(0xFFCFD5DE)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isChecked) Color.White else Color(0xFF4B88EA),
                modifier = Modifier.size(18.dp)
            )
            if (isChecked) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WaveChart(
    data: List<Pair<String, Int>>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (data.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val maxVal = data.maxOf { it.second }.coerceAtLeast(1)
        val padding = 4.dp.toPx()
        val points = data.mapIndexed { index, (_, value) ->
            val x = padding + (index.toFloat() / (data.size - 1).coerceAtLeast(1)) * (w - 2 * padding)
            val y = h - padding - (value.toFloat() / maxVal) * (h - 2 * padding)
            Offset(x, y)
        }
        if (points.size >= 2) {
            val fillPath = Path().apply {
                moveTo(points.first().x, h)
                lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cx1 = (prev.x + curr.x) / 2
                    cubicTo(cx1, prev.y, cx1, curr.y, curr.x, curr.y)
                }
                lineTo(points.last().x, h)
                close()
            }
            drawPath(path = fillPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.02f)), startY = 0f, endY = h))
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cx1 = (prev.x + curr.x) / 2
                    cubicTo(cx1, prev.y, cx1, curr.y, curr.x, curr.y)
                }
            }
            drawPath(path = linePath, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        points.forEach { point -> drawCircle(color = color, radius = 3.dp.toPx(), center = point) }
    }
}
