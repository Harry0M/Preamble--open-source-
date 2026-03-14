package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.PredefinedTags

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    selectedTags: Set<String>,
    onTagsChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PredefinedTags.tags.forEach { tag ->
            FilterChip(
                selected = tag.name in selectedTags,
                onClick = {
                    val newTags = if (tag.name in selectedTags) selectedTags - tag.name
                    else selectedTags + tag.name
                    onTagsChanged(newTags)
                },
                label = { Text(tag.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(tag.color, CircleShape)
                    )
                }
            )
        }
    }
}
