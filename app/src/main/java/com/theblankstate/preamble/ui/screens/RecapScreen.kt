package com.theblankstate.preamble.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.viewmodel.StatsState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val SLIDE_MS = 4500

/* ───────── slide data ───────── */

private data class RecapSlide(
    val id: String,
    val kicker: String,
    val title: String,
    val value: String,
    val sub: String,
    val accent: Boolean = false,
)

private fun buildSlides(stats: StatsState, name: String?): List<RecapSlide> {
    val weekNum = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    val greeting = name?.takeIf { it.isNotBlank() }?.let { "$it," } ?: "Your"

    val tasksThisWeek = stats.thisWeekCompleted
    val growthPct = (stats.weekOverWeekGrowth * 100).toInt()
    val growthLabel = when {
        growthPct > 0 -> "+$growthPct% vs last week"
        growthPct < 0 -> "$growthPct% vs last week"
        else -> "Steady as last week"
    }

    val bestDay = stats.peakDayOfWeek.ifBlank { "—" }
    val topTag = stats.mostActiveTag ?: "uncategorized"

    val weekFocus = stats.weeklyFocusData.sumOf { it.second }
    val focusHours = weekFocus / 60
    val focusMins = weekFocus % 60
    val focusBig = if (focusHours > 0) "${focusHours}h ${focusMins}m" else "${focusMins}m"

    val streak = stats.streak
    val longestStreak = stats.longestStreak
    val score = if (stats.productivityScore > 0) stats.productivityScore else 70

    return listOf(
        RecapSlide(
            id = "header",
            kicker = "WEEK $weekNum",
            title = "$greeting\nrecap.",
            value = "",
            sub = "Tap anywhere to advance.",
        ),
        RecapSlide(
            id = "tasks",
            kicker = "TASKS CRUSHED",
            title = tasksThisWeek.toString(),
            value = tasksThisWeek.toString(),
            sub = growthLabel,
            accent = true,
        ),
        RecapSlide(
            id = "peak",
            kicker = "PEAK DAY",
            title = bestDay,
            value = bestDay,
            sub = "Most active tag: #$topTag",
        ),
        RecapSlide(
            id = "focus",
            kicker = "DEEP FOCUS",
            title = focusBig,
            value = focusBig,
            sub = if (weekFocus > 0) "of focus time this week" else "No focus sessions yet",
        ),
        RecapSlide(
            id = "streak",
            kicker = "STREAK",
            title = "$streak days",
            value = "$streak",
            sub = if (longestStreak > streak) "Personal best: $longestStreak" else "Longest yet. Keep going.",
            accent = true,
        ),
        RecapSlide(
            id = "score",
            kicker = "PRODUCTIVITY SCORE",
            title = "$score / 100",
            value = score.toString(),
            sub = "Productivity index for the week",
        ),
    )
}

/* ───────── main screen — story-style ───────── */

@Composable
fun RecapScreen(
    statsState: StatsState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val isDark = isSystemInDarkTheme()
    val surface = if (isDark) Color(0xFF0E0E0E) else Color.White
    val fg = if (isDark) Color.White else Color.Black
    val fgMuted = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.52f)
    val hair = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    val tile = if (isDark) Color(0xFF1A1A1A) else Color(0xFFF4F4F3)
    val accent = com.theblankstate.preamble.ui.theme.LocalAccentColor.current

    val profile = remember { UserProfileStore.load(ctx) }
    val slides = remember(statsState) { buildSlides(statsState, profile.name) }

    // Branded weekly-recap shareable wiring (Requirement 7): project the existing
    // StatsState into a WeeklyRecapSummary consumed by ShareableContentMapper.
    val normalizedPreambleId = remember { UserProfileStore.ensurePreambleId(ctx) }
    val recapSummary = remember(statsState) {
        val weekNum = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
        val perfectDays = statsState.dailyStatsWithDates
            .takeLast(7)
            .count { (_, done, total) -> total > 0 && done >= total }
        com.theblankstate.preamble.share.WeeklyRecapSummary(
            weekLabel = "Week $weekNum",
            tasksCompleted = statsState.thisWeekCompleted,
            perfectDays = perfectDays,
        )
    }
    val recapShareContent = remember(recapSummary) {
        com.theblankstate.preamble.share.ShareableContentMapper.fromWeeklyRecap(recapSummary)
    }

    var index by remember { mutableStateOf(0) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(index) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(SLIDE_MS, easing = LinearEasing)
        )
        if (index < slides.lastIndex) {
            index++
        } else {
            onDismiss()
        }
    }

    val slide = slides[index]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .pointerInput(slides.size) {
                detectTapGestures(
                    onTap = { pos ->
                        val w = size.width.toFloat()
                        if (pos.x < w * 0.35f) {
                            if (index > 0) index -= 1
                            else scope.launch { progress.snapTo(0f) }
                        } else {
                            if (index < slides.lastIndex) index += 1 else onDismiss()
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Progress bars ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                slides.forEachIndexed { i, _ ->
                    val fill = when {
                        i < index -> 1f
                        i == index -> progress.value
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(fg.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fill)
                                .height(2.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(fg)
                        )
                    }
                }
            }

            // ── Top row: kicker + close + share ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    slide.kicker,
                    color = fgMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Branded weekly-recap shareable (Requirement 7): renders the
                    // branded card off-screen and shares it with an invite-link caption.
                    com.theblankstate.preamble.ui.components.ShareMomentChip(
                        content = recapShareContent,
                        normalizedPreambleId = normalizedPreambleId,
                    )
                    // Share this slide
                    if (slide.id != "header") {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(fg.copy(alpha = 0.06f))
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = {
                                        scope.launch {
                                            shareSlideAsImage(ctx, slide, slides, surface, fg, fgMuted, tile, accent, isDark)
                                        }
                                    })
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = fg,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    // Close
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(fg.copy(alpha = 0.06f))
                            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", color = fg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Slide content — clean big text ──
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    (fadeIn(tween(420)) togetherWith fadeOut(tween(220)))
                },
                label = "slide",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) { i ->
                val s = slides[i]
                Column {
                    Text(
                        (i + 1).toString().padStart(2, '0'),
                        color = fgMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        s.title,
                        color = fg,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 66.sp,
                        letterSpacing = (-2.2).sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        s.sub,
                        color = fgMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))



            Text(
                "PREAMBLE · WEEK RECAP",
                color = fgMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 22.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/* ───────── image export: phone-screen-sized card ───────── */

private fun shareSlideAsImage(
    ctx: Context,
    slide: RecapSlide,
    allSlides: List<RecapSlide>,
    surface: Color,
    fg: Color,
    fgMuted: Color,
    tile: Color,
    accent: Color,
    isDark: Boolean,
) {
    // Phone screen size: 1080 x 1920 (9:16)
    val W = 1080
    val H = 1920

    val surfaceArgb = surface.toArgb()
    val fgArgb = fg.toArgb()
    val fgMutedArgb = fgMuted.toArgb()
    val tileArgb = tile.toArgb()
    val accentArgb = accent.toArgb()

    val cardBgArgb = if (slide.accent) accentArgb else tileArgb
    val cardFgArgb = if (slide.accent) android.graphics.Color.BLACK else fgArgb
    val cardFgMutedArgb = if (slide.accent)
        android.graphics.Color.argb(153, 0, 0, 0)
    else
        fgMutedArgb

    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)

    // Background
    c.drawColor(surfaceArgb)

    // Card rect
    val cardLeft = 60f
    val cardTop = H * 0.28f
    val cardRight = W - 60f
    val cardBottom = H * 0.72f
    val cardRadius = 64f

    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBgArgb }
    c.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, cardRadius, cardRadius, cardPaint)

    // Kicker text
    val kickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardFgMutedArgb
        textSize = 28f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.15f
    }
    c.drawText(slide.kicker, cardLeft + 52f, cardTop + 70f, kickerPaint)

    // Title text (big)
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardFgArgb
        textSize = 140f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = -0.04f
    }
    // Handle multi-line title
    val titleLines = slide.title.split("\n")
    var titleY = cardTop + 200f
    titleLines.forEach { line ->
        c.drawText(line, cardLeft + 52f, titleY, titlePaint)
        titleY += 155f
    }

    // Sub text
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cardFgMutedArgb
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    c.drawText(slide.sub, cardLeft + 52f, cardBottom - 60f, subPaint)

    // Branding at bottom
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fgMutedArgb
        textSize = 26f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.2f
        textAlign = Paint.Align.CENTER
    }
    c.drawText("PREAMBLE · WEEK RECAP", W / 2f, H - 80f, brandPaint)

    // Week indicator at top
    val weekNum = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)
    val weekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fgMutedArgb
        textSize = 28f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.15f
    }
    c.drawText("WEEK $weekNum RECAP", 60f, 100f, weekPaint)

    // Progress dots to indicate slide position
    val dotCount = allSlides.size
    val slideIdx = allSlides.indexOfFirst { it.id == slide.id }.coerceAtLeast(0)
    val dotRadius = 8f
    val dotSpacing = 30f
    val dotsWidth = (dotCount * dotRadius * 2) + ((dotCount - 1) * dotSpacing)
    var dotX = (W - dotsWidth) / 2f + dotRadius
    val dotY = H - 140f
    val dotActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fgArgb }
    val dotInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fgMutedArgb }
    repeat(dotCount) { di ->
        c.drawCircle(dotX, dotY, dotRadius, if (di == slideIdx) dotActivePaint else dotInactivePaint)
        dotX += dotRadius * 2 + dotSpacing
    }

    // Save & share
    try {
        val uri = saveBitmapToGallery(ctx, bmp, "preamble_recap_${slide.id}")
        if (uri != null) {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(share, "Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            Toast.makeText(ctx, "Could not save image", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(ctx, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/* ───────── helpers ───────── */

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, name: String): Uri? {
    val filename = "${name}_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Preamble")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return null
    resolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
    }
    return uri
}

