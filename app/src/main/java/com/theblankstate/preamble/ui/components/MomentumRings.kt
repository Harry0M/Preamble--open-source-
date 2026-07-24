package com.theblankstate.preamble.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class MomentumRingsState(
    val tasksCompleted: Int = 0,
    val tasksTarget: Int = 5,
    val habitsCompleted: Int = 0,
    val habitsTarget: Int = 3,
    val focusMinutesCompleted: Int = 0,
    val focusMinutesTarget: Int = 60
)

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
fun DailyMomentumRingsCard(
    state: MomentumRingsState,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    hair: Color,
    scaleFactor: Float = 1f,
    modifier: Modifier = Modifier
) {
    val taskProgress = (state.tasksCompleted.toFloat() / state.tasksTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val habitProgress = (state.habitsCompleted.toFloat() / state.habitsTarget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val focusProgress = (state.focusMinutesCompleted.toFloat() / state.focusMinutesTarget.coerceAtLeast(1)).coerceIn(0f, 1f)

    val avgProgress = ((taskProgress + habitProgress + focusProgress) / 3f * 100f).roundToInt()

    val taskAnim = remember { Animatable(0f) }
    val habitAnim = remember { Animatable(0f) }
    val focusAnim = remember { Animatable(0f) }

    LaunchedEffect(taskProgress, habitProgress, focusProgress) {
        taskAnim.animateTo(taskProgress, animationSpec = tween(1000))
        habitAnim.animateTo(habitProgress, animationSpec = tween(1000))
        focusAnim.animateTo(focusProgress, animationSpec = tween(1000))
    }

    val taskColor = Color(0xFFFF5252)
    val habitColor = Color(0xFF00E676)
    val focusColor = Color(0xFF40C4FF)

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp * scaleFactor))
            .background(tile)
            .padding(18.dp * scaleFactor)
            .expressivePressScale(interactionSource)
    ) {
        Text(
            text = "DAILY MOMENTUM RINGS",
            color = fgMuted,
            fontSize = 11.sp * scaleFactor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.6.sp * scaleFactor
        )

        Spacer(Modifier.height(14.dp * scaleFactor))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Concentric 3-Rings Canvas
            Box(
                modifier = Modifier.size(130.dp * scaleFactor),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(126.dp * scaleFactor)) {
                    val strokeWidth = 9.dp.toPx() * scaleFactor
                    val space = 5.dp.toPx() * scaleFactor

                    // Ring 1: Tasks (Outer)
                    val r1Size = size.width - strokeWidth
                    val r1TopLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    drawArc(
                        color = taskColor.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = r1TopLeft,
                        size = Size(r1Size, r1Size),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = taskColor,
                        startAngle = -90f,
                        sweepAngle = 360f * taskAnim.value,
                        useCenter = false,
                        topLeft = r1TopLeft,
                        size = Size(r1Size, r1Size),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Ring 2: Habits (Middle)
                    val r2Offset = strokeWidth + space
                    val r2Size = size.width - (r2Offset * 2)
                    val r2TopLeft = Offset(r2Offset, r2Offset)
                    drawArc(
                        color = habitColor.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = r2TopLeft,
                        size = Size(r2Size, r2Size),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = habitColor,
                        startAngle = -90f,
                        sweepAngle = 360f * habitAnim.value,
                        useCenter = false,
                        topLeft = r2TopLeft,
                        size = Size(r2Size, r2Size),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Ring 3: Focus (Inner)
                    val r3Offset = (strokeWidth + space) * 2
                    val r3Size = size.width - (r3Offset * 2)
                    val r3TopLeft = Offset(r3Offset, r3Offset)
                    drawArc(
                        color = focusColor.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = r3TopLeft,
                        size = Size(r3Size, r3Size),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = focusColor,
                        startAngle = -90f,
                        sweepAngle = 360f * focusAnim.value,
                        useCenter = false,
                        topLeft = r3TopLeft,
                        size = Size(r3Size, r3Size),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Center Text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$avgProgress%",
                        color = fg,
                        fontSize = 22.sp * scaleFactor,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp * scaleFactor
                    )
                    Text(
                        text = "DAY SCORE",
                        color = fgMuted,
                        fontSize = 8.sp * scaleFactor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.width(16.dp * scaleFactor))

            // Metric Legend Labels
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp * scaleFactor)
            ) {
                RingLegendRow(
                    label = "Tasks",
                    valueStr = "${state.tasksCompleted}/${state.tasksTarget}",
                    color = taskColor,
                    fg = fg,
                    fgMuted = fgMuted,
                    scaleFactor = scaleFactor
                )
                RingLegendRow(
                    label = "Habits",
                    valueStr = "${state.habitsCompleted}/${state.habitsTarget}",
                    color = habitColor,
                    fg = fg,
                    fgMuted = fgMuted,
                    scaleFactor = scaleFactor
                )
                RingLegendRow(
                    label = "Focus",
                    valueStr = "${state.focusMinutesCompleted}m/${state.focusMinutesTarget}m",
                    color = focusColor,
                    fg = fg,
                    fgMuted = fgMuted,
                    scaleFactor = scaleFactor
                )
            }
        }
    }
}

@Composable
private fun RingLegendRow(
    label: String,
    valueStr: String,
    color: Color,
    fg: Color,
    fgMuted: Color,
    scaleFactor: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp * scaleFactor)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(8.dp * scaleFactor))
            Text(
                text = label,
                color = fg,
                fontSize = 13.sp * scaleFactor,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = valueStr,
            color = fgMuted,
            fontSize = 12.sp * scaleFactor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
