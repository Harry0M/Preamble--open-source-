package com.theblankstate.preamble.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.theblankstate.preamble.R
import com.theblankstate.preamble.auth.AuthManager
import com.theblankstate.preamble.data.PrimaryGoal
import com.theblankstate.preamble.data.TasksPerDay
import com.theblankstate.preamble.data.UserProfile
import com.theblankstate.preamble.data.UserProfileStore
import com.theblankstate.preamble.data.UserRole
import com.theblankstate.preamble.data.computeBaselineScore
import com.theblankstate.preamble.data.computePercentile
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val ONBOARDING_PAGES = 10
private const val PAGE_WELCOME = 0
private const val PAGE_LOGIN = 1
private const val PAGE_WELCOME_BACK = 2
private const val PAGE_NAME = 3
private const val PAGE_AGE_GENDER = 4
private const val PAGE_ROLE = 5
private const val PAGE_TASKS_GOAL = 6
private const val PAGE_NOTATIONS = 7
private const val PAGE_PERMISSIONS = 8
private const val PAGE_REVEAL = 9

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGES })

    val hasAudioPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasNotifPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true

    var audioPermGranted by remember { mutableStateOf(hasAudioPerm) }
    var notifPermGranted by remember { mutableStateOf(hasNotifPerm) }
    var isExistingUser by rememberSaveable { mutableStateOf(false) }
    var loginUserName by rememberSaveable { mutableStateOf<String?>(null) }

    // Profile state threaded across pages
    var profileName by rememberSaveable { mutableStateOf("") }
    var profileAge by rememberSaveable { mutableStateOf<Int?>(null) }
    var profileGender by rememberSaveable { mutableStateOf<String?>(null) }
    var profileRole by rememberSaveable { mutableStateOf<UserRole?>(null) }
    var profileTasks by rememberSaveable { mutableStateOf<TasksPerDay?>(null) }
    var profileGoals by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver<Set<PrimaryGoal>, String>(
            save = { goals -> goals.joinToString(",") { g -> g.key } },
            restore = { raw ->
                if (raw.isBlank()) emptySet()
                else raw.split(',').mapNotNull { PrimaryGoal.fromKey(it) }.toSet()
            }
        )
    ) { mutableStateOf(emptySet()) }

    fun buildProfile(): UserProfile = UserProfile(
        name = profileName.trim().ifBlank { null },
        age = profileAge,
        gender = profileGender,
        role = profileRole,
        tasksPerDay = profileTasks,
        goal = profileGoals.firstOrNull(),
        goals = profileGoals,
    )

    // Gate advancement per-page based on required answers
    fun canAdvance(currentPage: Int): Boolean = when (currentPage) {
        PAGE_NAME -> profileName.trim().length >= 2
        PAGE_AGE_GENDER -> profileAge != null && profileGender != null
        PAGE_ROLE -> profileRole != null
        PAGE_TASKS_GOAL -> profileTasks != null && profileGoals.isNotEmpty()
        PAGE_PERMISSIONS -> notifPermGranted && audioPermGranted
        else -> true
    }

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
                userScrollEnabled = false,
                beyondViewportPageCount = 0,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp)
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        PAGE_WELCOME -> WelcomePage(context, imageLoader)
                        PAGE_LOGIN -> LoginPage(
                            context = context,
                            imageLoader = imageLoader,
                            onLoginResult = { signInResult ->
                                if (signInResult != null) {
                                    isExistingUser = !signInResult.isNewUser
                                    loginUserName = signInResult.user.displayName
                                    if (!signInResult.isNewUser) {
                                        // Existing user → Welcome Back celebration
                                        scope.launch { pagerState.animateScrollToPage(PAGE_WELCOME_BACK) }
                                    } else {
                                        // New user → full onboarding
                                        scope.launch { pagerState.animateScrollToPage(PAGE_NAME) }
                                    }
                                }
                            },
                            onSkip = {
                                // Skip login → new user flow
                                isExistingUser = false
                                scope.launch { pagerState.animateScrollToPage(PAGE_NAME) }
                            }
                        )
                        PAGE_WELCOME_BACK -> WelcomeBackPage(
                            userName = loginUserName,
                            onFinish = {
                                // Persist minimal profile for existing user
                                val p = UserProfile(
                                    name = loginUserName,
                                    onboardingCompletedAt = System.currentTimeMillis()
                                )
                                UserProfileStore.save(context, p)
                                UserProfileStore.syncToFirestore(p)
                                onComplete()
                            }
                        )
                        PAGE_NAME -> NameQuestionPage(
                            name = profileName,
                            onNameChange = { profileName = it }
                        )
                        PAGE_AGE_GENDER -> AgeGenderQuestionPage(
                            age = profileAge,
                            gender = profileGender,
                            onAgeChange = { profileAge = it },
                            onGenderChange = { profileGender = it }
                        )
                        PAGE_ROLE -> RoleQuestionPage(
                            role = profileRole,
                            onRoleChange = { profileRole = it }
                        )
                        PAGE_TASKS_GOAL -> TasksGoalQuestionPage(
                            tasksPerDay = profileTasks,
                            goals = profileGoals,
                            onTasksChange = { profileTasks = it },
                            onGoalsChange = { profileGoals = it }
                        )
                        PAGE_NOTATIONS -> NotationsPage()
                        PAGE_PERMISSIONS -> PermissionsPage(context, imageLoader, notifPermGranted, audioPermGranted, { notifPermGranted = it }, { audioPermGranted = it })
                        PAGE_REVEAL -> RevealPage(
                            profile = UserProfileStore.load(context),
                            onFinish = onComplete
                        )
                    }
                }
            }

            // Bottom Navigation — hidden on Login, WelcomeBack, and Reveal (they have own CTAs)
            val showBottomNav = pagerState.currentPage != PAGE_LOGIN &&
                    pagerState.currentPage != PAGE_WELCOME_BACK &&
                    pagerState.currentPage != PAGE_REVEAL
            if (showBottomNav) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.White)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                ) {
                    // Progress bar
                    val visiblePages = ONBOARDING_PAGES - 2 // Exclude Login + WelcomeBack
                    val effectivePage = when {
                        pagerState.currentPage <= PAGE_WELCOME -> 0
                        pagerState.currentPage >= PAGE_NAME -> pagerState.currentPage - 2 // Offset for login/welcomeback
                        else -> 0
                    }
                    val progress = (effectivePage + 1).toFloat() / visiblePages.toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.08f))
                    ) {
                        val animProgress by animateFloatAsState(
                            targetValue = progress.coerceIn(0f, 1f),
                            animationSpec = tween(300),
                            label = "progress"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animProgress)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pagerState.currentPage > PAGE_NAME) {
                            TextButton(onClick = {
                                scope.launch {
                                    val prev = (pagerState.currentPage - 1).coerceAtLeast(PAGE_NAME)
                                    pagerState.animateScrollToPage(prev)
                                }
                            }) {
                                Text("Back", color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }

                        val enabled = canAdvance(pagerState.currentPage)
                        val isLastBeforeReveal = pagerState.currentPage == PAGE_PERMISSIONS
                        Button(
                            onClick = {
                                scope.launch {
                                    if (isLastBeforeReveal) {
                                        // Save profile before reveal
                                        val p = buildProfile()
                                        val score = computeBaselineScore(p)
                                        val enriched = p.copy(
                                            baselineScore = score,
                                            percentile = computePercentile(score),
                                            onboardingCompletedAt = System.currentTimeMillis()
                                        )
                                        UserProfileStore.save(context, enriched)
                                        UserProfileStore.syncToFirestore(enriched)
                                    }
                                    val next = (pagerState.currentPage + 1).coerceAtMost(ONBOARDING_PAGES - 1)
                                    pagerState.animateScrollToPage(next)
                                }
                            },
                            enabled = enabled,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black,
                                contentColor = Color.White,
                                disabledContainerColor = Color.Black.copy(alpha = 0.25f),
                                disabledContentColor = Color.White,
                            ),
                            modifier = Modifier.height(48.dp).widthIn(min = 120.dp)
                        ) {
                            Text(if (isLastBeforeReveal) "Finish" else "Next", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
        border = null
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
fun LoginPage(
    context: Context,
    imageLoader: ImageLoader,
    onLoginResult: (AuthManager.SignInResult?) -> Unit,
    onSkip: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }

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
            contentDescription = "Sign in",
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
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).padding(8.dp)
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { termsAccepted = it },
                    colors = CheckboxDefaults.colors(checkedColor = Color.Black, uncheckedColor = Color.Gray, checkmarkColor = Color.White)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val termsUrl = "https://theblankstate.com/preamble/terms"
                val privacyUrl = "https://theblankstate.com/preamble/privacy"
                val annotated = androidx.compose.ui.text.buildAnnotatedString {
                    append("I agree to the ")
                    pushStringAnnotation(tag = "URL", annotation = privacyUrl)
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    ) { append("Privacy Policy") }
                    pop()
                    append(" & ")
                    pushStringAnnotation(tag = "URL", annotation = termsUrl)
                    withStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    ) { append("Terms") }
                    pop()
                    append(".")
                }
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                androidx.compose.foundation.text.ClickableText(
                    text = annotated,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.Black),
                    onClick = { offset ->
                        val link = annotated.getStringAnnotations("URL", offset, offset).firstOrNull()
                        if (link != null) uriHandler.openUri(link.item)
                        else termsAccepted = !termsAccepted
                    }
                )
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
                        if (result.isSuccess) {
                            onLoginResult(result.getOrNull())
                        } else {
                            Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
                        }
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
                    onSkip()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                border = null
            ) {
                Text("Start Offline (Skip)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun WelcomeBackPage(userName: String?, onFinish: () -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val greet = userName?.takeIf { it.isNotBlank() }?.let { "Welcome back, $it!" } ?: "Welcome back!"

    // Konfetti party config
    val party = remember {
        listOf(
            Party(
                emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(40),
                position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0))
            ),
            Party(
                emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(40),
                position = Position.Relative(0.5, 0.0)
            )
        )
    }

    // Celebration haptics
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        kotlinx.coroutines.delay(500)
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        kotlinx.coroutines.delay(400)
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }

    // Scale-in animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "wbScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Konfetti overlay
        KonfettiView(
            modifier = Modifier.fillMaxSize(),
            parties = party
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(scale)
            ) {
                Text(
                    "🎉",
                    fontSize = 72.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    greet,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Your data is still here.\nPick up right where you left off.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                )
            }

            Button(
                onClick = onFinish,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Let's go", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NotationsPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            "YOUR NOTATION SYSTEM",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            "A new way\nto plan.",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            color = Color.Black,
            lineHeight = 40.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            "Every task gets a visual indicator so you know exactly how it behaves.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            lineHeight = 22.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        NotationCard(
            title = "One-Day Task",
            desc = "Lives only for today. If not completed, it disappears tomorrow — perfect for daily intentions.",
            type = "solid"
        )
        Spacer(modifier = Modifier.height(16.dp))

        NotationCard(
            title = "Active Until Complete",
            desc = "Rolls over to the next day automatically until you finish it. Your persistent to-do.",
            type = "half_dotted"
        )
        Spacer(modifier = Modifier.height(16.dp))

        NotationCard(
            title = "Recurring Task",
            desc = "Repeats on a schedule you set — daily, weekly, or custom. Builds habits effortlessly.",
            type = "fully_dotted"
        )
    }
}

@Composable
fun NotationCard(title: String, desc: String, type: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF5F5F5))
            .padding(20.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.padding(top = 2.dp, end = 18.dp)) {
            when (type) {
                "solid" -> {
                    Canvas(modifier = Modifier.size(40.dp)) {
                        drawCircle(color = Color.Black, radius = size.width / 2)
                    }
                }
                "half_dotted" -> {
                    Canvas(modifier = Modifier.size(40.dp)) {
                        drawArc(
                            color = Color.Black,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                        )
                        drawArc(
                            color = Color.Black,
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
                "fully_dotted" -> {
                    Canvas(modifier = Modifier.size(40.dp)) {
                        drawCircle(
                            color = Color.Black,
                            radius = size.width / 2,
                            style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, lineHeight = 20.sp)
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
        colors = CardDefaults.cardColors(containerColor = if (isGranted) Color(0xFFEDEDED) else Color(0xFFF5F5F5)),
        border = null
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

/* ────────────────── Question pages (commitment-consistency) ────────────────── */

@Composable
private fun QuestionHeader(kicker: String, title: String, sub: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            kicker.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
            color = Color.Black,
            lineHeight = 40.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            sub,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            lineHeight = 22.sp,
        )
    }
}

@Composable
fun NameQuestionPage(name: String, onNameChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Top
    ) {
        QuestionHeader(
            kicker = "Step 1 of 5",
            title = "What should\nwe call you?",
            sub = "Just a first name — used to make Preamble feel like yours."
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 40) onNameChange(it) },
            placeholder = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun AgeGenderQuestionPage(
    age: Int?,
    gender: String?,
    onAgeChange: (Int?) -> Unit,
    onGenderChange: (String?) -> Unit,
) {
    data class GenderOpt(val key: String, val label: String, val icon: ImageVector)
    val genderOptions = listOf(
        GenderOpt("male", "Male", Icons.Filled.Person),
        GenderOpt("female", "Female", Icons.Filled.Female),
        GenderOpt("nonbinary", "Non-binary", Icons.Filled.Transgender),
        GenderOpt("na", "Skip", Icons.Filled.VisibilityOff),
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var sliderAge by remember(age) { mutableStateOf((age ?: 22).coerceIn(8, 99)) }
    var lastHapticAge by remember { mutableStateOf(sliderAge) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
    ) {
        QuestionHeader(
            kicker = "Step 2 of 5",
            title = "A bit about you.",
            sub = "Helps us tune reminders, streak copy and perks."
        )
        Spacer(modifier = Modifier.height(28.dp))

        Text("I identify as", fontWeight = FontWeight.SemiBold, color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genderOptions.forEach { opt ->
                val selected = gender == opt.key
                val bg by animateColorAsState(
                    targetValue = if (selected) Color.Black else Color(0xFFF5F5F5),
                    animationSpec = tween(220), label = "gBg"
                )
                val sc by animateFloatAsState(
                    if (selected) 1.04f else 1f, tween(180), label = "gSc"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .scale(sc)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onGenderChange(if (selected) null else opt.key)
                        }
                        .padding(vertical = 16.dp, horizontal = 4.dp)
                ) {
                    Icon(
                        opt.icon,
                        contentDescription = opt.label,
                        tint = if (selected) Color.White else Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        opt.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else Color.DarkGray,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("My age", fontWeight = FontWeight.SemiBold, color = Color.Black)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                sliderAge.toString(),
                fontSize = 56.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                letterSpacing = (-2).sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "years",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = sliderAge.toFloat(),
            onValueChange = { v ->
                val next = v.toInt().coerceIn(8, 99)
                if (next != sliderAge) {
                    sliderAge = next
                    onAgeChange(next)
                    if (next != lastHapticAge) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        lastHapticAge = next
                    }
                }
            },
            valueRange = 8f..99f,
            steps = (99 - 8 - 1),
            modifier = Modifier.fillMaxWidth()
        )
        LaunchedEffect(Unit) { onAgeChange(sliderAge) }
    }
}

@Composable
fun RoleQuestionPage(role: UserRole?, onRoleChange: (UserRole?) -> Unit) {
    data class RoleOpt(val value: UserRole, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
    val opts = listOf(
        RoleOpt(UserRole.STUDENT, "Student", Icons.Filled.School),
        RoleOpt(UserRole.WORKING, "Working professional", Icons.Filled.Work),
        RoleOpt(UserRole.OTHER, "Something else", Icons.Filled.AutoAwesome),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
    ) {
        QuestionHeader(
            kicker = "Step 3 of 5",
            title = "What best\ndescribes you?",
            sub = "We use this only to pick sensible defaults and unlock discounts later."
        )
        Spacer(modifier = Modifier.height(24.dp))
        opts.forEach { opt ->
            val selected = role == opt.value
            val bg by animateColorAsState(
                targetValue = if (selected) Color(0xFFE8E8E8) else Color(0xFFF5F5F5),
                animationSpec = tween(200), label = "roleBg"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .clickable { onRoleChange(if (selected) null else opt.value) }
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.Black else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        opt.icon,
                        contentDescription = null,
                        tint = if (selected) Color.White else Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    opt.label,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                }
            }
        }
    }
}

@Composable
fun TasksGoalQuestionPage(
    tasksPerDay: TasksPerDay?,
    goals: Set<PrimaryGoal>,
    onTasksChange: (TasksPerDay?) -> Unit,
    onGoalsChange: (Set<PrimaryGoal>) -> Unit,
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    // Map slider value to TasksPerDay enum
    val initialSlider = when (tasksPerDay) {
        TasksPerDay.LIGHT -> 2
        TasksPerDay.MODERATE -> 6
        TasksPerDay.HEAVY -> 12
        null -> 5
    }
    var sliderValue by remember(tasksPerDay) { mutableStateOf(initialSlider) }
    var lastHapticVal by remember { mutableStateOf(sliderValue) }

    fun sliderToTasksPerDay(v: Int): TasksPerDay = when {
        v <= 3 -> TasksPerDay.LIGHT
        v <= 8 -> TasksPerDay.MODERATE
        else -> TasksPerDay.HEAVY
    }

    // Material icons for goals
    fun goalIcon(g: PrimaryGoal): ImageVector = when (g) {
        PrimaryGoal.STUDY -> Icons.AutoMirrored.Filled.MenuBook
        PrimaryGoal.WORK -> Icons.Filled.BusinessCenter
        PrimaryGoal.HEALTH -> Icons.Filled.FitnessCenter
        PrimaryGoal.HABIT -> Icons.Filled.Loop
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
    ) {
        QuestionHeader(
            kicker = "Step 4 of 5",
            title = "How much do you\njuggle in a day?",
            sub = "And what's the one thing you want Preamble to help you win at?"
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text("Tasks per day", fontWeight = FontWeight.SemiBold, color = Color.Black)
        Spacer(modifier = Modifier.height(10.dp))

        // Big number display like age slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                if (sliderValue >= 15) "15+" else sliderValue.toString(),
                fontSize = 48.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                letterSpacing = (-2).sp,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    "tasks",
                    fontSize = 16.sp,
                    color = Color.Gray,
                )
                Text(
                    sliderToTasksPerDay(sliderValue).hint,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Slider(
            value = sliderValue.toFloat(),
            onValueChange = { v ->
                val next = v.roundToInt().coerceIn(1, 15)
                if (next != sliderValue) {
                    sliderValue = next
                    onTasksChange(sliderToTasksPerDay(next))
                    if (next != lastHapticVal) {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        lastHapticVal = next
                    }
                }
            },
            valueRange = 1f..15f,
            steps = 13,
            modifier = Modifier.fillMaxWidth()
        )
        LaunchedEffect(Unit) { onTasksChange(sliderToTasksPerDay(sliderValue)) }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Primary goals", fontWeight = FontWeight.SemiBold, color = Color.Black)
            Text(
                "Pick up to 2 · ${goals.size}/2",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryGoal.values().toList().chunked(2).forEach { rowOpts ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOpts.forEach { g ->
                        val selected = g in goals
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Color.Black else Color(0xFFF5F5F5))
                                .clickable {
                                    val next = if (selected) goals - g
                                    else if (goals.size >= 2) {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        Toast.makeText(context, "Pick max 2 goals", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    } else goals + g
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    onGoalsChange(next)
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                goalIcon(g),
                                contentDescription = g.label,
                                tint = if (selected) Color.White else Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                g.label,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (selected) Color.White else Color.Black
                            )
                        }
                    }
                    if (rowOpts.size == 1) Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/* ────────────────── Reveal (Peak-End moment) ────────────────── */

@Composable
fun RevealPage(profile: UserProfile, onFinish: () -> Unit) {
    val target = profile.baselineScore.coerceAtLeast(1)
    val animated by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "score"
    )
    val displayedScore = animated.toInt().coerceAtLeast(0)
    val percentile = profile.percentile.coerceAtLeast(3)
    val greet = profile.name?.takeIf { it.isNotBlank() }?.let { "Nice to meet you, $it." } ?: "You're all set."
    val goalText = profile.effectiveGoals.firstOrNull()?.label ?: profile.goal?.label ?: "your goal"

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var lastTickedScore by remember { mutableStateOf(-1) }
    LaunchedEffect(displayedScore) {
        if (displayedScore != lastTickedScore && displayedScore % 10 == 0 && displayedScore > 0) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            lastTickedScore = displayedScore
        }
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1450)
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "YOU'RE IN",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.8.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                greet,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$displayedScore",
                fontSize = 112.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                letterSpacing = (-4).sp,
            )
            Text(
                "baseline productivity score",
                fontSize = 13.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "Top $percentile% of planners",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Finish 3 tasks today to push this past 80.\nYour ${goalText.lowercase()} starts now.",
                fontSize = 14.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
        }

        Button(
            onClick = onFinish,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Let's go", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
