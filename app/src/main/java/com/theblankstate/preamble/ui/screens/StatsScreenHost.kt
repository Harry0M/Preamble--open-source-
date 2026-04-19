package com.theblankstate.preamble.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.viewmodel.StatsState

private val StatsTweaksSaver: Saver<StatsTweaks, Any> = Saver(
    save = { t ->
        listOf(
            (t.accent.value shr 32).toLong(),
            t.theme.name,
            t.density.name
        )
    },
    restore = { raw ->
        val list = raw as List<*>
        StatsTweaks(
            accent = Color((list[0] as Long).toULong() shl 32),
            theme = StatsTheme.valueOf(list[1] as String),
            density = StatsDensity.valueOf(list[2] as String),
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreenHost(
    statsState: StatsState,
    onRefreshStats: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var tweaks by rememberSaveable(stateSaver = StatsTweaksSaver) {
        mutableStateOf(StatsTweaks())
    }
    var range by rememberSaveable { mutableStateOf(StatsRange.MONTH) }
    var showTweaks by rememberSaveable { mutableStateOf(false) }

    val surface = if (tweaks.theme == StatsTheme.DARK) Color(0xFF0E0E0E) else Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
    ) {
        StatsEditorialScreen(
            statsState = statsState,
            tweaks = tweaks,
            onOpenTweaks = {
                showTweaks = true
                onRefreshStats?.invoke()
            },
            range = range,
            onRangeChange = { range = it },
            modifier = Modifier.fillMaxSize()
        )

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

        TweakGroup(label = "Canvas theme", fg = fg, fgMuted = fgMuted) {
            TweakSegments(
                options = listOf(
                    "Light" to StatsTheme.LIGHT,
                    "Dark" to StatsTheme.DARK,
                ),
                selected = tweaks.theme,
                onSelect = { onChange(tweaks.copy(theme = it)) },
                chipBg = chipBg,
                dark = dark,
                fgMuted = fgMuted
            )
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
