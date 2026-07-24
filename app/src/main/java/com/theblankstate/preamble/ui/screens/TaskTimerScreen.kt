package com.theblankstate.preamble.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.ui.components.CircularWavyProgressIndicator
import com.theblankstate.preamble.ui.components.CustomDurationBottomSheet
import com.theblankstate.preamble.ui.viewmodels.TaskTimerViewModel
import kotlinx.coroutines.delay

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
fun TaskTimerScreen(
    timerViewModel: TaskTimerViewModel,
    initialTaskId: String? = null,
    initialTaskTitle: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hardware / Gesture Back Press Handler
    BackHandler { onBack() }

    val haptic = LocalHapticFeedback.current

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val scaleFactor: Float = (screenWidthDp / 360f).coerceIn(0.85f, 1.15f)

    val timerState by timerViewModel.timerState.collectAsState()
    val selectedPresetMinutes by timerViewModel.selectedPresetMinutes.collectAsState()
    val showCustomSheet by timerViewModel.showCustomDurationSheet.collectAsState()

    val currentTaskId = timerState.taskId ?: initialTaskId
    val currentTaskTitle = timerState.taskTitle ?: initialTaskTitle

    // Extension Pulsing Ripple Wave feedback trigger state
    var showExtensionRipple by remember { mutableStateOf(false) }

    LaunchedEffect(showExtensionRipple) {
        if (showExtensionRipple) {
            delay(1500)
            showExtensionRipple = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Under-Status-Bar Scrollable Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp * scaleFactor),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Generous top clearance spacer so task title badge never crowds floating top header FABs
                Spacer(modifier = Modifier.height(115.dp * scaleFactor))

                // ═══════════════════════════════════════════════════════════════
                // TASK BADGE / STANDALONE INDICATOR
                // ═══════════════════════════════════════════════════════════════
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (currentTaskId != null)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    else
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 12.dp * scaleFactor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (currentTaskId != null)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp * scaleFactor)
                        )
                        Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                        Text(
                            text = if (!currentTaskTitle.isNullOrBlank()) currentTaskTitle else "Standalone Session",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp * scaleFactor
                            ),
                            color = if (currentTaskId != null)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp * scaleFactor))

                // ═══════════════════════════════════════════════════════════════
                // MATERIAL ALIVE WAVY TIMER RING DISPLAY
                // ═══════════════════════════════════════════════════════════════
                val remainingSec = timerState.remainingSeconds
                val totalSec = if (timerState.totalSeconds > 0) timerState.totalSeconds else (selectedPresetMinutes * 60)
                val rawProgress = if (totalSec > 0) (remainingSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f) else 1f

                val animatedProgress by animateFloatAsState(
                    targetValue = rawProgress,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    label = "timerProgress"
                )

                val minutes = remainingSec / 60
                val seconds = remainingSec % 60
                val formattedTime = String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)

                Box(
                    modifier = Modifier
                        .size(270.dp * scaleFactor)
                        .padding(12.dp * scaleFactor),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Ripple Wave Animation Overlay on Time Extension
                    if (showExtensionRipple) {
                        RipplePulseRing(scaleFactor = scaleFactor)
                    }

                    // Material Alive Wavy Circular Progress Indicator
                    CircularWavyProgressIndicator(
                        progress = animatedProgress,
                        strokeWidth = 14.dp * scaleFactor,
                        amplitude = if (timerState.isRunning) 6f else 2f,
                        modifier = Modifier.fillMaxSize()
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 48.sp * scaleFactor
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp * scaleFactor))
                        Text(
                            text = if (timerState.isRunning) {
                                if (timerState.isPaused) "Paused" else "Focused"
                            } else "Ready",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp * scaleFactor
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp * scaleFactor))

                // ═══════════════════════════════════════════════════════════════
                // DYNAMIC RUNNING TIME INCREASE BUBBLES
                // ═══════════════════════════════════════════════════════════════
                AnimatedVisibility(
                    visible = timerState.isRunning,
                    enter = fadeIn() + androidx.compose.animation.expandVertically(),
                    exit = fadeOut() + androidx.compose.animation.shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 16.dp * scaleFactor)
                    ) {
                        Text(
                            text = "Extend Running Session",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp * scaleFactor
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 6.dp * scaleFactor)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val timeAddButtons = listOf(
                                "+5m" to 300,
                                "+10m" to 600,
                                "+15m" to 900,
                                "+30m" to 1800
                            )

                            timeAddButtons.forEach { (label, addSeconds) ->
                                val interactionSource = remember { MutableInteractionSource() }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null
                                        ) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            timerViewModel.addTimeSeconds(addSeconds)
                                            showExtensionRipple = true
                                        }
                                        .expressivePressScale(interactionSource)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp * scaleFactor,
                                            vertical = 8.dp * scaleFactor
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(13.dp * scaleFactor)
                                        )
                                        Spacer(Modifier.width(2.dp * scaleFactor))
                                        Text(
                                            text = label.removePrefix("+"),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp * scaleFactor
                                            ),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════════════
                // PRESETS SELECTION ROW
                // ═══════════════════════════════════════════════════════════════
                Text(
                    text = "Session Duration Presets",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp * scaleFactor
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp * scaleFactor)
                )

                val presets = listOf(25, 30, 45, 60)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presets.forEach { preset ->
                        val isSelected = selectedPresetMinutes == preset
                        val interactionSource = remember { MutableInteractionSource() }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    enabled = !timerState.isRunning
                                ) {
                                    timerViewModel.selectPreset(preset)
                                }
                                .expressivePressScale(interactionSource)
                        ) {
                            Text(
                                text = "${preset}m",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp * scaleFactor
                                ),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 8.dp * scaleFactor)
                            )
                        }
                    }

                    // Auto-scaling Custom preset pill
                    val isCustomSelected = !presets.contains(selectedPresetMinutes)
                    val customInteractionSource = remember { MutableInteractionSource() }
                    val customLabel = if (isCustomSelected) "${selectedPresetMinutes}m" else "Custom"

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isCustomSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = customInteractionSource,
                                indication = null,
                                enabled = !timerState.isRunning
                            ) {
                                timerViewModel.openCustomDurationSheet()
                            }
                            .expressivePressScale(customInteractionSource)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 10.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = customLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = if (customLabel.length > 4) (10.sp * scaleFactor) else (12.sp * scaleFactor)
                                ),
                                color = if (isCustomSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(3.dp * scaleFactor))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Custom duration",
                                tint = if (isCustomSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp * scaleFactor)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp * scaleFactor))

                // ═══════════════════════════════════════════════════════════════
                // CONTROLS ROW
                // ═══════════════════════════════════════════════════════════════
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!timerState.isRunning) {
                        // Start button
                        val startInteraction = remember { MutableInteractionSource() }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(72.dp * scaleFactor)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = startInteraction,
                                    indication = null
                                ) {
                                    timerViewModel.startTimer(currentTaskId, currentTaskTitle)
                                }
                                .expressivePressScale(startInteraction)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start Timer",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp * scaleFactor)
                                )
                            }
                        }
                    } else {
                        // Stop button
                        val stopInteraction = remember { MutableInteractionSource() }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .size(56.dp * scaleFactor)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = stopInteraction,
                                    indication = null
                                ) {
                                    timerViewModel.stopTimer()
                                }
                                .expressivePressScale(stopInteraction)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Timer",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(26.dp * scaleFactor)
                                )
                            }
                        }

                        // Pause / Resume button
                        val playPauseInteraction = remember { MutableInteractionSource() }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(72.dp * scaleFactor)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = playPauseInteraction,
                                    indication = null
                                ) {
                                    if (timerState.isPaused) {
                                        timerViewModel.resumeTimer()
                                    } else {
                                        timerViewModel.pauseTimer()
                                    }
                                }
                                .expressivePressScale(playPauseInteraction)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (timerState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (timerState.isPaused) "Resume" else "Pause",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(36.dp * scaleFactor)
                                )
                            }
                        }

                        // Complete / Finish button
                        val finishInteraction = remember { MutableInteractionSource() }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .size(56.dp * scaleFactor)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = finishInteraction,
                                    indication = null
                                ) {
                                    timerViewModel.finishTimer()
                                }
                                .expressivePressScale(finishInteraction)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Complete Session",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(26.dp * scaleFactor)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp * scaleFactor))
            }

            // ═══════════════════════════════════════════════════════════════
            // FLOATING HEADER ROW (FAB-Style Streamlined)
            // ═══════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back FAB
                val backInteraction = remember { MutableInteractionSource() }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp * scaleFactor)
                        )
                    }
                }

                // Title Pill Capsule
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
                ) {
                    Text(
                        text = "Task Timer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp * scaleFactor
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp * scaleFactor, vertical = 8.dp * scaleFactor)
                    )
                }

                // Right spacer to keep floating title pill centered
                Spacer(modifier = Modifier.size(44.dp * scaleFactor))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CUSTOM DURATION SLIDER BOTTOM SHEET
    // ═══════════════════════════════════════════════════════════════
    if (showCustomSheet) {
        CustomDurationBottomSheet(
            initialMinutes = selectedPresetMinutes,
            onDismiss = { timerViewModel.closeCustomDurationSheet() },
            onConfirm = { minutes -> timerViewModel.setCustomDurationMinutes(minutes) }
        )
    }
}

@Composable
private fun RipplePulseRing(scaleFactor: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripplePulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rippleScale"
    )

    Canvas(modifier = Modifier.fillMaxSize().scale(scale)) {
        val radius = (size.minDimension) / 2
        drawCircle(
            color = Color(0xFFA1C6FF).copy(alpha = 0.35f),
            radius = radius
        )
    }
}
