package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.DailyFocusStats
import com.theblankstate.preamble.data.FocusSession
import com.theblankstate.preamble.data.PerTaskTimerStats
import com.theblankstate.preamble.viewmodel.StatsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskFocusBreakdownScreen(
    statsState: StatsState,
    onBack: () -> Unit,
    onTaskClick: ((taskId: String?, taskTitle: String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    dark: Boolean = true
) {
    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f)
    val hair = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val tile = if (dark) Color(0xFF1A1A1A) else Color(0xFFF4F4F3)
    val surface = if (dark) Color(0xFF0E0E0E) else Color.White
    val accent = MaterialTheme.colorScheme.primary

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var expandedTaskId by rememberSaveable { mutableStateOf<String?>(null) }

    val allTaskStats = remember(statsState.perTaskStatsList, statsState.topFocusedTasks) {
        if (statsState.perTaskStatsList.isNotEmpty()) {
            statsState.perTaskStatsList
        } else {
            statsState.topFocusedTasks.map {
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

    val grandTotalSeconds = remember(allTaskStats) {
        allTaskStats.sumOf { it.totalSeconds }.coerceAtLeast(1)
    }

    val filteredTaskStats = remember(allTaskStats, searchQuery) {
        if (searchQuery.isBlank()) {
            allTaskStats
        } else {
            allTaskStats.filter {
                (it.taskTitle ?: "Standalone Session").contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Sessions map grouped by taskId or title
    val sessionsByTask = remember(statsState.recentSessionsList) {
        statsState.recentSessionsList.groupBy { it.taskId ?: it.taskTitle ?: "standalone" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        // Floating Top Header
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = fg
                )
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = "FOCUS STATS",
                    color = fgMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = "Task Breakdown",
                    color = fg,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Search Bar & Metric Summary Pill
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = tile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = fgMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = fg,
                            fontSize = 14.sp
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search task focus history...",
                                    color = fgMuted,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredTaskStats.size} TASKS TRACKED",
                    color = fgMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "TOTAL: ${formatMinutesLong(grandTotalSeconds / 60)}",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filteredTaskStats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No tasks matching '$searchQuery'" else "No task focus statistics recorded yet.",
                    color = fgMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = filteredTaskStats,
                    key = { index, item -> item.taskId ?: item.taskTitle ?: "key_$index" }
                ) { _, stat ->
                    val isExpanded = expandedTaskId == stat.taskId
                    val percentage = (stat.totalSeconds.toFloat() / grandTotalSeconds.toFloat() * 100f).coerceIn(0f, 100f)

                    TaskFocusItemCard(
                        stat = stat,
                        percentage = percentage,
                        isExpanded = isExpanded,
                        sessions = sessionsByTask[stat.taskId] ?: sessionsByTask[stat.taskTitle] ?: emptyList(),
                        fg = fg,
                        fgMuted = fgMuted,
                        tile = tile,
                        hair = hair,
                        accent = accent,
                        onToggleExpand = {
                            expandedTaskId = if (isExpanded) null else stat.taskId
                        },
                        onTaskClick = {
                            onTaskClick?.invoke(stat.taskId, stat.taskTitle)
                        }
                    )
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun TaskFocusItemCard(
    stat: PerTaskTimerStats,
    percentage: Float,
    isExpanded: Boolean,
    sessions: List<FocusSession>,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    hair: Color,
    accent: Color,
    onToggleExpand: () -> Unit,
    onTaskClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tile)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.taskTitle ?: "Standalone Session",
                    color = fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Sessions: ${stat.sessionCount} · Avg: ${stat.avgDurationSeconds / 60}m",
                    color = fgMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMinutesLong(stat.totalSeconds / 60),
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format(Locale.US, "%.1f%%", percentage),
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = fgMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(fgMuted.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accent)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(hair)
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "DAILY FOCUS TIMELINE",
                    color = fgMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )

                Spacer(Modifier.height(8.dp))

                if (sessions.isEmpty()) {
                    Text(
                        text = "No detailed session timeline available.",
                        color = fgMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    val dailyGroups = remember(sessions) {
                        sessions.groupBy { it.date }
                            .map { (date, list) ->
                                val totalSec = list.sumOf { it.actualDurationCompletedSeconds }
                                val count = list.size
                                DailyFocusStats(date = date, totalSeconds = totalSec, sessionCount = count)
                            }
                            .sortedByDescending { it.date }
                    }

                    val maxDailySec = remember(dailyGroups) {
                        dailyGroups.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1) ?: 1
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dailyGroups.take(10).forEach { daily ->
                            val dailyPct = (daily.totalSeconds.toFloat() / maxDailySec.toFloat()).coerceIn(0.05f, 1f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(fgMuted.copy(alpha = 0.08f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formatTimelineDate(daily.date),
                                        color = fg,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${daily.sessionCount} session${if (daily.sessionCount > 1) "s" else ""}",
                                        color = fgMuted,
                                        fontSize = 10.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(fgMuted.copy(alpha = 0.2f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(dailyPct)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(accent)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = formatMinutesLong(daily.totalSeconds / 60),
                                        color = fg,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatMinutesLong(minutes: Int): String {
    val hrs = minutes / 60
    val mins = minutes % 60
    return when {
        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
        hrs > 0 -> "${hrs}h"
        else -> "${mins}m"
    }
}

private fun formatTimelineDate(dateStr: String): String {
    return try {
        val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsedDate = sdfInput.parse(dateStr) ?: return dateStr

        val sdfToday = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = sdfToday.format(Date())

        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdfToday.format(cal.time)

        when (dateStr) {
            todayStr -> "Today"
            yesterdayStr -> "Yesterday"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsedDate)
        }
    } catch (_: Exception) {
        dateStr
    }
}
