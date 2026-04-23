package com.theblankstate.preamble.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/**
 * A composition-local that exposes a guaranteed-visible accent color.
 *
 * For vivid themes it equals `primary`. For neutral themes (pure white/black/mono)
 * it falls back to a soft accent so stats charts, karma badges etc. remain legible.
 */
val LocalAccentColor = compositionLocalOf { Color(0xFF6750A4) }   // Material default purple

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
    val lum = primaryColor.luminance()
    val isPale = lum > 0.70f
    val isLight = lum > 0.5f
    val onPrimaryColor = if (isLight) Color.Black else Color.White

    // For pale colors in light mode: darken primary for readability,
    // keep original pale as container/surface tint
    val effectivePrimary = if (isPale && !darkTheme) {
        lerp(primaryColor, Color.Black, 0.55f)  // darkened hue-preserving version
    } else {
        primaryColor
    }
    val effectiveOnPrimary = if (effectivePrimary.luminance() > 0.5f) Color.Black else Color.White
    
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
    } else if (isPale) {
        // Pale light theme — readable primary + soft containers
        lightColorScheme(
            primary = effectivePrimary,
            onPrimary = effectiveOnPrimary,
            primaryContainer = primaryColor,                        // original pale for cards
            onPrimaryContainer = Color.Black,
            secondary = effectivePrimary,
            onSecondary = effectiveOnPrimary,
            tertiary = effectivePrimary,
            onTertiary = effectiveOnPrimary,
            tertiaryContainer = primaryColor,
            onTertiaryContainer = Color.Black,
            background = Color.White,
            onBackground = Color.Black,
            surface = Color.White,
            onSurface = Color.Black,
            surfaceVariant = lerp(primaryColor, Color.White, 0.3f), // subtle pale tint
            onSurfaceVariant = Color.DarkGray,
            outline = lerp(primaryColor, Color.Gray, 0.5f)
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

/**
 * Derives the accent color for stats and mandatory accent areas.
 *
 * - For pale themes: primaryContainer holds the original pale color → use it as accent
 * - For vivid themes: primary is usable directly
 * - For neutral (B&W): try tertiary, then Material default purple
 */
private fun deriveAccent(scheme: androidx.compose.material3.ColorScheme): Color {
    val container = scheme.primaryContainer
    val containerLum = container.luminance()
    // Pale theme: primaryContainer IS the original pale color (luminance > 0.70)
    // Use it directly so stats accent = the actual pale chosen
    if (containerLum > 0.70f) return container

    val primary = scheme.primary
    val lum = primary.luminance()
    // Vivid primary → use it
    if (lum in 0.05f..0.90f) return primary
    // Try tertiary
    val tLum = scheme.tertiary.luminance()
    if (tLum in 0.05f..0.90f) return scheme.tertiary
    // Absolute fallback
    return Color(0xFF6750A4)
}

@Composable
fun PreambleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val customColor by ThemePreferences.themeColor.collectAsState()
    val themeMode by ThemePreferences.themeMode.collectAsState()
    val materialYou by ThemePreferences.materialYou.collectAsState()

    val isAmoled = themeMode == ThemePreferences.ThemeMode.AMOLED
    val useDarkTheme = when (themeMode) {
        ThemePreferences.ThemeMode.SYSTEM -> darkTheme
        ThemePreferences.ThemeMode.LIGHT -> false
        ThemePreferences.ThemeMode.DARK -> true
        ThemePreferences.ThemeMode.AMOLED -> true
    }

    val context = LocalContext.current

    val colorScheme = when {
        // Material You — dynamic wallpaper colors (Android 12+)
        materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Custom user color
        customColor != null -> {
            generateCustomColorScheme(customColor!!, useDarkTheme, isAmoled)
        }
        // Monochrome fallback
        else -> when {
            isAmoled -> AmoledDarkColorScheme
            useDarkTheme -> MonochromeDarkColorScheme
            else -> MonochromeLightColorScheme
        }
    }

    val accent = deriveAccent(colorScheme)

    CompositionLocalProvider(LocalAccentColor provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}