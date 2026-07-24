package com.theblankstate.preamble.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive - Alive Morphing Loading Indicator
 * Uses AndroidX Graphics-Shapes (RoundedPolygon + Morph) for smooth shape-shifting "alive" animation.
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shapeMorphing")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morphProgress"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indicatorRotation"
    )

    val poly1 = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 5,
            radius = 1f,
            innerRadius = 0.5f,
            rounding = CornerRounding(0.2f)
        )
    }

    val poly2 = remember {
        RoundedPolygon.star(
            numVerticesPerRadius = 4,
            radius = 1f,
            innerRadius = 0.8f,
            rounding = CornerRounding(0.4f)
        )
    }

    val morph = remember(poly1, poly2) {
        Morph(poly1, poly2)
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(scale = 1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerPx = Offset(this.size.width / 2, this.size.height / 2)
            val radiusPx = (this.size.minDimension / 2) * 0.85f

            val path = Path()
            val numSteps = 60
            for (i in 0..numSteps) {
                val t = i.toFloat() / numSteps
                val angle = (t * 2 * Math.PI + (rotation * Math.PI / 180)).toFloat()

                // Calculate morphed radius using spring-like wave interpolator
                val morphVal = (sin(t * Math.PI * 4 + progress * Math.PI * 2) * 0.15f + 0.85f).toFloat()
                val currentRadius = radiusPx * morphVal

                val x = centerPx.x + currentRadius * cos(angle)
                val y = centerPx.y + currentRadius * sin(angle)

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                color = color
            )
        }
    }
}

/**
 * Material 3 Expressive - Contained Alive Indicator
 * Morphing organic shape animating inside a container shape boundary.
 */
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = 56.dp
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            LoadingIndicator(color = indicatorColor, size = size * 0.6f)
        }
    }
}

/**
 * Material 3 Expressive - Circular Wavy Progress Indicator
 * Wavy circular active track animation for 0% to 100% progress.
 */
@Composable
fun CircularWavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 8.dp,
    amplitude: Float = 4f,
    wavelength: Dp = 12.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val strokePx = strokeWidth.toPx()
        val radius = (size.minDimension - strokePx * 2) / 2

        // Background track
        drawCircle(
            color = trackColor,
            radius = radius,
            center = center,
            style = Stroke(width = strokePx)
        )

        // Active Wavy Arc
        val sweepAngle = (progress.coerceIn(0f, 1f) * 360f)
        val path = Path()
        val steps = (sweepAngle * 2).toInt().coerceAtLeast(10)

        for (i in 0..steps) {
            val angleDeg = -90f + (i.toFloat() / steps) * sweepAngle
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

            // Wave offset along radius
            val waveOffset = (sin(i * 0.3f + wavePhase) * amplitude).toFloat()
            val r = radius + waveOffset

            val x = center.x + r * cos(angleRad)
            val y = center.y + r * sin(angleRad)

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

/**
 * Material 3 Expressive - Linear Wavy Progress Indicator
 * Progressive bar featuring a fluid rising wavy active track.
 */
@Composable
fun LinearWavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 10.dp,
    amplitude: Float = 3f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "linearWaveAnim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "linearPhase"
    )

    Canvas(modifier = modifier.height(height)) {
        val widthPx = size.width
        val heightPx = size.height
        val activeWidth = widthPx * progress.coerceIn(0f, 1f)

        // Track
        drawRoundRect(
            color = trackColor,
            size = Size(widthPx, heightPx),
            cornerRadius = CornerRadius(heightPx / 2, heightPx / 2)
        )

        if (activeWidth > 0) {
            val path = Path()
            val steps = (activeWidth / 2).toInt().coerceAtLeast(10)

            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * activeWidth
                val waveY = (heightPx / 2) + (sin(x * 0.1f + phase) * amplitude).toFloat()
                if (i == 0) path.moveTo(x, waveY) else path.lineTo(x, waveY)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = heightPx, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Material 3 Expressive - ButtonGroup
 * Container dynamically shifting shape & scale based on child selection state.
 */
@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.padding(4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp)
        ) {
            content()
        }
    }
}

/**
 * Material 3 Expressive - SplitButton
 * Two-part action button splitting primary action from trailing menu trigger.
 */
@Composable
fun SplitButton(
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    primaryText: String,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main action
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = primaryText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        // Divider line
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f))
        )

        // Trailing dropdown trigger
        Box(
            modifier = Modifier
                .clickable { onMenuClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "▼",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
