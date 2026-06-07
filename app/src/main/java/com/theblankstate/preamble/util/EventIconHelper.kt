package com.theblankstate.preamble.util

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.EventNote

object EventIconHelper {
    val iconsMap = mapOf(
        "festival" to Icons.Default.Festival,
        "cake" to Icons.Default.Cake,
        "flight" to Icons.Default.Flight,
        "restaurant" to Icons.Default.Restaurant,
        "local_bar" to Icons.Default.LocalBar,
        "fitness_center" to Icons.Default.FitnessCenter,
        "directions_run" to Icons.Default.DirectionsRun,
        "directions_car" to Icons.Default.DirectionsCar,
        "work" to Icons.Default.Work,
        "school" to Icons.Default.School,
        "celebration" to Icons.Default.Celebration,
        "lightbulb" to Icons.Default.Lightbulb,
        "shopping_cart" to Icons.Default.ShoppingCart,
        "favorite" to Icons.Default.Favorite,
        "medical_services" to Icons.Default.MedicalServices,
        "sports_esports" to Icons.Default.SportsEsports,
        "music_note" to Icons.Default.MusicNote,
        "event" to Icons.AutoMirrored.Filled.EventNote
    )

    val iconToEmojiMap = mapOf(
        "festival" to "🏰",
        "cake" to "🎂",
        "flight" to "✈️",
        "restaurant" to "🍔",
        "local_bar" to "🍹",
        "fitness_center" to "🏋️",
        "directions_run" to "🏃",
        "directions_car" to "🚗",
        "work" to "💼",
        "school" to "🎓",
        "celebration" to "🎉",
        "lightbulb" to "💡",
        "shopping_cart" to "🛒",
        "favorite" to "❤️",
        "medical_services" to "🏥",
        "sports_esports" to "🎮",
        "music_note" to "🎵",
        "event" to "📅"
    )

    val emojiToIconMap = iconToEmojiMap.entries.associate { it.value to it.key }

    fun getIconByName(name: String?): ImageVector {
        return iconsMap[name] ?: Icons.AutoMirrored.Filled.EventNote
    }

    fun getEmojiForIcon(iconName: String?): String {
        return iconToEmojiMap[iconName] ?: "📅"
    }

    fun getIconForEmoji(emoji: String?): String {
        return emojiToIconMap[emoji] ?: "event"
    }
}
