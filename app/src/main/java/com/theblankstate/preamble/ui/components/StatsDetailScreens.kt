package com.theblankstate.preamble.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.viewmodel.StatsState

// ═══════════════════════════════════════════════════════════════════════════════
// Shared M3 Expressive Detail Sheet Wrapper
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDetailSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            // Clean title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Period Navigation Component (Google Fit-style < This Week > 📅)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PeriodNavigator(
    periodLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            periodLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ChevronLeft, "Previous", modifier = Modifier.size(24.dp))
            }
            IconButton(
                onClick = onNext,
                enabled = canGoNext,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.ChevronRight, "Next", modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Filled.CalendarMonth,
                "Calendar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Clean Detail Card (M3 Expressive - SurfaceContainerLow, no gradients)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Column { content() }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Hero Stat Icon (M3 Expressive — icon + value + label, clean)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun HeroStat(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Comparison Chip (shows delta with icon)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ComparisonChip(
    current: Int,
    previous: Int,
    label: String
) {
    val delta = current - previous
    val isPositive = delta >= 0
    val color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
            null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Text(
            "${if (isPositive) "+" else ""}$delta $label",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 1. Productivity Score Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ProductivityScoreDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var periodOffset by remember { mutableIntStateOf(0) }
    val periodLabels = listOf("Today", "Yesterday", "2 days ago", "3 days ago", "4 days ago", "5 days ago", "6 days ago")
    val periodLabel = periodLabels.getOrElse(periodOffset) { "${periodOffset}d ago" }

    StatsDetailSheet("Productivity Score", onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodNavigator(
                    periodLabel = periodLabel,
                    onPrev = { periodOffset++ },
                    onNext = { if (periodOffset > 0) periodOffset-- },
                    canGoNext = periodOffset > 0
                )
            }

            // Hero Score
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    val gradeColor = when (stats.performanceGrade) {
                        "A" -> Color(0xFF4CAF50)
                        "B" -> Color(0xFF8BC34A)
                        "C" -> Color(0xFFFFC107)
                        "D" -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                    Text(
                        "${stats.productivityScore}",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "out of 100  ·  Grade ${stats.performanceGrade}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Comparison with yesterday
                    if (stats.productivityScoreHistory.size >= 2) {
                        val todayScore = stats.productivityScoreHistory.lastOrNull()?.second ?: 0
                        val prevScore = stats.productivityScoreHistory.getOrNull(stats.productivityScoreHistory.size - 2)?.second ?: 0
                        ComparisonChip(todayScore, prevScore, "vs yesterday")
                    }
                }
            }

            // Score Breakdown
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Speed, title = "Score Breakdown")
                    Spacer(modifier = Modifier.height(12.dp))
                    CleanProgressRow("Completion Rate", stats.completionRate30Day, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    CleanProgressRow("Streak Bonus", (stats.streak.toFloat() / 30f).coerceAtMost(1f), Color(0xFFFF9800))
                    Spacer(modifier = Modifier.height(10.dp))
                    CleanProgressRow("Focus Ratio", (stats.todayFocusMinutes.toFloat() / 120f).coerceAtMost(1f), Color(0xFF2196F3))
                    Spacer(modifier = Modifier.height(10.dp))
                    CleanProgressRow("Consistency", stats.weeklyConsistencyDays.toFloat() / 7f, Color(0xFF9C27B0))
                }
            }

            // Completion Rates Comparison
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.CalendarMonth, title = "Completion Rates")
                    Spacer(modifier = Modifier.height(12.dp))
                    CleanMetricRow("Today", "${(stats.completionRateToday * 100).toInt()}%")
                    CleanMetricRow("7-Day Avg", "${(stats.completionRate7Day * 100).toInt()}%")
                    CleanMetricRow("30-Day Avg", "${(stats.completionRate30Day * 100).toInt()}%")
                }
            }

            // Score History Chart
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.AutoMirrored.Filled.TrendingUp, title = "30-Day History")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (stats.productivityScoreHistory.isNotEmpty()) {
                        MiniLineChart(
                            values = stats.productivityScoreHistory.map { it.second.toFloat() },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("30 days ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text("Not enough data yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. Streak & Consistency Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StreakConsistencyDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var weekOffset by remember { mutableIntStateOf(0) }
    val periodLabel = when (weekOffset) {
        0 -> "This week"
        1 -> "Last week"
        else -> "${weekOffset} weeks ago"
    }

    StatsDetailSheet("Streak & Consistency", onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodNavigator(
                    periodLabel = periodLabel,
                    onPrev = { weekOffset++ },
                    onNext = { if (weekOffset > 0) weekOffset-- },
                    canGoNext = weekOffset > 0
                )
            }

            // Hero Stats
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroStat(Icons.Filled.LocalFireDepartment, "${stats.streak}", "Current", Color(0xFFFF9800))
                    HeroStat(Icons.Filled.EmojiEvents, "${stats.longestStreak}", "Longest", Color(0xFF4CAF50))
                    HeroStat(Icons.Filled.CalendarMonth, "${stats.weeklyConsistencyDays}/7", "This Week", Color(0xFF2196F3))
                }
            }

            // Consistency Score Ring
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.FitnessCenter, title = "Consistency Score")
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val consistencyColor = when {
                            stats.consistencyScore >= 80 -> Color(0xFF4CAF50)
                            stats.consistencyScore >= 50 -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        }
                        val animatedProgress by animateFloatAsState(
                            targetValue = stats.consistencyScore / 100f,
                            animationSpec = tween(1000)
                        )
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 10.dp.toPx()
                                drawArc(
                                    color = consistencyColor.copy(alpha = 0.12f),
                                    startAngle = -90f, sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = consistencyColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${stats.consistencyScore}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = consistencyColor)
                                Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        when {
                            stats.consistencyScore >= 80 -> "Excellent! You're highly consistent"
                            stats.consistencyScore >= 50 -> "Good consistency, room to improve"
                            else -> "Try completing tasks daily for better consistency"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Weekday vs Weekend
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Schedule, title = "Weekday vs Weekend")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HeroStat(Icons.Filled.CalendarMonth, String.format("%.1f", stats.weekdayAvg), "Weekday avg", Color(0xFF2196F3))
                        HeroStat(Icons.Filled.CalendarMonth, String.format("%.1f", stats.weekendAvg), "Weekend avg", Color(0xFF9C27B0))
                    }
                    // Comparison
                    Spacer(modifier = Modifier.height(8.dp))
                    val diff = stats.weekdayAvg - stats.weekendAvg
                    Text(
                        when {
                            diff > 1 -> "You're ${String.format("%.0f", diff)}% more productive on weekdays"
                            diff < -1 -> "You're ${String.format("%.0f", -diff)}% more productive on weekends"
                            else -> "Your weekday and weekend productivity is balanced"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Variability
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Speed, title = "Output Variability")
                    Spacer(modifier = Modifier.height(8.dp))
                    CleanMetricRow("Standard Deviation (σ)", String.format("%.1f", stats.stdDevDaily))
                    Text(
                        if (stats.stdDevDaily < 2f) "Low variability — very stable output" else "High variability — your daily output varies a lot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Yearly Heatmap
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.CalendarMonth, title = "Yearly Activity")
                    Spacer(modifier = Modifier.height(12.dp))
                    YearHeatmapCalendar(heatmap = stats.yearlyHeatmap, primaryColor = MaterialTheme.colorScheme.primary)
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3. Completion Trends Detail Screen (includes merged 90-day history)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CompletionTrendsDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var selectedRange by remember { mutableIntStateOf(30) }
    val rangeOptions = listOf(7, 14, 30, 60, 90)
    var periodOffset by remember { mutableIntStateOf(0) }

    val periodLabel = when {
        periodOffset == 0 -> "Last $selectedRange days"
        else -> "${selectedRange * periodOffset + 1}–${selectedRange * (periodOffset + 1)} days ago"
    }

    StatsDetailSheet("Completion Trends", onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodNavigator(
                    periodLabel = periodLabel,
                    onPrev = { periodOffset++ },
                    onNext = { if (periodOffset > 0) periodOffset-- },
                    canGoNext = periodOffset > 0
                )
            }

            // Range chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rangeOptions.forEach { range ->
                        FilterChip(
                            selected = selectedRange == range,
                            onClick = { selectedRange = range; periodOffset = 0 },
                            label = { Text("${range}D") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Summary Stats
            item {
                val rangeData = stats.dailyStatsWithDates.takeLast(selectedRange)
                val totalDone = rangeData.sumOf { it.second }
                val totalAll = rangeData.sumOf { it.third }.coerceAtLeast(1)
                val avgPerDay = if (rangeData.isNotEmpty()) totalDone.toFloat() / rangeData.size else 0f

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroStat(Icons.Filled.Star, "$totalDone", "Completed", MaterialTheme.colorScheme.primary)
                    HeroStat(Icons.Filled.Speed, String.format("%.1f", avgPerDay), "Avg/day", Color(0xFF2196F3))
                    HeroStat(Icons.Filled.EmojiEvents, "${(totalDone.toFloat() / totalAll * 100).toInt()}%", "Rate", Color(0xFF4CAF50))
                }
            }

            // Period Comparison
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.AutoMirrored.Filled.TrendingUp, title = "Period Comparison")
                    Spacer(modifier = Modifier.height(12.dp))
                    PeriodComparisonRow("Today vs Yesterday", stats.todayCompleted, stats.yesterdayCompleted)
                    Spacer(modifier = Modifier.height(6.dp))
                    PeriodComparisonRow("This week vs Last", stats.thisWeekCompleted, stats.lastWeekCompleted)
                    Spacer(modifier = Modifier.height(6.dp))
                    PeriodComparisonRow("This month vs Last", stats.thisMonthCompleted, stats.lastMonthCompleted)
                }
            }

            // Completion Rate Chart
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.AutoMirrored.Filled.TrendingUp, title = "Completion Rate")
                    Spacer(modifier = Modifier.height(12.dp))
                    val data = stats.dailyStatsWithDates.takeLast(selectedRange).map { (_, done, total) ->
                        if (total > 0) done.toFloat() / total else 0f
                    }
                    if (data.isNotEmpty()) {
                        MiniLineChart(
                            values = data,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.fillMaxWidth().height(140.dp)
                        )
                    }
                }
            }

            // Forecast
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Star, title = "7-Day Forecast")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (stats.forecastNext7.isNotEmpty()) {
                        val forecastVals = stats.forecastNext7.map { it.second }
                        MiniLineChart(
                            values = forecastVals,
                            color = Color(0xFFFF9800),
                            isDashed = true,
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Predicted avg: ${String.format("%.1f", forecastVals.average())} tasks/day",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Not enough data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Daily Breakdown (90-day full history — merged from old history sheet)
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.CalendarMonth, title = "Daily Breakdown ($selectedRange days)")
                    Spacer(modifier = Modifier.height(12.dp))
                    stats.dailyStatsWithDates.takeLast(selectedRange).reversed().forEach { (date, done, total) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(date.takeLast(5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(50.dp))
                            val rate = if (total > 0) done.toFloat() / total else 0f
                            LinearProgressIndicator(
                                progress = { rate },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .padding(horizontal = 8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when {
                                    rate >= 1f -> Color(0xFF4CAF50)
                                    rate >= 0.5f -> Color(0xFFFFC107)
                                    else -> Color(0xFFF44336)
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            Text("$done/$total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.width(45.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 4. Task Breakdown Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TaskBreakdownDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    StatsDetailSheet("Task Breakdown", onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Source Breakdown
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.CalendarMonth, title = "By Source")
                    Spacer(modifier = Modifier.height(12.dp))
                    val colors = listOf(
                        Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
                        Color(0xFF9C27B0), Color(0xFFE91E63)
                    )
                    stats.taskTypeBreakdown.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(colors[idx % colors.size])
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${item.count}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${(item.percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Priority Distribution
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Warning, title = "By Priority")
                    Spacer(modifier = Modifier.height(12.dp))
                    val pColors = mapOf(
                        "None" to Color(0xFF9E9E9E), "Low" to Color(0xFF4CAF50),
                        "Medium" to Color(0xFFFFC107), "High" to Color(0xFFF44336)
                    )
                    stats.priorityDistribution.forEach { (label, pct) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(pColors[label] ?: Color.Gray))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.width(100.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = pColors[label] ?: Color.Gray,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // Task Health
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Schedule, title = "Task Health")
                    Spacer(modifier = Modifier.height(12.dp))
                    CleanMetricRow("Avg Task Age", "${String.format("%.1f", stats.avgTaskAgeDays)} days")
                    CleanMetricRow("Procrastination Index", "${(stats.procrastinationIndex * 100).toInt()}%")
                    CleanMetricRow("Peak Hour", stats.peakHourLabel.ifEmpty { "—" })
                }
            }

            // Hourly Distribution
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Schedule, title = "Activity by Time of Day")
                    Spacer(modifier = Modifier.height(12.dp))
                    val maxVal = stats.hourlyDistribution.maxOfOrNull { it.second } ?: 1
                    stats.hourlyDistribution.forEach { (label, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
                            LinearProgressIndicator(
                                progress = { if (maxVal > 0) count.toFloat() / maxVal else 0f },
                                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 5. Keep Active Health Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun KeepActiveHealthDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    StatsDetailSheet("Keep Active Health", onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroStat(Icons.Filled.Warning, "${stats.activeRolloverCount}", "Active", Color(0xFFFF9800))
                    HeroStat(Icons.Filled.Schedule, String.format("%.0f", stats.averageRolloverDaysPending), "Avg days", Color(0xFF2196F3))
                    HeroStat(Icons.Filled.Star, "${(stats.rolloverCompletionRate * 100).toInt()}%", "Done rate", Color(0xFF4CAF50))
                }
            }

            if (stats.oldestRolloverTaskTitle != null) {
                item {
                    DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(icon = Icons.Filled.Warning, title = "Oldest Pending")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stats.oldestRolloverTaskTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${stats.oldestRolloverDays} days old", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336), fontWeight = FontWeight.Medium)
                    }
                }
            }

            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.FitnessCenter, title = "Health Summary")
                    Spacer(modifier = Modifier.height(12.dp))
                    val healthColor = when {
                        stats.activeRolloverCount == 0 -> Color(0xFF4CAF50)
                        stats.averageRolloverDaysPending < 5 -> Color(0xFF8BC34A)
                        stats.averageRolloverDaysPending < 10 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }
                    val healthLabel = when {
                        stats.activeRolloverCount == 0 -> "Clean! No pending rollover tasks"
                        stats.averageRolloverDaysPending < 5 -> "Healthy — tasks resolved quickly"
                        stats.averageRolloverDaysPending < 10 -> "Needs attention — tasks aging"
                        else -> "Critical — old tasks piling up"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(healthColor))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(healthLabel, style = MaterialTheme.typography.bodyMedium, color = healthColor)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 6. Tag Analytics Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TagAnalyticsDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var sortBy by remember { mutableStateOf("count") }

    StatsDetailSheet("Tag Analytics", onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Sort controls
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = sortBy == "count",
                        onClick = { sortBy = "count" },
                        label = { Text("By Count") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                    FilterChip(
                        selected = sortBy == "rate",
                        onClick = { sortBy = "rate" },
                        label = { Text("By Completion %") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            val sortedTags = when (sortBy) {
                "rate" -> stats.tagStats.sortedByDescending { it.completionRate }
                else -> stats.tagStats.sortedByDescending { it.totalCount }
            }

            items(sortedTags) { tag ->
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tag.tag, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${tag.completedCount}/${tag.totalCount} completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${(tag.completionRate * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                tag.completionRate >= 0.8f -> Color(0xFF4CAF50)
                                tag.completionRate >= 0.5f -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { tag.completionRate },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = when {
                            tag.completionRate >= 0.8f -> Color(0xFF4CAF50)
                            tag.completionRate >= 0.5f -> Color(0xFFFFC107)
                            else -> Color(0xFFF44336)
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 7. Trend Intelligence Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TrendIntelligenceDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var weekOffset by remember { mutableIntStateOf(0) }
    val periodLabel = when (weekOffset) {
        0 -> "This week"
        1 -> "Last week"
        else -> "${weekOffset} weeks ago"
    }

    StatsDetailSheet("Trend Intelligence", onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodNavigator(
                    periodLabel = periodLabel,
                    onPrev = { weekOffset++ },
                    onNext = { if (weekOffset > 0) weekOffset-- },
                    canGoNext = weekOffset > 0
                )
            }

            // Momentum & Velocity
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroStat(
                        Icons.Filled.Speed, "${stats.momentumScore}", "Momentum",
                        if (stats.momentumScore >= 60) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                    HeroStat(
                        if (stats.completionVelocity >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        "${if (stats.completionVelocity >= 0) "+" else ""}${String.format("%.1f", stats.completionVelocity)}",
                        "Velocity/day",
                        if (stats.completionVelocity >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }

            // Burnout Risk
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Warning, title = "Burnout Risk Analysis")
                    Spacer(modifier = Modifier.height(12.dp))
                    val burnoutColor = when {
                        stats.burnoutRiskScore >= 0.65f -> Color(0xFFF44336)
                        stats.burnoutRiskScore >= 0.35f -> Color(0xFFFFC107)
                        else -> Color(0xFF4CAF50)
                    }
                    val burnoutLabel = when {
                        stats.burnoutRiskScore >= 0.65f -> "High Risk — Take a break"
                        stats.burnoutRiskScore >= 0.35f -> "Moderate — Watch workload"
                        stats.burnoutRiskScore >= 0.1f -> "Low — You're in a good zone"
                        else -> "Minimal — Keep going!"
                    }
                    LinearProgressIndicator(
                        progress = { stats.burnoutRiskScore },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = burnoutColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(burnoutLabel, style = MaterialTheme.typography.bodyMedium, color = burnoutColor, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Based on comparing last 3 days to 7-day baseline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Growth Metrics
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.AutoMirrored.Filled.TrendingUp, title = "Growth Metrics")
                    Spacer(modifier = Modifier.height(12.dp))
                    GrowthMetricRow("Week over Week", stats.weekOverWeekGrowth)
                    Spacer(modifier = Modifier.height(8.dp))
                    GrowthMetricRow("Month over Month", stats.monthOverMonthGrowth)
                }
            }

            // Historical Comparison
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.CalendarMonth, title = "Period Comparison")
                    Spacer(modifier = Modifier.height(12.dp))
                    PeriodComparisonRow("Today vs Yesterday", stats.todayCompleted, stats.yesterdayCompleted)
                    Spacer(modifier = Modifier.height(6.dp))
                    PeriodComparisonRow("This week vs Last", stats.thisWeekCompleted, stats.lastWeekCompleted)
                    Spacer(modifier = Modifier.height(6.dp))
                    PeriodComparisonRow("This month vs Last", stats.thisMonthCompleted, stats.lastMonthCompleted)
                }
            }

            // Peak Analysis
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.EmojiEvents, title = "Peak Analysis")
                    Spacer(modifier = Modifier.height(12.dp))
                    CleanMetricRow("Peak Day", stats.peakDayOfWeek.ifEmpty { "—" })
                    CleanMetricRow("Peak Time", stats.peakHourLabel.ifEmpty { "—" })
                    CleanMetricRow("7-Day Moving Avg", String.format("%.1f", stats.movingAvg7Day))
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 8. Pomodoro Analytics Detail Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PomodoroAnalyticsDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var weekOffset by remember { mutableIntStateOf(0) }
    val periodLabel = when (weekOffset) {
        0 -> "This week"
        1 -> "Last week"
        else -> "${weekOffset} weeks ago"
    }

    StatsDetailSheet("Focus Analytics", onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodNavigator(
                    periodLabel = periodLabel,
                    onPrev = { weekOffset++ },
                    onNext = { if (weekOffset > 0) weekOffset-- },
                    canGoNext = weekOffset > 0
                )
            }

            // Hero Stats
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroStat(Icons.Filled.Timer, "${stats.todayFocusMinutes}", "Today (min)", MaterialTheme.colorScheme.primary)
                    HeroStat(Icons.Filled.LocalFireDepartment, "${stats.todayPomodoroSessions}", "Sessions", Color(0xFF2196F3))
                    HeroStat(Icons.Filled.EmojiEvents, String.format("%.1f", stats.totalFocusHours), "Total hrs", Color(0xFF4CAF50))
                }
            }

            // Summary
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.Timer, title = "Focus Summary")
                    Spacer(modifier = Modifier.height(12.dp))
                    CleanMetricRow("Avg Daily Focus", "${stats.averageDailyFocusMinutes} min")
                    stats.bestFocusDay?.let { CleanMetricRow("Best Focus Day", "$it (${stats.bestFocusDayMinutes} min)") }
                }
            }

            // Weekly Focus Chart
            item {
                DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(icon = Icons.Filled.CalendarMonth, title = "Weekly Focus")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (stats.weeklyFocusData.isNotEmpty() && stats.weeklyFocusData.any { it.second > 0 }) {
                        MiniBarChart(
                            labels = stats.weeklyFocusData.map { it.first },
                            values = stats.weeklyFocusData.map { it.second.toFloat() },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    } else {
                        Text("No focus data this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Top Tasks
            if (stats.topFocusedTasks.isNotEmpty()) {
                item {
                    DetailCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(icon = Icons.Filled.Star, title = "Most Focused Tasks")
                        Spacer(modifier = Modifier.height(12.dp))
                        stats.topFocusedTasks.forEachIndexed { idx, task ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${idx + 1}.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.taskTitle ?: "Unnamed", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${task.sessionCount} sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${task.totalSeconds / 60}m", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared UI Helper Components — M3 Expressive
// ═══════════════════════════════════════════════════════════════════════════════

/** Section header with Material icon */
@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

/** Clean metric row — label left, value right */
@Composable
fun CleanMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Progress bar row */
@Composable
fun CleanProgressRow(label: String, value: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

/** Period comparison row — shows current vs previous with delta chip */
@Composable
fun PeriodComparisonRow(label: String, current: Int, previous: Int) {
    val delta = current - previous
    val isPositive = delta >= 0
    val color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFF44336)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$current", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("vs $previous", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                null, modifier = Modifier.size(14.dp), tint = color
            )
        }
    }
}

@Composable
fun GrowthMetricRow(label: String, growthRate: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = if (growthRate >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            Icon(
                if (growthRate >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                null, modifier = Modifier.size(16.dp), tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "${if (growthRate >= 0) "+" else ""}${(growthRate * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun StatBigNumber(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = color)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScoreBreakdownBar(label: String, value: Float, weight: Float, color: Color) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("${(value * 100).toInt()}% × ${(weight * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
fun ComparisonRow(label: String, rate: Float) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { rate },
                modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = when {
                    rate >= 0.8f -> Color(0xFF4CAF50)
                    rate >= 0.5f -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("${(rate * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Chart Components
// ═══════════════════════════════════════════════════════════════════════════════

/** Mini line chart drawn with Canvas. */
@Composable
fun MiniLineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    isDashed: Boolean = false
) {
    if (values.isEmpty()) return
    val maxVal = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val minVal = values.minOrNull() ?: 0f
    val range = (maxVal - minVal).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padding = 4.dp.toPx()
        val chartW = w - padding * 2
        val chartH = h - padding * 2

        if (values.size < 2) return@Canvas
        val stepX = chartW / (values.size - 1)

        val path = Path()
        values.forEachIndexed { i, v ->
            val x = padding + i * stepX
            val y = padding + chartH - ((v - minVal) / range) * chartH
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Fill gradient
        val fillPath = Path().apply {
            addPath(path)
            lineTo(padding + (values.size - 1) * stepX, padding + chartH)
            lineTo(padding, padding + chartH)
            close()
        }
        drawPath(
            fillPath,
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.15f), Color.Transparent))
        )

        // Stroke
        drawPath(path, color = color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // End dot
        val lastX = padding + (values.size - 1) * stepX
        val lastY = padding + chartH - ((values.last() - minVal) / range) * chartH
        drawCircle(color, 5.dp.toPx(), Offset(lastX, lastY))
    }
}

/** Mini bar chart drawn with Canvas. */
@Composable
fun MiniBarChart(
    labels: List<String>,
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val maxVal = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val w = size.width
            val h = size.height
            val barCount = values.size
            val barWidth = w / barCount * 0.6f
            val gap = w / barCount * 0.4f

            values.forEachIndexed { i, v ->
                val barH = (v / maxVal) * h * 0.9f
                val x = i * (barWidth + gap) + gap / 2
                drawRoundRect(
                    color = color.copy(alpha = 0.7f),
                    topLeft = Offset(x, h - barH),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEach { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
            }
        }
    }
}
