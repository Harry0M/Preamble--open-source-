package com.theblankstate.preamble.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.analytics.AnalyticsManager
import kotlinx.coroutines.launch

/** Fixed Referral_Reward granted to each side of an eligible referral (Requirements 1.1, 1.5). */
private const val REFERRAL_REWARD = 50

/**
 * The Referral_CTA surfaced on the friends/workspace surface (Growth-loops Requirement 1).
 *
 * Displays the signed-in user's [inviteLink] (Requirement 1.2), and exposes a Copy
 * control (clipboard + confirmation snackbar — Requirement 1.3) and a Share control
 * (`ACTION_SEND` text carrying the link — Requirement 1.4). Both controls fire
 * `referral-invite-shared` through [AnalyticsManager] (Requirement 6.1).
 *
 * The reward-statement copy is gated on [rewardsEnabled] (social-hub-redesign
 * Requirement 8.2): while the Referral_Reward is disabled in Development_Mode the
 * CTA does NOT state that both sides receive AI credits, and the invite/share flow
 * keeps working unchanged. Flipping [rewardsEnabled] back to `true` restores the
 * two-sided 50-credit reward messaging (Requirements 1.1, 1.5), so the change is
 * reversible through configuration (Requirement 8.4).
 *
 * @param inviteLink the user's own Invite_Link, built by `WorkspaceViewModel.buildInviteLink()`.
 * @param rewardsEnabled whether the two-sided Referral_Reward is active; mirrors the
 *   server-side `REFERRAL_REWARDS_ENABLED` gate and defaults to `false` in Development_Mode.
 */
@Composable
fun ReferralCta(
    inviteLink: String,
    modifier: Modifier = Modifier,
    rewardsEnabled: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFD4FF70), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CardGiftcard,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = "Invite a friend",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        // Reward statement is gated on the Referral_Reward being enabled
                        // (social-hub-redesign Req 8.2): while disabled, omit the credits
                        // claim and use neutral copy; when enabled, state both sides earn
                        // the 50-credit reward (Req 1.1, 1.5).
                        text = if (rewardsEnabled) {
                            "You both get $REFERRAL_REWARD credits when they join"
                        } else {
                            "Share Preamble with your friends"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // The user's own Invite_Link (Req 1.2).
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = inviteLink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    // Copy to clipboard + confirmation snackbar (Req 1.3), funnel (Req 6.1).
                    clipboard.setText(AnnotatedString(inviteLink))
                    AnalyticsManager.trackReferralInviteShared("copy")
                    scope.launch { snackbarHostState.showSnackbar("Invite link copied") }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Copy", modifier = Modifier.padding(start = 8.dp))
            }

            Button(
                onClick = {
                    // ACTION_SEND text carrying the link (Req 1.4), funnel (Req 6.1).
                    // The credits claim is gated on the reward being enabled (Req 8.2).
                    val shareText = if (rewardsEnabled) {
                        "Join me on Preamble and we both get $REFERRAL_REWARD AI credits! $inviteLink"
                    } else {
                        "Join me on Preamble! $inviteLink"
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    AnalyticsManager.trackReferralInviteShared("share")
                    context.startActivity(Intent.createChooser(shareIntent, "Share invite link"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Text("Share", color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}
