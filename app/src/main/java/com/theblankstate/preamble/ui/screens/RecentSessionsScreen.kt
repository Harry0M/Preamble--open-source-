package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.FocusSession
import com.theblankstate.preamble.viewmodel.StatsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

@Composable
fun RecentSessionsScreen(
    statsState: StatsState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = true
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val scaleFactor: Float = (screenWidthDp / 360f).coerceIn(0.85f, 1.15f)

    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f)
    val hair = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val tile = if (dark) Color(0xFF1A1A1A) else Color(0xFFF4F4F3)
    val surface = if (dark) Color(0xFF0E0E0E) else Color.White
    val accent = MaterialTheme.colorScheme.primary

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val allSessions = remember(statsState.recentSessionsList) {
        statsState.recentSessionsList
    }

    val filteredSessions = remember(allSessions, searchQuery) {
        if (searchQuery.isBlank()) {
            allSessions
        } else {
            allSessions.filter {
                (it.taskTitle ?: "Standalone Session").contains(searchQuery, ignoreCase = true) ||
                        it.date.contains(searchQuery, ignoreCase = true) ||
                        it.completionStatus.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Clearance Spacer for Floating Top Header Bar
            Spacer(modifier = Modifier.height(76.dp * scaleFactor))

            // Search Bar & Metric Pill
            Column(modifier = Modifier.padding(horizontal = 20.dp * scaleFactor, vertical = 6.dp * scaleFactor)) {
                Surface(
                    shape = RoundedCornerShape(16.dp * scaleFactor),
                    color = tile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp * scaleFactor, vertical = 10.dp * scaleFactor),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = fgMuted,
                            modifier = Modifier.size(18.dp * scaleFactor)
                        )
                        Spacer(Modifier.width(10.dp * scaleFactor))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = fg,
                                fontSize = 14.sp * scaleFactor
                            ),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search session history...",
                                        color = fgMuted,
                                        fontSize = 14.sp * scaleFactor
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp * scaleFactor))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredSessions.size} SESSIONS RECORDED",
                        color = fgMuted,
                        fontSize = 11.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp * scaleFactor
                    )
                }
            }

            Spacer(Modifier.height(8.dp * scaleFactor))

            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp * scaleFactor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No sessions matching '$searchQuery'" else "No focus sessions recorded yet.",
                        color = fgMuted,
                        fontSize = 14.sp * scaleFactor
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp * scaleFactor),
                    verticalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
                ) {
                    itemsIndexed(
                        items = filteredSessions,
                        key = { _, sess -> sess.id }
                    ) { _, sess ->
                        RecentSessionCard(
                            session = sess,
                            fg = fg,
                            fgMuted = fgMuted,
                            tile = tile,
                            hair = hair,
                            accent = accent,
                            scaleFactor = scaleFactor
                        )
                    }
                    item { Spacer(Modifier.height(40.dp * scaleFactor)) }
                }
            }
        }

        // Floating Header Bar (FAB Style Signature)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp * scaleFactor, vertical = 6.dp * scaleFactor),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            Surface(
                shape = CircleShape,
                color = tile.copy(alpha = 0.95f),
                modifier = Modifier
                    .size(44.dp * scaleFactor)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null
                    ) { onBack() }
                    .expressivePressScale(backInteraction)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = fg,
                        modifier = Modifier.size(20.dp * scaleFactor)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = tile.copy(alpha = 0.95f),
                modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
            ) {
                Text(
                    text = "Recent Sessions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp * scaleFactor
                    ),
                    color = fg,
                    modifier = Modifier.padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor)
                )
            }

            Spacer(modifier = Modifier.size(44.dp * scaleFactor))
        }
    }
}

@Composable
private fun RecentSessionCard(
    session: FocusSession,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    hair: Color,
    accent: Color,
    scaleFactor: Float
) {
    val completed = session.completionStatus == "COMPLETED"
    val durationMins = maxOf(1, session.actualDurationCompletedSeconds / 60)

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val startTimeStr = remember(session.startTimestamp) {
        timeFormat.format(Date(session.startTimestamp))
    }
    val endTimeStr = remember(session.endTimestamp) {
        timeFormat.format(Date(session.endTimestamp))
    }

    val cardInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp * scaleFactor))
            .background(tile)
            .padding(14.dp * scaleFactor)
            .expressivePressScale(cardInteraction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(38.dp * scaleFactor)
                .clip(CircleShape)
                .background(if (completed) accent.copy(alpha = 0.15f) else fgMuted.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (completed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = session.completionStatus,
                tint = if (completed) accent else fgMuted,
                modifier = Modifier.size(20.dp * scaleFactor)
            )
        }
        Spacer(Modifier.width(12.dp * scaleFactor))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.taskTitle ?: "Standalone Session",
                color = fg,
                fontSize = 14.sp * scaleFactor,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp * scaleFactor))
            Text(
                text = "${formatSessionDate(session.date)} · $startTimeStr - $endTimeStr",
                color = fgMuted,
                fontSize = 11.sp * scaleFactor
            )
        }
        Spacer(Modifier.width(8.dp * scaleFactor))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (completed) accent.copy(alpha = 0.2f) else fgMuted.copy(alpha = 0.15f)
                )
                .padding(horizontal = 10.dp * scaleFactor, vertical = 5.dp * scaleFactor)
        ) {
            Text(
                text = "${durationMins}m",
                color = fg,
                fontSize = 12.sp * scaleFactor,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun formatSessionDate(dateStr: String): String {
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
