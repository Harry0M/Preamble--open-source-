package com.theblankstate.preamble.ui.screens

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.auth.AuthManager
import com.theblankstate.preamble.data.ProblemReport
import com.theblankstate.preamble.repository.ProblemReportRepository
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleSyncCoordinator
import com.theblankstate.preamble.sync.GoogleTasksManager
import com.theblankstate.preamble.ui.theme.ThemePreferences
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsSubscreen {
    Main,
    AccountAndSync,
    Appearance,
    Notifications,
    Ai,
    Personal,
    AlarmsAndGeneral,
    SupportAndAbout,
    OssLicenses,
    ReportProblem,
    MyReports,
    ReportDetail,
}

/**
 * Helper modifier for physics-based bouncy scale feedback on touch.
 */
@Composable
private fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "expressivePressScale",
    )
    return this.scale(scale)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    yearlyHeatmap: Map<String, Float> = emptyMap(),
    onOpenUpgrade: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as PreambleApplication
    val scope = app.appScope

    // Dynamic scale factor based on screen width
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val scaleFactor = (screenWidthDp / 360f).coerceIn(0.85f, 1.15f)

    var hasSystemNotificationPermission by remember { mutableStateOf(areNotificationsEnabled(context)) }
    var isBatteryOptimized by remember { mutableStateOf(!isIgnoringBatteryOptimizations(context)) }
    var notificationPrefEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .getBoolean("notification_enabled", true)
        )
    }

    var notificationUpdateMode by remember {
        mutableStateOf(
            com.theblankstate.preamble.notification.NotificationUpdatePreference.fromStringOrDefault(
                context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                    .getString("notification_update_mode", null)
            )
        )
    }

    var alarmToneName by remember { mutableStateOf(getCurrentAlarmToneName(context)) }
    var showReviewSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showTermsSheet by remember { mutableStateOf(false) }
    var settingsSubscreen by remember { mutableStateOf(SettingsSubscreen.Main) }
    var selectedReport by remember { mutableStateOf<ProblemReport?>(null) }

    BackHandler(enabled = settingsSubscreen != SettingsSubscreen.Main) {
        settingsSubscreen = when (settingsSubscreen) {
            SettingsSubscreen.ReportDetail -> SettingsSubscreen.MyReports
            SettingsSubscreen.MyReports -> SettingsSubscreen.SupportAndAbout
            SettingsSubscreen.ReportProblem -> SettingsSubscreen.SupportAndAbout
            else -> SettingsSubscreen.Main
        }
    }

    val currentUser by AuthManager.currentUser.collectAsState()
    var signInLoading by remember { mutableStateOf(false) }
    var signOutLoading by remember { mutableStateOf(false) }

    val calendarLinked by GoogleCalendarManager.isLinked.collectAsState()
    val calendarSyncing by GoogleCalendarManager.isSyncing.collectAsState()
    val calendarEmail by GoogleCalendarManager.linkedEmail.collectAsState()
    val lastCalSyncTime by GoogleCalendarManager.lastSyncTime.collectAsState()
    val tasksLinked by GoogleTasksManager.isLinked.collectAsState()
    val tasksSyncing by GoogleTasksManager.isSyncing.collectAsState()
    val lastTasksSyncTime by GoogleTasksManager.lastSyncTime.collectAsState()
    val googleLinked = calendarLinked || tasksLinked
    val googleSyncing = calendarSyncing || tasksSyncing
    val lastSyncTime = maxOf(lastCalSyncTime, lastTasksSyncTime)
    var googleLinkLoading by remember { mutableStateOf(false) }
    val activity = context as? Activity

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                val email = account?.email ?: "Unknown"
                GoogleCalendarManager.onSignInSuccess(context, email)
                GoogleTasksManager.onSignInSuccess(context)
                GoogleCalendarManager.resetSyncState(context)
                GoogleTasksManager.resetSyncState(context)
                com.theblankstate.preamble.sync.GoogleSyncWorker.enqueueFullSync(
                    context = context,
                    reason = "link_bootstrap"
                )
                Toast.makeText(context, "Syncing Google Calendar in background...", Toast.LENGTH_SHORT).show()
                googleLinkLoading = false
            } catch (e: Throwable) {
                Toast.makeText(context, "Link failed: ${e.message}", Toast.LENGTH_SHORT).show()
                googleLinkLoading = false
            }
        } else {
            googleLinkLoading = false
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            hasSystemNotificationPermission = areNotificationsEnabled(context)
            isBatteryOptimized = !isIgnoringBatteryOptimizations(context)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSystemNotificationPermission = granted
        if (granted) {
            notificationPrefEnabled = true
            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("notification_enabled", true).apply()
            com.theblankstate.preamble.notification.TaskNotificationService.start(context)
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        if (uri != null) {
            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .edit().putString("alarm_tone_uri", uri.toString()).apply()
            alarmToneName = getCurrentAlarmToneName(context)
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
        val installTime = prefs.getLong("install_time", 0L)
        if (installTime == 0L) {
            prefs.edit().putLong("install_time", System.currentTimeMillis()).apply()
        } else {
            val twoDays = 2 * 24 * 60 * 60 * 1000L
            val hasReviewed = prefs.getBoolean("has_reviewed", false)
            if (!hasReviewed && System.currentTimeMillis() - installTime > twoDays) {
                showReviewSheet = true
            }
        }
    }

    // Scroll state & scroll-to-hide floating header bar
    val scrollState = rememberScrollState()
    var isHeaderVisible by remember { mutableStateOf(true) }
    var lastScrollPosition by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollState.value) {
        val currentScroll = scrollState.value
        val diff = currentScroll - lastScrollPosition
        if (diff > 14 && currentScroll > 60) {
            isHeaderVisible = false
        } else if (diff < -10 || currentScroll < 30) {
            isHeaderVisible = true
        }
        lastScrollPosition = currentScroll
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            // Under-Status-Bar Edge-to-Edge Scroll Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp * scaleFactor),
                verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)
            ) {
                // Top Clearance Spacer so top settings item rests cleanly under floating FAB header
                Spacer(modifier = Modifier.height(96.dp * scaleFactor))

                when (settingsSubscreen) {
                    SettingsSubscreen.AccountAndSync -> {
                        AccountAndSyncSubscreen(
                            currentUser = currentUser,
                            signInLoading = signInLoading,
                            signOutLoading = signOutLoading,
                            googleLinked = googleLinked,
                            calendarEmail = calendarEmail,
                            lastSyncTime = lastSyncTime,
                            googleSyncing = googleSyncing,
                            googleLinkLoading = googleLinkLoading,
                            onSignIn = {
                                signInLoading = true
                                scope.launch {
                                    AuthManager.signInWithGoogle(context)
                                    signInLoading = false
                                }
                            },
                            onSignOut = {
                                signOutLoading = true
                                scope.launch {
                                    try {
                                        app.repository.flushAndClearLocalOnLogout()
                                        AuthManager.signOut()
                                    } finally {
                                        signOutLoading = false
                                    }
                                }
                            },
                            onLinkGoogle = {
                                googleLinkLoading = true
                                val signInClient = GoogleCalendarManager.getSignInClient(context)
                                googleSignInLauncher.launch(signInClient.signInIntent)
                            },
                            onUnlinkGoogle = {
                                scope.launch {
                                    app.repository.clearCalendarEvents()
                                    app.repository.clearGoogleTasks()
                                    GoogleCalendarManager.unlink(context)
                                    GoogleTasksManager.unlink(context)
                                    Toast.makeText(context, "Google Calendar & Tasks unlinked", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSyncNow = {
                                scope.launch {
                                    try {
                                        val summary = GoogleSyncCoordinator.syncLinkedData(
                                            context = context,
                                            forceFull = false,
                                            isManual = true,
                                            reason = "settings_sync_now"
                                        )
                                        Toast.makeText(context, "Updated ${summary.calendarEvents} events + ${summary.googleTasks} tasks", Toast.LENGTH_SHORT).show()
                                    } catch (e: Throwable) {
                                        Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            scaleFactor = scaleFactor
                        )
                    }
                    SettingsSubscreen.Appearance -> {
                        AppearanceSubscreen(scaleFactor = scaleFactor)
                    }
                    SettingsSubscreen.Notifications -> {
                        NotificationsSubscreen(
                            hasSystemNotificationPermission = hasSystemNotificationPermission,
                            notificationPrefEnabled = notificationPrefEnabled,
                            notificationUpdateMode = notificationUpdateMode,
                            isBatteryOptimized = isBatteryOptimized,
                            onNotifPermissionRequest = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    })
                                }
                            },
                            onNotificationPrefToggle = { enable ->
                                if (enable) {
                                    if (!hasSystemNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notificationPrefEnabled = true
                                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).edit().putBoolean("notification_enabled", true).apply()
                                        com.theblankstate.preamble.notification.TaskNotificationService.start(context)
                                    }
                                } else {
                                    notificationPrefEnabled = false
                                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).edit().putBoolean("notification_enabled", false).apply()
                                    com.theblankstate.preamble.notification.TaskNotificationService.stop(context)
                                }
                            },
                            onUpdateModeChange = { mode ->
                                notificationUpdateMode = mode
                                context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                    .edit().putString("notification_update_mode", mode.name).apply()
                            },
                            onRequestIgnoreBattery = { requestIgnoreBatteryOptimizations(context) },
                            scaleFactor = scaleFactor
                        )
                    }
                    SettingsSubscreen.Ai -> {
                        AiSettingsSubscreen(scaleFactor = scaleFactor)
                    }
                    SettingsSubscreen.Personal -> {
                        PersonalTouchSettingsSubscreen(scaleFactor = scaleFactor)
                    }
                    SettingsSubscreen.AlarmsAndGeneral -> {
                        AlarmsAndGeneralSubscreen(
                            alarmToneName = alarmToneName,
                            onPickAlarmTone = {
                                ringtonePickerLauncher.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Tone")
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                        val saved = context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).getString("alarm_tone_uri", null)
                                        if (saved != null) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(saved))
                                    }
                                )
                            },
                            scaleFactor = scaleFactor
                        )
                    }
                    SettingsSubscreen.SupportAndAbout -> {
                        SupportAndAboutSubscreen(
                            onOpenReportProblem = { settingsSubscreen = SettingsSubscreen.ReportProblem },
                            onOpenMyReports = { settingsSubscreen = SettingsSubscreen.MyReports },
                            onOpenLicenses = { settingsSubscreen = SettingsSubscreen.OssLicenses },
                            onOpenPrivacy = { showPrivacySheet = true },
                            onOpenTerms = { showTermsSheet = true },
                            onOpenReview = { showReviewSheet = true },
                            scaleFactor = scaleFactor
                        )
                    }
                    SettingsSubscreen.ReportProblem -> {
                        ProblemReportSettingsSubscreen(scaleFactor = scaleFactor)
                    }
                    SettingsSubscreen.MyReports -> {
                        MyReportsSubscreen(
                            onSelectReport = { report ->
                                selectedReport = report
                                settingsSubscreen = SettingsSubscreen.ReportDetail
                            },
                            scaleFactor = scaleFactor
                        )
                    }
                    SettingsSubscreen.ReportDetail -> {
                        selectedReport?.let { report ->
                            ReportDetailSubscreen(report = report, scaleFactor = scaleFactor)
                        } ?: run {
                            settingsSubscreen = SettingsSubscreen.MyReports
                        }
                    }
                    SettingsSubscreen.OssLicenses -> {
                        OssLicensesSubscreen(scaleFactor = scaleFactor)
                    }
                    SettingsSubscreen.Main -> {
                        // Simplified High-Level Settings Menu Categories
                        SettingsGroupSection(title = "General Settings", scaleFactor = scaleFactor) {
                            SettingsItemRow(
                                title = "Account & Cloud Sync",
                                subtitle = if (currentUser != null) currentUser?.email ?: "Signed in" else "Sign in with Google & sync data",
                                icon = Icons.Default.Person,
                                iconTint = Color(0xFF1A73E8),
                                iconBgColor = Color(0xFFE8F0FE),
                                scaleFactor = scaleFactor,
                                onClick = { settingsSubscreen = SettingsSubscreen.AccountAndSync },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )

                            SettingsItemRow(
                                title = "Appearance & Theme",
                                subtitle = "Light/Dark/AMOLED, colors & card tags",
                                icon = Icons.Default.Palette,
                                iconTint = Color(0xFF9334E6),
                                iconBgColor = Color(0xFFF3E8FD),
                                scaleFactor = scaleFactor,
                                onClick = { settingsSubscreen = SettingsSubscreen.Appearance },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )

                            SettingsItemRow(
                                title = "Notifications & Reminders",
                                subtitle = "Task bar notification, frequency & battery settings",
                                icon = Icons.Default.NotificationsActive,
                                iconTint = Color(0xFFF9AB00),
                                iconBgColor = Color(0xFFFEF7E0),
                                scaleFactor = scaleFactor,
                                onClick = { settingsSubscreen = SettingsSubscreen.Notifications },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }

                        SettingsGroupSection(title = "AI & Personalization", scaleFactor = scaleFactor) {
                            SettingsItemRow(
                                title = "Preamble AI",
                                subtitle = "Memory, smart auto-fill & breakdown intensity",
                                icon = Icons.Default.AutoAwesome,
                                iconTint = Color(0xFF00897B),
                                iconBgColor = Color(0xFFE0F2F1),
                                scaleFactor = scaleFactor,
                                onClick = { settingsSubscreen = SettingsSubscreen.Ai },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )

                            SettingsItemRow(
                                title = "Personal touches",
                                subtitle = "Greetings, progress motivation & celebrations",
                                icon = Icons.Default.Favorite,
                                iconTint = Color(0xFFD81B60),
                                iconBgColor = Color(0xFFFCE4EC),
                                scaleFactor = scaleFactor,
                                onClick = { settingsSubscreen = SettingsSubscreen.Personal },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )

                            SettingsItemRow(
                                title = "Alarms & Haptics",
                                subtitle = "Alarm ringtone & vibration feedback",
                                icon = Icons.Default.Tune,
                                iconTint = Color(0xFF3F51B5),
                                iconBgColor = Color(0xFFE8EAF6),
                                scaleFactor = scaleFactor,
                                onClick = { settingsSubscreen = SettingsSubscreen.AlarmsAndGeneral },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }

                        SettingsGroupSection(title = "Help & Information", scaleFactor = scaleFactor) {
                            SettingsItemRow(
                                title = "Support & Legal",
                                subtitle = "Report a problem, What's New v1.6 & licenses",
                                icon = Icons.Default.HelpOutline,
                                iconTint = Color(0xFF00ACC1),
                                iconBgColor = Color(0xFFE0F7FA),
                                scaleFactor = scaleFactor,
                                onClick = { settingsSubscreen = SettingsSubscreen.SupportAndAbout },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            )
                        }

                        val rateInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { showReviewSheet = true },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp * scaleFactor)
                                .expressivePressScale(rateInteraction)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "Rate Preamble ★",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = (15 * scaleFactor).sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        // Compact bottom spacer without extra gap above bottom nav bar
                        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
                    }
                }
            }

            // Floating Header Overlay Layer (Scroll-to-Hide FAB Top Bar)
            AnimatedVisibility(
                visible = isHeaderVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp * scaleFactor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp * scaleFactor),
                    contentAlignment = Alignment.Center
                ) {
                    if (settingsSubscreen != SettingsSubscreen.Main) {
                        val backInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                settingsSubscreen = when (settingsSubscreen) {
                                    SettingsSubscreen.ReportDetail -> SettingsSubscreen.MyReports
                                    SettingsSubscreen.MyReports -> SettingsSubscreen.SupportAndAbout
                                    SettingsSubscreen.ReportProblem -> SettingsSubscreen.SupportAndAbout
                                    else -> SettingsSubscreen.Main
                                }
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(44.dp * scaleFactor)
                                .expressivePressScale(backInteraction)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp * scaleFactor)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .height(44.dp * scaleFactor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 22.dp * scaleFactor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (settingsSubscreen) {
                                    SettingsSubscreen.Main -> "Settings"
                                    SettingsSubscreen.AccountAndSync -> "Account & Cloud Sync"
                                    SettingsSubscreen.Appearance -> "Appearance & Theme"
                                    SettingsSubscreen.Notifications -> "Notifications & Reminders"
                                    SettingsSubscreen.Ai -> "Preamble AI"
                                    SettingsSubscreen.Personal -> "Personal touches"
                                    SettingsSubscreen.AlarmsAndGeneral -> "Alarms & Haptics"
                                    SettingsSubscreen.SupportAndAbout -> "Support & About"
                                    SettingsSubscreen.ReportProblem -> "Report a problem"
                                    SettingsSubscreen.MyReports -> "My Problem Reports"
                                    SettingsSubscreen.ReportDetail -> "Report Details"
                                    SettingsSubscreen.OssLicenses -> "Open-source licenses"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = (15 * scaleFactor).sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (showReviewSheet) {
            ReimaginedRatePreambleSheet(
                onDismiss = { showReviewSheet = false },
                onRateNow = { rating ->
                    showReviewSheet = false
                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("has_reviewed", true).apply()
                    activity?.let { act ->
                        val reviewManager = com.theblankstate.preamble.util.InAppReviewManager(act)
                        reviewManager.launchReviewFlow(forceFake = false)
                    }
                }
            )
        }

        if (showPrivacySheet) {
            LegalDocumentSheet(
                title = "Privacy Policy",
                sections = privacyPolicySections(),
                onDismiss = { showPrivacySheet = false },
            )
        }

        if (showTermsSheet) {
            LegalDocumentSheet(
                title = "Terms and Conditions",
                sections = termsAndConditionsSections(),
                onDismiss = { showTermsSheet = false },
            )
        }
    }
}

/**
 * Re-imagined Rate Preamble Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReimaginedRatePreambleSheet(
    onDismiss: () -> Unit,
    onRateNow: (Int) -> Unit
) {
    var selectedStars by remember { mutableIntStateOf(5) }
    val haptics = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Visual Anchor Star Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF7E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFF9AB00),
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Enjoying Preamble?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Your feedback helps us continuously improve Preamble for everyone.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            // Interactive Tactile Star Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..5).forEach { starIndex ->
                    val isSelected = starIndex <= selectedStars
                    val starInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(interactionSource = starInteraction, indication = null) {
                                selectedStars = starIndex
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            .expressivePressScale(starInteraction, pressedScale = 0.85f),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Star $starIndex",
                            tint = if (isSelected) Color(0xFFF9AB00) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val laterInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .expressivePressScale(laterInteraction)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "Later",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val rateInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { onRateNow(selectedStars) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .expressivePressScale(rateInteraction)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "Rate Now",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Account & Google Sync Subscreen
 */
@Composable
private fun AccountAndSyncSubscreen(
    currentUser: com.google.firebase.auth.FirebaseUser?,
    signInLoading: Boolean,
    signOutLoading: Boolean,
    googleLinked: Boolean,
    calendarEmail: String?,
    lastSyncTime: Long,
    googleSyncing: Boolean,
    googleLinkLoading: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onLinkGoogle: () -> Unit,
    onUnlinkGoogle: () -> Unit,
    onSyncNow: () -> Unit,
    scaleFactor: Float = 1f
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)) {
        SettingsGroupSection(title = "Account", scaleFactor = scaleFactor) {
            if (currentUser != null) {
                SettingsItemRow(
                    title = currentUser.displayName ?: "User",
                    subtitle = currentUser.email ?: "",
                    icon = Icons.Default.Person,
                    iconTint = Color(0xFF1A73E8),
                    iconBgColor = Color(0xFFE8F0FE),
                    scaleFactor = scaleFactor,
                    trailingContent = {
                        val signOutInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = onSignOut,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.expressivePressScale(signOutInteraction)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (signOutLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp * scaleFactor), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        "Sign Out",
                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = (12 * scaleFactor).sp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                )
            } else {
                SettingsItemRow(
                    title = "Sign in with Google",
                    subtitle = "Sync habits, tasks and backups across devices",
                    icon = Icons.Default.Person,
                    iconTint = Color(0xFF1A73E8),
                    iconBgColor = Color(0xFFE8F0FE),
                    scaleFactor = scaleFactor,
                    onClick = onSignIn,
                    trailingContent = {
                        if (signInLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp * scaleFactor), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }

        SettingsGroupSection(title = "Google Sync", scaleFactor = scaleFactor) {
            if (googleLinked) {
                val syncTimeString = if (lastSyncTime > 0) {
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastSyncTime))
                } else null

                SettingsItemRow(
                    title = "Linked Google Account",
                    subtitle = listOfNotNull(calendarEmail, syncTimeString?.let { "Last synced: $it" }).joinToString(" • "),
                    icon = Icons.Default.CloudDone,
                    iconTint = Color(0xFF1E8E3E),
                    iconBgColor = Color(0xFFE6F4EA),
                    scaleFactor = scaleFactor,
                    trailingContent = {
                        val unlinkInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = onUnlinkGoogle,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.expressivePressScale(unlinkInteraction)
                        ) {
                            Text(
                                "Unlink",
                                modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = (12 * scaleFactor).sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                SettingsItemRow(
                    title = "Sync Now",
                    subtitle = "Fetch calendar events & Google Tasks",
                    icon = Icons.Default.Refresh,
                    iconTint = Color(0xFF1E8E3E),
                    iconBgColor = Color(0xFFE6F4EA),
                    scaleFactor = scaleFactor,
                    onClick = onSyncNow,
                    trailingContent = {
                        if (googleSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp * scaleFactor), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                val syncVoice by GoogleTasksManager.syncVoiceTasks.collectAsState()
                SettingsToggleItemRow(
                    title = "Sync Voice Tasks",
                    subtitle = "Auto-add voice tasks to Google Tasks",
                    icon = Icons.Default.Mic,
                    iconTint = Color(0xFF1E8E3E),
                    iconBgColor = Color(0xFFE6F4EA),
                    checked = syncVoice,
                    onToggle = { GoogleTasksManager.setSyncVoiceTasks(context, it) },
                    scaleFactor = scaleFactor
                )

                val autoDelete by GoogleTasksManager.autoDeleteGoogleTasks.collectAsState()
                SettingsToggleItemRow(
                    title = "Auto-delete synced tasks",
                    subtitle = "Delete from app when removed from Google Tasks",
                    icon = Icons.Default.DeleteSweep,
                    iconTint = Color(0xFF1E8E3E),
                    iconBgColor = Color(0xFFE6F4EA),
                    checked = autoDelete,
                    onToggle = { GoogleTasksManager.setAutoDeleteGoogleTasks(context, it) },
                    scaleFactor = scaleFactor
                )
            } else {
                SettingsItemRow(
                    title = "Link Google Calendar & Tasks",
                    subtitle = "Sync events, holidays & tasks from Google",
                    icon = Icons.Default.CalendarMonth,
                    iconTint = Color(0xFF1E8E3E),
                    iconBgColor = Color(0xFFE6F4EA),
                    scaleFactor = scaleFactor,
                    onClick = onLinkGoogle,
                    trailingContent = {
                        if (googleLinkLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp * scaleFactor), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

/**
 * Appearance Subscreen
 */
@Composable
private fun AppearanceSubscreen(scaleFactor: Float = 1f) {
    val context = LocalContext.current
    val colorfulCards by ThemePreferences.colorfulCards.collectAsState()
    val showCategoryTags by ThemePreferences.showCategoryTags.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)) {
        SettingsGroupSection(title = "Appearance & Themes", scaleFactor = scaleFactor) {
            ThemePickerCard(context)

            SettingsToggleItemRow(
                title = "Colorful Task Cards",
                subtitle = "Give each task card a unique color tint",
                icon = Icons.Default.Palette,
                iconTint = Color(0xFF9334E6),
                iconBgColor = Color(0xFFF3E8FD),
                checked = colorfulCards,
                onToggle = { v -> ThemePreferences.setColorfulCards(context, v) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Show Category Tags",
                subtitle = "Display category tags below task title",
                icon = Icons.Default.Label,
                iconTint = Color(0xFF9334E6),
                iconBgColor = Color(0xFFF3E8FD),
                checked = showCategoryTags,
                onToggle = { v -> ThemePreferences.setShowCategoryTags(context, v) },
                scaleFactor = scaleFactor
            )
        }
        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

/**
 * Notifications Subscreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsSubscreen(
    hasSystemNotificationPermission: Boolean,
    notificationPrefEnabled: Boolean,
    notificationUpdateMode: com.theblankstate.preamble.notification.NotificationUpdatePreference,
    isBatteryOptimized: Boolean,
    onNotifPermissionRequest: () -> Unit,
    onNotificationPrefToggle: (Boolean) -> Unit,
    onUpdateModeChange: (com.theblankstate.preamble.notification.NotificationUpdatePreference) -> Unit,
    onRequestIgnoreBattery: () -> Unit,
    scaleFactor: Float = 1f
) {
    val context = LocalContext.current
    var showAutoStartSheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)) {
        SettingsGroupSection(title = "Notifications & Frequency", scaleFactor = scaleFactor) {
            SettingsToggleItemRow(
                title = "Permanent Notification",
                subtitle = "Quick Add & Voice Task from notification bar",
                icon = Icons.Default.NotificationsActive,
                iconTint = Color(0xFFF9AB00),
                iconBgColor = Color(0xFFFEF7E0),
                checked = notificationPrefEnabled && hasSystemNotificationPermission,
                onToggle = onNotificationPrefToggle,
                scaleFactor = scaleFactor
            )

            if (!hasSystemNotificationPermission) {
                TextButton(
                    onClick = onNotifPermissionRequest,
                    modifier = Modifier.padding(start = 12.dp * scaleFactor, bottom = 4.dp * scaleFactor),
                    shape = CircleShape
                ) { Text("Grant Notification Permission", style = MaterialTheme.typography.labelMedium.copy(fontSize = (12 * scaleFactor).sp)) }
            }

            if (notificationPrefEnabled) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp * scaleFactor, vertical = 8.dp * scaleFactor)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Update Frequency", style = MaterialTheme.typography.bodyLarge.copy(fontSize = (14 * scaleFactor).sp, fontWeight = FontWeight.Medium))
                        Text(
                            when (notificationUpdateMode) {
                                com.theblankstate.preamble.notification.NotificationUpdatePreference.AGGRESSIVE -> "10s"
                                com.theblankstate.preamble.notification.NotificationUpdatePreference.BALANCED -> "30s"
                                com.theblankstate.preamble.notification.NotificationUpdatePreference.BATTERY_SAVER -> "2 min"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = (13 * scaleFactor).sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        when (notificationUpdateMode) {
                            com.theblankstate.preamble.notification.NotificationUpdatePreference.AGGRESSIVE -> "Fastest — changes show in ~10 sec, uses more battery"
                            com.theblankstate.preamble.notification.NotificationUpdatePreference.BALANCED -> "Recommended — changes show in ~30 sec"
                            com.theblankstate.preamble.notification.NotificationUpdatePreference.BATTERY_SAVER -> "Battery saver — updates every 2 min"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scaleFactor).sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    val sliderValue = when (notificationUpdateMode) {
                        com.theblankstate.preamble.notification.NotificationUpdatePreference.BATTERY_SAVER -> 0f
                        com.theblankstate.preamble.notification.NotificationUpdatePreference.BALANCED -> 1f
                        com.theblankstate.preamble.notification.NotificationUpdatePreference.AGGRESSIVE -> 2f
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { raw ->
                            val mode = when (raw.toInt()) {
                                0 -> com.theblankstate.preamble.notification.NotificationUpdatePreference.BATTERY_SAVER
                                1 -> com.theblankstate.preamble.notification.NotificationUpdatePreference.BALANCED
                                else -> com.theblankstate.preamble.notification.NotificationUpdatePreference.AGGRESSIVE
                            }
                            onUpdateModeChange(mode)
                        },
                        valueRange = 0f..2f,
                        steps = 1,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("2 min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("30 sec", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("10 sec", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            val eveningEnabled = remember { mutableStateOf(com.theblankstate.preamble.notification.EveningReminderScheduler.isEnabled(context)) }
            SettingsToggleItemRow(
                title = "Morning Briefing",
                subtitle = "Daily 6 AM summary of today's tasks",
                icon = Icons.Default.WbSunny,
                iconTint = Color(0xFFF9AB00),
                iconBgColor = Color(0xFFFEF7E0),
                checked = eveningEnabled.value,
                onToggle = {
                    eveningEnabled.value = it
                    com.theblankstate.preamble.notification.EveningReminderScheduler.setEnabled(context, it)
                },
                scaleFactor = scaleFactor
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SettingsItemRow(
                    title = "Battery Optimization",
                    subtitle = if (isBatteryOptimized) "Status: Optimized - may stop notification bar" else "Status: Unrestricted",
                    icon = Icons.Default.BatteryChargingFull,
                    iconTint = Color(0xFFD93025),
                    iconBgColor = Color(0xFFFCE8E6),
                    scaleFactor = scaleFactor,
                    trailingContent = {
                        if (isBatteryOptimized) {
                            val optInteraction = remember { MutableInteractionSource() }
                            Surface(
                                onClick = onRequestIgnoreBattery,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier.expressivePressScale(optInteraction)
                            ) {
                                Text(
                                    "Unrestrict",
                                    modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = (12 * scaleFactor).sp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                )
            }

            SettingsItemRow(
                title = "Fix: Always-ready task bar",
                subtitle = "Fix background notification stopping issue in 2 steps",
                icon = Icons.Default.Build,
                iconTint = Color(0xFFD93025),
                iconBgColor = Color(0xFFFCE8E6),
                scaleFactor = scaleFactor,
                onClick = { showAutoStartSheet = true },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )
        }

        if (showAutoStartSheet) {
            val batteryUnrestricted = !isBatteryOptimized
            ModalBottomSheet(
                onDismissRequest = { showAutoStartSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFCE8E6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = Color(0xFFD93025),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "Fix Always-Ready Task Bar",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Prevent your phone from stopping Preamble in background",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ReimaginedNotifFixStep(
                                number = 1,
                                title = "Set Battery to Unrestricted",
                                body = if (batteryUnrestricted) "Already set to Unrestricted — active." else "Prevents the OS from putting Preamble to sleep.",
                                done = batteryUnrestricted,
                                icon = Icons.Default.BatteryChargingFull,
                                action = {
                                    if (!batteryUnrestricted) {
                                        val btnInteraction = remember { MutableInteractionSource() }
                                        Surface(
                                            onClick = onRequestIgnoreBattery,
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            tonalElevation = 0.dp,
                                            shadowElevation = 0.dp,
                                            modifier = Modifier.fillMaxWidth().height(42.dp).expressivePressScale(btnInteraction)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("Set Unrestricted", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    }
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                            ReimaginedNotifFixStep(
                                number = 2,
                                title = "Pin Preamble in Recent Apps",
                                body = "Open recent apps → long-press Preamble → tap the lock 🔒 icon to pin.",
                                done = false,
                                icon = Icons.Default.PushPin,
                                action = {}
                            )
                        }
                    }

                    val doneInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { showAutoStartSheet = false },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .expressivePressScale(doneInteraction)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Done", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

@Composable
private fun ReimaginedNotifFixStep(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    icon: ImageVector,
    action: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (done) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (done) Icons.Default.Check else icon,
                contentDescription = null,
                tint = if (done) Color(0xFF1E8E3E) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                if (done) {
                    Surface(shape = CircleShape, color = Color(0xFFE6F4EA)) {
                        Text("Active", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E8E3E), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            if (!done) {
                Spacer(Modifier.height(10.dp))
                action()
            }
        }
    }
}

/**
 * Alarms & Haptics Subscreen
 */
@Composable
private fun AlarmsAndGeneralSubscreen(
    alarmToneName: String,
    onPickAlarmTone: () -> Unit,
    scaleFactor: Float = 1f
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)) {
        SettingsGroupSection(title = "Alarms & Vibration", scaleFactor = scaleFactor) {
            SettingsItemRow(
                title = "Alarm Tone",
                subtitle = alarmToneName,
                icon = Icons.Default.Alarm,
                iconTint = Color(0xFFF57C00),
                iconBgColor = Color(0xFFFFF3E0),
                scaleFactor = scaleFactor,
                onClick = onPickAlarmTone,
                trailingContent = {
                    Text("Change", style = MaterialTheme.typography.labelLarge.copy(fontSize = (13 * scaleFactor).sp), color = MaterialTheme.colorScheme.primary)
                }
            )

            var hapticEnabled by remember {
                mutableStateOf(
                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                        .getBoolean("haptic_feedback_enabled", true)
                )
            }
            SettingsToggleItemRow(
                title = "Haptic Feedback",
                subtitle = "Vibrate on task completion & touch actions",
                icon = Icons.Default.Vibration,
                iconTint = Color(0xFF3F51B5),
                iconBgColor = Color(0xFFE8EAF6),
                checked = hapticEnabled,
                onToggle = { enabled ->
                    hapticEnabled = enabled
                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("haptic_feedback_enabled", enabled).apply()
                },
                scaleFactor = scaleFactor
            )
        }
        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

/**
 * Support & About Subscreen
 */
@Composable
private fun SupportAndAboutSubscreen(
    onOpenReportProblem: () -> Unit,
    onOpenMyReports: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenReview: () -> Unit,
    scaleFactor: Float = 1f
) {
    val context = LocalContext.current
    var showChangelogSheetManual by remember { mutableStateOf(false) }

    if (showChangelogSheetManual) {
        com.theblankstate.preamble.ui.components.ChangelogSheet(
            onDismissRequest = { showChangelogSheetManual = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)) {
        SettingsGroupSection(title = "Support & Feedback", scaleFactor = scaleFactor) {
            SettingsItemRow(
                title = "Report a problem",
                subtitle = "Send screenshots or videos to our support team",
                icon = Icons.Default.BugReport,
                iconTint = Color(0xFF00ACC1),
                iconBgColor = Color(0xFFE0F7FA),
                scaleFactor = scaleFactor,
                onClick = onOpenReportProblem,
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            SettingsItemRow(
                title = "My problem reports",
                subtitle = "View submitted reports and admin responses",
                icon = Icons.Default.Assignment,
                iconTint = Color(0xFF00ACC1),
                iconBgColor = Color(0xFFE0F7FA),
                scaleFactor = scaleFactor,
                onClick = onOpenMyReports,
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            SettingsItemRow(
                title = "Email us",
                subtitle = "theblankstate@theblankstate.com",
                icon = Icons.Default.Email,
                iconTint = Color(0xFF00ACC1),
                iconBgColor = Color(0xFFE0F7FA),
                scaleFactor = scaleFactor,
                onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:theblankstate@theblankstate.com"))) }
            )
            SettingsItemRow(
                title = "GitHub Repository",
                subtitle = "github.com/Harry0M/Preamble",
                icon = Icons.Default.Code,
                iconTint = Color(0xFF00ACC1),
                iconBgColor = Color(0xFFE0F7FA),
                scaleFactor = scaleFactor,
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Harry0M"))) }
            )
            SettingsItemRow(
                title = "Official Website",
                subtitle = "theblankstate.com",
                icon = Icons.Default.Language,
                iconTint = Color(0xFF00ACC1),
                iconBgColor = Color(0xFFE0F7FA),
                scaleFactor = scaleFactor,
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://theblankstate.com"))) }
            )
        }

        SettingsGroupSection(title = "About & Legal", scaleFactor = scaleFactor) {
            SettingsItemRow(
                title = "What's New in v1.6",
                subtitle = "Habits, custom events, and smarter AI",
                icon = Icons.Default.Info,
                iconTint = Color(0xFF546E7A),
                iconBgColor = Color(0xFFECEFF1),
                scaleFactor = scaleFactor,
                onClick = { showChangelogSheetManual = true },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            SettingsItemRow(
                title = "Privacy Policy",
                subtitle = "Data, AI services, Google integrations and analytics",
                icon = Icons.Default.Security,
                iconTint = Color(0xFF546E7A),
                iconBgColor = Color(0xFFECEFF1),
                scaleFactor = scaleFactor,
                onClick = onOpenPrivacy,
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            SettingsItemRow(
                title = "Terms and Conditions",
                subtitle = "Usage rules, AI limitations and service availability",
                icon = Icons.Default.Gavel,
                iconTint = Color(0xFF546E7A),
                iconBgColor = Color(0xFFECEFF1),
                scaleFactor = scaleFactor,
                onClick = onOpenTerms,
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            SettingsItemRow(
                title = "Open-source licenses",
                subtitle = "Attribution for open-source libraries",
                icon = Icons.Default.Description,
                iconTint = Color(0xFF546E7A),
                iconBgColor = Color(0xFFECEFF1),
                scaleFactor = scaleFactor,
                onClick = onOpenLicenses,
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }
        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

/**
 * Dedicated Subscreen to list My Problem Reports with Pagination
 */
@Composable
private fun MyReportsSubscreen(
    onSelectReport: (ProblemReport) -> Unit,
    scaleFactor: Float = 1f
) {
    val currentUser by AuthManager.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<ProblemReport>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    val pageSize = 5

    fun refreshReports() {
        scope.launch {
            isLoading = true
            errorText = null
            runCatching { ProblemReportRepository.loadMyReports() }
                .onSuccess { reports = it }
                .onFailure { errorText = it.message ?: "Could not load problem reports." }
            isLoading = false
        }
    }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) refreshReports()
    }

    val totalPages = (reports.size + pageSize - 1).coerceAtLeast(1) / pageSize
    val pagedReports = reports.drop(currentPage * pageSize).take(pageSize)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)) {
        SettingsGroupSection(title = "Submitted Problem Reports", scaleFactor = scaleFactor) {
            Column(modifier = Modifier.padding(6.dp * scaleFactor)) {
                if (currentUser == null) {
                    Text(
                        "Sign in to view your problem reports.",
                        modifier = Modifier.padding(12.dp * scaleFactor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp * scaleFactor),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading reports...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (reports.isEmpty()) {
                    Text(
                        "No problem reports submitted yet.",
                        modifier = Modifier.padding(16.dp * scaleFactor),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    pagedReports.forEachIndexed { index, report ->
                        SettingsItemRow(
                            title = report.title,
                            subtitle = listOfNotNull(
                                formatReportDate(report.createdAt),
                                if (!report.adminNote.isNullOrBlank()) "Response received" else null
                            ).joinToString(" • "),
                            icon = when (report.status) {
                                ProblemReport.STATUS_RESOLVED -> Icons.Default.CheckCircle
                                ProblemReport.STATUS_IN_PROGRESS -> Icons.Default.HourglassTop
                                else -> Icons.Default.BugReport
                            },
                            iconTint = when (report.status) {
                                ProblemReport.STATUS_RESOLVED -> Color(0xFF1E8E3E)
                                ProblemReport.STATUS_IN_PROGRESS -> Color(0xFF1A73E8)
                                else -> Color(0xFFF9AB00)
                            },
                            iconBgColor = when (report.status) {
                                ProblemReport.STATUS_RESOLVED -> Color(0xFFE6F4EA)
                                ProblemReport.STATUS_IN_PROGRESS -> Color(0xFFE8F0FE)
                                else -> Color(0xFFFEF7E0)
                            },
                            scaleFactor = scaleFactor,
                            onClick = { onSelectReport(report) },
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ProblemReportStatusChip(report.status)
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        )
                        if (index != pagedReports.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }

        // Paging Navigation Controls
        if (reports.size > pageSize) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val prevInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { if (currentPage > 0) currentPage-- },
                    enabled = currentPage > 0,
                    shape = CircleShape,
                    color = if (currentPage > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.expressivePressScale(prevInteraction)
                ) {
                    Text(
                        "← Previous",
                        modifier = Modifier.padding(horizontal = 14.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = (12 * scaleFactor).sp),
                        color = if (currentPage > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }

                Text(
                    "Page ${currentPage + 1} of $totalPages",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = (12 * scaleFactor).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val nextInteraction = remember { MutableInteractionSource() }
                Surface(
                    onClick = { if (currentPage < totalPages - 1) currentPage++ },
                    enabled = currentPage < totalPages - 1,
                    shape = CircleShape,
                    color = if (currentPage < totalPages - 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.expressivePressScale(nextInteraction)
                ) {
                    Text(
                        "Next →",
                        modifier = Modifier.padding(horizontal = 14.dp * scaleFactor, vertical = 8.dp * scaleFactor),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = (12 * scaleFactor).sp),
                        color = if (currentPage < totalPages - 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

/**
 * Dedicated Subscreen for Report Details with Expandable/Compressible Response Text
 */
@Composable
private fun ReportDetailSubscreen(
    report: ProblemReport,
    scaleFactor: Float = 1f
) {
    var isAdminNoteExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)) {
        SettingsGroupSection(title = "Report Information", scaleFactor = scaleFactor) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp * scaleFactor),
                verticalArrangement = Arrangement.spacedBy(10.dp * scaleFactor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatReportDate(report.createdAt),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scaleFactor).sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ProblemReportStatusChip(report.status)
                }

                Text(
                    report.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = (17 * scaleFactor).sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    report.description,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * scaleFactor).sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (report.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Attachments (${report.attachments.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = (13 * scaleFactor).sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    report.attachments.forEach { att ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    att.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scaleFactor).sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!report.adminNote.isNullOrBlank()) {
            SettingsGroupSection(title = "Admin Response", scaleFactor = scaleFactor) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp * scaleFactor),
                    verticalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
                ) {
                    val noteText = report.adminNote ?: ""
                    Text(
                        text = noteText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * scaleFactor).sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (isAdminNoteExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (noteText.length > 120) {
                        val expandInteraction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { isAdminNoteExpanded = !isAdminNoteExpanded },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.expressivePressScale(expandInteraction)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    if (isAdminNoteExpanded) "Show less ▲" else "Show more ▼",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = (12 * scaleFactor).sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

/**
 * Clean borderless visual anchor item row with Home-Screen matched typography.
 */
@Composable
private fun SettingsItemRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBgColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    scaleFactor: Float = 1f
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp * scaleFactor))
            .then(
                if (onClick != null) Modifier
                    .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                    .expressivePressScale(interactionSource)
                else Modifier
            )
            .padding(vertical = 10.dp * scaleFactor, horizontal = 10.dp * scaleFactor),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp * scaleFactor)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp * scaleFactor)
                .clip(CircleShape)
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp * scaleFactor)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (14 * scaleFactor).sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp * scaleFactor))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = (12 * scaleFactor).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/**
 * Settings item row with built-in toggle switch.
 */
@Composable
private fun SettingsToggleItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBgColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    scaleFactor: Float = 1f
) {
    SettingsItemRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        iconBgColor = iconBgColor,
        scaleFactor = scaleFactor,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        }
    )
}

/**
 * Container card section grouping related settings.
 */
@Composable
private fun SettingsGroupSection(
    title: String,
    scaleFactor: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp * scaleFactor)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = (13 * scaleFactor).sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp * scaleFactor, bottom = 2.dp * scaleFactor)
        )
        Surface(
            shape = RoundedCornerShape(22.dp * scaleFactor),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp * scaleFactor),
                verticalArrangement = Arrangement.spacedBy(2.dp * scaleFactor),
                content = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsSubscreen(
    scaleFactor: Float = 1f,
) {
    val context = LocalContext.current

    var showMemorySheet by remember { mutableStateOf(false) }
    var showProcessLogSheet by remember { mutableStateOf(false) }
    var showExperimentalSheet by remember { mutableStateOf(false) }
    var smartModeEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .getBoolean("ai_smart_mode", true)
        )
    }
    var aiControlSheetEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .getBoolean("ai_control_task_sheet", true)
        )
    }
    var subtaskIntensity by remember {
        mutableStateOf(
            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .getInt("ai_subtask_intensity", 2)
        )
    }
    var notifEditEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .getBoolean("ai_notif_edit", false)
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor),
    ) {
        SettingsGroupSection(title = "Preamble AI Memory & Automation", scaleFactor = scaleFactor) {
            SettingsToggleItemRow(
                title = "Smart Preamble AI (long-term memory)",
                subtitle = "AI remembers your name, goals and preferences across chats.",
                icon = Icons.Default.Psychology,
                iconTint = Color(0xFF00897B),
                iconBgColor = Color(0xFFE0F2F1),
                checked = smartModeEnabled,
                onToggle = { enabled ->
                    smartModeEnabled = enabled
                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("ai_smart_mode", enabled).apply()
                },
                scaleFactor = scaleFactor
            )

            SettingsItemRow(
                title = "What Preamble AI remembers",
                subtitle = "View or delete the facts AI has learned about you",
                icon = Icons.Default.Psychology,
                iconTint = Color(0xFF00897B),
                iconBgColor = Color(0xFFE0F2F1),
                scaleFactor = scaleFactor,
                onClick = { showMemorySheet = true },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )

            SettingsItemRow(
                title = "Preamble AI activity",
                subtitle = "See every AI call, what it did and how long it took",
                icon = Icons.Default.Timeline,
                iconTint = Color(0xFF00897B),
                iconBgColor = Color(0xFFE0F2F1),
                scaleFactor = scaleFactor,
                onClick = { showProcessLogSheet = true },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            )

            SettingsToggleItemRow(
                title = "Let Preamble AI control task sheet",
                subtitle = "AI auto-fills date, time, priority and tags as you type",
                icon = Icons.Default.AutoFixHigh,
                iconTint = Color(0xFF00897B),
                iconBgColor = Color(0xFFE0F2F1),
                checked = aiControlSheetEnabled,
                onToggle = { enabled ->
                    aiControlSheetEnabled = enabled
                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("ai_control_task_sheet", enabled).apply()
                },
                scaleFactor = scaleFactor
            )

            SmartTaskBreakdownSetting(
                value = subtaskIntensity,
                onChange = { value ->
                    subtaskIntensity = value
                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                        .edit().putInt("ai_subtask_intensity", value).apply()
                },
                scaleFactor = scaleFactor
            )

            AiNotificationEditSetting(
                enabled = notifEditEnabled,
                onChange = { enabled ->
                    if (enabled) {
                        showExperimentalSheet = true
                    } else {
                        notifEditEnabled = false
                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("ai_notif_edit", false).apply()
                    }
                },
                scaleFactor = scaleFactor
            )
        }

        SettingsGroupSection(title = "Preamble AI Reminders", scaleFactor = scaleFactor) {
            AiReminderSettingsCard()
        }

        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }

    if (showMemorySheet) {
        AiMemoryBottomSheet(onDismiss = { showMemorySheet = false })
    }
    if (showProcessLogSheet) {
        AiProcessLogBottomSheet(onDismiss = { showProcessLogSheet = false })
    }
    if (showExperimentalSheet) {
        AiNotificationEditWarningSheet(
            onDismiss = { showExperimentalSheet = false },
            onConfirm = {
                notifEditEnabled = true
                context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("ai_notif_edit", true).apply()
                showExperimentalSheet = false
            },
        )
    }
}

@Composable
private fun PersonalTouchSettingsSubscreen(
    scaleFactor: Float = 1f,
) {
    val context = LocalContext.current
    val pmGreeting by ThemePreferences.pmGreeting.collectAsState()
    val pmSmartProgress by ThemePreferences.pmSmartProgress.collectAsState()
    val pmLateNight by ThemePreferences.pmLateNight.collectAsState()
    val pmSmartEmpty by ThemePreferences.pmSmartEmpty.collectAsState()
    val pmLastTask by ThemePreferences.pmLastTask.collectAsState()
    val pmStreakWarn by ThemePreferences.pmStreakWarn.collectAsState()
    val pmBests by ThemePreferences.pmBests.collectAsState()
    val pmMilestones by ThemePreferences.pmMilestones.collectAsState()
    val pmSparkle by ThemePreferences.pmSparkle.collectAsState()
    val pmEasterEgg by ThemePreferences.pmEasterEgg.collectAsState()
    val pmVariableRewards by ThemePreferences.pmVariableRewards.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor),
    ) {
        SettingsGroupSection(title = "Personal Touches", scaleFactor = scaleFactor) {
            SettingsToggleItemRow(
                title = "Time-aware greeting",
                subtitle = "Good morning greeting - updates through the day",
                icon = Icons.Default.WbSunny,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmGreeting,
                onToggle = { ThemePreferences.setPmGreeting(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Smart progress messages",
                subtitle = "Motivation messages that adapt to your completion pace",
                icon = Icons.Default.TrendingUp,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmSmartProgress,
                onToggle = { ThemePreferences.setPmSmartProgress(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Late-night care banner",
                subtitle = "Gentle reminder to rest when using app after 11 PM",
                icon = Icons.Default.NightsStay,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmLateNight,
                onToggle = { ThemePreferences.setPmLateNight(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Variable rewards",
                subtitle = "Unpredictable haptics and cheers on task completion",
                icon = Icons.Default.CardGiftcard,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmVariableRewards,
                onToggle = { ThemePreferences.setPmVariableRewards(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Contextual empty state",
                subtitle = "Time-aware messages when your task list is empty",
                icon = Icons.Default.Inbox,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmSmartEmpty,
                onToggle = { ThemePreferences.setPmSmartEmpty(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Last-task amplification",
                subtitle = "Extra motivation when only 1 task remains",
                icon = Icons.Default.CheckCircleOutline,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmLastTask,
                onToggle = { ThemePreferences.setPmLastTask(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Streak at-risk warning",
                subtitle = "Notifies you when your streak is about to break",
                icon = Icons.Default.LocalFireDepartment,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmStreakWarn,
                onToggle = { ThemePreferences.setPmStreakWarn(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Personal bests",
                subtitle = "Toast when you beat your all-time daily task count",
                icon = Icons.Default.EmojiEvents,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmBests,
                onToggle = { ThemePreferences.setPmBests(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Streak milestone celebrations",
                subtitle = "Special message at 7, 14, 30, 50, and 100-day streaks",
                icon = Icons.Default.Star,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmMilestones,
                onToggle = { ThemePreferences.setPmMilestones(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Completion sparkle",
                subtitle = "Glow animation when you complete all tasks for the day",
                icon = Icons.Default.AutoAwesome,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmSparkle,
                onToggle = { ThemePreferences.setPmSparkle(context, it) },
                scaleFactor = scaleFactor
            )

            SettingsToggleItemRow(
                title = "Hidden easter egg",
                subtitle = "Tap the app title 7 times to discover a secret message",
                icon = Icons.Default.Casino,
                iconTint = Color(0xFFD81B60),
                iconBgColor = Color(0xFFFCE4EC),
                checked = pmEasterEgg,
                onToggle = { ThemePreferences.setPmEasterEgg(context, it) },
                scaleFactor = scaleFactor
            )
        }

        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

@Composable
private fun ProblemReportSettingsSubscreen(
    scaleFactor: Float = 1f,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AuthManager.currentUser.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<List<ProblemReportRepository.DraftMedia>>(emptyList()) }
    var reports by remember { mutableStateOf<List<ProblemReport>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUser?.uid) {
        if (currentUser != null) {
            runCatching { ProblemReportRepository.loadMyReports() }
                .onSuccess { reports = it }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { ProblemReportRepository.inspectMedia(context, uris) }
                .onSuccess { inspected ->
                    selectedMedia = (selectedMedia + inspected).distinctBy { it.uri.toString() }
                    errorText = null
                }
                .onFailure { errorText = it.message ?: "Could not attach selected media." }
        }
    }

    val activeReports = reports.filter { !it.isResolved }
    val hasUnresolvedReport = activeReports.isNotEmpty()
    val canSubmit = currentUser != null &&
        !hasUnresolvedReport &&
        !isSubmitting &&
        title.isNotBlank() &&
        description.isNotBlank()

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor),
    ) {
        SettingsGroupSection(title = "New Report", scaleFactor = scaleFactor) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp * scaleFactor),
                verticalArrangement = Arrangement.spacedBy(12.dp * scaleFactor),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Attach images or videos up to 50 MB each. New reports stay locked while one is open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                if (hasUnresolvedReport) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFF3E0),
                    ) {
                        Text(
                            "Your current report is ${activeReports.first().statusLabel.lowercase()}. You can send another once admin marks it resolved.",
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE65100),
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(120) },
                    enabled = currentUser != null && !hasUnresolvedReport && !isSubmitting,
                    label = { Text("Problem title") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(2000) },
                    enabled = currentUser != null && !hasUnresolvedReport && !isSubmitting,
                    label = { Text("What happened?") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (selectedMedia.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedMedia.forEach { media ->
                            ProblemReportMediaRow(
                                media = media,
                                enabled = !isSubmitting,
                                onRemove = {
                                    selectedMedia = selectedMedia.filterNot { it.uri == media.uri }
                                },
                            )
                        }
                    }
                }

                errorText?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { mediaPicker.launch(arrayOf("image/*", "video/*")) },
                        enabled = currentUser != null && !hasUnresolvedReport && !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Attach")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                isSubmitting = true
                                errorText = null
                                runCatching {
                                    ProblemReportRepository.submitReport(
                                        context = context,
                                        title = title,
                                        description = description,
                                        media = selectedMedia,
                                    )
                                }.onSuccess {
                                    title = ""
                                    description = ""
                                    selectedMedia = emptyList()
                                    Toast.makeText(context, "Report sent", Toast.LENGTH_SHORT).show()
                                    reports = ProblemReportRepository.loadMyReports()
                                }.onFailure {
                                    errorText = it.message ?: "Could not send report."
                                }
                                isSubmitting = false
                            }
                        },
                        enabled = canSubmit,
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSubmitting) "Sending" else "Send")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

@Composable
private fun ProblemReportStatusChip(status: String) {
    val (background, content, icon) = when (status) {
        ProblemReport.STATUS_RESOLVED -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Filled.CheckCircle)
        ProblemReport.STATUS_IN_PROGRESS -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Icons.Filled.HourglassTop)
        else -> Triple(Color(0xFFFFF3E0), Color(0xFFE65100), Icons.Filled.BugReport)
    }
    Surface(shape = CircleShape, color = background) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = content)
            Text(
                when (status) {
                    ProblemReport.STATUS_RESOLVED -> "Resolved"
                    ProblemReport.STATUS_IN_PROGRESS -> "In review"
                    else -> "Open"
                },
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProblemReportMediaRow(
    media: ProblemReportRepository.DraftMedia,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(media.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(
                    formatReportBytes(media.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(Icons.Filled.Close, contentDescription = "Remove attachment")
            }
        }
    }
}

@Composable
private fun SmartTaskBreakdownSetting(
    value: Int,
    onChange: (Int) -> Unit,
    scaleFactor: Float = 1f
) {
    val intensityLabels = listOf("Off", "Light", "Balanced", "Aggressive")
    val intensityDescs = listOf(
        "No auto-subtasks. Only extract when you list items.",
        "Subtasks only for clearly multi-step planning tasks.",
        "Subtasks for events, trips, projects, preparations.",
        "Subtasks for almost any task with 2+ natural steps.",
    )
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp * scaleFactor)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Smart Task Breakdown", style = MaterialTheme.typography.bodyLarge.copy(fontSize = (14 * scaleFactor).sp, fontWeight = FontWeight.SemiBold))
            Text(
                intensityLabels[value],
                style = MaterialTheme.typography.labelMedium.copy(fontSize = (13 * scaleFactor).sp),
                color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            intensityDescs[value],
            style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scaleFactor).sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 2.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 3)) },
            valueRange = 0f..3f,
            steps = 2,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun AiNotificationEditSetting(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    scaleFactor: Float = 1f
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp * scaleFactor),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Preamble AI edit from notification", style = MaterialTheme.typography.bodyLarge.copy(fontSize = (14 * scaleFactor).sp, fontWeight = FontWeight.SemiBold))
                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFF3E0)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color(0xFFE65100))
                        Text("Experimental", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                "Control tasks with voice commands in notification bar",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scaleFactor).sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiNotificationEditWarningSheet(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF3E0)) {
                    Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.padding(10.dp).size(28.dp), tint = Color(0xFFE65100))
                }
                Column {
                    Text("Experimental Feature", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Preamble AI edit from notification", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("What this feature does:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "\"gym cancel karo\" deletes your Gym task",
                    "\"meeting ko kal shift karo\" moves Meeting to tomorrow",
                    "\"hospital urgent kar do\" sets Hospital task to High priority",
                    "\"camping alarm 7:30\" adds reminder to Camping task",
                ).forEach { example ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = MaterialTheme.colorScheme.primary)
                        Text(example, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF8E1)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFFF57F17))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Use with caution", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFF57F17))
                        Text("AI uses fuzzy matching to find tasks. It may occasionally modify or delete the wrong task if multiple tasks have similar names.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF795548))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = CircleShape) { Text("Cancel") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f), shape = CircleShape) { Text("I Understand, Enable") }
            }
        }
    }
}

private fun formatReportDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown date"
    return SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun formatReportBytes(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val mb = bytes / (1024f * 1024f)
    return if (mb >= 1f) {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    } else {
        String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
    }
}

@Composable
private fun OssLicensesSubscreen(
    modifier: Modifier = Modifier,
    scaleFactor: Float = 1f
) {
    val licenses = listOf(
        Triple("Jetpack Compose", "Android's modern toolkit for building native UI.", "Apache 2.0"),
        Triple("Kotlin Coroutines", "Asynchronous programming with coroutines.", "Apache 2.0"),
        Triple("Material Components", "Modular and customizable Material Design UI components.", "Apache 2.0"),
        Triple("Firebase Android SDK", "Google Firebase services for app development.", "Apache 2.0"),
        Triple("Google AI Client SDK", "Google Generative AI SDK for Android.", "Apache 2.0"),
        Triple("Room Persistence Library", "Abstraction layer over SQLite.", "Apache 2.0"),
        Triple("WorkManager", "Background task scheduling library.", "Apache 2.0"),
    )

    Column(
        modifier = modifier.padding(vertical = 12.dp * scaleFactor),
        verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)
    ) {
        SettingsGroupSection(title = "Open-Source Libraries", scaleFactor = scaleFactor) {
            licenses.forEachIndexed { index, (name, desc, license) ->
                LibraryItemRow(name = name, desc = desc, license = license, scaleFactor = scaleFactor)
                if (index != licenses.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp * scaleFactor))
    }
}

@Composable
private fun LibraryItemRow(name: String, desc: String, license: String, scaleFactor: Float = 1f) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp * scaleFactor),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = (14 * scaleFactor).sp, fontWeight = FontWeight.SemiBold))
            Text(desc, style = MaterialTheme.typography.bodySmall.copy(fontSize = (12 * scaleFactor).sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
            Text(
                license,
                modifier = Modifier.padding(horizontal = 10.dp * scaleFactor, vertical = 4.dp * scaleFactor),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scaleFactor).sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun areNotificationsEnabled(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.areNotificationsEnabled()
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(PowerManager::class.java)
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

private fun getCurrentAlarmToneName(context: Context): String {
    val prefs = context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
    val savedUri = prefs.getString("alarm_tone_uri", null)
    return if (savedUri != null) {
        try {
            RingtoneManager.getRingtone(context, Uri.parse(savedUri))?.getTitle(context) ?: "Default Alarm"
        } catch (_: Exception) { "Default Alarm" }
    } else "Default Alarm"
}

@Composable
private fun ThemePickerCard(context: Context) {
    val currentMode by ThemePreferences.themeMode.collectAsState()
    val currentColor by ThemePreferences.themeColor.collectAsState()
    val materialYou by ThemePreferences.materialYou.collectAsState()

    val palePresets = listOf(
        Color(0xFFF5EBE0), Color(0xFFE8EDDF), Color(0xFFFCE4E4),
        Color(0xFFE4E6F1), Color(0xFFFFF3C4), Color(0xFFD6EAF8),
    )
    val vividPresets = listOf(
        Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
        Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4),
        Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A),
        Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
        Color(0xFF795548), Color(0xFF9E9E9E), Color(0xFF607D8B)
    )
    val modes = listOf(
        ThemePreferences.ThemeMode.LIGHT  to "Light",
        ThemePreferences.ThemeMode.DARK   to "Dark",
        ThemePreferences.ThemeMode.AMOLED to "AMOLED",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Theme Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))

        // Full Rounded Corner Chips (RoundedCornerShape(50) / CircleShape)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (mode, label) ->
                val active = !materialYou && currentMode == mode
                val pillBg by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ), label = "pillBg_$label"
                )
                val pillTextColor by animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200), label = "pillText_$label"
                )
                val pillScale by animateFloatAsState(
                    targetValue = if (active) 1.05f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ), label = "pillScale_$label"
                )
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { scaleX = pillScale; scaleY = pillScale }
                        .clip(RoundedCornerShape(50))
                        .background(pillBg)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            ThemePreferences.setMaterialYou(context, false)
                            ThemePreferences.setThemeMode(context, mode)
                        }
                        .expressivePressScale(interactionSource)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = pillTextColor)
                }
            }
            // Material You pill
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val youBg by animateColorAsState(
                    targetValue = if (materialYou) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ), label = "youBg"
                )
                val youScale by animateFloatAsState(
                    targetValue = if (materialYou) 1.05f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ), label = "youScale"
                )
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { scaleX = youScale; scaleY = youScale }
                        .clip(RoundedCornerShape(50))
                        .background(youBg)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            ThemePreferences.setMaterialYou(context, true)
                            ThemePreferences.setColor(context, null)
                        }
                        .expressivePressScale(interactionSource)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You", style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (materialYou) FontWeight.Bold else FontWeight.Medium,
                        color = if (materialYou) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Color circles without text labels ("PALE", "COLORS" text removed!)
        AnimatedVisibility(
            visible = !materialYou,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(220)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(150))
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                Text("Theme Color", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))

                // Pale row without text label
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(palePresets.size) { i ->
                        val c = palePresets[i]
                        val isActive = !materialYou && currentColor == c
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.24f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ), label = "ps$i"
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) else Color.Transparent,
                            animationSpec = tween(200), label = "pc$i"
                        )
                        val circleInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape)
                                .background(c)
                                .border(if (isActive) 2.dp else 0.dp, borderColor, CircleShape)
                                .clickable(interactionSource = circleInteraction, indication = null) { ThemePreferences.setColor(context, c) }
                                .expressivePressScale(circleInteraction, pressedScale = 0.88f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Vivid row without text label
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        val isActive = currentColor == null
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.24f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ), label = "ms"
                        )
                        val circleInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape)
                                .background(Color(0xFFEEEEEE))
                                .border(if (isActive) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape)
                                .clickable(interactionSource = circleInteraction, indication = null) { ThemePreferences.setColor(context, null) }
                                .expressivePressScale(circleInteraction, pressedScale = 0.88f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF333333)))
                        }
                    }
                    items(vividPresets.size) { i ->
                        val c = vividPresets[i]
                        val isActive = !materialYou && currentColor == c
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.24f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ), label = "vs$i"
                        )
                        val circleInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape)
                                .background(c)
                                .border(if (isActive) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape)
                                .clickable(interactionSource = circleInteraction, indication = null) { ThemePreferences.setColor(context, c) }
                                .expressivePressScale(circleInteraction, pressedScale = 0.88f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
