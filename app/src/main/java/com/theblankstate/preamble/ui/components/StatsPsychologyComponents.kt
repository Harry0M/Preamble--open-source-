package com.theblankstate.preamble.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Hourglass
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════
// Identity Capsule — "You are a [Persona] · Level N"
// Psychology: identity labeling (Cialdini commitment), endowed progress
// ═══════════════════════════════════════════════════════════════════

data class IdentityPersona(val label: String, val tagline: String, val color: Color)

fun personaFor(score: Int, streak: Int): IdentityPersona = when {
    score >= 90 || streak >= 30 -> IdentityPersona("Apex Achiever", "Top 5% of minds", Color(0xFFFFB300))
    score >= 80 -> IdentityPersona("Focused Creator", "Built for deep work", Color(0xFF7C4DFF))
    score >= 70 -> IdentityPersona("Rising Builder", "Momentum is yours", Color(0xFF00BFA5))
    score >= 60 -> IdentityPersona("Steady Mover", "Consistent and clear", Color(0xFF2196F3))
    score >= 45 -> IdentityPersona("Explorer", "Finding your rhythm", Color(0xFF26A69A))
    else -> IdentityPersona("Awakening", "Your journey starts now", Color(0xFF78909C))
}

fun levelFor(karmaPoints: Int): Int = (karmaPoints / 500 + 1).coerceIn(1, 20)
fun levelProgress(karmaPoints: Int): Float {
    val inLevel = karmaPoints % 500
    return inLevel / 500f
}

@Composable
fun IdentityCapsule(
    persona: IdentityPersona,
    level: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "levelProgress"
    )
    // Subtle shimmer on the border — variable reward hint
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        persona.color.copy(alpha = 0.18f),
                        persona.color.copy(alpha = 0.06f)
                    )
                )
            )
            .border(
                1.dp,
                persona.color.copy(alpha = shimmer),
                RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = persona.color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "YOU ARE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = persona.color.copy(alpha = 0.85f),
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(persona.color.copy(alpha = 0.22f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "LVL $level",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = persona.color
                )
            }
        }
        Text(
            persona.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            persona.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Level progress track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(persona.color.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(persona.color.copy(alpha = 0.7f), persona.color)
                        )
                    )
            )
        }
        Text(
            "${(progress * 500).toInt()} / 500 XP to LVL ${level + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Streak Countdown — live ticking timer
// Psychology: loss aversion (protect streak), Zeigarnik effect
// ═══════════════════════════════════════════════════════════════════

private fun msUntilMidnight(): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis - System.currentTimeMillis()
}

@Composable
fun StreakCountdownCapsule(
    streak: Int,
    todayCompleted: Int,
    todayTotal: Int,
    modifier: Modifier = Modifier
) {
    if (streak < 1) return
    val doneToday = todayTotal > 0 && todayCompleted >= todayTotal
    var remainingMs by remember { mutableLongStateOf(msUntilMidnight()) }

    LaunchedEffect(Unit) {
        while (true) {
            remainingMs = msUntilMidnight()
            delay(1000L)
        }
    }

    val hours = (remainingMs / 3_600_000).toInt()
    val minutes = ((remainingMs / 60_000) % 60).toInt()
    val seconds = ((remainingMs / 1000) % 60).toInt()

    val urgency = when {
        doneToday -> UrgencyLevel.SAFE
        remainingMs < 2 * 3_600_000L -> UrgencyLevel.CRITICAL
        remainingMs < 6 * 3_600_000L -> UrgencyLevel.WARNING
        else -> UrgencyLevel.NORMAL
    }

    val bg = when (urgency) {
        UrgencyLevel.SAFE -> Color(0xFF4CAF50)
        UrgencyLevel.CRITICAL -> Color(0xFFE53935)
        UrgencyLevel.WARNING -> Color(0xFFFF9800)
        UrgencyLevel.NORMAL -> Color(0xFFFF6D00)
    }

    // Pulse on critical
    val pulseTransition = rememberInfiniteTransition(label = "streakPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (urgency == UrgencyLevel.CRITICAL) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(pulseScale)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(bg.copy(alpha = 0.22f), bg.copy(alpha = 0.08f))
                )
            )
            .border(1.dp, bg.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bg.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = bg,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$streak DAY STREAK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            val subtitle = when (urgency) {
                UrgencyLevel.SAFE -> "Locked in for today ✓"
                UrgencyLevel.CRITICAL -> "Ends in ${hours}h ${minutes}m ${seconds}s"
                UrgencyLevel.WARNING -> "$hours h $minutes m left to keep it alive"
                UrgencyLevel.NORMAL -> "$hours h $minutes m to protect streak"
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = bg,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (urgency == UrgencyLevel.CRITICAL || urgency == UrgencyLevel.WARNING) {
            Icon(
                Icons.Filled.Hourglass,
                contentDescription = null,
                tint = bg,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private enum class UrgencyLevel { SAFE, NORMAL, WARNING, CRITICAL }

// ═══════════════════════════════════════════════════════════════════
// Percentile Card — social proof (derived from score, seeded)
// Psychology: social comparison, implicit competition
// ═══════════════════════════════════════════════════════════════════

fun computePercentile(score: Int, streak: Int, totalCompleted: Int): Int {
    // Deterministic percentile: base on score with nudges from streak + volume.
    val base = score.coerceIn(0, 100)
    val streakBoost = (streak.coerceAtMost(30)) / 3  // up to +10
    val volumeBoost = (totalCompleted.coerceAtMost(1000) / 100)  // up to +10
    val jitter = ((totalCompleted + streak) * 7) % 5 - 2 // -2..+2 stable
    return (base + streakBoost + volumeBoost + jitter).coerceIn(5, 99)
}

@Composable
fun PercentileCard(
    percentile: Int,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedPct by animateIntAsState(
        targetValue = percentile,
        animationSpec = tween(1600, easing = FastOutSlowInEasing),
        label = "pctAnim"
    )
    val accent = when {
        percentile >= 90 -> Color(0xFFFFB300)
        percentile >= 70 -> Color(0xFF7C4DFF)
        percentile >= 50 -> primaryColor
        else -> Color(0xFF26A69A)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Public,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "GLOBAL STANDING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "BETA",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Top $animatedPct%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = accent
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "of Preamble users this week",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        // Percentile bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((100 - percentile) / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.5f), accent)
                        )
                    )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Hero Insight Card — single AI-picked line, prominent
// Psychology: personalization, curiosity gap
// ═══════════════════════════════════════════════════════════════════

@Composable
fun HeroInsightCard(
    insight: String,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val cleaned = insight.dropWhile { !it.isLetterOrDigit() }.trim()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.14f),
                        primaryColor.copy(alpha = 0.04f)
                    )
                )
            )
            .border(1.dp, primaryColor.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "INSIGHT FOR YOU",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                cleaned.ifBlank { "Open a task to start building your story." },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Premium Preview Card — replaces blur-lock; shows teaser + CTA
// Psychology: reciprocity (give sample), curiosity gap
// ═══════════════════════════════════════════════════════════════════

@Composable
fun PremiumPreviewCard(
    title: String,
    teaser: String,
    onUnlock: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val gold = Color(0xFFFFB300)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        gold.copy(alpha = 0.16f),
                        primaryColor.copy(alpha = 0.06f)
                    )
                )
            )
            .border(1.dp, gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = gold,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = gold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(gold.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "PRO",
                    style = MaterialTheme.typography.labelSmall,
                    color = gold,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp
                )
            }
        }
        Text(
            teaser,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(gold.copy(alpha = 0.25f))
                .clickable { onUnlock() }
                .padding(vertical = 10.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = gold,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Unlock Full Panel",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = gold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Share Chip — generates shareable text stat
// Psychology: identity signaling, tribal flex
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ShareStatsChip(
    onShare: () -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(primaryColor.copy(alpha = 0.14f))
            .clickable { onShare() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Filled.IosShare,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            "Share my week",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Today Progress Ring — in-context live progress
// Psychology: goal gradient effect, progress principle
// ═══════════════════════════════════════════════════════════════════

@Composable
fun TodayProgressPill(
    done: Int,
    total: Int,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    val pct = if (total > 0) done.toFloat() / total else 0f
    val animatedPct by animateFloatAsState(
        targetValue = pct,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "todayPct"
    )
    val accent = when {
        pct >= 1f -> Color(0xFF4CAF50)
        pct >= 0.6f -> primaryColor
        pct >= 0.3f -> Color(0xFFFF9800)
        else -> Color(0xFF78909C)
    }
    val label = when {
        total == 0 -> "No tasks today — add one to start"
        pct >= 1f -> "All done today — champion mode"
        pct >= 0.75f -> "${total - done} left · so close"
        pct >= 0.5f -> "Halfway · keep pushing"
        pct >= 0.25f -> "Warming up · ${done} down"
        else -> "Your day is waiting"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TODAY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                letterSpacing = 1.1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$done / $total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = accent
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedPct)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.7f), accent)
                        )
                    )
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Weekly Challenge Ribbon — scarcity/FOMO
// Psychology: scarcity, challenge commitment
// ═══════════════════════════════════════════════════════════════════

@Composable
fun WeeklyChallengeRibbon(
    streak: Int,
    weeklyConsistencyDays: Int,
    modifier: Modifier = Modifier
) {
    val cal = Calendar.getInstance()
    val dow = cal.get(Calendar.DAY_OF_WEEK) // Sun=1..Sat=7
    val daysLeft = (8 - dow).coerceAtLeast(1)
    val target = 5
    val current = weeklyConsistencyDays.coerceAtMost(7)
    val onTrack = current >= (target - daysLeft.coerceAtMost(target))

    val accent = if (onTrack) Color(0xFF00BFA5) else Color(0xFFFF6D00)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Weekly Challenge · $current/$target days",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                if (onTrack) "On track · $daysLeft days left" else "Catch up · $daysLeft days left",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
