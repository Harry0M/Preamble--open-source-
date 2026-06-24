package com.theblankstate.preamble.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.theblankstate.preamble.viewmodel.DayPlanState

/**
 * Track A Iteration 2 — the dedicated, full-screen Plan-My-Day surface (Req 18).
 *
 * Replaces the retired `DayPlanReviewSheet`. Rendered by the host (`HomeScreen`) inside an
 * `AnimatedVisibility` + `BackHandler` overlay (the same pattern as the Friends/Circles
 * full-screen overlays), so it opens immediately on invoke and renders **every**
 * [DayPlanState] in place:
 *
 * - [DayPlanState.Loading] → an alive Material 3 Expressive morphing-shape progress surface
 *   so the user never sees a blank delay (Req 18.1, 18.2).
 * - [DayPlanState.Review] → each task's title + proposed `HH:mm` with Accept / Discard, the
 *   `Plan_Adjustment` free-text field with a Revise action, and an unplaced/advisory callout
 *   when present (Req 18.3, 15.1, 16.2).
 * - [DayPlanState.Error] → a human-readable message with a Retry button, never a raw status
 *   code or a blank screen (Req 18.4, 18.5).
 * - [DayPlanState.NoSchedulableTasks] / [DayPlanState.NoRemainingTimeToday] /
 *   [DayPlanState.CouldNotGenerate] / [DayPlanState.InsufficientCredits] → each renders its
 *   own in-screen human-readable message with a Done action (Req 18.6).
 * - [DayPlanState.Applying] → a brief applying surface; [DayPlanState.Applied] → a success
 *   confirmation (still allowing a post-apply Plan_Adjustment per Req 15.6);
 *   [DayPlanState.Failed] → a human-readable "could not apply" message.
 *
 * Task titles are displayed **unchanged** in any language/script (Req 17.3) — the titles come
 * straight from [DayPlanState.Review.tasksById] and are never rewritten.
 */
@Composable
fun PlanningScreen(
    state: DayPlanState,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
    onSubmitAdjustment: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar with a close affordance (also reachable via the host BackHandler).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Plan my day",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    DayPlanState.Idle -> Unit

                    DayPlanState.Loading ->
                        AlivePlanningProgress(message = "Planning your day…")

                    DayPlanState.Applying ->
                        AlivePlanningProgress(message = "Applying your plan…")

                    is DayPlanState.Review ->
                        ReviewContent(
                            review = state,
                            onAccept = onAccept,
                            onDiscard = onDiscard,
                            onSubmitAdjustment = onSubmitAdjustment,
                        )

                    DayPlanState.Applied ->
                        AppliedContent(
                            onClose = onClose,
                            onSubmitAdjustment = onSubmitAdjustment,
                        )

                    DayPlanState.Error ->
                        ErrorContent(onRetry = onRetry, onClose = onClose)

                    DayPlanState.Failed ->
                        MessageContent(
                            icon = Icons.Filled.ErrorOutline,
                            iconTint = MaterialTheme.colorScheme.error,
                            title = "Couldn't apply the plan",
                            body = "Something went wrong while applying your schedule. Your tasks were left unchanged.",
                            primaryLabel = "Done",
                            onPrimary = onClose,
                        )

                    DayPlanState.NoSchedulableTasks ->
                        MessageContent(
                            icon = Icons.Filled.Info,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Nothing to plan",
                            body = "You have no unscheduled tasks for today. Add a task without a time and try again.",
                            primaryLabel = "Done",
                            onPrimary = onClose,
                        )

                    DayPlanState.NoRemainingTimeToday ->
                        MessageContent(
                            icon = Icons.Filled.Schedule,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "No time left today",
                            body = "There isn't enough time remaining in today's working window to plan around. Try again tomorrow.",
                            primaryLabel = "Done",
                            onPrimary = onClose,
                        )

                    DayPlanState.CouldNotGenerate ->
                        MessageContent(
                            icon = Icons.Filled.Info,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Couldn't build a plan",
                            body = "We couldn't put together a workable schedule from today's tasks. Try adjusting your tasks and planning again.",
                            primaryLabel = "Done",
                            onPrimary = onClose,
                        )

                    DayPlanState.InsufficientCredits ->
                        MessageContent(
                            icon = Icons.Filled.AutoAwesome,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Out of AI credits",
                            body = "You need more AI credits to plan your day. Top up and try again.",
                            primaryLabel = "Done",
                            onPrimary = onClose,
                        )
                }
            }
        }
    }
}

/**
 * An alive Material 3 Expressive loading surface (Req 18.2): a lime morphing shape that
 * continuously morphs between a star and a rounded hexagon while slowly rotating, echoing the
 * Social_Hub design language, with an indeterminate progress ring and a status message.
 */
@Composable
private fun AlivePlanningProgress(message: String) {
    val lime = Color(0xFFD4FF70) // Vibrant Lime Green, echoing the Social_Hub hero treatment

    val morph = remember {
        Morph(
            start = RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = 0.7f,
                rounding = CornerRounding(0.2f),
            ),
            end = RoundedPolygon(
                numVertices = 6,
                rounding = CornerRounding(0.3f),
            ),
        )
    }
    val transition = rememberInfiniteTransition(label = "planLoading")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "planLoadingMorph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "planLoadingRotation",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(MorphPolygonShape(morph, progress, rotation))
                    .background(lime),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp),
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.size(132.dp),
                strokeWidth = 4.dp,
                color = lime,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The reviewable plan (Req 18.3, 15.1, 16.2): the title + proposed `HH:mm` rows (the layout
 * carried over from the retired `DayPlanReviewSheet`), an optional advisory/unplaced callout,
 * the `Plan_Adjustment` field with a Revise action, and Accept / Discard actions.
 */
@Composable
private fun ReviewContent(
    review: DayPlanState.Review,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    onSubmitAdjustment: (String) -> Unit,
) {
    var adjustment by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = "Your day plan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "Review the proposed times before applying. Nothing changes until you accept.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.size(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (assignment in review.schedule.assignments) {
                val title = review.tasksById[assignment.taskId]?.title ?: "Untitled task"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = assignment.time,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Unplaced / advisory callout (Req 16.2, 15.5): surface tasks that could not be placed
        // in the remaining window so they are never silently dropped.
        val unplacedTitles = review.schedule.unplaced.map { it.title }
        if (review.advisory != null || unplacedTitles.isNotEmpty()) {
            Spacer(modifier = Modifier.size(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    val advisory = review.advisory
                        ?: "Some tasks couldn't fit the remaining time: ${unplacedTitles.joinToString(", ")}"
                    Text(
                        text = advisory,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(24.dp))

        // Plan_Adjustment free-text channel (Req 15.1): revise the proposal conversationally.
        Text(
            text = "Want changes? Tell the planner",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.size(8.dp))
        OutlinedTextField(
            value = adjustment,
            onValueChange = { adjustment = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. keep mornings free, it's raining so plan indoors") },
            minLines = 2,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        OutlinedButton(
            onClick = {
                val text = adjustment.trim()
                if (text.isNotEmpty()) onSubmitAdjustment(text)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Revise")
        }

        Spacer(modifier = Modifier.size(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Text("Discard")
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Text("Accept")
            }
        }

        Spacer(modifier = Modifier.size(24.dp))
    }
}

/**
 * The post-apply success surface (Req 18, 15.6): confirms the plan was applied and still
 * offers a Plan_Adjustment channel so the user can request a Revised_Schedule afterwards.
 */
@Composable
private fun AppliedContent(
    onClose: () -> Unit,
    onSubmitAdjustment: (String) -> Unit,
) {
    var adjustment by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "Day plan applied",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Your tasks now have their proposed times. Want to adjust again?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        OutlinedTextField(
            value = adjustment,
            onValueChange = { adjustment = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. push everything 30 minutes later") },
            minLines = 2,
            shape = RoundedCornerShape(16.dp),
        )
        OutlinedButton(
            onClick = {
                val text = adjustment.trim()
                if (text.isNotEmpty()) onSubmitAdjustment(text)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Adjust again")
        }
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
        ) {
            Text("Done")
        }
    }
}

/**
 * The retryable error surface (Req 18.4, 18.5): a human-readable message — never a raw status
 * code — with a Retry action wired to [onRetry] and a way out via [onClose].
 */
@Composable
private fun ErrorContent(
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "We couldn't reach the planner",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Check your connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Text("Close")
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
            ) {
                Text("Retry")
            }
        }
    }
}

/**
 * A reusable terminal-message surface (Req 18.6) used for the in-screen human-readable copy
 * of the non-review outcomes.
 */
@Composable
private fun MessageContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.heightIn(min = 0.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
        ) {
            Text(primaryLabel)
        }
    }
}
