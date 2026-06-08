package com.theblankstate.preamble.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "What's New in v1.6 🎉",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Feature 1: Habits
                FeatureItem(
                    title = "New Habit System",
                    description = "Build routines effortlessly. Track streaks, maintain your daily goals, and never miss a beat.",
                    graphic = { AnimatedHabitGraphic() }
                )

                // Feature 2: Events
                FeatureItem(
                    title = "New Event System",
                    description = "Separate actionable work from specific occasions, meetings, and parties with custom event icons.",
                    graphic = { AnimatedEventGraphic() }
                )

                // Feature 3: Enhanced AI
                FeatureItem(
                    title = "Universal AI Awareness",
                    description = "Our enhanced AI now understands and replies in your native language script (Hinglish, Hindi, Chinese, etc.) and smartly detects your habits and events.",
                    graphic = { AnimatedAiGraphic() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                ) {
                    Text("Awesome!")
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(
    title: String,
    description: String,
    graphic: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        graphic()
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AnimatedHabitGraphic() {
    val infiniteTransition = rememberInfiniteTransition(label = "habit")
    
    // Progress bar animation
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    // Calendar blocks animation
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            // Calendar grid
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { index ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (index < 4) MaterialTheme.colorScheme.primary
                                else if (index == 4) MaterialTheme.colorScheme.primary.copy(alpha = alphaAnim)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }
            
            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun AnimatedEventGraphic() {
    val infiniteTransition = rememberInfiniteTransition(label = "event")
    
    val float1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "f1"
    )
    val float2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "f2"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        // Event cards floating
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EventChip(text = "🎂 Birthday", offset = float1)
            EventChip(text = "✈️ Flight", offset = float2)
            EventChip(text = "🎸 Concert", offset = float1 * 1.5f)
        }
    }
}

@Composable
private fun EventChip(text: String, offset: Float) {
    Surface(
        modifier = Modifier.offset(y = offset.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnimatedAiGraphic() {
    val infiniteTransition = rememberInfiniteTransition(label = "ai")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rot"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp).rotate(rotation * 2)
        )
        
        // Floating language / type symbols
        SymbolFloating(text = "A", xOffset = -80f, yOffset = -20f, delayMs = 0)
        SymbolFloating(text = "अ", xOffset = -50f, yOffset = 30f, delayMs = 500)
        SymbolFloating(text = "文", xOffset = 80f, yOffset = -10f, delayMs = 1000)
        SymbolFloating(text = "🔁", xOffset = 50f, yOffset = 30f, delayMs = 1500)
        SymbolFloating(text = "🎤", xOffset = 0f, yOffset = -40f, delayMs = 800)
    }
}

@Composable
private fun SymbolFloating(text: String, xOffset: Float, yOffset: Float, delayMs: Int) {
    var floatAnim by remember { mutableStateOf(0f) }
    
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        while (true) {
            floatAnim = 10f
            delay(1000)
            floatAnim = -10f
            delay(1000)
        }
    }
    
    val animatedY by animateFloatAsState(
        targetValue = floatAnim,
        animationSpec = tween(1000, easing = LinearEasing),
        label = "y"
    )

    Text(
        text = text,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .offset(x = xOffset.dp, y = (yOffset + animatedY).dp)
    )
}
