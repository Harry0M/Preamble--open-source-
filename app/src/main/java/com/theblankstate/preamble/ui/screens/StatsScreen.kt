package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.viewmodel.StatsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    statsState: StatsState,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

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

            Spacer(modifier = Modifier.height(32.dp))
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
