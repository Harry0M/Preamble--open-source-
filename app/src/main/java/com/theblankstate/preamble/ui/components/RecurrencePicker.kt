package com.theblankstate.preamble.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecurrencePicker(
    recurrenceType: String?,
    recurrenceInterval: Int,
    recurrenceDays: Set<Int>,
    recurrenceEndDate: String?,
    onRecurrenceTypeChanged: (String?) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onDaysChanged: (Set<Int>) -> Unit,
    onEndDateChanged: (String?) -> Unit
) {
    val types = listOf(null to "None", "daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly", "yearly" to "Yearly", "custom" to "Custom")
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displaySdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Repeat",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            types.forEach { (type, label) ->
                FilterChip(
                    selected = recurrenceType == type,
                    onClick = { onRecurrenceTypeChanged(type) },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }

        // Weekly: weekday selector
        AnimatedVisibility(visible = recurrenceType == "weekly") {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "On days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                val weekdays = listOf(
                    Calendar.SUNDAY to "S",
                    Calendar.MONDAY to "M",
                    Calendar.TUESDAY to "T",
                    Calendar.WEDNESDAY to "W",
                    Calendar.THURSDAY to "T",
                    Calendar.FRIDAY to "F",
                    Calendar.SATURDAY to "S"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    weekdays.forEach { (dayValue, label) ->
                        FilterChip(
                            selected = dayValue in recurrenceDays,
                            onClick = {
                                val newDays = recurrenceDays.toMutableSet()
                                if (dayValue in newDays) newDays.remove(dayValue) else newDays.add(dayValue)
                                onDaysChanged(newDays)
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.size(height = 32.dp, width = 40.dp)
                        )
                    }
                }
            }
        }

        // Custom: interval picker "Every N days"
        AnimatedVisibility(visible = recurrenceType == "custom") {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Every", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { if (recurrenceInterval > 1) onIntervalChanged(recurrenceInterval - 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "$recurrenceInterval",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onIntervalChanged(recurrenceInterval + 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("days", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Interval picker for daily/weekly/monthly/yearly
        AnimatedVisibility(visible = recurrenceType != null && recurrenceType != "custom") {
            val periodLabel = when (recurrenceType) {
                "daily" -> if (recurrenceInterval == 1) "day" else "days"
                "weekly" -> if (recurrenceInterval == 1) "week" else "weeks"
                "monthly" -> if (recurrenceInterval == 1) "month" else "months"
                "yearly" -> if (recurrenceInterval == 1) "year" else "years"
                else -> ""
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Every", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { if (recurrenceInterval > 1) onIntervalChanged(recurrenceInterval - 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "$recurrenceInterval",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onIntervalChanged(recurrenceInterval + 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(periodLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // End date picker
        AnimatedVisibility(visible = recurrenceType != null) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showEndDatePicker = true },
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (recurrenceEndDate != null) {
                            "Ends: ${try { displaySdf.format(sdf.parse(recurrenceEndDate)!!) } catch (_: Exception) { recurrenceEndDate }}"
                        } else "No end date",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (recurrenceEndDate != null) {
                    TextButton(onClick = { onEndDateChanged(null) }) {
                        Text("Clear", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = recurrenceEndDate?.let {
                try { sdf.parse(it)?.time } catch (_: Exception) { null }
            } ?: (System.currentTimeMillis() + 86400000L)
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onEndDateChanged(sdf.format(Date(millis)))
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
