package com.theblankstate.preamble.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.posthog.PostHog

/**
 * PostHog Feature Flag & Privacy Examples — Preamble ke liye.
 *
 * Yeh file dikhata hai:
 *   1. Feature flag check karke conditionally UI dikhana (A/B testing)
 *   2. Session Replay mein sensitive content mask karna (privacy)
 *   3. Compose mein PostHog ke saath best practices
 *
 * Yeh file ek reference/example hai — directly use mat karo production mein.
 * Copy-paste karke apni screens mein adapt karo.
 */

// ═══════════════════════════════════════════════════
//  1. FEATURE FLAG — A/B Testing Example
// ═══════════════════════════════════════════════════

/**
 * A/B Test: "new_ui_experiment" feature flag ke basis pe
 * alag-alag UI variant dikhata hai.
 *
 * PostHog dashboard pe flag banaao:
 *   Key: "new_ui_experiment"
 *   Type: Boolean (on/off) ya Multivariate (variant strings)
 *
 * Usage in any screen:
 *   FeatureFlagGatedComponent()
 *
 * Note: Feature flags app start pe preload hoti hain (PreambleApplication mein
 * preloadFeatureFlags = true set hai). First launch pe network call hoti hai,
 * uske baad cached rehti hain.
 */
@Composable
fun FeatureFlagGatedComponent() {
    // Feature flag check karo — yeh cached result deta hai (non-blocking)
    var isNewUiEnabled by remember { mutableStateOf(false) }

    // LaunchedEffect mein flag check karo — composition ke time pe ek baar
    LaunchedEffect(Unit) {
        isNewUiEnabled = AnalyticsManager.isFeatureEnabled("new_ui_experiment")

        // Agar multivariate flag hai, toh payload bhi check kar sakte ho:
        // val variant = AnalyticsManager.getFeatureFlagPayload("new_ui_experiment")
        // variant can be: "control", "variant_a", "variant_b", etc.
    }

    // Conditionally UI render karo based on flag
    if (isNewUiEnabled) {
        // ✅ Naya UI — experiment variant
        NewExperimentalCard()
    } else {
        // ⬜ Purana UI — control group
        OriginalCard()
    }
}

/**
 * Naya experimental card — A/B test variant.
 * Isko dikhao agar "new_ui_experiment" flag enabled hai.
 */
@Composable
private fun NewExperimentalCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("new_ui_experiment_card"), // Testing ke liye
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "✨ Naya Feature!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Yeh naya UI experiment hai. PostHog dashboard pe results dekho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Original/control card — default UI jab flag off hai.
 */
@Composable
private fun OriginalCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("original_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Standard View",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Yeh default/control UI hai.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


// ═══════════════════════════════════════════════════
//  2. SESSION REPLAY PRIVACY — Sensitive Data Masking
// ═══════════════════════════════════════════════════

/**
 * PostHog Session Replay mein sensitive content mask karna.
 *
 * PostHog Android SDK mein Compose ke liye specific masking tags hain:
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  METHOD 1: Global masking (ALREADY DONE in PreambleApplication)│
 * │                                                                 │
 * │  sessionReplayConfig.maskAllTextInputs = true                   │
 * │  → Saare TextFields automatically mask ho jaate hain            │
 * │  → Task title input, notes field, search bar — sab masked       │
 * │                                                                 │
 * │  sessionReplayConfig.maskAllImages = false                      │
 * │  → Images by default show hongi (non-sensitive content)         │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  METHOD 2: Per-element masking via Modifier.semantics           │
 * │                                                                 │
 * │  PostHog Session Replay uses Compose's Semantics tree to        │
 * │  identify elements. We use contentDescription with a special    │
 * │  marker to control masking behavior.                            │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  METHOD 3: PostHog View masking via testTag                     │
 * │                                                                 │
 * │  Use testTag("ph-no-capture") to exclude specific Compose       │
 * │  elements from Session Replay entirely.                         │
 * │                                                                 │
 * │  Example:                                                       │
 * │  Text(                                                          │
 * │      text = "Sensitive note content",                           │
 * │      modifier = Modifier.testTag("ph-no-capture")               │
 * │  )                                                              │
 * └─────────────────────────────────────────────────────────────────┘
 */

/**
 * Example: Sensitive task title ko mask karna Session Replay mein.
 *
 * PostHog Session Replay mein yeh text dikh nahi paayega —
 * user ke personal task titles private rehte hain.
 */
@Composable
fun SensitiveTaskTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            // "ph-no-capture" testTag lagane se PostHog Session Replay
            // is element ko record nahi karega — privacy safe!
            .testTag("ph-no-capture")
            // Accessibility ke liye generic description do
            .semantics { contentDescription = "Task title" }
    )
}

/**
 * Example: Personal note ko Session Replay se mask karna.
 */
@Composable
fun SensitiveNoteContent(note: String, modifier: Modifier = Modifier) {
    Text(
        text = note,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .testTag("ph-no-capture")
            .semantics { contentDescription = "Personal note" }
    )
}


// ═══════════════════════════════════════════════════
//  3. USAGE CHEATSHEET — Quick Reference
// ═══════════════════════════════════════════════════

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  PostHog Integration Cheatsheet for Preamble               ║
 * ╠══════════════════════════════════════════════════════════════╣
 * ║                                                              ║
 * ║  IDENTIFY USER (sign-in ke baad):                           ║
 * ║  AnalyticsManager.identifyUser(firebaseUid, email, name)    ║
 * ║                                                              ║
 * ║  RESET USER (sign-out pe):                                  ║
 * ║  AnalyticsManager.resetUser()                                ║
 * ║                                                              ║
 * ║  TRACK EVENTS:                                              ║
 * ║  AnalyticsManager.trackTaskCreated("Work", isPriority=true) ║
 * ║  AnalyticsManager.trackTaskCompleted(taskId)                ║
 * ║  AnalyticsManager.trackFocusMode("started")                 ║
 * ║  AnalyticsManager.trackNotificationClicked(campaignId)      ║
 * ║  AnalyticsManager.trackScreenView("HomeScreen")             ║
 * ║                                                              ║
 * ║  FEATURE FLAGS:                                             ║
 * ║  val enabled = AnalyticsManager.isFeatureEnabled("flag_key")║
 * ║  val payload = AnalyticsManager.getFeatureFlagPayload("key")║
 * ║  AnalyticsManager.reloadFeatureFlags()                      ║
 * ║                                                              ║
 * ║  PRIVACY (Session Replay masking):                          ║
 * ║  Modifier.testTag("ph-no-capture")  → element hidden        ║
 * ║  maskAllTextInputs = true           → all inputs masked     ║
 * ║                                                              ║
 * ║  USER PROPERTIES:                                           ║
 * ║  AnalyticsManager.setUserProperties(mapOf("plan" to "pro")) ║
 * ║                                                              ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
