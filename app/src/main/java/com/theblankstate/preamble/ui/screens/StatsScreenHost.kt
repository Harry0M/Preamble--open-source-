package com.theblankstate.preamble.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ---------- persistence ---------- */

private const val PREFS = "preamble_stats_tweaks"
private const val K_ACCENT = "accent"
private const val K_THEME = "theme"
private const val K_DENSITY = "density"
private const val K_VARIANT = "variant"

private fun loadTweaks(ctx: Context): StatsTweaks {
    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val accentLong = p.getLong(K_ACCENT, Long.MIN_VALUE)
    val accent = if (accentLong == Long.MIN_VALUE) AccentPalette.last().color
        else Color((accentLong.toULong()) shl 32)
    val theme = runCatching { StatsTheme.valueOf(p.getString(K_THEME, null) ?: "LIGHT") }
        .getOrElse { StatsTheme.LIGHT }
    val density = runCatching { StatsDensity.valueOf(p.getString(K_DENSITY, null) ?: "COMPACT") }
        .getOrElse { StatsDensity.COMPACT }
    val variant = runCatching { StatsVariant.valueOf(p.getString(K_VARIANT, null) ?: "EDITORIAL") }
        .getOrElse { StatsVariant.EDITORIAL }
    return StatsTweaks(variant = variant, accent = accent, theme = theme, density = density)
}

private fun saveTweaks(ctx: Context, t: StatsTweaks) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putLong(K_ACCENT, (t.accent.value shr 32).toLong())
        .putString(K_THEME, t.theme.name)
        .putString(K_DENSITY, t.density.name)
        .putString(K_VARIANT, t.variant.name)
        .apply()
}

/* ---------- deep-dive nav context (so variants can request drill-in) ---------- */

val LocalStatsDeepDive = compositionLocalOf<((StatsCategory) -> Unit)?> { null }

private val DeepDiveSaver: Saver<StatsCategory?, Any> = Saver(
    save = { it?.name ?: "" },
    restore = { raw ->
        val s = raw as String
        if (s.isBlank()) null else runCatching { StatsCategory.valueOf(s) }.getOrNull()
    }
)

/* ---------- host ---------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreenHost(
    statsState: com.theblankstate.preamble.viewmodel.StatsState,
    onRefreshStats: (() -> Unit)? = null,
    onOpenWrapped: (() -> Unit)? = null,
    onFeatureLocked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val appAccent = com.theblankstate.preamble.ui.theme.LocalAccentColor.current
    val appIsDark = androidx.compose.foundation.isSystemInDarkTheme().let { sysDark ->
        val mode = com.theblankstate.preamble.ui.theme.ThemePreferences.themeMode.collectAsState().value
        when (mode) {
            com.theblankstate.preamble.ui.theme.ThemePreferences.ThemeMode.DARK,
            com.theblankstate.preamble.ui.theme.ThemePreferences.ThemeMode.AMOLED -> true
            com.theblankstate.preamble.ui.theme.ThemePreferences.ThemeMode.LIGHT -> false
            com.theblankstate.preamble.ui.theme.ThemePreferences.ThemeMode.SYSTEM -> sysDark
        }
    }
    val loaded = remember { loadTweaks(ctx) }
    // Sync stats accent + dark/light with the app theme; user can override from tweaks panel
    var tweaks by remember(appIsDark, appAccent) {
        mutableStateOf(loaded.copy(
            theme = if (appIsDark) StatsTheme.DARK else StatsTheme.LIGHT,
            accent = appAccent
        ))
    }
    var range by rememberSaveable { mutableStateOf(StatsRange.MONTH) }
    var showTweaks by rememberSaveable { mutableStateOf(false) }
    var deepDive by rememberSaveable(stateSaver = DeepDiveSaver) { mutableStateOf<StatsCategory?>(null) }

    val gatedRangeChange: (StatsRange) -> Unit = { r -> range = r }
    val gatedDeepDive: (StatsCategory) -> Unit = { c -> deepDive = c }

    LaunchedEffect(tweaks) { saveTweaks(ctx, tweaks) }

    val surface = if (tweaks.theme == StatsTheme.DARK) Color(0xFF0E0E0E) else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        CompositionLocalProvider(LocalStatsDeepDive provides gatedDeepDive) {
            when (tweaks.variant) {
                StatsVariant.EDITORIAL -> StatsEditorialScreen(
                    statsState = statsState,
                    tweaks = tweaks,
                    onOpenTweaks = {
                        showTweaks = true
                        onRefreshStats?.invoke()
                    },
                    onOpenWrapped = onOpenWrapped,
                    range = range,
                    onRangeChange = gatedRangeChange,
                    modifier = Modifier.fillMaxSize()
                )
                StatsVariant.CAPSULE -> StatsCapsuleScreen(
                    statsState = statsState,
                    tweaks = tweaks,
                    onOpenTweaks = {
                        showTweaks = true
                        onRefreshStats?.invoke()
                    },
                    onOpenWrapped = onOpenWrapped,
                    range = range,
                    onRangeChange = gatedRangeChange,
                    modifier = Modifier.fillMaxSize()
                )
                StatsVariant.RIBBON -> StatsRibbonScreen(
                    statsState = statsState,
                    tweaks = tweaks,
                    onOpenTweaks = {
                        showTweaks = true
                        onRefreshStats?.invoke()
                    },
                    onOpenWrapped = onOpenWrapped,
                    range = range,
                    onRangeChange = gatedRangeChange,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        BackHandler(enabled = deepDive != null || showTweaks) {
            when {
                showTweaks -> showTweaks = false
                deepDive != null -> deepDive = null
            }
        }

        AnimatedVisibility(
            visible = deepDive != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            deepDive?.let {
                StatsDeepDiveScreen(
                    category = it,
                    statsState = statsState,
                    tweaks = tweaks,
                    onBack = { deepDive = null },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showTweaks) {
            val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showTweaks = false },
                sheetState = sheet,
                containerColor = if (tweaks.theme == StatsTheme.DARK) Color(0xFF161616) else Color.White,
            ) {
                TweaksPanel(
                    tweaks = tweaks,
                    onChange = { tweaks = it }
                )
            }
        }
    }
}

@Composable
private fun TweaksPanel(
    tweaks: StatsTweaks,
    onChange: (StatsTweaks) -> Unit
) {
    val dark = tweaks.theme == StatsTheme.DARK
    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f)
    val chipBg = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            "TWEAKS",
            color = fgMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(16.dp))

        TweakGroup(label = "Variant", fg = fg, fgMuted = fgMuted) {
            TweakSegments(
                options = listOf(
                    "Editorial" to StatsVariant.EDITORIAL,
                    "Capsule" to StatsVariant.CAPSULE,
                    "Ribbon" to StatsVariant.RIBBON,
                ),
                selected = tweaks.variant,
                onSelect = { onChange(tweaks.copy(variant = it)) },
                chipBg = chipBg,
                dark = dark,
                fgMuted = fgMuted
            )
        }

        Spacer(Modifier.height(18.dp))

        TweakGroup(label = "Accent color", fg = fg, fgMuted = fgMuted) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccentPalette.forEach { sw ->
                    val active = sw.color == tweaks.accent
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(sw.color)
                            .border(
                                width = if (active) 2.dp else 0.dp,
                                color = if (active) fg else Color.Transparent,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable { onChange(tweaks.copy(accent = sw.color)) }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        TweakGroup(label = "Density", fg = fg, fgMuted = fgMuted) {
            TweakSegments(
                options = listOf(
                    "Compact" to StatsDensity.COMPACT,
                    "Cozy" to StatsDensity.COZY,
                    "Airy" to StatsDensity.AIRY,
                ),
                selected = tweaks.density,
                onSelect = { onChange(tweaks.copy(density = it)) },
                chipBg = chipBg,
                dark = dark,
                fgMuted = fgMuted
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TweakGroup(
    label: String,
    fg: Color,
    fgMuted: Color,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            label,
            color = fgMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun <T> TweakSegments(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    chipBg: Color,
    dark: Boolean,
    fgMuted: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(chipBg)
            .padding(3.dp)
    ) {
        options.forEach { (label, value) ->
            val active = value == selected
            val bg = if (active) (if (dark) Color.White else Color.Black) else Color.Transparent
            val fgColor = if (active) (if (dark) Color.Black else Color.White) else fgMuted
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = fgColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
