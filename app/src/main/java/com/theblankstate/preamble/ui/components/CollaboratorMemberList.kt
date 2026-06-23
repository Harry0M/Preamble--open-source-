package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.collab.CollaboratorPreview
import com.theblankstate.preamble.collab.CollaboratorView
import com.theblankstate.preamble.collab.Reactions
import com.theblankstate.preamble.collab.TaskReaction
import com.theblankstate.preamble.data.CollabAssigneeStatus

/**
 * Shared collaborator-list composable for the collaborative-tasks feature (Requirements 20, 21).
 *
 * Single source of truth for rendering the member list in both the read-only tap-to-open
 * [TaskDetailBottomSheet] (`showRoleControls = false`) and the edit [TaskDetailSheet]
 * (`showRoleControls = true`), so the two surfaces cannot drift.
 *
 * Behavior:
 *  - The displayed set is the admin row (from [adminUid]/[adminName]) prepended to the visible
 *    members, where visible members are exactly the `pending`/`accepted`/`completed` assignees
 *    (terminal `declined`/`left`/`removed` excluded) via [CollaboratorView.visibleMembers]
 *    (Requirement 20.1).
 *  - The list is collapsible via [CollaboratorPreview.preview]: a preview of
 *    [CollaboratorPreview.PREVIEW_COUNT] with an expand control when collapsed, a collapse control
 *    when expanded, and no control when the list fits within the preview cap (Requirements 21.1–21.4).
 *  - Each member row shows the admin badge (Requirement 20.2), a completion indicator from
 *    `isCompleted` (Requirement 20.3), and a separate acceptance chip distinguishing `pending` from
 *    `accepted` (Requirement 20.4).
 *  - Remove / transfer / leave controls are gated behind [showRoleControls] (only the edit sheet
 *    passes `true`).
 */
@Composable
fun CollaboratorMemberList(
    adminUid: String?,
    adminName: String?,
    assignees: List<CollabAssigneeStatus>,
    currentUserUid: String?,
    showRoleControls: Boolean,
    modifier: Modifier = Modifier,
    onRemoveMember: ((String) -> Unit)? = null,
    onTransferOwnership: ((String) -> Unit)? = null,
    onLeave: (() -> Unit)? = null,
    reactions: List<TaskReaction> = emptyList(),
    onReact: ((String) -> Unit)? = null,
    onNudge: ((String) -> Unit)? = null,
    canNudge: ((String) -> Boolean)? = null,
    nudgeCooldownRemaining: ((String) -> Long)? = null,
) {
    // Build the displayed list: admin row first, then the non-terminal members
    // (Requirement 20.1, reusing the Property 16 filter).
    val rows: List<CollabRow> = remember(adminUid, adminName, assignees) {
        buildList {
            if (adminUid != null) {
                add(CollabRow.Admin(adminUid, adminName ?: "Admin"))
            }
            CollaboratorView.visibleMembers(assignees) { it.status }
                .forEach { add(CollabRow.Member(it)) }
        }
    }

    // Collapsible preview state (Requirement 21).
    var expanded by remember { mutableStateOf(false) }
    val preview = CollaboratorPreview.preview(rows, expanded)

    val isCurrentUserAdmin = currentUserUid != null && currentUserUid == adminUid
    val isCurrentUserMember = currentUserUid != null && assignees.any { it.uid == currentUserUid }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        preview.shown.forEach { row ->
            when (row) {
                is CollabRow.Admin -> AdminRow(name = row.name)
                is CollabRow.Member -> MemberRow(
                    assignee = row.assignee,
                    showRoleControls = showRoleControls,
                    canManage = showRoleControls && isCurrentUserAdmin && row.assignee.uid != currentUserUid,
                    onRemoveMember = onRemoveMember,
                    onTransferOwnership = onTransferOwnership,
                    currentUserUid = currentUserUid,
                    onNudge = onNudge,
                    canNudge = canNudge,
                    nudgeCooldownRemaining = nudgeCooldownRemaining,
                )
            }
        }

        // Expand/collapse affordance (Requirements 21.1–21.3); none when the list fits (21.4).
        if (preview.canExpand) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (expanded) "Show less" else "Show ${preview.overflow} more",
                    fontSize = 13.sp
                )
            }
        }

        // ── Reactions (social-engagement Requirements 1.1, 3.1, 3.3) ──
        // The reactor summary (name + emoji) is shown to everyone viewing the list;
        // the six-emoji control is offered only to participants (members/admin).
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        ReactionSummaryRow(reactions = reactions)
        if (onReact != null && (isCurrentUserMember || isCurrentUserAdmin)) {
            val myEmoji = reactions.firstOrNull { it.reactorUid == currentUserUid }?.emoji
            ReactionEmojiRow(currentEmoji = myEmoji, onReact = onReact)
        }

        // Self-removal control for a non-admin member, gated behind showRoleControls.
        if (showRoleControls && !isCurrentUserAdmin && isCurrentUserMember && onLeave != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            TextButton(
                onClick = { onLeave() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Leave this shared task",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** A row in the displayed collaborator list: either the admin or a visible member. */
private sealed interface CollabRow {
    data class Admin(val uid: String, val name: String) : CollabRow
    data class Member(val assignee: CollabAssigneeStatus) : CollabRow
}

@Composable
private fun AdminRow(name: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = "$name (Admin)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        // Admin badge (Requirement 20.2).
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "Admin",
            tint = Color(0xFFFFB300),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun MemberRow(
    assignee: CollabAssigneeStatus,
    showRoleControls: Boolean,
    canManage: Boolean,
    onRemoveMember: ((String) -> Unit)?,
    onTransferOwnership: ((String) -> Unit)?,
    currentUserUid: String? = null,
    onNudge: ((String) -> Unit)? = null,
    canNudge: ((String) -> Boolean)? = null,
    nudgeCooldownRemaining: ((String) -> Long)? = null,
) {
    val elapsedDaysStr = remember(assignee.assignedTimestamp, assignee.completedTimestamp, assignee.isCompleted) {
        val endMs = assignee.completedTimestamp ?: System.currentTimeMillis()
        val startMs = assignee.assignedTimestamp
        val diffMs = endMs - startMs
        val days = (diffMs / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
        if (assignee.isCompleted) {
            if (days <= 0) "Completed today" else "Completed in $days day${if (days > 1) "s" else ""}"
        } else {
            if (days <= 0) "Pending from today" else "Pending for $days day${if (days > 1) "s" else ""}"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = assignee.name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(
                    text = assignee.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = elapsedDaysStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Acceptance status (pending vs accepted), shown separately from the
            // completion flag (Requirement 20.4).
            val hasAccepted = assignee.status == "accepted" || assignee.status == "completed"
            val acceptanceLabel = if (hasAccepted) "Accepted" else "Pending"
            val acceptanceColor = if (hasAccepted) Color(0xFF4CAF50) else Color(0xFFFF9800)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = acceptanceColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = acceptanceLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = acceptanceColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            // Completion indicator (completed vs not completed) per Requirement 20.3.
            Icon(
                imageVector = if (assignee.isCompleted) Icons.Default.CheckCircle else Icons.Default.Schedule,
                contentDescription = if (assignee.isCompleted) "Completed" else "Not completed",
                tint = if (assignee.isCompleted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(18.dp)
            )
            // One-tap nudge control for a pending member other than the signed-in user
            // (social-engagement Requirements 10.1, 10.4, 11.2, 12.2). Disabled with a
            // cooldown label while the 60-minute rate-limit window is still open.
            if (onNudge != null && assignee.status == "pending" && assignee.uid != currentUserUid) {
                val allowed = canNudge?.invoke(assignee.uid) ?: true
                val cooldownLabel = if (!allowed) {
                    val remainingMs = nudgeCooldownRemaining?.invoke(assignee.uid) ?: 0L
                    val mins = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1)
                    "Wait ${mins}m"
                } else {
                    "Nudge"
                }
                TextButton(
                    onClick = { onNudge(assignee.uid) },
                    enabled = allowed
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = "Nudge ${assignee.name}",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(cooldownLabel, fontSize = 12.sp)
                }
            }
            // Admin-only role controls, gated behind showRoleControls (Requirements 13.5–13.6).
            if (showRoleControls && canManage) {
                TextButton(
                    onClick = { onTransferOwnership?.invoke(assignee.uid) },
                    enabled = onTransferOwnership != null
                ) {
                    Text("Make admin", fontSize = 12.sp)
                }
                IconButton(
                    onClick = { onRemoveMember?.invoke(assignee.uid) },
                    enabled = onRemoveMember != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove member",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * The six-emoji reaction control (social-engagement Requirement 1.1). The signed-in
 * user's current reaction (if any) is highlighted; tapping the highlighted emoji removes
 * it and tapping a different one changes it — the toggle decision is made by the pure
 * [Reactions] logic in the ViewModel, so this control simply forwards the tapped emoji.
 */
@Composable
private fun ReactionEmojiRow(
    currentEmoji: String?,
    onReact: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Reactions.EMOJI_SET.forEach { emoji ->
            val selected = emoji == currentEmoji
            Surface(
                onClick = { onReact(emoji) },
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                },
                border = if (selected) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = emoji, fontSize = 18.sp)
                }
            }
        }
    }
}

/**
 * The reactor summary (social-engagement Requirement 3.1): each reactor's display name
 * paired with their chosen emoji. Shows an explicit empty-state when there are no
 * reactions yet (Requirement 3.3).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReactionSummaryRow(
    reactions: List<TaskReaction>,
) {
    if (reactions.isEmpty()) {
        Text(
            text = "No reactions yet",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { reaction ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = reaction.emoji, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = reaction.reactorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
