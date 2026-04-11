package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * One-time dialog shown after sign-in to collect gender and age.
 * Data is used for admin group targeting. The user can skip it.
 */
@Composable
fun ProfileSetupDialog(
    onSubmit: (gender: String?, age: Int?) -> Unit,
    onSkip: () -> Unit
) {
    var selectedGender by remember { mutableStateOf<String?>(null) }
    var ageText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onSkip,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Personalize Your Experience",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    "Help us tailor recommendations for you. This is optional and can be skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Gender",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("male" to "Male", "female" to "Female", "other" to "Other").forEach { (value, label) ->
                        FilterChip(
                            selected = selectedGender == value,
                            onClick = { selectedGender = if (selectedGender == value) null else value },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = ageText,
                    onValueChange = { text ->
                        if (text.length <= 3 && text.all { it.isDigit() }) {
                            ageText = text
                        }
                    },
                    label = { Text("Age") },
                    placeholder = { Text("e.g. 25") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val age = ageText.toIntOrNull()
                    onSubmit(selectedGender, age)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        }
    )
}
