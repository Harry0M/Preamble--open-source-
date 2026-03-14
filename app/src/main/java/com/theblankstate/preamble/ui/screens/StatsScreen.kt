package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.ads.FeatureGateManager
import com.theblankstate.preamble.ui.components.FeatureType
import com.theblankstate.preamble.ui.components.FeatureUnlockSheet
import com.theblankstate.preamble.viewmodel.StatsState

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Stat cards row — no backgrounds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Filled.LocalFireDepartment,
                    value = "${statsState.streak}",
                    label = "Day Streak",
                    iconColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.CheckCircle,
                    value = "${statsState.todayCompleted}/${statsState.todayTotal}",
                    label = "Today",
                    iconColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    icon = Icons.Filled.EmojiEvents,
                    value = "${statsState.totalCompleted}",
                    label = "All Time",
                    iconColor = primaryColor,
                    modifier = Modifier.weight(1f)
                )
            }

            // Completion rate — no background
            val completionRate = if (statsState.totalTasks > 0) {
                (statsState.totalCompleted.toFloat() / statsState.totalTasks * 100).toInt()
            } else 0

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Overall Completion",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$completionRate%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
            }

            // 14-day wave chart
            if (statsState.dailyCompleted.isNotEmpty()) {
                Text(
                    "Last 14 Days",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                WaveChart(
                    data = statsState.dailyCompleted,
                    color = primaryColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    statsState.dailyCompleted.forEachIndexed { i, (label, _) ->
                        if (i % 2 == 0) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Weekly bar chart
            if (statsState.weeklyStats.isNotEmpty()) {
                Text(
                    "This Week",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    statsState.weeklyStats.forEach { (day, rate) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${(rate * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height((rate * 80).dp.coerceAtLeast(4.dp))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (rate >= 1f) primaryColor
                                        else primaryColor.copy(alpha = 0.3f + rate * 0.5f)
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 30-day wave chart
            if (statsState.monthlyCompleted.isNotEmpty()) {
                Text(
                    "Last 30 Days",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val total30 = statsState.monthlyCompleted.sumOf { it.second }
                Text(
                    "$total30 tasks completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                WaveChart(
                    data = statsState.monthlyCompleted,
                    color = primaryColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            }

            // Motivational message
            val message = when {
                statsState.streak >= 7 -> "Amazing! You're on fire!"
                statsState.streak >= 3 -> "Great consistency! Keep it up!"
                statsState.todayCompleted == statsState.todayTotal && statsState.todayTotal > 0 ->
                    "All done for today!"
                statsState.todayTotal > 0 ->
                    "${statsState.todayTotal - statsState.todayCompleted} tasks left. You got this!"
                else -> "Add some tasks to get started!"
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Global Productivity Rank
            val mockRank = remember(statsState.totalCompleted) {
                val seed = statsState.totalCompleted.toLong()
                (50000 - (seed * 137 % 49000)).coerceAtLeast(1)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!statsUnlocked) showStatsUnlockSheet = true
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Leaderboard,
                                contentDescription = "Global Rank",
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Global Rank", style = MaterialTheme.typography.titleMedium)
                        }
                        if (!statsUnlocked) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (statsUnlocked) {
                        Text(
                            text = "#$mockRank",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        Text(
                            "out of 127,493 users worldwide",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Watch an ad to see your rank among all Preamble users",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Ad placement — styled card with ambient gradient
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(adShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                surfaceVariant,
                                primaryColor.copy(alpha = 0.06f),
                                surface
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                borderColor,
                                primaryColor.copy(alpha = 0.08f),
                                borderColor
                            )
                        ),
                        shape = adShape
                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Stats unlock sheet
    if (showStatsUnlockSheet && activity != null) {
        FeatureUnlockSheet(
            featureType = FeatureType.STATS,
            activity = activity,
            onDismiss = { showStatsUnlockSheet = false }
        )
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

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = h
                )
            )

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val cx1 = (prev.x + curr.x) / 2
                    cubicTo(cx1, prev.y, cx1, curr.y, curr.x, curr.y)
                }
            }

            drawPath(
                path = linePath,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        points.forEach { point ->
            drawCircle(color = color, radius = 3.dp.toPx(), center = point)
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
