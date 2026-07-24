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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    val variant = runCatching { StatsVariant.valueOf(p.getString(K_VARIANT, null) ?: "RIBBON") }
        .getOrElse { StatsVariant.RIBBON }
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
    onOpenRecap: (() -> Unit)? = null,
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
    var showHowItWorks by rememberSaveable { mutableStateOf(false) }
    var deepDive by rememberSaveable(stateSaver = DeepDiveSaver) { mutableStateOf<StatsCategory?>(null) }

    val gatedRangeChange: (StatsRange) -> Unit = { r -> range = r }
    val gatedDeepDive: (StatsCategory) -> Unit = { c ->
        if (c != StatsCategory.BESTS && c != StatsCategory.WEEKLY) {
            deepDive = c
        }
    }

    LaunchedEffect(tweaks) { saveTweaks(ctx, tweaks) }

    val surface = if (tweaks.theme == StatsTheme.DARK) Color(0xFF0E0E0E) else Color.White

    // Recap day gating
    val isRecapDay = remember { com.theblankstate.preamble.notification.RecapDayManager.isRecapDayToday(ctx) }
    val effectiveDay = remember { com.theblankstate.preamble.notification.RecapDayManager.getEffectiveDay(ctx) }
    val recapDayLabel = remember { com.theblankstate.preamble.notification.RecapDayManager.dayShortLabel(effectiveDay) }
    val recapDayFullLabel = remember { com.theblankstate.preamble.notification.RecapDayManager.dayLabel(effectiveDay) }
    var showRecapLockedMessage by remember { mutableStateOf(false) }

    val gatedRecap: (() -> Unit)? = if (onOpenRecap != null) {
        {
            if (isRecapDay) {
                onOpenRecap()
            } else {
                showRecapLockedMessage = true
            }
        }
    } else null

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
                    onOpenRecap = gatedRecap,
                    recapDayLabel = recapDayLabel,
                    isRecapDay = isRecapDay,
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
                    onOpenRecap = gatedRecap,
                    recapDayLabel = recapDayLabel,
                    isRecapDay = isRecapDay,
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
                    onOpenHowItWorks = { showHowItWorks = true },
                    onOpenRecap = gatedRecap,
                    recapDayLabel = recapDayLabel,
                    isRecapDay = isRecapDay,
                    range = range,
                    onRangeChange = gatedRangeChange,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Locked recap message
        if (showRecapLockedMessage) {
            val dark = tweaks.theme == StatsTheme.DARK
            val msgBg = if (dark) Color(0xFF2A2A2A) else Color(0xFF333333)
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(msgBg)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        "Weekly recap unlocks every $recapDayFullLabel",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            LaunchedEffect(showRecapLockedMessage) {
                kotlinx.coroutines.delay(2500)
                showRecapLockedMessage = false
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

        if (showHowItWorks) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showHowItWorks = false },
                sheetState = sheetState,
                containerColor = if (tweaks.theme == StatsTheme.DARK) Color(0xFF161616) else Color.White,
            ) {
                HowItWorksPanel(
                    dark = tweaks.theme == StatsTheme.DARK,
                    accent = tweaks.accent
                )
            }
        }
    }
}

@Composable
private fun HowItWorksPanel(dark: Boolean, accent: Color) {
    val fg = if (dark) Color.White else Color.Black
    val fgMuted = if (dark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.55f)
    val hair = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "HOW STATS WORK",
            color = fgMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(14.dp))

        val formulas = remember {
            listOf(
                "Productivity Score (0–100)" to "Blend of task completions (40%), streak momentum (20%), focus timer ratio (20%), and consistency stability (20%).",
                "Streak Calculation" to "Consecutive active days with at least 1 completed task. Streak updates after midnight.",
                "Consistency Score" to "Output stability calculated over the last 30 days based on daily task completion variance.",
                "Peak Hours Detection" to "Histogram of task completion timestamps aggregated by hour of day (local time).",
                "Focus Time Tracking" to "Sum of timer session durations across tasks, habits, and standalone focus periods.",
                "Momentum EMA" to "30-day exponential moving average of daily completions with smoothing factor alpha = 0.3.",
                "Rollover Health Index" to "Ratio of cleared vs pending rolled-over tasks, weighted by pending age in days."
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            formulas.forEachIndexed { idx, pair ->
                val (title, desc) = pair
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(title, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(desc, color = fgMuted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                if (idx < formulas.lastIndex) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(hair))
                }
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

        Spacer(Modifier.height(18.dp))

        // Recap day picker
        val ctx = LocalContext.current
        var recapSelectedDay by remember {
            mutableStateOf(com.theblankstate.preamble.notification.RecapDayManager.getSelectedDay(ctx))
        }
        val recapHasPending = remember {
            com.theblankstate.preamble.notification.RecapDayManager.hasPendingChange(ctx)
        }

        TweakGroup(label = "Recap day", fg = fg, fgMuted = fgMuted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(chipBg)
                    .padding(3.dp)
            ) {
                com.theblankstate.preamble.notification.RecapDayManager.SELECTABLE_DAYS.forEach { day ->
                    val active = day == recapSelectedDay
                    val bg = if (active) (if (dark) Color.White else Color.Black) else Color.Transparent
                    val fgColor = if (active) (if (dark) Color.Black else Color.White) else fgMuted
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(bg)
                            .clickable {
                                recapSelectedDay = day
                                com.theblankstate.preamble.notification.RecapDayManager.setDay(ctx, day)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            com.theblankstate.preamble.notification.RecapDayManager.dayShortLabel(day).first().toString(),
                            color = fgColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (recapHasPending) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Changes next week",
                    color = fgMuted,
                    fontSize = 11.sp,
                )
            }
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
