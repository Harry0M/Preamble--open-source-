package com.theblankstate.preamble.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import coil.compose.AsyncImage
import com.theblankstate.preamble.collab.AvatarSource
import com.theblankstate.preamble.collab.CollaboratorPreview
import com.theblankstate.preamble.collab.CollaboratorView
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.ui.screens.MorphPolygonShape

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
private data class ClusterMember(val name: String, val status: String, val photoUrl: String?)

/** Diameter of each initials avatar, matching the existing initials convention (32.dp). */
private val AVATAR_SIZE = 32.dp

/**
 * Expressive_Member_Shape (collaborative-tasks Requirement 25) — builds the Material 3
 * Expressive morphing shape shared by every avatar and the "+N" chip in the cluster.
 *
 * It morphs from a soft 6-pointed [RoundedPolygon.star] into a settled rounded hexagon, so on
 * appearance the avatars "bloom" into shape rather than popping in as static circles. The same
 * [Morph] is reused for all members so the treatment is uniform, and it is exposed as a Compose
 * [Shape] through the shared [MorphPolygonShape] adapter (the exact pattern used by the Social
 * Hub's hero/invite badges in `WorkspaceScreen`).
 */
private fun expressiveMemberMorph(): Morph = Morph(
    start = RoundedPolygon.star(
        numVerticesPerRadius = 6,
        innerRadius = 0.72f,
        rounding = CornerRounding(0.3f),
    ),
    end = RoundedPolygon(
        numVertices = 6,
        rounding = CornerRounding(0.4f),
    ),
)

/**
 * A compact, end-aligned avatar cluster for a collaborative task row (Requirement 22), now drawn
 * inside the Expressive_Member_Shape (Requirement 25).
 *
 * Renders nothing for a non-collaborative task or one with no visible members
 * (`task.collabAdminUid == null && task.collabAssignees.isEmpty()`), satisfying 22.5 and the
 * "at least one member" precondition of 22.1.
 *
 * The displayed set is the admin row plus the non-terminal assignees
 * (`CollaboratorView.visibleMembers(task.collabAssignees) { it.status }`). That set is reduced by
 * the shared [CollaboratorPreview.preview] (`expanded = false`) so the cluster draws up to
 * [CollaboratorPreview.PREVIEW_COUNT] (3) overlapping avatars plus a trailing "+N" chip
 * when `overflow > 0` (22.2, 22.3) — the exact same arithmetic that drives the collapsible
 * collaborator list, keeping the two surfaces consistent.
 *
 * Each avatar is styled by [avatarStatusStyle]: `accepted`/`completed` use a filled/primary
 * treatment and `pending` uses a muted/outlined treatment, giving a visible distinction (22.4 /
 * 25.4). Only the container shape changed from a circle to the Expressive morphing shape; the
 * Req 22 selection/overflow/status logic is untouched.
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
            // The admin's photoUrl is not carried on the local Task projection, so the admin row
            // renders the Default_Avatar (initials) for now (a real default drawable can be added
            // later). Each assignee carries its own photoUrl from CollabAssigneeStatus (Req 26).
            add(ClusterMember(name = task.collabAdminName.orEmpty(), status = "accepted", photoUrl = null))
        }
        CollaboratorView.visibleMembers(task.collabAssignees) { it.status }
            .forEach { add(ClusterMember(name = it.name, status = it.status, photoUrl = it.photoUrl)) }
    }
    if (displayed.isEmpty()) return

    val preview = CollaboratorPreview.preview(displayed, expanded = false)

    // Shared Expressive morph reused by every avatar + the overflow chip (Req 25).
    val morph = remember { expressiveMemberMorph() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        // Negative spacing makes the avatars overlap.
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
    ) {
        preview.shown.forEach { member ->
            // Each avatar gets its own one-shot appearance (star -> rounded hexagon + scale-up),
            // then the settled Expressive shape is handed to MemberAvatar to clip the photo/default.
            val progress = rememberAppearance()
            val shape: Shape = MorphPolygonShape(morph, percentage = progress)
            val appearScale = 0.8f + 0.2f * progress
            MemberAvatar(
                photoUrl = member.photoUrl,
                name = member.name,
                style = avatarStatusStyle(member.status),
                shape = shape,
                modifier = Modifier
                    .size(AVATAR_SIZE)
                    .graphicsLayer {
                        scaleX = appearScale
                        scaleY = appearScale
                    },
            )
        }
        if (preview.overflow > 0) {
            OverflowChip(count = preview.overflow, morph = morph)
        }
    }
}

/**
 * Drives the one-shot appearance animation shared by avatars and the overflow chip: the
 * Expressive shape morphs from the star toward the settled rounded hexagon while the container
 * scales up subtly, giving the "alive" feel called for by Req 25 without any continuous motion.
 */
@Composable
private fun rememberAppearance(): Float {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
        label = "memberAvatarAppearance",
    )
    return progress
}

/**
 * Member_Avatar image with Default_Avatar fallback (collaborative-tasks Requirement 26).
 *
 * Renders a single member inside the Expressive_Member_Shape [shape], layering the member's real
 * Google profile photo over a Default_Avatar according to the pure [AvatarSource] precedence:
 *
 *  - [AvatarSource.Source.REAL_PHOTO] → a Coil [AsyncImage] of [photoUrl], clipped to the shape and
 *    cropped to fill it (Req 26.2). An `onError` flips a local [fetchFailed] flag so the next
 *    composition falls back to the Default_Avatar (Req 26.2). While the photo is still loading, the
 *    Default_Avatar drawn beneath shows through, acting as the loading placeholder (Req 26.5).
 *  - [AvatarSource.Source.DEFAULT] → the bundled Default_Avatar drawable inside the shape. No such
 *    drawable is bundled today, so the existing initials-in-shape rendering is used as the
 *    Default_Avatar content (Req 26.3, 26.4); a real default drawable can be dropped in later.
 *
 * The status-derived [style] (filled vs. outlined, Req 22.4 / 25.4) drives the colours of the
 * Default_Avatar content and the legibility separator ring, so the Req 22 status distinction
 * survives whether a photo or the default is shown.
 */
@Composable
fun MemberAvatar(
    photoUrl: String?,
    name: String,
    style: AvatarStatusStyle,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    // A failed Coil fetch flips this, so the precedence yields DEFAULT on the next composition
    // (Req 26.2). Keyed on photoUrl so a new url gets a fresh chance to load.
    var fetchFailed by remember(photoUrl) { mutableStateOf(false) }

    val source = AvatarSource.select(
        hasRealPhoto = !photoUrl.isNullOrBlank(),
        isInitialsPlaceholder = AvatarSource.isGeneratedInitialsAvatar(photoUrl),
        fetchFailed = fetchFailed,
    )

    val scheme = MaterialTheme.colorScheme
    val background: Color
    val borderColor: Color
    when (style) {
        AvatarStatusStyle.FILLED -> {
            background = scheme.primary
            // Separator ring so overlapping avatars stay legible.
            borderColor = scheme.surface
        }
        AvatarStatusStyle.OUTLINED -> {
            background = scheme.surfaceVariant
            borderColor = scheme.primary
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .border(width = 2.dp, color = borderColor, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        // Default_Avatar content (initials). Always drawn: it is the DEFAULT rendering and also the
        // loading placeholder beneath a real photo (Req 26.5).
        DefaultAvatarContent(name = name, style = style)

        if (source == AvatarSource.Source.REAL_PHOTO) {
            AsyncImage(
                model = photoUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onError = { fetchFailed = true },
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape),
            )
        }
    }
}

/**
 * The Default_Avatar content: the member's initial, coloured per [AvatarStatusStyle]. Used both as
 * the [AvatarSource.Source.DEFAULT] rendering and as the placeholder shown while a real photo loads.
 */
@Composable
private fun DefaultAvatarContent(name: String, style: AvatarStatusStyle) {
    val initial = name.trim().take(1).uppercase().ifBlank { "?" }
    val scheme = MaterialTheme.colorScheme
    val contentColor = when (style) {
        AvatarStatusStyle.FILLED -> scheme.onPrimary
        AvatarStatusStyle.OUTLINED -> scheme.onSurfaceVariant
    }
    Text(
        text = initial,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        fontWeight = FontWeight.Bold,
    )
}

/** Trailing "+N" chip rendered when more members exist than the preview shows, in the Expressive shape. */
@Composable
private fun OverflowChip(count: Int, morph: Morph) {
    val scheme = MaterialTheme.colorScheme
    val progress = rememberAppearance()
    val shape: Shape = MorphPolygonShape(morph, percentage = progress)
    val appearScale = 0.8f + 0.2f * progress

    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .graphicsLayer {
                scaleX = appearScale
                scaleY = appearScale
            }
            .clip(shape)
            .background(scheme.secondaryContainer, shape)
            .border(width = 2.dp, color = scheme.surface, shape = shape),
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
