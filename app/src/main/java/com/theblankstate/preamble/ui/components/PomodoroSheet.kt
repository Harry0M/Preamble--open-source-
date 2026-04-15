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
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.remember

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
                DynamicShapeProgress(
                    progress = if (pomodoroState.totalSeconds > 0)
                        pomodoroState.remainingSeconds.toFloat() / pomodoroState.totalSeconds
                    else 0f,
                    isRunning = pomodoroState.isRunning && !pomodoroState.isPaused,
                    modifier = Modifier.size(200.dp)
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

@Composable
fun DynamicShapeProgress(
    progress: Float,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    // We morph between a squircle-circle and a squiggly star when running
    val startPolygon = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.85f,
            rounding = CornerRounding(radius = 0.15f)
        )
    }
    
    val endPolygon = remember {
        RoundedPolygon(
            numVertices = 8,
            rounding = CornerRounding(radius = 0.5f)
        )
    }
    
    val morph = remember { Morph(endPolygon, startPolygon) }

    val infiniteTransition = rememberInfiniteTransition(label = "morph")
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRunning) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morphProgress"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    
    val pathMeasure = remember { PathMeasure() }

    Canvas(modifier = modifier) {
        val morphPath = morph.toPath(morphProgress)
        val matrix = android.graphics.Matrix()
        
        // The default coordinates for graphics-shapes are [-1, 1]
        // Scale to Canvas dimensions and center
        val scale = (size.width / 2f) * 0.95f // 0.95f avoids clipping stroke
        matrix.setScale(scale, scale)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        
        // Rotating slightly offsets the star points
        matrix.postRotate(-90f, size.width / 2f, size.height / 2f)
        
        morphPath.transform(matrix)
        val composePath = morphPath.asComposePath()

        // 1. Draw track (background subtle shape)
        drawPath(
            path = composePath,
            color = trackColor,
            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        )

        // 2. Compute Segment
        pathMeasure.setPath(composePath, forceClosed = false)
        val progressPath = androidx.compose.ui.graphics.Path()
        val stopDistance = pathMeasure.length * progress
        
        if (stopDistance > 0f) {
            pathMeasure.getSegment(
                startDistance = 0f,
                stopDistance = stopDistance,
                destination = progressPath,
                startWithMoveTo = true
            )
            
            // 3. Draw active progress curve fitting to the morphed contour
            drawPath(
                path = progressPath,
                color = primaryColor,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}
