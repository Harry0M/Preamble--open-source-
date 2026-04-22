package com.theblankstate.preamble.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Animated circular gauge showing productivity score (0-100).
 */
@Composable
fun AnimatedCircularGauge(
    score: Int,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(score) {
        animatedProgress.animateTo(
            targetValue = score / 100f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }
    val animatedScore by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "score"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Background track
            drawArc(
                color = primaryColor.copy(alpha = 0.12f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Foreground arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.6f),
                        primaryColor,
                        primaryColor.copy(alpha = 0.8f)
                    )
                ),
                startAngle = 135f,
                sweepAngle = 270f * animatedProgress.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$animatedScore",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            Text(
                text = when {
                    score >= 80 -> "Excellent!"
                    score >= 60 -> "Great work"
                    score >= 40 -> "Good progress"
                    score >= 20 -> "Getting there"
                    else -> "Let's start"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Trend arrow showing up/down/neutral.
 */
@Composable
fun TrendArrow(trend: Int, modifier: Modifier = Modifier) {
    val (icon, color) = when {
        trend > 0 -> Icons.AutoMirrored.Filled.TrendingUp to Color(0xFF4CAF50)
        trend < 0 -> Icons.AutoMirrored.Filled.TrendingDown to Color(0xFFF44336)
        else -> Icons.AutoMirrored.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val label = when {
        trend > 0 -> "vs last week"
        trend < 0 -> "vs last week"
        else -> "same as last week"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Donut chart for task type breakdown.
 */
@Composable
fun DonutChart(
    segments: List<Pair<String, Float>>, // label to percentage (0-1)
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(segments) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }

    Canvas(modifier = modifier) {
        val strokeWidth = 28.dp.toPx()
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
        var startAngle = -90f

        segments.forEachIndexed { index, (_, percentage) ->
            val sweep = percentage * 360f * animatedProgress.value
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep.coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += percentage * 360f
        }
    }
}

/**
 * Monthly heatmap calendar (GitHub-style).
 */
@Composable
fun HeatmapCalendar(
    heatmap: Map<Int, Float>, // day-of-month to density 0-1
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val daysInMonth = heatmap.size.coerceAtLeast(28)
    val rows = (daysInMonth + 6) / 7

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                for (col in 0 until 7) {
                    val day = row * 7 + col + 1
                    if (day <= daysInMonth) {
                        val density = heatmap[day] ?: 0f
                        val bgColor = if (density > 0f)
                            primaryColor.copy(alpha = 0.15f + density * 0.75f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$day",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (density > 0.5f)
                                    Color.White
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Yearly heatmap calendar (GitHub-style profile grid).
 * Displays exactly 365 days (52 weeks) in a scrollable view.
 */
@Composable
fun YearHeatmapCalendar(
    heatmap: Map<String, Float>, // Date string to density 0-1
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    if (heatmap.isEmpty()) return
    
    // Sort dates chronologically to ensure they flow from oldest to newest
    val sortedDates = remember(heatmap) { heatmap.keys.sorted() }
    
    // Each column has up to 7 items. We iterate over columns.
    val numCols = (sortedDates.size + 6) / 7
    
    // We'll use a LazyRow so it scrolls nicely horizontally.
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        items(numCols) { col ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (row in 0 until 7) {
                    val index = col * 7 + row
                    if (index < sortedDates.size) {
                        val dateKey = sortedDates[index]
                        val density = heatmap[dateKey] ?: 0f
                        val bgColor = if (density > 0f)
                            primaryColor.copy(alpha = 0.15f + density * 0.75f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(bgColor)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/**
 * Focus bar chart for weekly pomodoro data — powered by Vico.
 */
@Composable
fun FocusBarChart(
    data: List<Pair<String, Int>>, // day label to minutes
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        modelProducer.runTransaction {
            val values: Array<Number> = data.map { it.second as Number }.toTypedArray()
            columnSeries { series(*values) }
        }
    }

    Column(modifier = modifier) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            data.forEach { (day, _) ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Section card with optional premium lock overlay.
 * When locked, content is blurred and covered with a frosted glass overlay
 * so data is completely unreadable but creates an enticing teaser effect.
 */
@Composable
fun StatsSectionCard(
    title: String,
    isLocked: Boolean = false,
    onLockedClick: () -> Unit = {},
    onDetailClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isLocked) Modifier.clickable { onLockedClick() }
                else if (onDetailClick != null) Modifier.clickable { onDetailClick() }
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .then(if (isLocked) Modifier.blur(20.dp) else Modifier)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.9f))
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!isLocked && onDetailClick != null) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "Details →",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Column(modifier = Modifier) {
                    content()
                }
            }

            // Frosted glass lock overlay — fully opaque, no data leaks
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                                )
                            ),
                            RoundedCornerShape(28.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            primaryColor.copy(alpha = 0.15f),
                                            primaryColor.copy(alpha = 0.05f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(22.dp),
                                tint = primaryColor.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Coming soon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mini stat tile for the Today's Dashboard 2x2 grid.
 */
@Composable
fun MiniStatTile(
    value: String,
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Consistency dots showing 7 days.
 */
@Composable
fun ConsistencyDots(
    consistencyDays: Int,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEachIndexed { index, day ->
            val filled = index < consistencyDays
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) primaryColor
                            else primaryColor.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (filled) {
                        Text(
                            text = "\u2713",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Tag stat row for tag analytics.
 */
@Composable
fun TagStatRow(
    tag: String,
    totalCount: Int,
    completionRate: Float,
    primaryColor: Color,
    isMostActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedRate by animateFloatAsState(
        targetValue = completionRate,
        animationSpec = tween(600),
        label = "tagRate"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isMostActive) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(80.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(primaryColor.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedRate)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(primaryColor.copy(alpha = 0.5f + animatedRate * 0.5f))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$totalCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )
        Text(
            text = "${(completionRate * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = primaryColor,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Rounded-top bar chart (fitness app image 1 style).
 * Today = todayIndex, shown with filled-circle label badge.
 */
@Composable
fun StyledBarChart(
    data: List<Pair<String, Int>>,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    todayIndex: Int = if (data.isEmpty()) -1 else data.lastIndex,
    previousData: List<Int>? = null,
    previousLabel: String = "prev",
) {
    if (data.isEmpty()) return
    val compColor = Color(0xFFFF9800)
    val currentMax = data.maxOf { it.second }
    val prevMax = previousData?.maxOrNull() ?: 0
    val maxVal = maxOf(currentMax, prevMax).coerceAtLeast(1)
    val barMaxHeight = 110

    Column(modifier = modifier) {
        // Legend when previous data present
        if (previousData != null && previousData.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(primaryColor))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Current", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(compColor.copy(alpha = 0.35f)))
                Spacer(modifier = Modifier.width(4.dp))
                Text(previousLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barMaxHeight.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEachIndexed { index, (_, value) ->
                val isToday = index == todayIndex
                val barHeight = ((value.toFloat() / maxVal) * barMaxHeight).dp.coerceAtLeast(4.dp)
                val prevVal = previousData?.getOrNull(index)
                val prevBarHeight = if (prevVal != null) ((prevVal.toFloat() / maxVal) * barMaxHeight).dp.coerceAtLeast(4.dp) else 0.dp

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        // Previous period ghost bar (behind)
                        if (prevVal != null && prevVal > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.58f)
                                    .height(prevBarHeight)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 10.dp, topEnd = 10.dp,
                                            bottomStart = 3.dp, bottomEnd = 3.dp
                                        )
                                    )
                                    .background(compColor.copy(alpha = 0.18f))
                            )
                        }
                        // Current period bar (foreground, narrower to show ghost behind)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (previousData != null) 0.42f else 0.58f)
                                .height(barHeight)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 10.dp, topEnd = 10.dp,
                                        bottomStart = 3.dp, bottomEnd = 3.dp
                                    )
                                )
                                .background(
                                    if (isToday) primaryColor else primaryColor.copy(alpha = 0.38f)
                                )
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            data.forEachIndexed { index, (day, _) ->
                val isToday = index == todayIndex
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(primaryColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = day.take(1),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Text(
                            text = day.take(1),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fully-rounded pill bar chart (fitness app image 3 style).
 * Capsule bars, today highlighted.
 */
@Composable
fun StyledCapsuleChart(
    data: List<Pair<String, Int>>,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    todayIndex: Int = if (data.isEmpty()) -1 else data.lastIndex,
) {
    if (data.isEmpty()) return
    val maxVal = data.maxOf { it.second }.coerceAtLeast(1)
    val barMaxHeight = 80

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barMaxHeight.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEachIndexed { index, (_, value) ->
                val isToday = index == todayIndex
                val barHeight = ((value.toFloat() / maxVal) * barMaxHeight).dp.coerceAtLeast(8.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.52f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (isToday) primaryColor else primaryColor.copy(alpha = 0.42f)
                            )
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            data.forEachIndexed { index, (day, _) ->
                val isToday = index == todayIndex
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(primaryColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = day.take(1),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Text(
                            text = day.take(1),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Line chart with dots (fitness app image 2 style).
 * Dotted avg reference line, today dot with ring, other dots solid.
 */
@Composable
fun StyledLineChart(
    data: List<Pair<String, Int>>,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    todayIndex: Int = if (data.isEmpty()) -1 else data.lastIndex,
    previousData: List<Int>? = null,
    previousLabel: String = "prev",
) {
    if (data.size < 2) return
    val compColor = Color(0xFFFF9800)
    // Use max across both current + previous for consistent Y scale
    val currentMax = data.maxOf { it.second }
    val prevMax = previousData?.maxOrNull() ?: 0
    val maxVal = maxOf(currentMax, prevMax).coerceAtLeast(1)
    val avg = data.sumOf { it.second } / data.size

    Column(modifier = modifier) {
        // Legend when previous data present
        if (previousData != null && previousData.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(primaryColor))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Current", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(compColor.copy(alpha = 0.5f)))
                Spacer(modifier = Modifier.width(4.dp))
                Text(previousLabel, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            val w = size.width
            val h = size.height
            val padH = 12.dp.toPx()
            val padV = 14.dp.toPx()
            val chartH = h - padV * 2
            val chartW = w - padH * 2

            fun xFor(i: Int, count: Int) = padH + (i.toFloat() / (count - 1).coerceAtLeast(1)) * chartW
            fun yFor(v: Int) = padV + chartH * (1f - v.toFloat() / maxVal)

            val avgY = padV + chartH * (1f - avg.toFloat() / maxVal)

            // Dotted avg reference line
            var dx = 0f
            val dash = 8.dp.toPx()
            val gap = 5.dp.toPx()
            while (dx < w) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.28f),
                    start = Offset(dx, avgY),
                    end = Offset((dx + dash).coerceAtMost(w), avgY),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
                dx += dash + gap
            }

            // Previous period ghost line (dashed)
            if (previousData != null && previousData.size >= 2) {
                val prevCount = previousData.size
                for (i in 0 until prevCount - 1) {
                    val x1 = xFor(i, prevCount)
                    val y1 = yFor(previousData[i])
                    val x2 = xFor(i + 1, prevCount)
                    val y2 = yFor(previousData[i + 1])
                    // Draw dashed line segment
                    val segLen = kotlin.math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
                    val dashPx = 6.dp.toPx()
                    val gapPx = 4.dp.toPx()
                    if (segLen > 0f) {
                        val dirX = (x2 - x1) / segLen
                        val dirY = (y2 - y1) / segLen
                        var traveled = 0f
                        while (traveled < segLen) {
                            val end = (traveled + dashPx).coerceAtMost(segLen)
                            drawLine(
                                color = compColor.copy(alpha = 0.45f),
                                start = Offset(x1 + dirX * traveled, y1 + dirY * traveled),
                                end = Offset(x1 + dirX * end, y1 + dirY * end),
                                strokeWidth = 2f,
                                cap = StrokeCap.Round,
                            )
                            traveled = end + gapPx
                        }
                    }
                }
                // Ghost dots
                previousData.forEachIndexed { i, v ->
                    drawCircle(compColor.copy(alpha = 0.30f), radius = 3.dp.toPx(), center = Offset(xFor(i, prevCount), yFor(v)))
                }
            }

            // Current period lines
            for (i in 0 until data.size - 1) {
                drawLine(
                    color = primaryColor,
                    start = Offset(xFor(i, data.size), yFor(data[i].second)),
                    end = Offset(xFor(i + 1, data.size), yFor(data[i + 1].second)),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round,
                )
            }

            // Current dots
            data.forEachIndexed { i, (_, v) ->
                val cx = xFor(i, data.size)
                val cy = yFor(v)
                val isToday = i == todayIndex
                if (isToday) {
                    drawCircle(primaryColor.copy(alpha = 0.20f), radius = 11.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(primaryColor, radius = 5.dp.toPx(), center = Offset(cx, cy))
                } else {
                    drawCircle(primaryColor.copy(alpha = 0.55f), radius = 4.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            data.forEachIndexed { index, (day, _) ->
                val isToday = index == todayIndex
                Box(
                    modifier = if (isToday)
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                    else Modifier,
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.take(1),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (isToday)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Comparison Row — shows current vs previous with % growth badge
// ═══════════════════════════════════════════════════════════════

@Composable
fun ComparisonRow(
    label: String,
    current: Int,
    previous: Int,
    currentLabel: String,
    previousLabel: String,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val delta = current - previous
    val pct = if (previous > 0) delta.toFloat() / previous * 100f else 0f
    val growthColor = if (delta >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val trendIcon = when {
        delta > 0 -> Icons.Filled.ArrowDropUp
        delta < 0 -> Icons.Filled.ArrowDropDown
        else -> Icons.Filled.Remove
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "$current",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Text(currentLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(growthColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(trendIcon, null, tint = growthColor, modifier = Modifier.size(14.dp))
                    Text(
                        "${if (delta >= 0) "+" else ""}${pct.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = growthColor
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(previousLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$previous",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Intelligence Metric Row — animated bar + label + score
// ═══════════════════════════════════════════════════════════════

@Composable
fun IntelligenceMetricRow(
    label: String,
    score: Int,           // 0–100
    primaryColor: Color,
    modifier: Modifier = Modifier,
    barColor: Color = primaryColor
) {
    val animatedWidth by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "intel_bar"
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(barColor.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedWidth)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.7f), barColor)
                        )
                    )
            )
        }
        Text(
            "$score",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = barColor,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Burnout Risk Chip
// ═══════════════════════════════════════════════════════════════

@Composable
fun BurnoutRiskChip(risk: Float, modifier: Modifier = Modifier) {
    val (label, color) = when {
        risk >= 0.65f -> "HIGH" to Color(0xFFF44336)
        risk >= 0.35f -> "MEDIUM" to Color(0xFFF59E0B)
        else -> "LOW" to Color(0xFF4CAF50)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Completion History Detail Sheet — full-screen drill-down
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionHistorySheet(
    dailyStatsWithDates: List<Triple<String, Int, Int>>,
    primaryColor: Color,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedRange by remember { mutableIntStateOf(14) }
    val ranges = listOf(7 to "7D", 14 to "14D", 30 to "30D", 90 to "90D")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Completion History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Range selector chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEach { (days, label) ->
                    val selected = selectedRange == days
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) primaryColor else primaryColor.copy(alpha = 0.10f)
                            )
                            .clickable { selectedRange = days }
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else primaryColor
                        )
                    }
                }
            }

            // Build display data for selected range
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val labelSdf = SimpleDateFormat("MMM d", Locale.getDefault())
            val filtered = dailyStatsWithDates.takeLast(selectedRange)
            val chartData = filtered.map { (date, comp, _) ->
                val label = try { labelSdf.format(sdf.parse(date)!!) } catch (_: Exception) { date.takeLast(5) }
                label to comp
            }

            if (chartData.isNotEmpty()) {
                StyledLineChart(data = chartData, primaryColor = primaryColor, modifier = Modifier.fillMaxWidth().height(120.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Summary stats for selected range
            val values = filtered.map { it.second }
            val avg = if (values.isNotEmpty()) values.average() else 0.0
            val maxVal = values.maxOrNull() ?: 0
            val total = values.sum()
            val last7Avg = filtered.takeLast(7).map { it.second }.average()
            val prev7Avg = if (filtered.size >= 14)
                filtered.dropLast(7).takeLast(7).map { it.second }.average() else 0.0
            val delta7 = if (prev7Avg > 0) ((last7Avg - prev7Avg) / prev7Avg * 100).toInt() else 0

            Text("Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryMetricTile("Total", "$total", primaryColor)
                SummaryMetricTile("Avg/Day", String.format("%.1f", avg), primaryColor)
                SummaryMetricTile("Best Day", "$maxVal", primaryColor)
                SummaryMetricTile("7D trend", if (delta7 >= 0) "+$delta7%" else "$delta7%",
                    if (delta7 >= 0) Color(0xFF4CAF50) else Color(0xFFF44336))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Raw data (last 10 rows)
            Text("Daily Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            filtered.reversed().take(10).forEach { (date, comp, tot) ->
                val displayDate = try { labelSdf.format(sdf.parse(date)!!) } catch (_: Exception) { date }
                val rate = if (tot > 0) (comp.toFloat() / tot * 100).toInt() else 0
                val barColor = when {
                    rate >= 75 -> Color(0xFF4CAF50)
                    rate >= 40 -> Color(0xFFF59E0B)
                    else -> primaryColor.copy(alpha = 0.5f)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(displayDate, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(primaryColor.copy(alpha = 0.10f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(rate / 100f).height(6.dp)
                                .clip(RoundedCornerShape(3.dp)).background(barColor)
                        )
                    }
                    Text("$comp${if (tot > 0) "/$tot" else ""}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricTile(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
