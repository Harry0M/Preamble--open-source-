package com.theblankstate.preamble.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.theblankstate.preamble.R
import com.theblankstate.preamble.auth.AuthManager
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })
    
    val hasAudioPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    
    var audioPermGranted by remember { mutableStateOf(hasAudioPerm) }
    var notifPermGranted by remember { mutableStateOf(hasNotifPerm) }
    var termsAccepted by remember { mutableStateOf(false) }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components { add(SvgDecoder.Factory()) }
            .build()
    }

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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp)
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
                        1 -> NotationsPage(context, imageLoader)
                        2 -> PermissionsPage(context, imageLoader, notifPermGranted, audioPermGranted, { notifPermGranted = it }, { audioPermGranted = it })
                        3 -> SyncAndTermsPage(context, imageLoader, termsAccepted, { termsAccepted = it }, onComplete)
                    }
                }
            }

            // Bottom Navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { iteration ->
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { 
                        scope.launch { pagerState.animateScrollToPage(3) }
                    }) {
                        if (pagerState.currentPage < 3) {
                            Text("Skip", color = Color.Gray, fontWeight = FontWeight.Medium)
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                    }

                    if (pagerState.currentPage < 3) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val nextPage = (pagerState.currentPage + 1).coerceAtMost(3)
                                    pagerState.animateScrollToPage(nextPage)
                                }
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White
                            ),
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
fun FeatureTag(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    SuggestionChip(
        onClick = { },
        label = { Text(text, fontWeight = FontWeight.SemiBold, color = Color.Black) },
        icon = { if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black) },
        shape = CircleShape,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = Color(0xFFF5F5F5)
        ),
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    )
}

@Composable
fun WelcomePage(context: Context, imageLoader: ImageLoader) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("android.resource://${context.packageName}/${R.raw.doodle_croods}")
                .crossfade(true).build(),
            contentDescription = "Welcome",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(0.5f).fillMaxWidth().padding(16.dp)
        )
        Column(modifier = Modifier.weight(0.5f).fillMaxWidth()) {
            Text(
                "Clear mind.\nEmpty inbox.",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                lineHeight = 40.sp,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                "Dump your thoughts instantly, and focus on what actually matters right now.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                lineHeight = 24.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureTag("Mental Clarity", Icons.Default.AutoAwesome)
                FeatureTag("Instant Capture", Icons.Default.Bolt)
            }
        }
    }
}

@Composable
fun NotationsPage(context: Context, imageLoader: ImageLoader) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("android.resource://${context.packageName}/${R.raw.doodle_screen}")
                .crossfade(true).build(),
            contentDescription = "Notations",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(0.4f).fillMaxWidth().padding(16.dp)
        )
        Column(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
            Text(
                "A new way to plan.",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                color = Color.Black,
                lineHeight = 40.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                "Understand the meaning behind our task indicators.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            NotationItem(
                title = "One-Day Task",
                desc = "Won't be accessible tomorrow.",
                type = "solid"
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            NotationItem(
                title = "Active Until Complete",
                desc = "Rolls over to next day until done.",
                type = "half_dotted"
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            NotationItem(
                title = "Recurring Task",
                desc = "Set with a recurrence until a target date.",
                type = "fully_dotted"
            )
        }
    }
}

@Composable
fun NotationItem(title: String, desc: String, type: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(top = 4.dp, end = 16.dp)) {
            when (type) {
                "solid" -> {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(color = Color.Black, radius = size.width / 2)
                    }
                }
                "half_dotted" -> {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawArc(
                            color = Color.Black,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                        )
                        drawArc(
                            color = Color.Black,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                "fully_dotted" -> {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        drawCircle(
                            color = Color.Black,
                            radius = size.width / 2,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                        )
                    }
                }
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
        }
    }
}

@Composable
fun PermissionsPage(
    context: Context,
    imageLoader: ImageLoader,
    notifGranted: Boolean,
    audioGranted: Boolean,
    onNotifChange: (Boolean) -> Unit,
    onAudioChange: (Boolean) -> Unit
) {
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onNotifChange(granted)
        if (!granted) Toast.makeText(context, "Notifications help you stay on track.", Toast.LENGTH_SHORT).show()
    }
    
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onAudioChange(granted)
        if (!granted) Toast.makeText(context, "Microphone needed for voice capture.", Toast.LENGTH_SHORT).show()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("android.resource://${context.packageName}/${R.raw.doodle_privacy}")
                .crossfade(true).build(),
            contentDescription = "Permissions",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(0.4f).fillMaxWidth().padding(16.dp)
        )
        
        Column(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
            Text(
                "Your assistant.",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                color = Color.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                "Enable core features to get the most out of Preamble.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                lineHeight = 24.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            PermissionRow(
                title = "Notifications",
                desc = "Quick-add tasks without opening the app.",
                icon = Icons.Default.Notifications,
                isGranted = notifGranted,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onNotifChange(true)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            PermissionRow(
                title = "Microphone",
                desc = "Capture tasks using your voice instantly.",
                icon = Icons.Default.Mic,
                isGranted = audioGranted,
                onRequest = {
                    audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        }
    }
}

@Composable
fun PermissionRow(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isGranted: Boolean, onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isGranted, onClick = onRequest),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        border = BorderStroke(1.dp, if (isGranted) Color.Black else Color.Transparent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isGranted) Color.Black else Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isGranted) Icons.Default.Check else icon, contentDescription = null, tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun SyncAndTermsPage(
    context: Context,
    imageLoader: ImageLoader,
    termsAccepted: Boolean,
    onAcceptChange: (Boolean) -> Unit,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("android.resource://${context.packageName}/${R.raw.doodle_logged_in}")
                .crossfade(true).build(),
            contentDescription = "Sync",
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(0.4f).fillMaxWidth().padding(16.dp)
        )
        
        Column(modifier = Modifier.weight(0.6f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
            Text(
                "One tap to sync.",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
                color = Color.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                "Sign in with Google to enable automatic cloud sync for Tasks & Calendar, all strictly offline-first.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                lineHeight = 24.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onAcceptChange(!termsAccepted) }.padding(8.dp)
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { onAcceptChange(it) },
                    colors = CheckboxDefaults.colors(checkedColor = Color.Black, uncheckedColor = Color.Gray, checkmarkColor = Color.White)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("I agree to the Privacy Policy & Terms.", style = MaterialTheme.typography.bodySmall, color = Color.Black)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (!termsAccepted) {
                        Toast.makeText(context, "Please accept terms first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        val result = AuthManager.signInWithGoogle(context)
                        isLoading = false
                        if (result.isSuccess) onComplete()
                        else Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                enabled = termsAccepted && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = {
                    if (!termsAccepted) {
                        Toast.makeText(context, "Please accept terms first", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    onComplete()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                border = BorderStroke(1.dp, Color.Black)
            ) {
                Text("Start Offline (Skip)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
