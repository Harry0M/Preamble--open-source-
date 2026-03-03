package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    selectedDateTasks: List<Task>,
    heatMap: Map<Int, Pair<Int, Int>>,
    onDateSelected: (String?) -> Unit,
    onMonthChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDay by remember { mutableIntStateOf(-1) }

    val sdfDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val sdfMonth = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val todayStr = remember { sdfDate.format(Date()) }

    LaunchedEffect(currentMonth) {
        onMonthChanged(currentMonth.get(Calendar.YEAR), currentMonth.get(Calendar.MONTH))
    }

    val rows = remember(currentMonth) {
        val cal = currentMonth.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDow = cal.get(Calendar.DAY_OF_WEEK) - 1
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val flat = MutableList<Int?>(firstDow) { null } + (1..maxDays).toList()
        flat.chunked(7)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier.fillMaxSize()) {
        // Month nav — static, not in lazy
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                selectedDay = -1; onDateSelected(null)
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous") }
            Text(sdfMonth.format(currentMonth.time), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = {
                currentMonth = (currentMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                selectedDay = -1; onDateSelected(null)
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next") }
        }

        // Day headers — static
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid — static Column (fast, no lazy overhead)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val day = row.getOrNull(col)
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (day != null) {
                                val cal = currentMonth.clone() as Calendar
                                cal.set(Calendar.DAY_OF_MONTH, day)
                                val dateStr = sdfDate.format(cal.time)
                                val isToday = dateStr == todayStr
                                val isSelected = day == selectedDay
                                val taskData = heatMap[day]

                                val bg = when {
                                    isSelected -> primaryColor
                                    taskData != null -> {
                                        val ratio = taskData.first.toFloat() / taskData.second
                                        primaryColor.copy(alpha = 0.12f + ratio * 0.55f)
                                    }
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(bg)
                                        .clickable {
                                            selectedDay = day
                                            onDateSelected(dateStr)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            day.toString(),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> onPrimaryColor
                                                isToday -> primaryColor
                                                taskData != null -> onSurfaceColor
                                                else -> onSurfaceVariantColor
                                            }
                                        )
                                        // Today dot
                                        if (isToday && !isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(primaryColor)
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

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariantColor)
            Spacer(Modifier.width(6.dp))
            listOf(0.12f, 0.3f, 0.5f, 0.67f).forEach { a ->
                Box(Modifier.size(12.dp).padding(1.dp).clip(CircleShape).background(primaryColor.copy(alpha = a)))
            }
            Spacer(Modifier.width(6.dp))
            Text("More", style = MaterialTheme.typography.labelSmall, color = onSurfaceVariantColor)
        }

        // Selected date tasks — scrollable LazyColumn
        if (selectedDay > 0) {
            val cal = currentMonth.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, selectedDay)
            val dateLabel = SimpleDateFormat("EEEE, dd MMM", Locale.getDefault()).format(cal.time)

            Text(
                dateLabel,
                style = MaterialTheme.typography.titleMedium,
                color = primaryColor,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )

            if (selectedDateTasks.isEmpty()) {
                Text(
                    "No tasks for this date.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariantColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(selectedDateTasks, key = { it.id }) { task ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(task.isCompleted, null, Modifier.padding(end = 8.dp), enabled = false)
                            Text(
                                task.title,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (task.isCompleted) onSurfaceVariantColor else onSurfaceColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (task.deadlineTime != null) {
                                Text(task.deadlineTime, style = MaterialTheme.typography.labelSmall, color = primaryColor)
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
