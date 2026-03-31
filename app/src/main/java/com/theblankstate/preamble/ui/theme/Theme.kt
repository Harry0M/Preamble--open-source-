package com.theblankstate.preamble.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color.White,
    secondary = Color.LightGray,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF444444),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF555555),
    onTertiaryContainer = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color.LightGray,
    outline = Color.DarkGray
)

private val MonochromeLightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black,
    secondary = Color.DarkGray,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCCCCC),
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDDDDD),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color.DarkGray,
    outline = Color.LightGray
)

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color.White,
    secondary = Color.LightGray,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF1A1A1A),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0D0D0D),
    onSurfaceVariant = Color.LightGray,
    outline = Color(0xFF333333)
)

fun generateCustomColorScheme(primaryColor: Color, darkTheme: Boolean, isAmoled: Boolean = false): androidx.compose.material3.ColorScheme {
    val isLight = primaryColor.luminance() > 0.5f
    val onPrimaryColor = if (isLight) Color.Black else Color.White
    
    return if (isAmoled) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryColor.copy(alpha = 0.2f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor,
            tertiary = primaryColor,
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF0D0D0D)
        )
    } else if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryColor.copy(alpha = 0.3f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor,
            tertiary = primaryColor,
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
            surfaceVariant = Color(0xFF2C2C2C)
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = onPrimaryColor,
            primaryContainer = primaryColor.copy(alpha = 0.2f),
            onPrimaryContainer = primaryColor,
            secondary = primaryColor,
            tertiary = primaryColor,
            background = Color.White,
            surface = Color.White,
            surfaceVariant = Color(0xFFEDEDED)
        )
    }
}

@Composable
fun PreambleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val customColor by ThemePreferences.themeColor.collectAsState()
    val themeMode by ThemePreferences.themeMode.collectAsState()

    val isAmoled = themeMode == ThemePreferences.ThemeMode.AMOLED
    val useDarkTheme = when (themeMode) {
        ThemePreferences.ThemeMode.SYSTEM -> darkTheme
        ThemePreferences.ThemeMode.LIGHT -> false
        ThemePreferences.ThemeMode.DARK -> true
        ThemePreferences.ThemeMode.AMOLED -> true
    }

    val colorScheme = if (customColor != null) {
        generateCustomColorScheme(customColor!!, useDarkTheme, isAmoled)
    } else {
        when {
            isAmoled -> AmoledDarkColorScheme
            useDarkTheme -> MonochromeDarkColorScheme
            else -> MonochromeLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}