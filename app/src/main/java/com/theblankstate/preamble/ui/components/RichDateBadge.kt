package com.theblankstate.preamble.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.util.FestivalHelper
import com.theblankstate.preamble.util.RichDate

/**
 * Displays a rich date like:
 *   [Monday]  10 March 2026  [Holi 🎨]
 *
 * - Day name in rounded colored badge
 * - Date text
 * - Festival (if any) in rounded colored badge
 */
@Composable
fun RichDateBadge(
    dateStr: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val richDate = FestivalHelper.formatDateRich(dateStr) ?: return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Day name badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isCompact) richDate.dayName.take(3) else richDate.dayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Day, Month, Year
        Text(
            text = "${richDate.day} ${richDate.month} ${richDate.year}",
            style = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Festival badge (if any)
        if (richDate.festival != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = richDate.festival,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/**
 * A header-style rich date for the top bar.
 * Shows: [Tuesday]  10 March 2026  [Republic Day 🇮🇳]
 */
@Composable
fun RichDateHeader(
    modifier: Modifier = Modifier
) {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        .format(java.util.Date())

    RichDateBadge(
        dateStr = today,
        modifier = modifier,
        isCompact = false
    )
}
