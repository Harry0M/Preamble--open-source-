package com.theblankstate.preamble.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.AdminTask

/**
 * Renders an admin task in the exact same Row layout as TaskItem,
 * with a material icon instead of a checkbox (matching the pattern used
 * for info-only events like festivals and birthdays).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AdminTaskCard(
    task: AdminTask,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val typeIcon = when (task.type) {
        "feature" -> Icons.Default.AutoAwesome     // Sparkles — feature discovery
        "promotion" -> Icons.Default.CardGiftcard   // Gift — offers/deals
        "social" -> Icons.Default.Favorite          // Heart — social/follow
        else -> Icons.Default.Campaign              // Megaphone — announcements
    }

    val accentColor = when (task.type) {
        "feature" -> Color(0xFF6366F1)   // Indigo
        "promotion" -> Color(0xFFFF9800) // Orange
        "social" -> Color(0xFFE91E63)    // Pink
        else -> Color(0xFF4CAF50)        // Green for announcements
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {
                    if (task.directRedirect) {
                        val url = task.actionUrl ?: task.deepLink
                        if (url != null) {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            } catch (_: Exception) { }
                        }
                        onAction()
                    } else {
                        onClick()
                    }
                },
                onLongClick = { onClick() }
            )
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon — same sizing as info-only events in TaskItem
        Icon(
            imageVector = typeIcon,
            contentDescription = task.type,
            tint = accentColor,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(24.dp)
                .padding(2.dp)
        )

        // Content column — matches TaskItem's Column(weight(1f))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!task.description.isNullOrBlank()) {
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Tags row — small colored pills like Google Calendar/Tasks tags
            if (task.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    task.tags.forEach { tag ->
                        val tagColor = tag.toComposeColor()
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(tagColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(tagColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = tagColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
