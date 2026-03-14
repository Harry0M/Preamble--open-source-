package com.theblankstate.preamble.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.ads.FeatureGateManager
import com.theblankstate.preamble.ads.RewardedAdManager

enum class FeatureType { THEME, STATS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureUnlockSheet(
    featureType: FeatureType,
    activity: Activity,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit = {}
) {
    val isTheme = featureType == FeatureType.THEME
    val unlocked by if (isTheme) FeatureGateManager.themeUnlocked.collectAsState()
                    else FeatureGateManager.statsUnlocked.collectAsState()
    val remainingMs = if (isTheme) FeatureGateManager.themeRemainingMs() else FeatureGateManager.statsRemainingMs()

    // Start loading the ad when the sheet opens so it's ready when user taps the button
    LaunchedEffect(featureType) {
        if (isTheme) RewardedAdManager.loadThemeAd() else RewardedAdManager.loadStatsAd()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isTheme) Icons.Filled.Palette else Icons.Filled.Leaderboard,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isTheme) "Unlock Theme Customization" else "Unlock Global Rank",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (unlocked && remainingMs > 0) {
                Text(
                    text = "Unlocked — ${FeatureGateManager.formatRemaining(remainingMs)} remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = if (isTheme)
                        "Watch a short video to unlock theme colors and app theme customization for 12 hours."
                    else
                        "Watch a short video to see your global productivity rank among all Preamble users for 12 hours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!unlocked) {
                Button(
                    onClick = {
                        if (isTheme) {
                            RewardedAdManager.showThemeAd(activity) { rewarded ->
                                if (rewarded) {
                                    onUnlocked()
                                    onDismiss()
                                }
                            }
                        } else {
                            RewardedAdManager.showStatsAd(activity) { rewarded ->
                                if (rewarded) {
                                    onUnlocked()
                                    onDismiss()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = CircleShape
                ) {
                    Text(if (isTheme) "Watch Ad to Unlock Theme" else "Watch Ad to Unlock Rank")
                }
            } else {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = CircleShape
                ) {
                    Text("Close")
                }
            }
        }
    }
}
