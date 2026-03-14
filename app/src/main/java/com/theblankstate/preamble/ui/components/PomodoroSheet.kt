package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.pomodoro.PomodoroPhase
import com.theblankstate.preamble.pomodoro.PomodoroTimerService
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroSheet(
    onDismiss: () -> Unit,
    taskId: String? = null,
    taskTitle: String? = null
) {
    val pomodoroState by PomodoroTimerService.state.collectAsState()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = if (pomodoroState.isRunning && pomodoroState.taskTitle != null)
                    "Focus: ${pomodoroState.taskTitle}"
                else if (taskTitle != null)
                    "Focus: $taskTitle"
                else "Pomodoro Timer",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Phase indicator
            Text(
                text = when (pomodoroState.currentPhase) {
                    PomodoroPhase.WORK -> "Work Session"
                    PomodoroPhase.SHORT_BREAK -> "Short Break"
                    PomodoroPhase.LONG_BREAK -> "Long Break"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Circular progress + time display
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = {
                        if (pomodoroState.totalSeconds > 0)
                            pomodoroState.remainingSeconds.toFloat() / pomodoroState.totalSeconds
                        else 0f
                    },
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = formatTime(pomodoroState.remainingSeconds),
                    style = MaterialTheme.typography.displayMedium
                )
            }

            // Sessions completed indicator
            Text(
                text = "${pomodoroState.sessionsCompleted} sessions completed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!pomodoroState.isRunning) {
                    Button(
                        onClick = {
                            PomodoroTimerService.start(context, taskId, taskTitle)
                        },
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start")
                    }
                } else {
                    // Pause/Resume
                    OutlinedButton(
                        onClick = {
                            if (pomodoroState.isPaused)
                                PomodoroTimerService.resume(context)
                            else
                                PomodoroTimerService.pause(context)
                        },
                        shape = CircleShape
                    ) {
                        Icon(
                            if (pomodoroState.isPaused) Icons.Default.PlayArrow
                            else Icons.Default.Pause,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (pomodoroState.isPaused) "Resume" else "Pause")
                    }

                    // Skip
                    OutlinedButton(
                        onClick = { PomodoroTimerService.skip(context) },
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null)
                    }

                    // Stop
                    Button(
                        onClick = { PomodoroTimerService.stop(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
