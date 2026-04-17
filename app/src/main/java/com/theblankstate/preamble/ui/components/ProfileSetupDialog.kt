package com.theblankstate.preamble.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/**
 * Beautiful profile setup dialog shown after onboarding sign-in.
 * Uses a Material Expressive gender slider and clean age input.
 * Data is used for admin group targeting. User can skip.
 */
@Composable
fun ProfileSetupDialog(
    onSubmit: (gender: String?, age: Int?) -> Unit,
    onSkip: () -> Unit
) {
    // Slider: 0 = Male, 1 = Female, 2 = Non-binary, 3 = Prefer not to say
    var genderSlider by remember { mutableFloatStateOf(-1f) }
    var ageText by remember { mutableStateOf("") }

    val genderOptions = listOf("Male", "Female", "Non-binary", "Prefer not to say")
    val genderKeys = listOf("male", "female", "nonbinary", null)
    val genderEmojis = listOf("👨", "👩", "🧑", "🤫")
    val genderColors = listOf(
        Color(0xFF42A5F5),  // Blue
        Color(0xFFEC407A),  // Pink
        Color(0xFFAB47BC),  // Purple
        Color(0xFF78909C)   // Grey
    )

    val selectedIndex = if (genderSlider >= 0) genderSlider.roundToInt().coerceIn(0, 3) else -1
    val selectedGender = if (selectedIndex >= 0) genderKeys[selectedIndex] else null

    Dialog(onDismissRequest = onSkip) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Tell us about yourself",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    "This helps us personalize your experience.\nCompletely optional — skip anytime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ── Gender Section ──
                Text(
                    "I identify as",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Gender chips row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genderOptions.forEachIndexed { index, label ->
                        val isSelected = selectedIndex == index
                        val bgColor by animateColorAsState(
                            if (isSelected) genderColors[index].copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            animationSpec = tween(300),
                            label = "chipBg"
                        )
                        val borderColor by animateColorAsState(
                            if (isSelected) genderColors[index]
                            else Color.Transparent,
                            animationSpec = tween(300),
                            label = "chipBorder"
                        )
                        val chipScale by animateFloatAsState(
                            if (isSelected) 1.05f else 1f,
                            animationSpec = tween(200),
                            label = "chipScale"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .scale(chipScale)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .clickable { genderSlider = index.toFloat() }
                                .padding(vertical = 12.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                genderEmojis[index],
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) genderColors[index] else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = genderColors[index]
                                )
                            }
                        }
                    }
                }

                // Expressive slider
                if (genderSlider >= 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = genderSlider,
                        onValueChange = { genderSlider = it },
                        valueRange = 0f..3f,
                        steps = 2,
                        colors = SliderDefaults.colors(
                            thumbColor = if (selectedIndex >= 0) genderColors[selectedIndex] else MaterialTheme.colorScheme.primary,
                            activeTrackColor = if (selectedIndex >= 0) genderColors[selectedIndex].copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ── Age Section ──
                Text(
                    "My age",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = ageText,
                    onValueChange = { text ->
                        if (text.length <= 3 && text.all { it.isDigit() }) {
                            ageText = text
                        }
                    },
                    placeholder = { Text("e.g. 22") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(28.dp))

                // ── Action Buttons ──
                Button(
                    onClick = {
                        val age = ageText.toIntOrNull()
                        onSubmit(selectedGender, age)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Continue", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Skip for now",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
