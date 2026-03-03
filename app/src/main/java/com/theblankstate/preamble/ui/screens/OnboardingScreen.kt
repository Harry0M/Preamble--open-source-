package com.theblankstate.preamble.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val illustrationType: IllustrationType = IllustrationType.EMOJI
)

enum class IllustrationType {
    EMOJI, NOTIFICATION_MOCKUP, VOICE_WAVE, PALETTE
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            emoji = "📝",
            title = "Track Your Tasks",
            description = "Preamble helps you remember your daily tasks. Add, complete, and organize everything in one place."
        ),
        OnboardingPage(
            emoji = "",
            title = "Always-On Notification",
            description = "A permanent notification lets you quickly add tasks, use voice input, and see your pending count — all without opening the app. We need notification permission for this.",
            illustrationType = IllustrationType.NOTIFICATION_MOCKUP
        ),
        OnboardingPage(
            emoji = "",
            title = "Voice Input & Reminders",
            description = "Speak your tasks hands-free and set alarm reminders so you never miss a deadline. We need microphone and alarm permissions.",
            illustrationType = IllustrationType.VOICE_WAVE
        ),
        OnboardingPage(
            emoji = "",
            title = "Make It Yours",
            description = "Choose your theme color from the full spectrum. Your app, your style. Let's get started!",
            illustrationType = IllustrationType.PALETTE
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        // Page indicators
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { idx ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == idx) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == idx)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pagerState.currentPage == pages.size - 1) {
            Button(
                onClick = {
                    val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                    onComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape
            ) {
                Text("Get Started", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                    onComplete()
                }) {
                    Text("Skip")
                }
                Button(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    shape = CircleShape
                ) {
                    Text("Next")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (page.illustrationType) {
            IllustrationType.EMOJI -> {
                Text(text = page.emoji, fontSize = 80.sp)
            }
            IllustrationType.NOTIFICATION_MOCKUP -> {
                NotificationMockup(
                    primary = primary,
                    surface = surface,
                    onSurface = onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(horizontal = 16.dp)
                )
            }
            IllustrationType.VOICE_WAVE -> {
                VoiceIllustration(
                    primary = primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
            IllustrationType.PALETTE -> {
                PaletteIllustration(
                    modifier = Modifier
                        .size(160.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun NotificationMockup(
    primary: Color,
    surface: Color,
    onSurface: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Notification card background
        drawRoundRect(
            color = surface,
            topLeft = Offset(0f, h * 0.1f),
            size = Size(w, h * 0.8f),
            cornerRadius = CornerRadius(24f, 24f)
        )

        // App icon circle
        drawCircle(
            color = primary,
            radius = 14f,
            center = Offset(32f, h * 0.3f)
        )

        // "preamble" title bar
        drawRoundRect(
            color = onSurface.copy(alpha = 0.7f),
            topLeft = Offset(56f, h * 0.26f),
            size = Size(w * 0.25f, 12f),
            cornerRadius = CornerRadius(6f, 6f)
        )

        // "3 tasks pending" text
        drawRoundRect(
            color = onSurface.copy(alpha = 0.4f),
            topLeft = Offset(32f, h * 0.44f),
            size = Size(w * 0.45f, 10f),
            cornerRadius = CornerRadius(5f, 5f)
        )

        // Task lines
        for (i in 0..2) {
            val y = h * 0.58f + i * 18f
            drawRoundRect(
                color = onSurface.copy(alpha = 0.2f),
                topLeft = Offset(44f, y),
                size = Size(w * (0.5f - i * 0.08f), 8f),
                cornerRadius = CornerRadius(4f, 4f)
            )
            drawCircle(
                color = primary.copy(alpha = 0.5f),
                radius = 4f,
                center = Offset(36f, y + 4f)
            )
        }

        // "Quick Add" button
        drawRoundRect(
            color = primary.copy(alpha = 0.2f),
            topLeft = Offset(w * 0.55f, h * 0.7f),
            size = Size(w * 0.18f, 22f),
            cornerRadius = CornerRadius(11f, 11f)
        )

        // "Voice Task" button
        drawRoundRect(
            color = primary.copy(alpha = 0.2f),
            topLeft = Offset(w * 0.76f, h * 0.7f),
            size = Size(w * 0.18f, 22f),
            cornerRadius = CornerRadius(11f, 11f)
        )
    }
}

@Composable
private fun VoiceIllustration(primary: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Mic body
        drawRoundRect(
            color = primary,
            topLeft = Offset(cx - 18f, cy - 50f),
            size = Size(36f, 70f),
            cornerRadius = CornerRadius(18f, 18f)
        )

        // Mic arc
        drawArc(
            color = primary.copy(alpha = 0.4f),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - 32f, cy - 20f),
            size = Size(64f, 50f)
        )

        // Mic stand
        drawRoundRect(
            color = primary.copy(alpha = 0.6f),
            topLeft = Offset(cx - 3f, cy + 30f),
            size = Size(6f, 24f),
            cornerRadius = CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = primary.copy(alpha = 0.6f),
            topLeft = Offset(cx - 20f, cy + 52f),
            size = Size(40f, 5f),
            cornerRadius = CornerRadius(3f, 3f)
        )

        // Sound waves
        for (i in 1..3) {
            drawArc(
                color = primary.copy(alpha = 0.15f * (4 - i)),
                startAngle = -60f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx + 20f + i * 18f, cy - 40f - i * 8f),
                size = Size(i * 16f, 80f + i * 16f)
            )
            drawArc(
                color = primary.copy(alpha = 0.15f * (4 - i)),
                startAngle = 120f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - 20f - i * 34f, cy - 40f - i * 8f),
                size = Size(i * 16f, 80f + i * 16f)
            )
        }
    }
}

@Composable
private fun PaletteIllustration(modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308),
        Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFF8B5CF6),
        Color(0xFFEC4899), Color(0xFF6B7280)
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2.5f

        // Draw color circle segments
        colors.forEachIndexed { idx, color ->
            val angle = (idx * 360f / colors.size) - 90f
            drawArc(
                color = color,
                startAngle = angle,
                sweepAngle = 360f / colors.size - 2f,
                useCenter = true,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2)
            )
        }

        // Center hole
        drawCircle(
            color = Color(0xFF1A1A1A),
            radius = radius * 0.45f,
            center = Offset(cx, cy)
        )

        // Brush handle
        drawRoundRect(
            color = Color(0xFF8B5CF6),
            topLeft = Offset(cx + radius * 0.3f, cy - radius * 1.2f),
            size = Size(12f, radius * 0.8f),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }
}
