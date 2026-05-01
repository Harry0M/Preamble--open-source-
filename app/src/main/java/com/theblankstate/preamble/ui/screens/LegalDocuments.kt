package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class LegalSection(
    val heading: String,
    val body: String = "",
    val bullets: List<String> = emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentSheet(
    title: String,
    sections: List<LegalSection>,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        section.heading,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (section.body.isNotEmpty()) {
                        Text(
                            section.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    section.bullets.forEach { bullet ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 4.dp),
                        ) {
                            Text("•", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text(bullet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

fun privacyPolicySections(): List<LegalSection> = listOf(
    LegalSection(
        heading = "Our Privacy Commitment",
        body = "Preamble does not sell your personal data. We use data only for legitimate product purposes:",
        bullets = listOf(
            "Make the app work reliably",
            "Improve the product and user experience",
            "Support AI features",
            "Manage crashes and failures",
            "Prevent abuse and maintain security",
            "Respond to support requests",
        ),
    ),
    LegalSection(
        heading = "What We Do Not Save",
        body = "We do not save your task content for our own use, including:",
        bullets = listOf(
            "Task titles and descriptions",
            "Tags, priorities, and dates",
            "Reminders and subtasks",
            "Completion status",
            "AI chat history as a permanent record",
        ),
    ),
    LegalSection(
        heading = "What We May Save",
        body = "We save information necessary to provide features you use:",
        bullets = listOf(
            "Account information (name, email, Firebase user ID)",
            "AI memory — only when you enable this feature",
            "App settings and preferences",
            "Problem reports with attachments you choose to add",
            "Device information, app version, and diagnostic data",
        ),
    ),
    LegalSection(
        heading = "AI Services",
        body = "Preamble uses third-party AI services for AI chat, task parsing, natural language processing, summaries, suggestions, and personalization. Relevant prompts may be sent to AI providers. AI memory is saved only when enabled.",
        bullets = listOf(
            "AI can make mistakes — output may be inaccurate",
            "Task parsing may incorrectly detect dates, priorities, or reminders",
            "You are responsible for reviewing important information before acting on it",
        ),
    ),
    LegalSection(
        heading = "Google and Firebase Services",
        body = "Preamble integrates with the following services:",
        bullets = listOf(
            "Google Sign-In — authentication",
            "Google Calendar Sync — optional, when you connect it",
            "Google Tasks Sync — optional, when you connect it",
            "Firebase Authentication, Firestore, Cloud Functions, Messaging, Storage",
        ),
    ),
    LegalSection(
        heading = "Analytics and Reliability",
        body = "We use PostHog analytics and Firebase operational data to:",
        bullets = listOf(
            "Understand what features are working well",
            "Detect and fix crashes and failures",
            "Improve reliability and performance",
            "NOT to sell your personal data",
        ),
    ),
    LegalSection(
        heading = "Advertising",
        body = "Preamble may show minimal ads to support AI feature costs.",
        bullets = listOf(
            "Ads appear only in the AI chat screen",
            "Ads are optional and user-initiated (rewarded ads for AI credits)",
            "Ads are not shown anywhere else in the app",
        ),
    ),
    LegalSection(
        heading = "Data Sharing",
        body = "We do not share your personal data except with service providers required to operate selected features:",
        bullets = listOf(
            "Google/Firebase — authentication, storage, sync",
            "PostHog — analytics",
            "Third-party AI providers — AI features",
            "Ad services — optional advertising only",
            "Legal authorities — only when required by law",
        ),
    ),
    LegalSection(
        heading = "Contact",
        body = "For privacy questions or requests:",
        bullets = listOf(
            "Email: theblankstate@theblankstate.com",
            "Website: theblankstate.com",
        ),
    ),
)

fun termsAndConditionsSections(): List<LegalSection> = listOf(
    LegalSection(
        heading = "Acceptance of Terms",
        body = "By using Preamble, you agree to these Terms and our Privacy Policy. Preamble provides:",
        bullets = listOf(
            "Task management and organization",
            "Reminders and notifications",
            "AI-powered assistance features",
            "Optional Google Calendar integration",
            "Optional Google Tasks integration",
        ),
    ),
    LegalSection(
        heading = "User Responsibilities",
        body = "By using Preamble, you agree to:",
        bullets = listOf(
            "Use the app only for lawful purposes",
            "Keep your account credentials secure",
            "Review important tasks and reminders before acting on them",
            "Not upload harmful, illegal, or abusive content",
            "Not attempt to disrupt, scrape, or reverse-engineer the service",
            "Comply with all applicable laws and regulations",
        ),
    ),
    LegalSection(
        heading = "AI Features and Limitations",
        body = "Preamble uses third-party AI for chat, task parsing, natural language processing, and suggestions.",
        bullets = listOf(
            "AI can make mistakes — output may be inaccurate or incomplete",
            "Task parsing may incorrectly detect dates, priorities, or reminders",
            "AI suggestions are not a substitute for your own judgement",
            "You are solely responsible for reviewing all important information",
        ),
    ),
    LegalSection(
        heading = "Google and Firebase Services",
        body = "Preamble integrates with the following services:",
        bullets = listOf(
            "Google Sign-In — authentication",
            "Google Calendar — optional sync",
            "Google Tasks — optional sync",
            "Firebase Authentication, Firestore, Cloud Functions, Messaging, Storage",
            "We do not share your Google data with third parties",
        ),
    ),
    LegalSection(
        heading = "Analytics and Service Improvement",
        body = "We use PostHog and Firebase data to:",
        bullets = listOf(
            "Improve product features and usability",
            "Detect and resolve crashes and failures",
            "Monitor reliability and performance",
            "Analytics data is never sold",
        ),
    ),
    LegalSection(
        heading = "Advertising",
        body = "Preamble may display minimal ads to support AI feature costs.",
        bullets = listOf(
            "Ads appear only in the AI chat screen",
            "Ads are optional and user-initiated (rewarded ads for AI credits)",
            "Core features work without viewing any ads",
        ),
    ),
    LegalSection(
        heading = "Service Availability and Liability",
        body = "Preamble may change, pause, or limit features at any time. The Blank State is not liable for:",
        bullets = listOf(
            "Service outages or interruptions",
            "Data loss or sync failures",
            "Losses from incorrect AI output",
            "Missed reminders or notifications",
            "Third-party service failures outside our control",
        ),
    ),
    LegalSection(
        heading = "Changes to Terms",
        body = "We may update these Terms at any time. Continued use of Preamble after updates means you accept the revised Terms.",
    ),
    LegalSection(
        heading = "Contact",
        body = "For legal or support questions:",
        bullets = listOf(
            "Email: theblankstate@theblankstate.com",
            "Website: theblankstate.com",
        ),
    ),
)
