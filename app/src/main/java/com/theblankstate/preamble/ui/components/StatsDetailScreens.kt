package com.theblankstate.preamble.ui.components

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import com.theblankstate.preamble.viewmodel.StatsState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
// Shared M3 Expressive Sheet Wrapper
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
        Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Date Range Picker Dialog (for custom date comparison)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CustomDateRangeDialog(
    onDismiss: () -> Unit,
    initialStartMs: Long? = null,
    initialEndMs: Long? = null,
    onConfirm: (startMs: Long, endMs: Long) -> Unit
) {
    val context = LocalContext.current
    val todayMidnight = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var startMs by remember { mutableLongStateOf(initialStartMs ?: (todayMidnight - 6L * DAY_MS)) }
    var endMs by remember { mutableLongStateOf(initialEndMs ?: todayMidnight) }
    val isValidRange = endMs >= startMs
    val days = if (isValidRange) (((endMs - startMs) / DAY_MS).toInt() + 1) else 0
    val displaySdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    fun openPicker(currentMs: Long, onDateSelected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentMs }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onDateSelected(picked)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                Text("Select Date Range")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pick exact start and end dates for comparison", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { openPicker(startMs) { startMs = it } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Start: ${displaySdf.format(Date(startMs))}")
                    }
                    OutlinedButton(
                        onClick = { openPicker(endMs) { endMs = it } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("End: ${displaySdf.format(Date(endMs))}")
                    }
                }
                if (isValidRange) {
                    InfoCapsule("range", "$days day${if (days == 1) "" else "s"}", MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        "End date must be on or after start date",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF44336)
                    )
                }
                val currentWindow = formatRangeLabel(startMs, endMs)
                val previousWindow = formatRangeLabel(startMs - days.toLong() * DAY_MS, startMs - DAY_MS)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Current: $currentWindow", style = MaterialTheme.typography.bodySmall)
                    Text("Previous: $previousWindow", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!isValidRange) return@TextButton
                onConfirm(startMs, endMs)
                onDismiss()
            }, enabled = isValidRange) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private data class PeriodComparisonData(
    val currentDone: Int,
    val previousDone: Int,
    val currentTotal: Int,
    val previousTotal: Int,
)

private fun computePeriodComparison(stats: StatsState, startMs: Long, endMs: Long): PeriodComparisonData {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val byEpoch = stats.dailyStatsWithDates.mapNotNull { (date, done, total) ->
        runCatching {
            val ms = sdf.parse(date)?.time ?: return@runCatching null
            Triple(ms, done, total)
        }.getOrNull()
    }.filterNotNull()

    val start = minOf(startMs, endMs)
    val end = maxOf(startMs, endMs)
    val days = (((end - start) / DAY_MS).toInt() + 1).coerceAtLeast(1)
    val prevStart = start - days.toLong() * DAY_MS
    val prevEnd = start - DAY_MS

    val current = byEpoch.filter { it.first in start..end }
    val previous = byEpoch.filter { it.first in prevStart..prevEnd }

    return PeriodComparisonData(
        currentDone = current.sumOf { it.second },
        previousDone = previous.sumOf { it.second },
        currentTotal = current.sumOf { it.third },
        previousTotal = previous.sumOf { it.third },
    )
}

private fun formatRangeLabel(startMs: Long, endMs: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return "${sdf.format(Date(startMs))} - ${sdf.format(Date(endMs))}"
}

private const val DAY_MS = 24L * 60L * 60L * 1000L

// ═══════════════════════════════════════════════════════════════════════════════
// Shared Range-Aware Stats — every detail sheet derives from this
// ═══════════════════════════════════════════════════════════════════════════════

data class RangeStats(
    val days: Int = 0,
    val totalDone: Int = 0,
    val totalAll: Int = 0,
    val completionRate: Float = 0f,
    val avgPerDay: Float = 0f,
    val dailyDone: List<Int> = emptyList(),
    val dailyRates: List<Float> = emptyList(),
    val dailyLabels: List<String> = emptyList(),
    val stdDev: Float = 0f,
    val velocity: Float = 0f,
    val momentum: Int = 0,
    val consistencyScore: Int = 0,
    val burnoutRisk: Float = 0f,
    val weekdayAvg: Float = 0f,
    val weekendAvg: Float = 0f,
    val peakDay: String = "",
    val grade: String = "C",
    val score: Int = 0,
)

private fun sliceByRange(
    all: List<Triple<String, Int, Int>>,
    range: Int,
    offset: Int
): List<Triple<String, Int, Int>> {
    val endIdx = (all.size - offset * range).coerceIn(0, all.size)
    val startIdx = (endIdx - range).coerceAtLeast(0)
    if (startIdx >= endIdx) return emptyList()
    return all.subList(startIdx, endIdx)
}

private fun sliceByMs(
    all: List<Triple<String, Int, Int>>,
    startMs: Long,
    endMs: Long
): List<Triple<String, Int, Int>> {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return all.filter { (date, _, _) ->
        val ms = runCatching { sdf.parse(date)?.time }.getOrNull() ?: return@filter false
        ms in startMs..endMs
    }
}

private fun computeRangeStats(data: List<Triple<String, Int, Int>>): RangeStats {
    if (data.isEmpty()) return RangeStats()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    val done = data.map { it.second }
    val totalDone = done.sum()
    val totalAll = data.sumOf { it.third }.coerceAtLeast(1)
    val rate = totalDone.toFloat() / totalAll
    val avg = totalDone.toFloat() / data.size
    val dailyRates = data.map { (_, d, t) -> if (t > 0) d.toFloat() / t else 0f }
    val labels = data.map { it.first.takeLast(5) } // MM-dd

    // Std dev
    val variance = done.map { (it - avg) * (it - avg) }.average().toFloat()
    val stdDev = kotlin.math.sqrt(variance.toDouble()).toFloat()

    // Velocity (OLS slope)
    val n = done.size
    val velocity = if (n >= 2) {
        val xMean = (n - 1) / 2.0
        val yMean = done.average()
        val num = done.indices.sumOf { i -> (i - xMean) * (done[i] - yMean) }
        val den = done.indices.sumOf { i -> (i - xMean) * (i - xMean) }
        if (den > 0) (num / den).toFloat() else 0f
    } else 0f

    // EMA momentum
    val alpha = 0.3f
    var emaVal = done.firstOrNull()?.toFloat() ?: 0f
    done.drop(1).forEach { v -> emaVal = alpha * v + (1 - alpha) * emaVal }
    val maxBase = done.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
    val momentum = ((emaVal / maxBase) * 100).toInt().coerceIn(0, 100)

    // Consistency (inverse CV)
    val cv = if (avg > 0) stdDev / avg else 1f
    val consistency = ((1f - cv.coerceIn(0f, 1f)) * 100).toInt().coerceIn(0, 100)

    // Burnout risk
    val burnout = if (done.size >= 5) {
        val recent = done.takeLast(3).average().toFloat()
        val baseline = done.average().toFloat()
        if (baseline > 0) (1f - recent / baseline).coerceIn(0f, 1f) else 0f
    } else 0f

    // Weekday / Weekend split
    val weekdayDone = mutableListOf<Int>()
    val weekendDone = mutableListOf<Int>()
    data.forEach { (date, d, _) ->
        runCatching {
            cal.time = sdf.parse(date)!!
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) weekendDone.add(d) else weekdayDone.add(d)
        }
    }
    val weekdayAvg = if (weekdayDone.isNotEmpty()) weekdayDone.average().toFloat() else 0f
    val weekendAvg = if (weekendDone.isNotEmpty()) weekendDone.average().toFloat() else 0f

    // Peak day of week
    val dayBuckets = mutableMapOf<Int, MutableList<Int>>()
    data.forEach { (date, d, _) ->
        runCatching {
            cal.time = sdf.parse(date)!!
            dayBuckets.getOrPut(cal.get(Calendar.DAY_OF_WEEK)) { mutableListOf() }.add(d)
        }
    }
    val peakDow = dayBuckets.maxByOrNull { (_, vals) -> vals.average() }?.key
    val dayNames = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
    val peakDay = dayNames[peakDow] ?: ""

    // Score & Grade
    val score = (rate * 40 + consistency / 100f * 30 + (1f - burnout) * 30).toInt().coerceIn(0, 100)
    val grade = when { score >= 85 -> "A"; score >= 70 -> "B"; score >= 55 -> "C"; score >= 40 -> "D"; else -> "F" }

    return RangeStats(
        days = data.size, totalDone = totalDone, totalAll = totalAll,
        completionRate = rate, avgPerDay = avg, dailyDone = done,
        dailyRates = dailyRates, dailyLabels = labels, stdDev = stdDev,
        velocity = velocity, momentum = momentum, consistencyScore = consistency,
        burnoutRisk = burnout, weekdayAvg = weekdayAvg, weekendAvg = weekendAvg,
        peakDay = peakDay, grade = grade, score = score,
    )
}

/** Resolve current + previous RangeStats from selection state. */
@Composable
private fun rememberRangeStats(
    allData: List<Triple<String, Int, Int>>,
    selectedRange: Int,
    periodOffset: Int,
    customRange: Pair<Long, Long>?
): Pair<RangeStats, RangeStats> {
    return remember(allData, selectedRange, periodOffset, customRange) {
        val currentSlice = if (customRange != null) {
            sliceByMs(allData, customRange.first, customRange.second)
        } else {
            sliceByRange(allData, selectedRange, periodOffset)
        }
        val prevSlice = if (customRange != null) {
            val duration = (customRange.second - customRange.first).coerceAtLeast(DAY_MS)
            sliceByMs(allData, customRange.first - duration - DAY_MS, customRange.first - DAY_MS)
        } else {
            sliceByRange(allData, selectedRange, periodOffset + 1)
        }
        computeRangeStats(currentSlice) to computeRangeStats(prevSlice)
    }
}

/** Standard range chip row + period nav shared by all detail sheets. */
@Composable
private fun RangeSelector(
    selectedRange: Int,
    periodOffset: Int,
    customRange: Pair<Long, Long>?,
    onRangeChange: (Int) -> Unit,
    onOffsetPrev: () -> Unit,
    onOffsetNext: () -> Unit,
    onCalendarClick: () -> Unit,
) {
    val periodLabel = customRange?.let { formatRangeLabel(it.first, it.second) }
        ?: when (periodOffset) { 0 -> "Last $selectedRange days"; else -> "${selectedRange * periodOffset + 1}–${selectedRange * (periodOffset + 1)}d ago" }

    PeriodNavigator(periodLabel, onPrev = onOffsetPrev, onNext = onOffsetNext, canGoNext = periodOffset > 0 || customRange != null, onCalendarClick = onCalendarClick)
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(7, 14, 30, 60, 90).forEach { range ->
            FilterChip(
                selected = selectedRange == range && customRange == null,
                onClick = { onRangeChange(range) },
                label = { Text("${range}D") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/** Comparison capsule row: current range vs previous. */
@Composable
private fun RangeComparisonCapsules(current: RangeStats, prev: RangeStats) {
    val growth = if (prev.totalDone > 0) (current.totalDone - prev.totalDone).toFloat() / prev.totalDone else 0f
    val growthColor = if (growth >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoCapsule("done", "${current.totalDone}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        InfoCapsule("avg/d", String.format("%.1f", current.avgPerDay), Color(0xFF2196F3), Modifier.weight(1f))
        InfoCapsule("rate", "${(current.completionRate * 100).toInt()}%", Color(0xFF4CAF50), Modifier.weight(1f))
        InfoCapsule("vs prev", "${if (growth >= 0) "+" else ""}${(growth * 100).toInt()}%", growthColor, Modifier.weight(1f))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Period Navigator with Calendar Button (Google Fit-style)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PeriodNavigator(
    periodLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean = true,
    onCalendarClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(periodLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ChevronLeft, "Previous", modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ChevronRight, "Next", modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onCalendarClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.CalendarMonth, "Calendar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Capsule Component — short info in pill shape (M3 Expressive)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun InfoCapsule(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Hero Stat with Expressive Icon Container
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun HeroStat(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Message Card — rounded bg for feedback messages
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun FeedbackMessage(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// Section Header with Icon
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 1. Productivity Score Detail
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ProductivityScoreDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var selectedRange by remember { mutableIntStateOf(30) }
    var periodOffset by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val (rs, prev) = rememberRangeStats(stats.dailyStatsWithDates, selectedRange, periodOffset, customRange)
    val gradeColor = when (rs.grade) { "A" -> Color(0xFF4CAF50); "B" -> Color(0xFF8BC34A); "C" -> Color(0xFFFFC107); else -> Color(0xFFF44336) }

    StatsDetailSheet("Productivity Score", onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                RangeSelector(selectedRange, periodOffset, customRange,
                    onRangeChange = { selectedRange = it; periodOffset = 0; customRange = null },
                    onOffsetPrev = { periodOffset++ },
                    onOffsetNext = { if (customRange != null) customRange = null else if (periodOffset > 0) periodOffset-- },
                    onCalendarClick = { showDatePicker = true })
            }

            // Range summary capsules
            item { RangeComparisonCapsules(rs, prev) }

            // Hero score — from range
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("${rs.score}", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoCapsule("grade", rs.grade, gradeColor)
                        InfoCapsule("out of", "100", MaterialTheme.colorScheme.primary)
                    }
                    if (prev.days > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val delta = rs.score - prev.score
                        InfoCapsule("vs prev period", "${if (delta >= 0) "+" else ""}$delta", if (delta >= 0) Color(0xFF4CAF50) else Color(0xFFF44336))
                    }
                }
            }

            // Score Breakdown — from range
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Speed, "Score Breakdown")
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpressiveProgressRow("Completion Rate", rs.completionRate, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    ExpressiveProgressRow("Consistency", rs.consistencyScore / 100f, Color(0xFF9C27B0))
                    Spacer(modifier = Modifier.height(10.dp))
                    ExpressiveProgressRow("Stability", (1f - rs.burnoutRisk), Color(0xFF2196F3))
                }
            }

            // Rates comparison — range vs previous
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.CalendarMonth, "Completion Rates")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoCapsule("selected", "${(rs.completionRate * 100).toInt()}%", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        InfoCapsule("previous", "${(prev.completionRate * 100).toInt()}%", Color(0xFF9E9E9E), Modifier.weight(1f))
                        val d = rs.completionRate - prev.completionRate
                        InfoCapsule("delta", "${if (d >= 0) "+" else ""}${(d * 100).toInt()}%", if (d >= 0) Color(0xFF4CAF50) else Color(0xFFF44336), Modifier.weight(1f))
                    }
                }
            }

            // Completion rate chart with previous overlay
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "Completion Rate History")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (rs.dailyRates.isNotEmpty()) {
                        MiniLineChart(
                            values = rs.dailyRates,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            previousValues = if (prev.dailyRates.isNotEmpty()) prev.dailyRates else null
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(rs.dailyLabels.firstOrNull() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(rs.dailyLabels.lastOrNull() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else { Text("Not enough data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // Period comparison
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.DateRange, "Period Comparison")
                    Spacer(modifier = Modifier.height(10.dp))
                    PeriodComparisonRow("Tasks done", rs.totalDone, prev.totalDone)
                    Spacer(modifier = Modifier.height(4.dp))
                    PeriodComparisonRow("Avg / day", rs.avgPerDay.toInt(), prev.avgPerDay.toInt())
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs -> customRange = startMs to endMs }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 2. Streak & Consistency Detail
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun StreakConsistencyDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var selectedRange by remember { mutableIntStateOf(30) }
    var periodOffset by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val (rs, prev) = rememberRangeStats(stats.dailyStatsWithDates, selectedRange, periodOffset, customRange)

    StatsDetailSheet("Streak & Consistency", onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                RangeSelector(selectedRange, periodOffset, customRange,
                    onRangeChange = { selectedRange = it; periodOffset = 0; customRange = null },
                    onOffsetPrev = { periodOffset++ },
                    onOffsetNext = { if (customRange != null) customRange = null else if (periodOffset > 0) periodOffset-- },
                    onCalendarClick = { showDatePicker = true })
            }

            item { RangeComparisonCapsules(rs, prev) }

            // Streak heroes (global — streaks are cumulative)
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HeroStat(Icons.Filled.LocalFireDepartment, "${stats.streak}", "Current", Color(0xFFFF9800))
                    HeroStat(Icons.Filled.EmojiEvents, "${stats.longestStreak}", "Longest", Color(0xFF4CAF50))
                    HeroStat(Icons.Filled.CalendarMonth, "${stats.weeklyConsistencyDays}/7", "This Week", Color(0xFF2196F3))
                }
            }

            // Consistency score ring — from range
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.FitnessCenter, "Consistency Score (${rs.days}d)")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        // Current range ring
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val cColor = when { rs.consistencyScore >= 80 -> Color(0xFF4CAF50); rs.consistencyScore >= 50 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
                            val animP by animateFloatAsState(targetValue = rs.consistencyScore / 100f, animationSpec = tween(1000))
                            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val sw = 10.dp.toPx()
                                    drawArc(color = cColor.copy(alpha = 0.12f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
                                    drawArc(color = cColor, startAngle = -90f, sweepAngle = 360f * animP, useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${rs.consistencyScore}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = cColor)
                                    Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Previous range ring
                        if (prev.days > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val pColor = when { prev.consistencyScore >= 80 -> Color(0xFF4CAF50); prev.consistencyScore >= 50 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
                                val animPrev by animateFloatAsState(targetValue = prev.consistencyScore / 100f, animationSpec = tween(1000))
                                Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val sw = 8.dp.toPx()
                                        drawArc(color = pColor.copy(alpha = 0.12f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
                                        drawArc(color = pColor.copy(alpha = 0.5f), startAngle = -90f, sweepAngle = 360f * animPrev, useCenter = false, style = Stroke(sw, cap = StrokeCap.Round))
                                    }
                                    Text("${prev.consistencyScore}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = pColor)
                                }
                                Text("Previous", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FeedbackMessage(
                        when { rs.consistencyScore >= 80 -> "Excellent! Highly consistent in this period"; rs.consistencyScore >= 50 -> "Good consistency, room to improve"; else -> "Try completing tasks daily for better consistency" },
                        when { rs.consistencyScore >= 80 -> Color(0xFF4CAF50); rs.consistencyScore >= 50 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
                    )
                }
            }

            // Daily output chart with previous overlay
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "Daily Output")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (rs.dailyDone.isNotEmpty()) {
                        MiniLineChart(
                            values = rs.dailyDone.map { it.toFloat() },
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            previousValues = if (prev.dailyDone.isNotEmpty()) prev.dailyDone.map { it.toFloat() } else null
                        )
                    }
                }
            }

            // Weekday vs Weekend — from range
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Schedule, "Weekday vs Weekend")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoCapsule("weekday", String.format("%.1f/d", rs.weekdayAvg), Color(0xFF2196F3), Modifier.weight(1f))
                        InfoCapsule("weekend", String.format("%.1f/d", rs.weekendAvg), Color(0xFF9C27B0), Modifier.weight(1f))
                    }
                    if (prev.days > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoCapsule("prev wd", String.format("%.1f", prev.weekdayAvg), Color(0xFF9E9E9E), Modifier.weight(1f))
                            InfoCapsule("prev we", String.format("%.1f", prev.weekendAvg), Color(0xFF9E9E9E), Modifier.weight(1f))
                        }
                    }
                    val diff = rs.weekdayAvg - rs.weekendAvg
                    Spacer(modifier = Modifier.height(8.dp))
                    FeedbackMessage(
                        when { diff > 1 -> "More productive on weekdays in this period"; diff < -1 -> "More productive on weekends in this period"; else -> "Balanced weekday/weekend productivity" },
                        MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Output Variability — from range
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Speed, "Output Variability")
                    Spacer(modifier = Modifier.height(12.dp))
                    val stableColor = if (rs.stdDev < 2f) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        InfoCapsule("σ", String.format("%.1f", rs.stdDev), stableColor, Modifier.weight(1f))
                        Column(modifier = Modifier.weight(1f)) {
                            ExpressiveProgressRow("Variability", (rs.stdDev / 5f).coerceIn(0f, 1f), stableColor)
                        }
                    }
                    if (prev.days > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val d = rs.stdDev - prev.stdDev
                        InfoCapsule("vs prev σ", "${if (d >= 0) "+" else ""}${String.format("%.1f", d)}", if (d <= 0) Color(0xFF4CAF50) else Color(0xFFFF9800))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FeedbackMessage(
                        if (rs.stdDev < 2f) "Low variability — very stable output" else "High variability — daily output fluctuates",
                        stableColor
                    )
                }
            }

            // Heatmap (global — yearly view)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.CalendarMonth, "Yearly Activity")
                    Spacer(modifier = Modifier.height(12.dp))
                    YearHeatmapCalendar(heatmap = stats.yearlyHeatmap, primaryColor = MaterialTheme.colorScheme.primary)
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs -> customRange = startMs to endMs }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 3. Completion Trends Detail (merged 90-day history)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CompletionTrendsDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var selectedRange by remember { mutableIntStateOf(30) }
    var periodOffset by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var visibleBreakdownCount by remember { mutableIntStateOf(15) }

    val periodLabel = customRange?.let { formatRangeLabel(it.first, it.second) }
        ?: when {
            periodOffset == 0 -> "Last $selectedRange days"
            else -> "${selectedRange * periodOffset + 1}–${selectedRange * (periodOffset + 1)}d ago"
        }
    val selectedRangeComparison = customRange?.let { (startMs, endMs) ->
        remember(stats.dailyStatsWithDates, startMs, endMs) {
            computePeriodComparison(stats, startMs, endMs)
        }
    }

    StatsDetailSheet("Completion Trends", onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PeriodNavigator(periodLabel, onPrev = { periodOffset++ }, onNext = { if (periodOffset > 0) periodOffset-- }, canGoNext = periodOffset > 0, onCalendarClick = { showDatePicker = true }) }

            // Range chips
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 30, 60, 90).forEach { range ->
                        FilterChip(
                            selected = selectedRange == range,
                            onClick = {
                                selectedRange = range
                                periodOffset = 0
                                visibleBreakdownCount = 15
                                customRange = null
                            },
                            label = { Text("${range}D") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        )
                    }
                }
            }

            // Summary capsules
            item {
                val rangeData = selectedRangeComparison?.let { comparison ->
                    val days = (((customRange!!.second - customRange!!.first) / DAY_MS).toInt() + 1).coerceAtLeast(1)
                    stats.dailyStatsWithDates.takeLast(days)
                } ?: stats.dailyStatsWithDates.takeLast(selectedRange)
                val totalDone = rangeData.sumOf { it.second }
                val totalAll = rangeData.sumOf { it.third }.coerceAtLeast(1)
                val avgPerDay = if (rangeData.isNotEmpty()) totalDone.toFloat() / rangeData.size else 0f

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCapsule("done", "$totalDone", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    InfoCapsule("avg/d", String.format("%.1f", avgPerDay), Color(0xFF2196F3), Modifier.weight(1f))
                    InfoCapsule("rate", "${(totalDone.toFloat() / totalAll * 100).toInt()}%", Color(0xFF4CAF50), Modifier.weight(1f))
                }
            }

            // Period Comparison
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "Period Comparison")
                    Spacer(modifier = Modifier.height(10.dp))
                    selectedRangeComparison?.let { comparison ->
                        val selected = customRange!!
                        Text(formatRangeLabel(selected.first, selected.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        PeriodComparisonRow("Selected vs Previous", comparison.currentDone, comparison.previousDone)
                    } ?: run {
                        PeriodComparisonRow("Today vs Yesterday", stats.todayCompleted, stats.yesterdayCompleted)
                        Spacer(modifier = Modifier.height(4.dp))
                        PeriodComparisonRow("This week vs Last", stats.thisWeekCompleted, stats.lastWeekCompleted)
                        Spacer(modifier = Modifier.height(4.dp))
                        PeriodComparisonRow("This month vs Last", stats.thisMonthCompleted, stats.lastMonthCompleted)
                    }
                }
            }

            // Completion Rate chart
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "Completion Rate")
                    Spacer(modifier = Modifier.height(12.dp))
                    val data = (selectedRangeComparison?.let {
                        val days = (((customRange!!.second - customRange!!.first) / DAY_MS).toInt() + 1).coerceAtLeast(1)
                        stats.dailyStatsWithDates.takeLast(days)
                    } ?: stats.dailyStatsWithDates.takeLast(selectedRange))
                        .map { (_, done, total) -> if (total > 0) done.toFloat() / total else 0f }
                    if (data.isNotEmpty()) { MiniLineChart(values = data, color = Color(0xFF4CAF50), modifier = Modifier.fillMaxWidth().height(140.dp)) }
                }
            }

            // Forecast
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Star, "7-Day Forecast")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (stats.forecastNext7.isNotEmpty()) {
                        MiniLineChart(values = stats.forecastNext7.map { it.second }, color = Color(0xFFFF9800), isDashed = true, modifier = Modifier.fillMaxWidth().height(100.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        InfoCapsule("predicted avg", "${String.format("%.1f", stats.forecastNext7.map { it.second }.average())} tasks/day", Color(0xFFFF9800))
                    } else { Text("Not enough data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // Daily Breakdown with Show More (15 at a time)
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp).animateContentSize()) {
                    SectionHeader(Icons.Filled.CalendarMonth, "Daily Breakdown")
                    Spacer(modifier = Modifier.height(10.dp))
                    val allDays = (selectedRangeComparison?.let {
                        val days = (((customRange!!.second - customRange!!.first) / DAY_MS).toInt() + 1).coerceAtLeast(1)
                        stats.dailyStatsWithDates.takeLast(days)
                    } ?: stats.dailyStatsWithDates.takeLast(selectedRange)).reversed()
                    val visibleDays = allDays.take(visibleBreakdownCount)

                    visibleDays.forEach { (date, done, total) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(date.takeLast(5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(50.dp))
                            val rate = if (total > 0) done.toFloat() / total else 0f
                            LinearProgressIndicator(
                                progress = { rate },
                                modifier = Modifier.weight(1f).height(10.dp).padding(horizontal = 8.dp).clip(RoundedCornerShape(5.dp)),
                                color = when { rate >= 1f -> Color(0xFF4CAF50); rate >= 0.5f -> Color(0xFFFFC107); else -> Color(0xFFF44336) },
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            Text("$done/$total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.width(45.dp), textAlign = TextAlign.End)
                        }
                    }

                    if (visibleBreakdownCount < allDays.size) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).clickable { visibleBreakdownCount += 15 }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Show more (${allDays.size - visibleBreakdownCount} remaining)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs ->
                customRange = startMs to endMs
                visibleBreakdownCount = 15
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 4. Task Breakdown Detail
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TaskBreakdownDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val selectedRangeComparison = customRange?.let { (startMs, endMs) ->
        remember(stats.dailyStatsWithDates, startMs, endMs) {
            computePeriodComparison(stats, startMs, endMs)
        }
    }

    StatsDetailSheet("Task Breakdown", onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, "Filter dates", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            selectedRangeComparison?.let { rangeData ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(Icons.Filled.DateRange, "Custom Range Comparison")
                        Spacer(modifier = Modifier.height(8.dp))
                        val selected = customRange!!
                        Text(formatRangeLabel(selected.first, selected.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        PeriodComparisonRow("Selected vs Previous", rangeData.currentDone, rangeData.previousDone)
                    }
                }
            }

            // Source Breakdown
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.CalendarMonth, "By Source")
                    Spacer(modifier = Modifier.height(12.dp))
                    val colors = listOf(Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFE91E63))
                    stats.taskTypeBreakdown.forEachIndexed { idx, item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(colors[idx % colors.size]))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(item.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            InfoCapsule("", "${item.count}", colors[idx % colors.size])
                        }
                    }
                }
            }

            // Priority
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Warning, "By Priority")
                    Spacer(modifier = Modifier.height(12.dp))
                    val pColors = mapOf("None" to Color(0xFF9E9E9E), "Low" to Color(0xFF4CAF50), "Medium" to Color(0xFFFFC107), "High" to Color(0xFFF44336))
                    stats.priorityDistribution.forEach { (label, pct) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            InfoCapsule(label, "${(pct * 100).toInt()}%", pColors[label] ?: Color.Gray, Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            ExpressiveProgressRow("", pct, pColors[label] ?: Color.Gray, Modifier.weight(2f))
                        }
                    }
                }
            }

            // Task Health capsules
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Schedule, "Task Health")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoCapsule("age", "${String.format("%.1f", stats.avgTaskAgeDays)}d", Color(0xFF2196F3), Modifier.weight(1f))
                        InfoCapsule("procrastination", "${(stats.procrastinationIndex * 100).toInt()}%", Color(0xFFFF9800), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    if (stats.peakHourLabel.isNotEmpty()) { InfoCapsule("peak hour", stats.peakHourLabel, MaterialTheme.colorScheme.primary) }
                }
            }

            // Hourly Distribution
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Schedule, "Activity by Time of Day")
                    Spacer(modifier = Modifier.height(12.dp))
                    val maxVal = stats.hourlyDistribution.maxOfOrNull { it.second } ?: 1
                    stats.hourlyDistribution.forEach { (label, count) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(70.dp))
                            LinearProgressIndicator(
                                progress = { if (maxVal > 0) count.toFloat() / maxVal else 0f },
                                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
                                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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

    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs -> customRange = startMs to endMs }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 5. Keep Active Health Detail
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun KeepActiveHealthDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val selectedRangeComparison = customRange?.let { (startMs, endMs) ->
        remember(stats.dailyStatsWithDates, startMs, endMs) {
            computePeriodComparison(stats, startMs, endMs)
        }
    }

    StatsDetailSheet("Keep Active Health", onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Filled.DateRange, "Filter dates", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            selectedRangeComparison?.let { rangeData ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(Icons.Filled.DateRange, "Custom Range Comparison")
                        Spacer(modifier = Modifier.height(8.dp))
                        val selected = customRange!!
                        Text(formatRangeLabel(selected.first, selected.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        PeriodComparisonRow("Selected vs Previous", rangeData.currentDone, rangeData.previousDone)
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    HeroStat(Icons.Filled.Warning, "${stats.activeRolloverCount}", "Active", Color(0xFFFF9800))
                    HeroStat(Icons.Filled.Schedule, String.format("%.0f", stats.averageRolloverDaysPending), "Avg days", Color(0xFF2196F3))
                    HeroStat(Icons.Filled.Star, "${(stats.rolloverCompletionRate * 100).toInt()}%", "Done rate", Color(0xFF4CAF50))
                }
            }

            if (stats.oldestRolloverTaskTitle != null) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(Icons.Filled.Warning, "Oldest Pending")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stats.oldestRolloverTaskTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        InfoCapsule("old", "${stats.oldestRolloverDays} days", Color(0xFFF44336))
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.FitnessCenter, "Health Summary")
                    Spacer(modifier = Modifier.height(12.dp))
                    val hColor = when { stats.activeRolloverCount == 0 -> Color(0xFF4CAF50); stats.averageRolloverDaysPending < 5 -> Color(0xFF8BC34A); stats.averageRolloverDaysPending < 10 -> Color(0xFFFFC107); else -> Color(0xFFF44336) }
                    val hLabel = when { stats.activeRolloverCount == 0 -> "Clean! No pending rollover tasks"; stats.averageRolloverDaysPending < 5 -> "Healthy — resolved quickly"; stats.averageRolloverDaysPending < 10 -> "Needs attention — aging"; else -> "Critical — piling up" }
                    FeedbackMessage(hLabel, hColor)
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs -> customRange = startMs to endMs }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 6. Tag Analytics Detail
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TagAnalyticsDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var sortBy by remember { mutableStateOf("count") }
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val selectedRangeComparison = customRange?.let { (startMs, endMs) ->
        remember(stats.dailyStatsWithDates, startMs, endMs) {
            computePeriodComparison(stats, startMs, endMs)
        }
    }

    StatsDetailSheet("Tag Analytics", onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = sortBy == "count", onClick = { sortBy = "count" }, label = { Text("By Count") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                    FilterChip(selected = sortBy == "rate", onClick = { sortBy = "rate" }, label = { Text("By Rate") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Filled.DateRange, "Filter dates", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            selectedRangeComparison?.let { rangeData ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(Icons.Filled.DateRange, "Custom Range Comparison")
                        Spacer(modifier = Modifier.height(8.dp))
                        val selected = customRange!!
                        Text(formatRangeLabel(selected.first, selected.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        PeriodComparisonRow("Selected vs Previous", rangeData.currentDone, rangeData.previousDone)
                    }
                }
            }

            val sorted = when (sortBy) { "rate" -> stats.tagStats.sortedByDescending { it.completionRate }; else -> stats.tagStats.sortedByDescending { it.totalCount } }

            items(sorted) { tag ->
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tag.tag, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${tag.completedCount}/${tag.totalCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        InfoCapsule("", "${(tag.completionRate * 100).toInt()}%", when { tag.completionRate >= 0.8f -> Color(0xFF4CAF50); tag.completionRate >= 0.5f -> Color(0xFFFFC107); else -> Color(0xFFF44336) })
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { tag.completionRate },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = when { tag.completionRate >= 0.8f -> Color(0xFF4CAF50); tag.completionRate >= 0.5f -> Color(0xFFFFC107); else -> Color(0xFFF44336) },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs -> customRange = startMs to endMs }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 7. Trend Intelligence Detail
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun TrendIntelligenceDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var weekOffset by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val periodLabel = when (weekOffset) { 0 -> "This week"; 1 -> "Last week"; else -> "${weekOffset}w ago" }
    val selectedRangeComparison = customRange?.let { (startMs, endMs) ->
        remember(stats.dailyStatsWithDates, startMs, endMs) {
            computePeriodComparison(stats, startMs, endMs)
        }
    }

    StatsDetailSheet("Trend Intelligence", onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PeriodNavigator(periodLabel, onPrev = { weekOffset++ }, onNext = { if (weekOffset > 0) weekOffset-- }, canGoNext = weekOffset > 0, onCalendarClick = { showDatePicker = true }) }

            selectedRangeComparison?.let { rangeData ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(Icons.Filled.DateRange, "Custom Range Comparison")
                        Spacer(modifier = Modifier.height(8.dp))
                        val selected = customRange!!
                        Text(formatRangeLabel(selected.first, selected.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        PeriodComparisonRow("Selected vs Previous", rangeData.currentDone, rangeData.previousDone)
                    }
                }
            }

            // Momentum & Velocity capsules
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCapsule("momentum", "${stats.momentumScore}/100", if (stats.momentumScore >= 60) Color(0xFF4CAF50) else Color(0xFFFF9800), Modifier.weight(1f))
                    InfoCapsule("velocity", "${if (stats.completionVelocity >= 0) "+" else ""}${String.format("%.1f", stats.completionVelocity)}/d", if (stats.completionVelocity >= 0) Color(0xFF4CAF50) else Color(0xFFF44336), Modifier.weight(1f))
                }
            }

            // Burnout
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Warning, "Burnout Risk")
                    Spacer(modifier = Modifier.height(12.dp))
                    val bColor = when { stats.burnoutRiskScore >= 0.65f -> Color(0xFFF44336); stats.burnoutRiskScore >= 0.35f -> Color(0xFFFFC107); else -> Color(0xFF4CAF50) }
                    LinearProgressIndicator(progress = { stats.burnoutRiskScore }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = bColor, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    Spacer(modifier = Modifier.height(8.dp))
                    FeedbackMessage(
                        when { stats.burnoutRiskScore >= 0.65f -> "High Risk — Take a break"; stats.burnoutRiskScore >= 0.35f -> "Moderate — Watch workload"; stats.burnoutRiskScore >= 0.1f -> "Low — Good zone"; else -> "Minimal — Keep going!" },
                        bColor
                    )
                }
            }

            // Growth
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "Growth Metrics")
                    Spacer(modifier = Modifier.height(12.dp))
                    GrowthMetricRow("Week over Week", stats.weekOverWeekGrowth)
                    Spacer(modifier = Modifier.height(8.dp))
                    GrowthMetricRow("Month over Month", stats.monthOverMonthGrowth)
                }
            }

            // Comparison
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.CalendarMonth, "Period Comparison")
                    Spacer(modifier = Modifier.height(10.dp))
                    PeriodComparisonRow("Today vs Yesterday", stats.todayCompleted, stats.yesterdayCompleted)
                    Spacer(modifier = Modifier.height(4.dp))
                    PeriodComparisonRow("This week vs Last", stats.thisWeekCompleted, stats.lastWeekCompleted)
                    Spacer(modifier = Modifier.height(4.dp))
                    PeriodComparisonRow("This month vs Last", stats.thisMonthCompleted, stats.lastMonthCompleted)
                }
            }

            // Peak
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.EmojiEvents, "Peak Analysis")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stats.peakDayOfWeek.isNotEmpty()) InfoCapsule("peak day", stats.peakDayOfWeek, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        if (stats.peakHourLabel.isNotEmpty()) InfoCapsule("peak time", stats.peakHourLabel, Color(0xFF2196F3), Modifier.weight(1f))
                        InfoCapsule("7d avg", String.format("%.1f", stats.movingAvg7Day), Color(0xFF4CAF50), Modifier.weight(1f))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs -> customRange = startMs to endMs }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 8. Pomodoro Analytics Detail
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PomodoroAnalyticsDetailSheet(stats: StatsState, onDismiss: () -> Unit) {
    var weekOffset by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val periodLabel = when (weekOffset) { 0 -> "This week"; 1 -> "Last week"; else -> "${weekOffset}w ago" }
    val selectedRangeComparison = customRange?.let { (startMs, endMs) ->
        remember(stats.dailyStatsWithDates, startMs, endMs) {
            computePeriodComparison(stats, startMs, endMs)
        }
    }

    StatsDetailSheet("Focus Analytics", onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PeriodNavigator(periodLabel, onPrev = { weekOffset++ }, onNext = { if (weekOffset > 0) weekOffset-- }, canGoNext = weekOffset > 0, onCalendarClick = { showDatePicker = true }) }

            selectedRangeComparison?.let { rangeData ->
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(Icons.Filled.DateRange, "Custom Range Comparison")
                        Spacer(modifier = Modifier.height(8.dp))
                        val selected = customRange!!
                        Text(formatRangeLabel(selected.first, selected.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        PeriodComparisonRow("Selected vs Previous", rangeData.currentDone, rangeData.previousDone)
                    }
                }
            }

            // Hero capsules
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCapsule("today", "${stats.todayFocusMinutes}m", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    InfoCapsule("sessions", "${stats.todayPomodoroSessions}", Color(0xFF2196F3), Modifier.weight(1f))
                    InfoCapsule("total", "${String.format("%.1f", stats.totalFocusHours)}h", Color(0xFF4CAF50), Modifier.weight(1f))
                }
            }

            // Summary
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.Timer, "Focus Summary")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoCapsule("daily avg", "${stats.averageDailyFocusMinutes}m", Color(0xFF2196F3), Modifier.weight(1f))
                        stats.bestFocusDay?.let { InfoCapsule("best", "$it (${stats.bestFocusDayMinutes}m)", Color(0xFFFF9800), Modifier.weight(1f)) }
                    }
                }
            }

            // Weekly chart
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(Icons.Filled.CalendarMonth, "Weekly Focus")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (stats.weeklyFocusData.isNotEmpty() && stats.weeklyFocusData.any { it.second > 0 }) {
                        MiniBarChart(labels = stats.weeklyFocusData.map { it.first }, values = stats.weeklyFocusData.map { it.second.toFloat() }, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().height(120.dp))
                    } else { Text("No focus data this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            // Top Tasks
            if (stats.topFocusedTasks.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(Icons.Filled.Star, "Most Focused Tasks")
                        Spacer(modifier = Modifier.height(10.dp))
                        stats.topFocusedTasks.forEachIndexed { idx, task ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                InfoCapsule("", "${idx + 1}", MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.taskTitle ?: "Unnamed", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${task.sessionCount} sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                InfoCapsule("", "${task.totalSeconds / 60}m", Color(0xFF4CAF50))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }

    if (showDatePicker) {
        CustomDateRangeDialog(
            onDismiss = { showDatePicker = false },
            initialStartMs = customRange?.first,
            initialEndMs = customRange?.second,
            onConfirm = { startMs, endMs -> customRange = startMs to endMs }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared Helpers — M3 Expressive
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ExpressiveProgressRow(label: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        if (label.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall)
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        LinearProgressIndicator(
            progress = { value },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
fun PeriodComparisonRow(label: String, current: Int, previous: Int) {
    val delta = current - previous
    val isPos = delta >= 0
    val color = if (isPos) Color(0xFF4CAF50) else Color(0xFFF44336)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$current", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("vs $previous", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            InfoCapsule("", "${if (isPos) "+" else ""}$delta", color)
        }
    }
}

@Composable
fun GrowthMetricRow(label: String, growthRate: Float) {
    val color = if (growthRate >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(if (growthRate >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null, modifier = Modifier.size(16.dp), tint = color)
            Text("${if (growthRate >= 0) "+" else ""}${(growthRate * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// Legacy helpers for StatsScreen compatibility
@Composable fun DetailCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(modifier = modifier.fillMaxWidth().padding(0.dp)) { Column { content() } }
@Composable fun StatBigNumber(label: String, value: String, unit: String, color: Color) = HeroStat(Icons.Filled.Star, value, "$unit  $label", color)
@Composable fun MetricRow(label: String, value: String) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) } }
@Composable fun ScoreBreakdownBar(label: String, value: Float, weight: Float, color: Color) = ExpressiveProgressRow(label, value, color)
@Composable fun ComparisonRow(label: String, rate: Float) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(progress = { rate }, modifier = Modifier.width(80.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), color = when { rate >= 0.8f -> Color(0xFF4CAF50); rate >= 0.5f -> Color(0xFFFFC107); else -> Color(0xFFF44336) }, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            Spacer(modifier = Modifier.width(8.dp))
            Text("${(rate * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Chart Components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun MiniLineChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    isDashed: Boolean = false,
    previousValues: List<Float>? = null,
    previousColor: Color = Color(0xFFFF9800),
) {
    if (values.isEmpty()) return
    val allMax = maxOf(values.maxOrNull() ?: 1f, previousValues?.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val allMin = minOf(values.minOrNull() ?: 0f, previousValues?.minOrNull() ?: 0f)
    val range = (allMax - allMin).coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val pad = 4.dp.toPx(); val cW = w - pad * 2; val cH = h - pad * 2

        fun yOf(v: Float) = pad + cH - ((v - allMin) / range) * cH

        // Previous period ghost line
        if (previousValues != null && previousValues.size >= 2) {
            val pStepX = cW / (previousValues.size - 1)
            val pPath = Path()
            previousValues.forEachIndexed { i, v ->
                val x = pad + i * pStepX; val y = yOf(v)
                if (i == 0) pPath.moveTo(x, y) else pPath.lineTo(x, y)
            }
            val pFill = Path().apply { addPath(pPath); lineTo(pad + (previousValues.size - 1) * pStepX, pad + cH); lineTo(pad, pad + cH); close() }
            drawPath(pFill, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(previousColor.copy(alpha = 0.08f), Color.Transparent)))
            drawPath(pPath, color = previousColor.copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }

        // Current line
        if (values.size >= 2) {
            val stepX = cW / (values.size - 1)
            val path = Path()
            values.forEachIndexed { i, v -> val x = pad + i * stepX; val y = yOf(v); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
            val fillPath = Path().apply { addPath(path); lineTo(pad + (values.size - 1) * stepX, pad + cH); lineTo(pad, pad + cH); close() }
            drawPath(fillPath, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.15f), Color.Transparent)))
            drawPath(path, color = color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            val lx = pad + (values.size - 1) * stepX; val ly = yOf(values.last())
            drawCircle(color, 5.dp.toPx(), Offset(lx, ly))
        }
    }
}

@Composable
fun MiniBarChart(labels: List<String>, values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    val maxVal = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val w = size.width; val h = size.height; val bc = values.size; val bw = w / bc * 0.6f; val gap = w / bc * 0.4f
            values.forEachIndexed { i, v -> val bH = (v / maxVal) * h * 0.9f; val x = i * (bw + gap) + gap / 2; drawRoundRect(color.copy(alpha = 0.7f), Offset(x, h - bH), Size(bw, bH), CornerRadius(4.dp.toPx())) }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun CleanMetricRow(label: String, value: String) = MetricRow(label, value)

@Composable
fun CleanProgressRow(label: String, value: Float, color: Color) = ExpressiveProgressRow(label, value, color)
