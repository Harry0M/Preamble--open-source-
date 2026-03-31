package com.theblankstate.preamble.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.theblankstate.preamble.R
import com.theblankstate.preamble.auth.AuthManager
import com.theblankstate.preamble.ui.theme.ThemePreferences
import com.theblankstate.preamble.ui.theme.generateCustomColorScheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 7 })
    
    // Permission tracking
    val hasAudioPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    
    var audioPermGranted by remember { mutableStateOf(hasAudioPerm) }
    var notifPermGranted by remember { mutableStateOf(hasNotifPerm) }

    // Privacy policy tracking
    var termsAccepted by remember { mutableStateOf(false) }

    // Prepare Coil
    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

    // Determine if "Next" button is enabled
    val isNextEnabled = when (pagerState.currentPage) {
        1 -> notifPermGranted // Screen 2: Notifications
        2 -> audioPermGranted // Screen 3: Voice
        5 -> termsAccepted // Screen 6: Privacy
        else -> true
    }

    // Force Light theme just for the Onboarding UI skeleton
    MaterialTheme(
        colorScheme = lightColorScheme(
            background = Color.White,
            surface = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            primary = Color.Black,
            onPrimary = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = isNextEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp) // leave space for bottom bar
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val alphaAnim = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 1f)
                val scaleAnim = 1f - (pageOffset.absoluteValue * 0.1f).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = alphaAnim
                            scaleX = scaleAnim
                            scaleY = scaleAnim
                            translationX = pageOffset * 200f
                        }
                ) {
                    when (page) {
                        0 -> WelcomePage(context, imageLoader)
                        1 -> NotificationPage(notifPermGranted, onPermissionsChanged = { notifPermGranted = it })
                        2 -> VoicePage(audioPermGranted, onPermissionsChanged = { audioPermGranted = it })
                        3 -> ThemeSelectionPage()
                        4 -> SyncPage()
                        5 -> PrivacyPolicyPage(termsAccepted) { termsAccepted = it }
                        6 -> LoginPage(context, imageLoader, onComplete)
                    }
                }
            }

            // Bottom Navigation Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                // expanding dot indicators
                if (pagerState.currentPage < 6) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(7) { iteration ->
                            val isSelected = pagerState.currentPage == iteration
                            val width by animateDpAsState(
                                targetValue = if (isSelected) 32.dp else 8.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                label = "indicator_width"
                            )
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .height(8.dp)
                                    .width(width)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }

                    // Next/Skip row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onComplete) {
                            Text("Skip", color = Color.Gray, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    val nextPage = (pagerState.currentPage + 1).coerceAtMost(6)
                                    pagerState.animateScrollToPage(nextPage)
                                }
                            },
                            enabled = isNextEnabled,
                            shape = CircleShape,
                            modifier = Modifier.height(48.dp).widthIn(min = 120.dp)
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureTag(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, baseColor: Color = Color.Gray) {
    SuggestionChip(
        onClick = { },
        label = { Text(text, fontWeight = FontWeight.SemiBold, color = baseColor) },
        icon = { if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = baseColor) },
        shape = CircleShape,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = baseColor.copy(alpha = 0.12f)
        ),
        border = null
    )
}

@Composable
fun WelcomePage(context: Context, imageLoader: ImageLoader) {
    val infiniteTransition = rememberInfiniteTransition()
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("android.resource://${context.packageName}/${R.raw.doodle_survey}")
                .crossfade(true).build(),
            contentDescription = "Welcome",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(0.5f)
                .fillMaxWidth()
                .padding(16.dp)
                .graphicsLayer { translationY = floatAnim }
        )
        Column(modifier = Modifier.weight(0.5f).fillMaxWidth()) {
            Text(
                buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color(0xFF2196F3))) {
                        append("Clear mind.\n")
                    }
                    append("Empty inbox.")
                },
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                lineHeight = 40.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                buildAnnotatedString {
                    append("Your brain is for generating ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) { append("ideas") }
                    append(", not holding them. Dump your thoughts into Preamble instantly, and focus on what actually ")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) { append("matters") }
                    append(" right now.")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.DarkGray,
                lineHeight = 24.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureTag("Mental Clarity", Icons.Default.AutoAwesome, Color(0xFF9C27B0))
                FeatureTag("Instant Capture", Icons.Default.Bolt, Color(0xFFFF9800))
            }
        }
    }
}

@Composable
fun NotificationPage(isGranted: Boolean, onPermissionsChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        val granted = map.values.all { it }
        onPermissionsChanged(granted)
        if (!granted) Toast.makeText(context, "Permissions required for features", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val floatAnim by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        // --- Notification Section ---
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.95f).height(64.dp).graphicsLayer { translationY = floatAnim },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preamble", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Text("Tap to add a task...", style = MaterialTheme.typography.bodyMedium, color = Color.Black)
                    }
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center) {
                        Text("3", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        }
        
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color(0xFF2196F3))) { append("Always on.\n") }
                append("Never in the way.")
            },
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            lineHeight = 40.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            "Keep your tasks exactly one swipe away. View your pending count and quick-add new ideas instantly without unlocking your flow.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.DarkGray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 48.dp)) {
            FeatureTag("Quick Add", Icons.Default.AddCircleOutline, Color(0xFF2196F3))
            FeatureTag("Pending Count", Icons.Default.Filter1, Color(0xFF9C27B0))
        }

        if (isGranted) {
            Button(
                onClick = { },
                enabled = false,
                colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFFE8F5E9), disabledContentColor = Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp).padding(end=8.dp))
                Text("Notifications Granted", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = {
                    val req = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        req.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (req.isNotEmpty()) {
                        launcher.launch(req.toTypedArray())
                    } else {
                        onPermissionsChanged(true)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape
            ) {
                Text("Enable Notifications", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun VoicePage(isGranted: Boolean, onPermissionsChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        val granted = map.values.all { it }
        onPermissionsChanged(granted)
        if (!granted) Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val floatAnim by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        // --- Voice Access Section ---
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp).graphicsLayer { translationY = floatAnim },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFF2196F3).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("\"Remind me to call...\"", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2196F3), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
        }

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color(0xFFE91E63))) { append("Hands free.\n") }
                append("Zero friction.")
            },
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            lineHeight = 40.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            "Speak your mind. Let our intelligent on-device AI handle the heavy lifting of extracting due dates and priorities automatically.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.DarkGray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 48.dp)) {
            FeatureTag("Voice Capture", Icons.Default.Mic, Color(0xFFE91E63))
            FeatureTag("Offline AI Parsing", Icons.Default.AutoAwesome, Color(0xFF4CAF50))
        }

        if (isGranted) {
            Button(
                onClick = { },
                enabled = false,
                colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFFE8F5E9), disabledContentColor = Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp).padding(end=8.dp))
                Text("Microphone Granted", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = {
                    launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape
            ) {
                Text("Enable Microphone", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ThemeSelectionPage() {
    val context = LocalContext.current
    val currentMode by ThemePreferences.themeMode.collectAsState()
    val customColor by ThemePreferences.themeColor.collectAsState()

    val isAmoled = currentMode == ThemePreferences.ThemeMode.AMOLED
    val useDarkTheme = when (currentMode) {
        ThemePreferences.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemePreferences.ThemeMode.LIGHT -> false
        ThemePreferences.ThemeMode.DARK -> true
        ThemePreferences.ThemeMode.AMOLED -> true
    }
    val defaultColor = Color(0xFF2196F3)
    val activeColor = customColor ?: defaultColor
    val previewScheme = generateCustomColorScheme(activeColor, useDarkTheme, isAmoled)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val floatAnim by infiniteTransition.animateFloat(
            initialValue = -4f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        MaterialTheme(colorScheme = previewScheme) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).graphicsLayer { translationY = floatAnim },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Design the future", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Tomorrow • High Priority", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            buildAnnotatedString {
                append("Make it ")
                withStyle(style = SpanStyle(color = activeColor)) {
                    append("yours.")
                }
            },
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            "Your tasks, your aesthetic. Pick a theme mode and an accent color that puts you in the mood to get things done.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.DarkGray,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Mode Selector
        Text("Theme Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ThemePreferences.ThemeMode.values().forEach { mode ->
                val display = if (mode == ThemePreferences.ThemeMode.AMOLED) "AMOLED" else mode.name.lowercase().replaceFirstChar { it.uppercase() }
                FilterChip(
                    selected = currentMode == mode,
                    onClick = { ThemePreferences.setThemeMode(context, mode) },
                    label = { Text(display) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = activeColor.copy(alpha=0.15f),
                        selectedLabelColor = activeColor
                    ),
                    border = null
                )
            }
        }

        // Color Spectrum Slider
        Text("Accent Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        var hue by remember { mutableStateOf(if (customColor != null) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(String.format("#%06X", 0xFFFFFF and customColor!!.value.toInt())), hsv)
            hsv[0]
        } else 210f) }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        hue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                        ThemePreferences.setColor(context, Color.hsv(hue, 1f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        ThemePreferences.setColor(context, Color.hsv(hue, 1f, 1f))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                )
                val cursorX = (hue / 360f) * size.width
                val safeCursor = cursorX.coerceIn(20f, size.width - 20f)
                drawCircle(color = Color.White, radius = 20.dp.toPx(), center = Offset(safeCursor, size.height / 2))
                drawCircle(color = Color.hsv(hue, 1f, 1f), radius = 16.dp.toPx(), center = Offset(safeCursor, size.height / 2))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        val presetRow = listOf(Color(0xFF2196F3), Color(0xFFF44336), Color(0xFF9C27B0), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFE91E63))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            presetRow.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { ThemePreferences.setColor(context, color) }
                        .border(
                            width = if (customColor == color) 3.dp else 0.dp,
                            color = if (customColor == color) Color.Black.copy(alpha = 0.5f) else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    if (customColor == color) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SyncPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val googleBlue = Color(0xFF4285F4)
        val googleRed = Color(0xFFEA4335)
        val googleYellow = Color(0xFFFBBC05)
        val googleGreen = Color(0xFF34A853)

        val infiniteTransition = rememberInfiniteTransition()
        val floatAnim by infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp).padding(bottom = 24.dp).graphicsLayer { translationY = floatAnim },
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(80.dp), tint = googleBlue)
                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                Icon(Icons.Default.TaskAlt, contentDescription = null, modifier = Modifier.size(80.dp), tint = googleBlue)
            }
        }
        
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                buildAnnotatedString {
                    withStyle(style = SpanStyle(color = googleBlue)) {
                        append("Stay in\n")
                    }
                    append("Sync.")
                },
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                lineHeight = 40.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                "Connect with Google to seamlessly view your Calendar events and pull in Tasks. Preamble remains offline-first.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.DarkGray,
                lineHeight = 24.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            val pros1 = listOf("Google Calendar" to Icons.Default.Event, "Google Tasks" to Icons.Default.Task)
            val pros2 = listOf("Offline-First" to Icons.Default.CloudOff, "Auto-Sync" to Icons.Default.Sync)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                FeatureTag(pros1[0].first, pros1[0].second, googleBlue)
                FeatureTag(pros1[1].first, pros1[1].second, googleBlue)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureTag(pros2[0].first, pros2[0].second, googleYellow)
                FeatureTag(pros2[1].first, pros2[1].second, googleGreen)
            }
        }
    }
}

@Composable
fun PrivacyPolicyPage(accepted: Boolean, onAccept: (Boolean) -> Unit) {
    val privacyText = """
# Privacy Policy
Last updated: March 2026

Preamble is a privacy-first app. Your data never leaves your device. We collect nothing — zero analytics, zero tracking, zero network calls.

## 1. Data Collection
Preamble does not collect, store, transmit, or share any personal data whatsoever. The app operates entirely on your device with no internet connectivity required unless syncing is manually enabled.

## 2. Data Storage
All your data is stored locally on your device using Room Database (SQLite). This data is never transmitted to any server or API except Google's official endpoints when you explicitly enable sync.

## 3. Permissions
Preamble requests: Notifications, Alarms, and Microphone. These are processed strictly locally using Android's built-in managers.

## 4. Voice Processing
Your voice is parsed entirely offline on your device via built-in NLP. No voice recordings or transcripts are ever gathered or shipped out.

## 5. Account Syncing
If you choose to sign in with Google, we request the bare minimum scopes to fetch and sync tasks and calendar events. We do not store your tokens on any servers; they never leave your device.
""".trimIndent()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color(0xFF4CAF50))) { append("Your data.") }
                append(" Your rules.")
            },
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Privacy Policy",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0)),
            shape = RoundedCornerShape(16.dp)
        ) {
            SelectionContainer {
                Text(
                    text = privacyText,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())
                )
            }
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onAccept(!accepted) }.padding(8.dp)
        ) {
            Checkbox(checked = accepted, onCheckedChange = { onAccept(it) })
            Spacer(modifier = Modifier.width(8.dp))
            Text("I have read and agree to the Privacy Policy and Terms.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LoginPage(context: Context, imageLoader: ImageLoader, onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val floatAnim by infiniteTransition.animateFloat(
            initialValue = -10f,
            targetValue = 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("android.resource://${context.packageName}/${R.raw.doodle_logged_in}")
                .crossfade(true).build(),
            contentDescription = "Login",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(0.5f).fillMaxWidth().padding(16.dp).graphicsLayer { translationY = floatAnim }
        )
        
        Column(modifier = Modifier.weight(0.5f).fillMaxWidth()) {
            Text(
                buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color(0xFF4285F4))) { append("One tap to\n") }
                    append("sync it all.")
                },
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                lineHeight = 40.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                "Sign in with Google to enable automatic cloud sync for Tasks & Calendar. Zero lock-in, effortless peace of mind.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.DarkGray,
                lineHeight = 24.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        val result = AuthManager.signInWithGoogle(context)
                        isLoading = false
                        if (result.isSuccess) onComplete()
                        else Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 12.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            
            OutlinedButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape
            ) {
                Text("Start Offline (Skip)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Gray)
            }
        }
    }
}
