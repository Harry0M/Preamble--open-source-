package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.ui.theme.ThemePreferences
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.fillMaxSize


@Composable
fun ColorPickerComponent() {
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val colors = listOf(
        Color(0xFF2C2C2C), Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
        Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4),
        Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A),
        Color(0xFFCDDC39), Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800),
        Color(0xFFFF5722), Color(0xFF795548), Color(0xFF9E9E9E), Color(0xFF607D8B)
    )

    IconButton(onClick = { showDialog = true }) {
        Icon(Icons.Default.Edit, contentDescription = "Theme Color")
    }

    if (showDialog) {
        var hue by remember { mutableStateOf(0f) }
        val selectedCustomColor = remember(hue) { Color.hsv(hue, 1f, 1f) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose Theme Color") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Custom Color", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    hue = (offset.x / size.width) * 360f
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val colors = listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                            drawRoundRect(
                                brush = Brush.horizontalGradient(colors),
                                size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                            )
                            
                            val cursorX = (hue / 360f) * size.width
                            drawCircle(
                                color = Color.White,
                                radius = 20.dp.toPx(),
                                center = Offset(cursorX, size.height / 2)
                            )
                            drawCircle(
                                color = selectedCustomColor,
                                radius = 16.dp.toPx(),
                                center = Offset(cursorX, size.height / 2)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            ThemePreferences.setColor(context, selectedCustomColor)
                            showDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = selectedCustomColor)
                    ) {
                        Text("Apply Custom Color", color = if (selectedCustomColor.luminance() > 0.5f) Color.Black else Color.White)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Presets", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    colors.chunked(5).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            rowColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable {
                                            if (color == Color(0xFF2C2C2C)) {
                                                ThemePreferences.setColor(context, null)
                                            } else {
                                                ThemePreferences.setColor(context, color)
                                            }
                                            showDialog = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    ThemePreferences.setColor(context, null)
                    showDialog = false 
                }) {
                    Text("Reset (Monochrome)")
                }
            }
        )
    }
}
