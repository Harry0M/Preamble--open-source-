package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.data.PremiumFeature

/**
 * Track B upsell surface (Req 11.3).
 *
 * A modal bottom sheet shown by a hosting feature when [com.theblankstate.preamble.data.FeatureGate.isUnlocked]
 * returns locked for a [PremiumFeature]. It **identifies the gated feature** with a human-readable
 * name and explains that premium access is required.
 *
 * This is intentionally informational only — there is no purchase or billing flow (out of scope for
 * this feature). Dismissing the sheet returns the user to where they were without performing the
 * locked action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumUpsellSheet(
    feature: PremiumFeature,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))

            Text(
                text = "${featureDisplayName(feature)} is a premium feature",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

            Text(
                text = "Unlock ${featureDisplayName(feature)} and more with Preamble Premium.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("Got it")
            }
        }
    }
}

/** Human-readable name for each gated [PremiumFeature], shown in the upsell (Req 11.3). */
private fun featureDisplayName(feature: PremiumFeature): String = when (feature) {
    PremiumFeature.WRAPPED -> "Wrapped"
    PremiumFeature.AI_AUTO_SUBTASKS -> "AI auto subtasks"
    PremiumFeature.AI_EDIT_FROM_NOTIFICATION -> "Edit from notification"
    PremiumFeature.EXPRESSIVE_APPEARANCE -> "Expressive appearance"
    PremiumFeature.STATS_EXTENDED_RANGE -> "Extended stats range"
    PremiumFeature.STATS_DEDICATED_SCREEN -> "Advanced statistics"
    PremiumFeature.AI_AUTO_PLANNING -> "Plan my day"
    PremiumFeature.UNLIMITED_AI_CREDITS -> "Unlimited AI credits"
}
