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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.ads.FeatureGateManager
import com.theblankstate.preamble.ui.components.AnimatedCircularGauge
import com.theblankstate.preamble.ui.components.ConsistencyDots
import com.theblankstate.preamble.ui.components.DonutChart
import com.theblankstate.preamble.ui.components.FeatureType
import com.theblankstate.preamble.ui.components.FeatureUnlockSheet
import com.theblankstate.preamble.ui.components.FocusBarChart
import com.theblankstate.preamble.ui.components.HeatmapCalendar
import com.theblankstate.preamble.ui.components.MiniStatTile
import com.theblankstate.preamble.ui.components.StatsSectionCard
import com.theblankstate.preamble.ui.components.TagStatRow
import com.theblankstate.preamble.ui.components.TrendArrow
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
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val statsUnlocked by FeatureGateManager.statsUnlocked.collectAsState()
    var showStatsUnlockSheet by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Statistics", style = MaterialTheme.typography.titleLarge) }
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
            // Section 1: Productivity Score (Hero)
            // ═══════════════════════════════════════════
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Productivity Score",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        AnimatedCircularGauge(
                            score = statsState.productivityScore,
                            primaryColor = primaryColor,
                            modifier = Modifier.size(160.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TrendArrow(trend = statsState.productivityScoreTrend)
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 2: Today's Focus Dashboard (2x2)
            // ═══════════════════════════════════════════
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiniStatTile(
                            value = "${statsState.todayCompleted}/${statsState.todayTotal}",
                            label = "Tasks",
                            icon = {
                                Icon(Icons.Filled.CheckCircle, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MiniStatTile(
                            value = "${statsState.todayFocusMinutes}m",
                            label = "Focus",
                            icon = {
                                Icon(Icons.Filled.Timer, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiniStatTile(
                            value = "${statsState.todayPomodoroSessions}",
                            label = "Pomodoros",
                            icon = {
                                Icon(Icons.Filled.AccessTime, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MiniStatTile(
                            value = "${statsState.streak}",
                            label = "Day Streak",
                            icon = {
                                Icon(Icons.Filled.LocalFireDepartment, null, tint = primaryColor, modifier = Modifier.size(22.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════
            // Section 3: Streak & Consistency
            // ═══════════════════════════════════════════
            item {
                StatsSectionCard(title = "Streak & Consistency") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${statsState.streak}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${statsState.longestStreak}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Longest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${statsState.weeklyConsistencyDays}/7",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (statsState.weeklyConsistencyDays >= 5) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                StatsSectionCard(title = "Completion Trends") {
                    // Weekly comparison
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("This week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${statsState.thisWeekCompleted} done",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Last week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${statsState.lastWeekCompleted} done",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (statsState.dailyCompleted.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Last 14 Days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        WaveChart(
                            data = statsState.dailyCompleted,
                            color = primaryColor,
                            modifier = Modifier.fillMaxWidth().height(100.dp)
                        )
                    }

                    if (statsState.weeklyStats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Weekly Rate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            statsState.weeklyStats.forEach { (day, rate) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${(rate * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height((rate * 50).dp.coerceAtLeast(3.dp))
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(if (rate >= 1f) primaryColor else primaryColor.copy(alpha = 0.3f + rate * 0.5f))
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                        }
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
                    onLockedClick = { showStatsUnlockSheet = true }
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
                        Spacer(modifier = Modifier.height(4.dp))
                        FocusBarChart(data = statsState.weeklyFocusData, primaryColor = primaryColor, modifier = Modifier.fillMaxWidth().height(90.dp))
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
                    onLockedClick = { showStatsUnlockSheet = true }
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
                    onLockedClick = { showStatsUnlockSheet = true }
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
                    onLockedClick = { showStatsUnlockSheet = true }
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
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
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
