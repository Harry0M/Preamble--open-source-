package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.collab.CollaboratorPreview
import com.theblankstate.preamble.collab.CollaboratorView
import com.theblankstate.preamble.data.Task

/**
 * Visual treatment for a single collaborator avatar, derived purely from a member's status.
 *
 * Feature: collaborative-tasks (Requirement 22.4). Members who have engaged with the task
 * (`accepted`/`completed`) read as [FILLED] (solid/primary), while members who have not yet
 * responded (`pending`) read as [OUTLINED] (muted/outlined). The distinction is intentionally
 * captured as a pure value so it can be unit-checked without Compose.
 */
enum class AvatarStatusStyle {
    /** Solid primary fill — used for `accepted` / `completed` members. */
    FILLED,

    /** Muted, outlined treatment — used for `pending` (and any non-engaged) members. */
    OUTLINED,
}

/**
 * Pure status -> style mapping for collaborator avatars (Requirement 22.4).
 *
 * `accepted` and `completed` map to [AvatarStatusStyle.FILLED]; every other (visible) status —
 * notably `pending` — maps to [AvatarStatusStyle.OUTLINED]. Kept free of Android/Compose
 * dependencies so the accepted/completed-vs-pending distinction is directly testable.
 */
fun avatarStatusStyle(status: String?): AvatarStatusStyle = when (status) {
    "accepted", "completed" -> AvatarStatusStyle.FILLED
    else -> AvatarStatusStyle.OUTLINED
}

/** Internal projection of a displayed cluster member: just the bits the cluster renders. */
private data class ClusterMember(val name: String, val status: String)

/** Diameter of each initials circle, matching the existing initials-circle convention (32.dp). */
private val AVATAR_SIZE = 32.dp

/**
 * A compact, end-aligned avatar cluster for a collaborative task row (Requirement 22).
 *
 * Renders nothing for a non-collaborative task or one with no visible members
 * (`task.collabAdminUid == null && task.collabAssignees.isEmpty()`), satisfying 22.5 and the
 * "at least one member" precondition of 22.1.
 *
 * The displayed set is the admin row plus the non-terminal assignees
 * (`CollaboratorView.visibleMembers(task.collabAssignees) { it.status }`). That set is reduced by
 * the shared [CollaboratorPreview.preview] (`expanded = false`) so the cluster draws up to
 * [CollaboratorPreview.PREVIEW_COUNT] (3) overlapping initials-circles plus a trailing "+N" chip
 * when `overflow > 0` (22.2, 22.3) — the exact same arithmetic that drives the collapsible
 * collaborator list, keeping the two surfaces consistent.
 *
 * Each avatar is styled by [avatarStatusStyle]: `accepted`/`completed` use a filled/primary
 * treatment and `pending` uses a muted/outlined treatment, giving a visible distinction (22.4).
 */
@Composable
fun CollaboratorAvatarCluster(
    task: Task,
    modifier: Modifier = Modifier,
) {
    // Non-collaborative / no-members guard (22.5, 22.1 precondition).
    if (task.collabAdminUid == null && task.collabAssignees.isEmpty()) return

    val displayed = buildList {
        if (task.collabAdminUid != null) {
            add(ClusterMember(name = task.collabAdminName.orEmpty(), status = "accepted"))
        }
        CollaboratorView.visibleMembers(task.collabAssignees) { it.status }
            .forEach { add(ClusterMember(name = it.name, status = it.status)) }
    }
    if (displayed.isEmpty()) return

    val preview = CollaboratorPreview.preview(displayed, expanded = false)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        // Negative spacing makes the circles overlap.
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
    ) {
        preview.shown.forEach { member ->
            AvatarCircle(name = member.name, style = avatarStatusStyle(member.status))
        }
        if (preview.overflow > 0) {
            OverflowChip(count = preview.overflow)
        }
    }
}

/** A single 32.dp initials circle, styled per [AvatarStatusStyle]. */
@Composable
private fun AvatarCircle(name: String, style: AvatarStatusStyle) {
    val initial = name.trim().take(1).uppercase().ifBlank { "?" }
    val scheme = MaterialTheme.colorScheme

    val background: Color
    val contentColor: Color
    val borderColor: Color
    when (style) {
        AvatarStatusStyle.FILLED -> {
            background = scheme.primary
            contentColor = scheme.onPrimary
            // Separator ring so overlapping circles stay legible.
            borderColor = scheme.surface
        }
        AvatarStatusStyle.OUTLINED -> {
            background = scheme.surfaceVariant
            contentColor = scheme.onSurfaceVariant
            borderColor = scheme.primary
        }
    }

    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .background(background, CircleShape)
            .border(width = 2.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Trailing "+N" chip rendered when more members exist than the preview shows. */
@Composable
private fun OverflowChip(count: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .background(scheme.secondaryContainer, CircleShape)
            .border(width = 2.dp, color = scheme.surface, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}
