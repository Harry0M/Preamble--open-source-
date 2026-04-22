package com.theblankstate.preamble.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.viewmodel.TaskViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val PREF_KEY_SOUND = "celebration_sound_enabled"

fun celebrationSoundEnabled(ctx: Context): Boolean =
    ctx.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
        .getBoolean(PREF_KEY_SOUND, true)

fun setCelebrationSoundEnabled(ctx: Context, enabled: Boolean) {
    ctx.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean(PREF_KEY_SOUND, enabled).apply()
}

private fun playTickSound() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
    } catch (_: Exception) {
    }
}

private data class ConfettiPiece(
    val color: Color,
    val startX: Float,
    val angleDeg: Float,
    val speed: Float,
    val rotationSpeed: Float,
    val sizePx: Float,
)

private fun generatePieces(count: Int, palette: List<Color>): List<ConfettiPiece> =
    List(count) {
        ConfettiPiece(
            color = palette.random(),
            startX = Random.nextFloat(),
            angleDeg = Random.nextFloat() * 360f,
            speed = 0.7f + Random.nextFloat() * 0.6f,
            rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
            sizePx = 6f + Random.nextFloat() * 8f
        )
    }

@Composable
fun CelebrationOverlay(
    event: TaskViewModel.CelebrationEvent?,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Progress 0→1 drives both confetti fall and fade
    val progress = remember(event) { Animatable(0f) }
    var showBanner by remember(event) { mutableStateOf(false) }

    LaunchedEffect(event) {
        val e = event ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (celebrationSoundEnabled(ctx)) playTickSound()

        val durationMs = when (e) {
            is TaskViewModel.CelebrationEvent.Standard -> 700
            is TaskViewModel.CelebrationEvent.Milestone -> 1800
            is TaskViewModel.CelebrationEvent.PerfectDay -> 2400
            is TaskViewModel.CelebrationEvent.StreakDay -> 2800
        }
        showBanner = e !is TaskViewModel.CelebrationEvent.Standard
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMs, easing = LinearOutSlowInEasing)
        )
        showBanner = false
        onDone()
    }

    val e = event ?: return

    val palette = when (e) {
        is TaskViewModel.CelebrationEvent.Standard -> listOf(Color(0xFF111111))
        is TaskViewModel.CelebrationEvent.Milestone -> listOf(
            Color(0xFFFFB300), Color(0xFF111111), Color(0xFF42A5F5), Color(0xFFEC407A)
        )
        is TaskViewModel.CelebrationEvent.PerfectDay -> listOf(
            Color(0xFFFFD54F), Color(0xFF66BB6A), Color(0xFF42A5F5)
        )
        is TaskViewModel.CelebrationEvent.StreakDay -> listOf(
            Color(0xFFFF7043), Color(0xFFFFCA28), Color(0xFFAB47BC), Color(0xFF111111)
        )
    }
    val pieceCount = when (e) {
        is TaskViewModel.CelebrationEvent.Standard -> 0
        is TaskViewModel.CelebrationEvent.Milestone -> 28
        is TaskViewModel.CelebrationEvent.PerfectDay -> 60
        is TaskViewModel.CelebrationEvent.StreakDay -> 90
    }
    val pieces = remember(event) { generatePieces(pieceCount, palette) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (pieces.isNotEmpty()) {
            val p = progress.value
            val alpha = (1f - (p - 0.6f).coerceAtLeast(0f) / 0.4f).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                pieces.forEach { piece ->
                    val y = -piece.sizePx + (h + piece.sizePx * 2) * p * piece.speed
                    val x = piece.startX * w + sin(p * 6f + piece.startX * 10) * 18f
                    val rot = piece.angleDeg + piece.rotationSpeed * p
                    rotate(degrees = rot, pivot = Offset(x, y)) {
                        drawRect(
                            color = piece.color.copy(alpha = alpha),
                            topLeft = Offset(x - piece.sizePx / 2, y - piece.sizePx / 4),
                            size = Size(piece.sizePx, piece.sizePx / 2)
                        )
                    }
                }
            }
        }

        if (showBanner) {
            val label = when (e) {
                is TaskViewModel.CelebrationEvent.Milestone -> "${e.count} tasks crushed."
                is TaskViewModel.CelebrationEvent.PerfectDay -> "Perfect day. ${e.tasks}/${e.tasks} done."
                is TaskViewModel.CelebrationEvent.StreakDay -> "Day ${e.days} locked in."
                is TaskViewModel.CelebrationEvent.Standard -> ""
            }
            AnimatedVisibility(
                visible = showBanner,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(modifier = Modifier.padding(top = 60.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
