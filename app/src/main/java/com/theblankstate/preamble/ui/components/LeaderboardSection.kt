package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.collab.Leaderboard

/**
 * The Friends_Leaderboard surface (social-engagement Requirements 9.1, 9.2, 9.6).
 *
 * Renders the already-ranked [entries] (self + friends, ordered by current Weekly_Window
 * points descending — the ordering is computed by the pure [Leaderboard.ranking] in the
 * ViewModel) as a numbered list of name + current-week points. The signed-in user's row is
 * highlighted. When the only entry is the signed-in user (no friends), a no-friends
 * empty-state is shown instead (Requirement 9.6).
 */
@Composable
fun LeaderboardSection(
    entries: List<Leaderboard.Entry>,
    currentUserUid: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "This Week's Leaderboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // No-friends empty-state: only the signed-in user is present (Requirement 9.6).
        if (entries.size <= 1) {
            Text(
                text = "Add friends to see how you stack up this week.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        entries.forEachIndexed { index, entry ->
            val isMe = currentUserUid != null && entry.uid == currentUserUid
            LeaderboardEntryRow(rank = index + 1, entry = entry, isMe = isMe)
        }
    }
}

/**
 * A single Friends_Leaderboard row (social-engagement Req 9.1) — a numbered rank, the
 * participant's display name (with a "(You)" suffix and highlight when it is the signed-in
 * user), and their current-window points. Exposed (public) so the Social_Hub's
 * Section_Organizer can render leaderboard entries as individual list items for client-side
 * Paged_Loading (social-hub-redesign Req 2.8) while preserving this established row styling.
 */
@Composable
fun LeaderboardEntryRow(
    rank: Int,
    entry: Leaderboard.Entry,
    isMe: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isMe) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            Color.Transparent
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (isMe) "${entry.name} (You)" else entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
            Text(
                text = "${entry.weeklyPoints} pts",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
        }
    }
}
