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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.buildAnnotatedString
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
    Ai,
    Personal,
    ReportProblem,
    OssLicenses,
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

    BackHandler(enabled = settingsSubscreen != SettingsSubscreen.Main) {
        settingsSubscreen = SettingsSubscreen.Main
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

    val pmGreeting       by ThemePreferences.pmGreeting.collectAsState()
    val pmSmartProgress  by ThemePreferences.pmSmartProgress.collectAsState()
    val pmLateNight      by ThemePreferences.pmLateNight.collectAsState()
    val pmSmartEmpty     by ThemePreferences.pmSmartEmpty.collectAsState()
    val pmLastTask       by ThemePreferences.pmLastTask.collectAsState()
    val pmStreakWarn     by ThemePreferences.pmStreakWarn.collectAsState()
    val pmBests          by ThemePreferences.pmBests.collectAsState()
    val pmMilestones     by ThemePreferences.pmMilestones.collectAsState()
    val pmSparkle        by ThemePreferences.pmSparkle.collectAsState()
    val pmEasterEgg      by ThemePreferences.pmEasterEgg.collectAsState()
    val pmVariableRewards by ThemePreferences.pmVariableRewards.collectAsState()
    val colorfulCards    by ThemePreferences.colorfulCards.collectAsState()
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (settingsSubscreen) {
                            SettingsSubscreen.Main -> "Settings"
                            SettingsSubscreen.Ai -> "Preamble AI"
                            SettingsSubscreen.Personal -> "Personal touches"
                            SettingsSubscreen.ReportProblem -> "Report a problem"
                            SettingsSubscreen.OssLicenses -> "Open-source licenses"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (settingsSubscreen != SettingsSubscreen.Main) {
                        IconButton(onClick = { settingsSubscreen = SettingsSubscreen.Main }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        }
    ) { padding ->
        when (settingsSubscreen) {
            SettingsSubscreen.Ai -> {
                AiSettingsSubscreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                )
                return@Scaffold
            }
            SettingsSubscreen.Personal -> {
                PersonalTouchSettingsSubscreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                )
                return@Scaffold
            }
            SettingsSubscreen.ReportProblem -> {
                ProblemReportSettingsSubscreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                )
                return@Scaffold
            }
            SettingsSubscreen.OssLicenses -> {
                OssLicensesSubscreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
                return@Scaffold
            }
            SettingsSubscreen.Main -> Unit
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            SectionTitle("Account")
            SettingsCard {
                if (currentUser != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentUser?.displayName ?: "User", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                currentUser?.email ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (signOutLoading) return@OutlinedButton
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
                            enabled = !signOutLoading,
                            shape = CircleShape
                        ) {
                            if (signOutLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Sign Out")
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !signInLoading) {
                                signInLoading = true
                                scope.launch {
                                    AuthManager.signInWithGoogle(context)
                                    signInLoading = false
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Sign in with Google", style = MaterialTheme.typography.bodyLarge)
                        if (signInLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            SectionTitle("Google Sync")
            SettingsCard {
                Column {
                    if (googleLinked) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Linked", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    calendarEmail ?: "Google Account",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (lastSyncTime > 0) {
                                    val syncTimeStr = remember(lastSyncTime) {
                                        java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
                                            .format(java.util.Date(lastSyncTime))
                                    }
                                    Text(
                                        "Last synced: $syncTimeStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        app.repository.clearCalendarEvents()
                                        app.repository.clearGoogleTasks()
                                        GoogleCalendarManager.unlink(context)
                                        GoogleTasksManager.unlink(context)
                                        Toast.makeText(context, "Google Calendar & Tasks unlinked", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = CircleShape
                            ) { Text("Unlink") }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !googleSyncing) {
                                    scope.launch {
                                        try {
                                            val summary = GoogleSyncCoordinator.syncLinkedData(
                                                context = context,
                                                forceFull = false,
                                                isManual = true,
                                                reason = "settings_sync_now"
                                            )
                                            Toast.makeText(
                                                context,
                                                "Updated ${summary.calendarEvents} events + ${summary.googleTasks} tasks",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (e: Throwable) {
                                            Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Sync Now", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Fetch calendar events & Google Tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            if (googleSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                        val syncVoice by GoogleTasksManager.syncVoiceTasks.collectAsState()
                        SettingsToggleRow(
                            title = "Sync Voice Tasks",
                            subtitle = "Auto-add voice tasks to Google Tasks",
                            checked = syncVoice,
                            onToggle = { GoogleTasksManager.setSyncVoiceTasks(context, it) }
                        )
                        HorizontalDivider()
                        val autoDelete by GoogleTasksManager.autoDeleteGoogleTasks.collectAsState()
                        SettingsToggleRow(
                            title = "Auto-delete synced tasks",
                            subtitle = "Delete from app when removed from Google Tasks",
                            checked = autoDelete,
                            onToggle = { GoogleTasksManager.setAutoDeleteGoogleTasks(context, it) }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !googleLinkLoading) {
                                    googleLinkLoading = true
                                    val signInClient = GoogleCalendarManager.getSignInClient(context)
                                    googleSignInLauncher.launch(signInClient.signInIntent)
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Link Google Calendar & Tasks", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Sync events, holidays & tasks from Google",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            if (googleLinkLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            SectionTitle("Appearance")
            SettingsCard {
                Column {
                    ThemePickerCard(context)
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Colorful Task Cards",
                        subtitle = "Give each task card a unique color tint",
                        checked = colorfulCards,
                        onToggle = { v -> ThemePreferences.setColorfulCards(context, v) }
                    )
                }
            }

            var showAutoStartSheet by remember { mutableStateOf(false) }

            SectionTitle("Notifications")
            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permanent Notification", style = MaterialTheme.typography.bodyLarge)
                            Text("Quick Add & Voice Task from notification bar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = notificationPrefEnabled && hasSystemNotificationPermission,
                            onCheckedChange = { enable ->
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
                            }
                        )
                    }
                    if (!hasSystemNotificationPermission) {
                        TextButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    })
                                }
                            },
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                            shape = CircleShape
                        ) { Text("Grant Permission") }
                    }
                    if (notificationPrefEnabled) {
                        HorizontalDivider()
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Update Frequency", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    when (notificationUpdateMode) {
                                        com.theblankstate.preamble.notification.NotificationUpdatePreference.AGGRESSIVE -> "10s"
                                        com.theblankstate.preamble.notification.NotificationUpdatePreference.BALANCED -> "30s"
                                        com.theblankstate.preamble.notification.NotificationUpdatePreference.BATTERY_SAVER -> "2 min"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                when (notificationUpdateMode) {
                                    com.theblankstate.preamble.notification.NotificationUpdatePreference.AGGRESSIVE -> "Fastest — changes show in ~10 sec, uses more battery"
                                    com.theblankstate.preamble.notification.NotificationUpdatePreference.BALANCED -> "Recommended — changes show in ~30 sec"
                                    com.theblankstate.preamble.notification.NotificationUpdatePreference.BATTERY_SAVER -> "Battery saver — updates every 2 min"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            // Slider: 0 = 2 min (Battery Saver), 1 = 30s (Balanced), 2 = 10s (Aggressive)
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
                                    notificationUpdateMode = mode
                                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                        .edit().putString("notification_update_mode", mode.name).apply()
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
                    HorizontalDivider()
                    val eveningEnabled = remember { mutableStateOf(com.theblankstate.preamble.notification.EveningReminderScheduler.isEnabled(context)) }
                    SettingsToggleRow(
                        title = "Morning Briefing",
                        subtitle = "Daily 6 AM summary of today's tasks",
                        checked = eveningEnabled.value,
                        onToggle = {
                            eveningEnabled.value = it
                            com.theblankstate.preamble.notification.EveningReminderScheduler.setEnabled(context, it)
                        }
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery Optimization", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (isBatteryOptimized) "Status: Optimized - may stop the notification on some devices" else "Status: Unrestricted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            if (isBatteryOptimized) {
                                TextButton(onClick = { requestIgnoreBatteryOptimizations(context) }, shape = CircleShape) { Text("Set to Unrestricted") }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fix: Always-ready task bar not showing", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Some phones stop the task bar in background. Fix in 2 steps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        TextButton(
                            onClick = { showAutoStartSheet = true },
                            shape = CircleShape
                        ) { Text("Fix") }
                    }
                }
            }

            if (showAutoStartSheet) {
                val batteryUnrestricted = !isBatteryOptimized
                ModalBottomSheet(
                    onDismissRequest = { showAutoStartSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 40.dp)
                    ) {
                        Text(
                            "Fix: Always-ready task bar not showing",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            "3 quick steps to stop your phone from killing Preamble in background.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Step 1 — Battery unrestricted
                        NotifFixStep(
                            number = 1,
                            title = "Set Battery to Unrestricted",
                            body = if (batteryUnrestricted)
                                "Already set to Unrestricted — nothing to do here."
                            else
                                "Prevents the phone from killing Preamble in background.",
                            done = batteryUnrestricted
                        ) {
                            if (!batteryUnrestricted) {
                                Button(
                                    onClick = { requestIgnoreBatteryOptimizations(context) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Set to Unrestricted") }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Step 2 — Pin in recents (always manual)
                        NotifFixStep(
                            number = 2,
                            title = "Pin Preamble in Recent Apps",
                            body = "Open recent apps → long-press the Preamble card → tap the lock 🔒 icon to pin it.",
                            done = false
                        ) {}

                        Spacer(Modifier.height(24.dp))
                        TextButton(
                            onClick = { showAutoStartSheet = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Done") }
                    }
                }
            }

            SectionTitle("Preamble AI")
            SettingsCard {
                SettingsNavigationRow(
                    title = "Preamble AI",
                    subtitle = "Memory, AI task controls and reminders",
                    onClick = { settingsSubscreen = SettingsSubscreen.Ai },
                )
            }

            SectionTitle("Personal touches")
            SettingsCard {
                SettingsNavigationRow(
                    title = "Personal touches",
                    subtitle = "Greetings, progress messages and celebration behavior",
                    onClick = { settingsSubscreen = SettingsSubscreen.Personal },
                )
            }

            if (settingsSubscreen == SettingsSubscreen.Ai) {
            SectionTitle("Preamble AI")
            var showMemorySheet by remember { mutableStateOf(false) }
            var showProcessLogSheet by remember { mutableStateOf(false) }
            SettingsCard {
                Column {
                    var smartModeEnabled by remember {
                        mutableStateOf(
                            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                .getBoolean("ai_smart_mode", true)
                        )
                    }
                    SettingsToggleRow(
                        title = "Smart Preamble AI (long-term memory)",
                        subtitle = "AI remembers your name, goals and preferences across chats. Turn off to use the simple assistant.",
                        checked = smartModeEnabled,
                        onToggle = { enabled ->
                            smartModeEnabled = enabled
                            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).edit().putBoolean("ai_smart_mode", enabled).apply()
                        }
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showMemorySheet = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("What Preamble AI remembers", style = MaterialTheme.typography.bodyLarge)
                            Text("View or delete the facts AI has learned about you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showProcessLogSheet = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Preamble AI activity", style = MaterialTheme.typography.bodyLarge)
                            Text("See every AI call, what it did and how long it took", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    var aiControlSheetEnabled by remember {
                        mutableStateOf(
                            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                .getBoolean("ai_control_task_sheet", true)
                        )
                    }
                    SettingsToggleRow(
                        title = "Let Preamble AI control task sheet",
                        subtitle = "AI auto-fills date, time, priority and tags as you type",
                        checked = aiControlSheetEnabled,
                        onToggle = { enabled ->
                            aiControlSheetEnabled = enabled
                            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).edit().putBoolean("ai_control_task_sheet", enabled).apply()
                        }
                    )
                    HorizontalDivider()
                    var subtaskIntensity by remember {
                        mutableStateOf(
                            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                .getInt("ai_subtask_intensity", 2)
                        )
                    }
                    val intensityLabels = listOf("Off", "Light", "Balanced", "Aggressive")
                    val intensityDescs = listOf(
                        "No auto-subtasks. Only extract when you list items.",
                        "Subtasks only for clearly multi-step planning tasks.",
                        "Subtasks for events, trips, projects, preparations.",
                        "Subtasks for almost any task with 2+ natural steps."
                    )
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Smart Task Breakdown", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                intensityLabels[subtaskIntensity],
                                style = MaterialTheme.typography.labelMedium,
                                color = if (subtaskIntensity == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            intensityDescs[subtaskIntensity],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Slider(
                            value = subtaskIntensity.toFloat(),
                            onValueChange = {
                                val v = it.toInt().coerceIn(0, 3)
                                if (v != subtaskIntensity) {
                                    subtaskIntensity = v
                                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                        .edit().putInt("ai_subtask_intensity", v).apply()
                                }
                            },
                            valueRange = 0f..3f,
                            steps = 2,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                    HorizontalDivider()
                    var notifEditEnabled by remember {
                        mutableStateOf(
                            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                .getBoolean("ai_notif_edit", false)
                        )
                    }
                    var showExperimentalSheet by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Preamble AI edit from notification", style = MaterialTheme.typography.bodyLarge)
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFFF3E0)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Filled.Science, contentDescription = null, modifier = Modifier.size(11.dp), tint = Color(0xFFE65100))
                                        Text("Experimental", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Text("Control tasks with voice commands in the notification bar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = notifEditEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) { showExperimentalSheet = true }
                                else {
                                    notifEditEnabled = false
                                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).edit().putBoolean("ai_notif_edit", false).apply()
                                }
                            }
                        )
                    }
                    if (showExperimentalSheet) {
                        ModalBottomSheet(
                            onDismissRequest = { showExperimentalSheet = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                        "\"camping alarm 7:30\" adds reminder to Camping task"
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
                                        verticalAlignment = Alignment.Top
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
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(onClick = { showExperimentalSheet = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                                    Button(
                                        onClick = {
                                            notifEditEnabled = true
                                            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).edit().putBoolean("ai_notif_edit", true).apply()
                                            showExperimentalSheet = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("I Understand, Enable") }
                                }
                            }
                        }
                    }
                }
            }

            if (showMemorySheet) {
                AiMemoryBottomSheet(onDismiss = { showMemorySheet = false })
            }
            if (showProcessLogSheet) {
                AiProcessLogBottomSheet(onDismiss = { showProcessLogSheet = false })
            }

            SectionTitle("Preamble AI Reminders")
            AiReminderSettingsCard()
            }

            SectionTitle("Alarms")
            SettingsCard(
                modifier = Modifier.clickable {
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
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Alarm Tone", style = MaterialTheme.typography.bodyLarge)
                        Text(alarmToneName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                    Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (settingsSubscreen == SettingsSubscreen.Personal) {
            SectionTitle("Personal Touches")
            SettingsCard {
                Column {
                    PersonalToggle(
                        label = "Time-aware greeting",
                        sub = "Good morning greeting - updates through the day",
                        checked = pmGreeting,
                        onToggle = { ThemePreferences.setPmGreeting(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Smart progress messages",
                        sub = "Motivation messages that adapt to your completion pace",
                        checked = pmSmartProgress,
                        onToggle = { ThemePreferences.setPmSmartProgress(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Late-night care banner",
                        sub = "Gentle reminder to rest when you use the app after 11 PM",
                        checked = pmLateNight,
                        onToggle = { ThemePreferences.setPmLateNight(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Variable rewards",
                        sub = "Unpredictable haptics and cheers on task completion",
                        checked = pmVariableRewards,
                        onToggle = { ThemePreferences.setPmVariableRewards(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Contextual empty state",
                        sub = "Time-aware messages when your task list is empty",
                        checked = pmSmartEmpty,
                        onToggle = { ThemePreferences.setPmSmartEmpty(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Last-task amplification",
                        sub = "Extra motivation when only 1 task remains",
                        checked = pmLastTask,
                        onToggle = { ThemePreferences.setPmLastTask(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Streak at-risk warning",
                        sub = "Notifies you when your streak is about to break",
                        checked = pmStreakWarn,
                        onToggle = { ThemePreferences.setPmStreakWarn(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Personal bests",
                        sub = "Toast when you beat your all-time daily task count",
                        checked = pmBests,
                        onToggle = { ThemePreferences.setPmBests(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Streak milestone celebrations",
                        sub = "Special message at 7, 14, 30, 50, and 100-day streaks",
                        checked = pmMilestones,
                        onToggle = { ThemePreferences.setPmMilestones(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Completion sparkle",
                        sub = "Glow animation when you complete all tasks for the day",
                        checked = pmSparkle,
                        onToggle = { ThemePreferences.setPmSparkle(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Hidden easter egg",
                        sub = "Tap the app title 7 times to discover a secret message",
                        checked = pmEasterEgg,
                        onToggle = { ThemePreferences.setPmEasterEgg(context, it) }
                    )
                }
            }
            }

            SectionTitle("General")
            SettingsCard {
                var hapticEnabled by remember {
                    mutableStateOf(
                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                            .getBoolean("haptic_feedback_enabled", true)
                    )
                }
                SettingsToggleRow(
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on task completion & actions",
                    checked = hapticEnabled,
                    onToggle = { enabled ->
                        hapticEnabled = enabled
                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("haptic_feedback_enabled", enabled).apply()
                    }
                )
            }

            SectionTitle("Support")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SupportLink("Report a problem", "Send screenshots or videos and track the status") {
                        settingsSubscreen = SettingsSubscreen.ReportProblem
                    }
                    SupportLink("Email us", "theblankstate@theblankstate.com") {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:theblankstate@theblankstate.com")))
                    }
                    SupportLink("GitHub", "github.com/Harry0M") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Harry0M")))
                    }
                    SupportLink("Website", "theblankstate.com") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://theblankstate.com")))
                    }
                }
            }

            SectionTitle("Legal")
            SettingsCard {
                Column {
                    SettingsNavigationRow(
                        title = "Privacy Policy",
                        subtitle = "Data, AI services, Google integrations, analytics and ads",
                        onClick = { showPrivacySheet = true },
                    )
                    HorizontalDivider()
                    SettingsNavigationRow(
                        title = "Terms and Conditions",
                        subtitle = "Usage rules, AI limitations, ads and service availability",
                        onClick = { showTermsSheet = true },
                    )
                    HorizontalDivider()
                    SettingsNavigationRow(
                        title = "Open-source licenses",
                        subtitle = "Attribution for libraries that power Preamble",
                        onClick = { settingsSubscreen = SettingsSubscreen.OssLicenses },
                    )
                }
            }

            Button(
                onClick = { showReviewSheet = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape
            ) { Text("Rate Preamble ★") }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showReviewSheet) {
            ModalBottomSheet(
                onDismissRequest = { showReviewSheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("★★★★★", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Enjoying Preamble?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "If you find Preamble helpful, please take a moment to leave a review. It helps us grow and improve!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showReviewSheet = false
                                context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("has_reviewed", true).apply()
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) { Text("Later") }
                        Button(
                            onClick = {
                                showReviewSheet = false
                                context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("has_reviewed", true).apply()
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")))
                                } catch (_: Exception) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        ) { Text("Rate Now") }
                    }
                }
            }
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

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsSubscreen(
    modifier: Modifier = Modifier,
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
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SectionTitle("Preamble AI")
        SettingsCard {
            Column {
                SettingsToggleRow(
                    title = "Smart Preamble AI (long-term memory)",
                    subtitle = "AI remembers your name, goals and preferences across chats.",
                    checked = smartModeEnabled,
                    onToggle = { enabled ->
                        smartModeEnabled = enabled
                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("ai_smart_mode", enabled).apply()
                    }
                )
                HorizontalDivider()
                SettingsNavigationRow(
                    title = "What Preamble AI remembers",
                    subtitle = "View or delete the facts AI has learned about you",
                    onClick = { showMemorySheet = true },
                )
                HorizontalDivider()
                SettingsNavigationRow(
                    title = "Preamble AI activity",
                    subtitle = "See every AI call, what it did and how long it took",
                    onClick = { showProcessLogSheet = true },
                )
                HorizontalDivider()
                SettingsToggleRow(
                    title = "Let Preamble AI control task sheet",
                    subtitle = "AI auto-fills date, time, priority and tags as you type",
                    checked = aiControlSheetEnabled,
                    onToggle = { enabled ->
                        aiControlSheetEnabled = enabled
                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("ai_control_task_sheet", enabled).apply()
                    }
                )
                HorizontalDivider()
                SmartTaskBreakdownSetting(
                    value = subtaskIntensity,
                    onChange = { value ->
                        subtaskIntensity = value
                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                            .edit().putInt("ai_subtask_intensity", value).apply()
                    },
                )
                HorizontalDivider()
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
                )
            }
        }

        SectionTitle("Preamble AI Reminders")
        AiReminderSettingsCard()

        Spacer(modifier = Modifier.height(32.dp))
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
    modifier: Modifier = Modifier,
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
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SectionTitle("Personal Touches")
        SettingsCard {
            Column {
                PersonalToggle(
                    label = "Time-aware greeting",
                    sub = "Good morning greeting - updates through the day",
                    checked = pmGreeting,
                    onToggle = { ThemePreferences.setPmGreeting(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Smart progress messages",
                    sub = "Motivation messages that adapt to your completion pace",
                    checked = pmSmartProgress,
                    onToggle = { ThemePreferences.setPmSmartProgress(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Late-night care banner",
                    sub = "Gentle reminder to rest when you use the app after 11 PM",
                    checked = pmLateNight,
                    onToggle = { ThemePreferences.setPmLateNight(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Variable rewards",
                    sub = "Unpredictable haptics and cheers on task completion",
                    checked = pmVariableRewards,
                    onToggle = { ThemePreferences.setPmVariableRewards(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Contextual empty state",
                    sub = "Time-aware messages when your task list is empty",
                    checked = pmSmartEmpty,
                    onToggle = { ThemePreferences.setPmSmartEmpty(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Last-task amplification",
                    sub = "Extra motivation when only 1 task remains",
                    checked = pmLastTask,
                    onToggle = { ThemePreferences.setPmLastTask(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Streak at-risk warning",
                    sub = "Notifies you when your streak is about to break",
                    checked = pmStreakWarn,
                    onToggle = { ThemePreferences.setPmStreakWarn(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Personal bests",
                    sub = "Toast when you beat your all-time daily task count",
                    checked = pmBests,
                    onToggle = { ThemePreferences.setPmBests(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Streak milestone celebrations",
                    sub = "Special message at 7, 14, 30, 50, and 100-day streaks",
                    checked = pmMilestones,
                    onToggle = { ThemePreferences.setPmMilestones(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Completion sparkle",
                    sub = "Glow animation when you complete all tasks for the day",
                    checked = pmSparkle,
                    onToggle = { ThemePreferences.setPmSparkle(context, it) },
                )
                HorizontalDivider()
                PersonalToggle(
                    label = "Hidden easter egg",
                    sub = "Tap the app title 7 times to discover a secret message",
                    checked = pmEasterEgg,
                    onToggle = { ThemePreferences.setPmEasterEgg(context, it) },
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProblemReportSettingsSubscreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AuthManager.currentUser.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMedia by remember { mutableStateOf<List<ProblemReportRepository.DraftMedia>>(emptyList()) }
    var reports by remember { mutableStateOf<List<ProblemReport>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

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
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SectionTitle("Your reports")
        SettingsCard {
            Column {
                if (currentUser == null) {
                    Text(
                        "Sign in to report a problem and track its status.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading reports...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (reports.isEmpty()) {
                    Text(
                        "No reports yet. Add a clear title, details, and optional screenshots or videos.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    reports.forEachIndexed { index, report ->
                        ProblemReportStatusRow(report = report)
                        if (index != reports.lastIndex) HorizontalDivider()
                    }
                }
            }
        }

        SectionTitle("New report")
        SettingsCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                            "Attach images or videos up to 50 MB each. New reports stay locked while one is open or in review.",
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
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(2000) },
                    enabled = currentUser != null && !hasUnresolvedReport && !isSubmitting,
                    label = { Text("What happened?") },
                    minLines = 4,
                    maxLines = 8,
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

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProblemReportStatusRow(report: ProblemReport) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(report.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    formatReportDate(report.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ProblemReportStatusChip(report.status)
        }

        Text(
            report.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (report.attachments.isNotEmpty()) {
            Text(
                "${report.attachments.size} attachment${if (report.attachments.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!report.adminNote.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Admin note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    LinkifiedAdminNoteText(report.adminNote)
                }
            }
        }
    }
}

@Composable
private fun LinkifiedAdminNoteText(text: String) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurface
    val urlRegex = remember { Regex("""https?://[^\s]+""") }
    val annotated = remember(text, linkColor, bodyColor) {
        buildAnnotatedString {
            var cursor = 0
            urlRegex.findAll(text).forEach { match ->
                if (match.range.first > cursor) {
                    append(text.substring(cursor, match.range.first))
                }
                val rawUrl = match.value.trimEnd('.', ',', ')', ']')
                val trailing = match.value.removePrefix(rawUrl)
                pushStringAnnotation(tag = "URL", annotation = rawUrl)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(rawUrl)
                }
                pop()
                append(trailing)
                cursor = match.range.last + 1
            }
            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(color = bodyColor),
        onClick = { offset ->
            annotated
                .getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { annotation -> uriHandler.openUri(annotation.item) }
        },
    )
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
        tonalElevation = 1.dp,
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
) {
    val intensityLabels = listOf("Off", "Light", "Balanced", "Aggressive")
    val intensityDescs = listOf(
        "No auto-subtasks. Only extract when you list items.",
        "Subtasks only for clearly multi-step planning tasks.",
        "Subtasks for events, trips, projects, preparations.",
        "Subtasks for almost any task with 2+ natural steps.",
    )
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Smart Task Breakdown", style = MaterialTheme.typography.bodyLarge)
            Text(
                intensityLabels[value],
                style = MaterialTheme.typography.labelMedium,
                color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            intensityDescs[value],
            style = MaterialTheme.typography.bodySmall,
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Preamble AI edit from notification", style = MaterialTheme.typography.bodyLarge)
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
                "Control tasks with voice commands in the notification bar",
                style = MaterialTheme.typography.bodySmall,
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
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("I Understand, Enable") }
            }
        }
    }
}

@Composable
private fun NotifFixStep(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    action: @Composable () -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        androidx.compose.material3.Surface(
            shape = CircleShape,
            color = if (done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                if (done) {
                    Text("✓", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        "$number",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!done) {
                Spacer(Modifier.height(8.dp))
                action()
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun PersonalToggle(
    label: String,
    sub: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        tonalElevation = 1.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun SupportLink(label: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
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
private fun LibraryItem(name: String, desc: String, license: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Text(license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
    var expanded by remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))

        // Mode pills
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { (mode, label) ->
                val active = !materialYou && currentMode == mode
                val pillBg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ), label = "pillBg_$label"
                )
                val pillTextColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = androidx.compose.animation.core.tween(200), label = "pillText_$label"
                )
                val pillScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (active) 1.06f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    ), label = "pillScale_$label"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { scaleX = pillScale; scaleY = pillScale }
                        .clip(RoundedCornerShape(12.dp))
                        .background(pillBg)
                        .clickable {
                            ThemePreferences.setMaterialYou(context, false)
                            ThemePreferences.setThemeMode(context, mode)
                            expanded = true
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = pillTextColor)
                }
            }
            // Material You pill
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val youBg by androidx.compose.animation.animateColorAsState(
                    targetValue = if (materialYou) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ), label = "youBg"
                )
                val youScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (materialYou) 1.06f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    ), label = "youScale"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { scaleX = youScale; scaleY = youScale }
                        .clip(RoundedCornerShape(12.dp))
                        .background(youBg)
                        .clickable {
                            ThemePreferences.setMaterialYou(context, true)
                            ThemePreferences.setColor(context, null)
                            expanded = false
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("You", style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (materialYou) FontWeight.Bold else FontWeight.Normal,
                        color = if (materialYou) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Expandable color panel with spring animation
        AnimatedVisibility(
            visible = expanded && !materialYou,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                )
            ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)),
            exit = androidx.compose.animation.shrinkVertically(
                androidx.compose.animation.core.tween(180)
            ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
        ) {
            Column {
                Spacer(Modifier.height(18.dp))

                // PALE row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("PALE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        letterSpacing = 1.4.sp, modifier = Modifier.width(52.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(palePresets.size) { i ->
                            val c = palePresets[i]
                            val isActive = !materialYou && currentColor == c
                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isActive) 1.24f else 1f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                ), label = "ps$i"
                            )
                            val borderColor by animateColorAsState(
                                targetValue = if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) else Color.Transparent,
                                animationSpec = androidx.compose.animation.core.tween(200), label = "pc$i"
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (isActive) 2.dp else 0.dp, borderColor, CircleShape)
                                    .clickable { ThemePreferences.setColor(context, c) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // COLORS row
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("COLORS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        letterSpacing = 1.4.sp, modifier = Modifier.width(52.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            val isActive = currentColor == null
                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isActive) 1.24f else 1f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                ), label = "ms"
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(CircleShape)
                                    .background(Color(0xFFEEEEEE))
                                    .border(if (isActive) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape)
                                    .clickable { ThemePreferences.setColor(context, null) },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF333333)))
                            }
                        }
                        items(vividPresets.size) { i ->
                            val c = vividPresets[i]
                            val isActive = !materialYou && currentColor == c
                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isActive) 1.24f else 1f,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                ), label = "vs$i"
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .graphicsLayer { scaleX = scale; scaleY = scale }
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (isActive) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape)
                                    .clickable { ThemePreferences.setColor(context, c) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        if (!expanded && !materialYou) {
            Spacer(Modifier.height(8.dp))
            Text("Tap a mode to pick color", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f))
        }
    }
}
