package com.theblankstate.preamble.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.collab.CircleRef
import com.theblankstate.preamble.collab.FriendRef
import com.theblankstate.preamble.collab.PageWindow
import com.theblankstate.preamble.collab.Recipient
import com.theblankstate.preamble.collab.RecipientResolution
import com.theblankstate.preamble.collab.SearchableRecipient
import com.theblankstate.preamble.collab.SocialSearch
import com.theblankstate.preamble.repository.Circle
import com.theblankstate.preamble.repository.Friend

/**
 * Social Hub lime accent used across the social surfaces; the picker reuses it so it reads as
 * part of the same theme (Social_Hub_Design_Language).
 */
private val RecipientPickerAccent = Color(0xFFD4FF70)

/**
 * Searchable Recipient_Picker (Feature: collaborative-tasks, Requirements 30, 31).
 *
 * A Material 3 Expressive [ModalBottomSheet] that lists Friends **and** Circles together as
 * selectable [Recipient]s, supports case-insensitive search over the FULL set (independent of
 * the paging window), client-side incremental paging, multi-select that survives filtering and
 * reopen, and a live Resolved_Assignee_Set count with a 50-cap guard on the confirm control.
 *
 * All matching/windowing/resolution is delegated to the pure helpers ([SocialSearch],
 * [PageWindow], [RecipientResolution]); this composable is presentation + local selection state.
 *
 * @param friends the user's friends, from `WorkspaceViewModel.friends`.
 * @param circles the user's Circles, from `CircleViewModel.circles` (shared-circles).
 * @param senderUid the sending user's uid, always excluded from the resolved assignee set.
 * @param initiallySelectedKeys selection keys to re-show on open (preserved across reopen, 31.5).
 * @param onConfirm invoked with the selected [Recipient]s when the user confirms (30.7).
 * @param onDismiss invoked when the sheet is dismissed without confirming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientPicker(
    friends: List<Friend>,
    circles: List<Circle>,
    senderUid: String,
    initiallySelectedKeys: Set<String>,
    onConfirm: (List<Recipient>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Build the unified recipient list: Friends first, then Circles (Req 30.1).
    val recipients = remember(friends, circles) {
        buildList {
            friends.forEach {
                add(Recipient.FriendRecipient(FriendRef(it.uid, it.name, it.preambleId, it.photoUrl)))
            }
            circles.forEach {
                add(Recipient.CircleRecipient(CircleRef(it.id, it.name, it.memberUids)))
            }
        }
    }
    val recipientByKey = remember(recipients) { recipients.associateBy { it.key } }

    // Session-scoped, keyed selection seeded from prior selection (Req 31.5, 31.6).
    var selectedKeys by remember { mutableStateOf(initiallySelectedKeys) }
    var query by remember { mutableStateOf("") }

    // Search runs over the FULL recipient set, before any paging window (Req 30.4, 30.5).
    val searchables = remember(recipients) { recipients.map { SearchableRecipient(it) } }
    val filtered = remember(query, searchables) {
        SocialSearch.filter(query, searchables).map { it.recipient }
    }

    // Client-side paging: window grows by one page as the user scrolls (Req 30.2). The page
    // count resets to one page whenever the active query changes.
    var pageCount by remember(query) { mutableStateOf(1) }
    val visible = PageWindow.visible(filtered, pageCount)

    val listState = rememberLazyListState()
    LaunchedEffect(listState, filtered) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                val loaded = PageWindow.visible(filtered, pageCount).size
                if (loaded < filtered.size && PageWindow.shouldLoadMore(lastVisible, loaded)) {
                    pageCount++
                }
            }
    }

    // Live resolution: derive selected friend uids + selected circles' member-uid lists and feed
    // the pure resolver, which dedupes, excludes the sender, and classifies against the 50 cap
    // (Req 29.3, 29.4, 30.6).
    val selectedRecipients = remember(selectedKeys, recipientByKey) {
        selectedKeys.mapNotNull { recipientByKey[it] }
    }
    val resolved = remember(selectedRecipients, senderUid) {
        val friendUids = selectedRecipients
            .filterIsInstance<Recipient.FriendRecipient>()
            .map { it.friend.uid }
        val circleMemberUids = selectedRecipients
            .filterIsInstance<Recipient.CircleRecipient>()
            .map { it.circle.memberUids }
        RecipientResolution.resolve(friendUids, circleMemberUids, senderUid)
    }
    val selectedCount = selectedRecipients.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Send to",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            // Search field over the full set; clearing restores the full set (Req 30.3, 31.4).
            RecipientSearchField(query = query, onQueryChange = { query = it })

            // Live Selected_Count + Resolved_Assignee_Set size, with the 50-cap warning (Req 29.3, 29.4).
            SelectionSummary(
                selectedCount = selectedCount,
                resolvedSize = resolved.size,
                withinLimit = resolved.withinLimit,
            )

            when {
                // Empty state: no friends AND no circles (Req 31.1).
                recipients.isEmpty() -> RecipientEmptyState(
                    message = "No friends or Circles to send to",
                )

                // No-match state: a non-blank query that matches nothing (Req 31.3).
                query.isNotBlank() && filtered.isEmpty() -> RecipientEmptyState(
                    message = "No matches for \"${query.trim()}\"",
                    icon = true,
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.key }) { recipient ->
                        RecipientRow(
                            recipient = recipient,
                            selected = recipient.key in selectedKeys,
                            onToggle = {
                                selectedKeys = if (recipient.key in selectedKeys) {
                                    selectedKeys - recipient.key
                                } else {
                                    selectedKeys + recipient.key
                                }
                            },
                        )
                    }
                    if (visible.size < filtered.size) {
                        item(key = "recipient_paging_sentinel") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = RecipientPickerAccent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onConfirm(selectedRecipients)
                    onDismiss()
                },
                // Disable confirm when the resolved set exceeds the 50 assignee maximum (Req 29.4).
                enabled = resolved.withinLimit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RecipientPickerAccent,
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    text = if (selectedCount == 0) "Done" else "Send to $selectedCount",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Reusable Expressive search field driving the picker's query, mirroring the Social Hub's
 * search affordance (lifts and tints on focus) for theme consistency (Req 30.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipientSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(28.dp)
    val leadingTint by animateColorAsState(
        targetValue = if (focused) RecipientPickerAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 250),
        label = "recipientSearchTint",
    )
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search friends and Circles") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = leadingTint) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear search",
                    modifier = Modifier.clickable { onQueryChange("") },
                )
            }
        },
        singleLine = true,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RecipientPickerAccent,
            focusedLabelColor = RecipientPickerAccent,
            cursorColor = RecipientPickerAccent,
        ),
    )
}

/**
 * Shows the Selected_Count and the live Resolved_Assignee_Set size, surfacing the "exceeds
 * maximum" indication when the resolved set is over the 50 cap (Req 29.3, 29.4).
 */
@Composable
private fun SelectionSummary(
    selectedCount: Int,
    resolvedSize: Int,
    withinLimit: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$selectedCount selected · $resolvedSize recipient${if (resolvedSize == 1) "" else "s"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(visible = !withinLimit) {
            Text(
                text = "Exceeds maximum (${RecipientResolution.MAX_ASSIGNEES})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * A single selectable recipient row: an avatar with a Friend-vs-Circle affordance, the name,
 * a Circle member count, and a selected indicator (Req 30.1, 30.6). The row background and the
 * selected check animate for an alive, on-theme feel.
 */
@Composable
private fun RecipientRow(
    recipient: Recipient,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            RecipientPickerAccent.copy(alpha = 0.20f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "recipientRowBg",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "recipientRowCheck",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = background,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RecipientAvatar(recipient = recipient)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipient.displayLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                when (recipient) {
                    is Recipient.FriendRecipient -> {
                        if (recipient.friend.preambleId.isNotBlank()) {
                            Text(
                                text = "@${recipient.friend.preambleId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is Recipient.CircleRecipient -> {
                        val count = recipient.circle.memberUids.size
                        Text(
                            text = "Circle · $count member${if (count == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Selected indicator: a filled accent check when selected, an empty ring otherwise.
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(RecipientPickerAccent)
                        } else {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(18.dp)
                            .scale(checkScale),
                    )
                }
            }
        }
    }
}

/** Circular avatar carrying the Friend-vs-Circle affordance (Req 30.1). */
@Composable
private fun RecipientAvatar(recipient: Recipient) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(RecipientPickerAccent.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        when (recipient) {
            is Recipient.FriendRecipient -> {
                val initials = recipient.friend.name.initials()
                if (initials.isNotEmpty()) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Friend",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            is Recipient.CircleRecipient -> Icon(
                Icons.Default.Group,
                contentDescription = "Circle",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Empty-state / no-match indication with no selectable rows (Req 31.1, 31.3). */
@Composable
private fun RecipientEmptyState(
    message: String,
    icon: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon) {
            Icon(
                Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.size(0.dp))
}

/** The human-readable label for a recipient row. */
private fun Recipient.displayLabel(): String = when (this) {
    is Recipient.FriendRecipient -> friend.name
    is Recipient.CircleRecipient -> circle.name
}

/** Up-to-two-letter uppercase initials from a display name. */
private fun String.initials(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}
