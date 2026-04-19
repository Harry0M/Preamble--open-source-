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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.ads.FeatureGateManager
import com.theblankstate.preamble.auth.AuthManager
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleSyncCoordinator
import com.theblankstate.preamble.sync.GoogleTasksManager
import com.theblankstate.preamble.ui.components.ColorPickerComponent
import com.theblankstate.preamble.ui.components.FeatureType
import com.theblankstate.preamble.ui.components.FeatureUnlockSheet
import com.theblankstate.preamble.ui.theme.ThemePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    yearlyHeatmap: Map<String, Float> = emptyMap(),
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
    var showThemeUnlockSheet by remember { mutableStateOf(false) }
    val themeUnlocked by FeatureGateManager.themeUnlocked.collectAsState()
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
    val expressiveNav    by ThemePreferences.expressiveNav.collectAsState()
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
                Toast.makeText(context, "Syncing Google Calendar in background…", Toast.LENGTH_SHORT).show()
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
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { padding ->
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
                            Text("→", style = MaterialTheme.typography.titleLarge)
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
                                Text("↻", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
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
                                Text("→", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }

            SectionTitle("Appearance")
            SettingsCard {
                Column {
                    SettingsToggleRow(
                        title = "Expressive Navigation",
                        subtitle = if (expressiveNav) "Dynamic nav bar — tap icon to reveal label" else "Classic nav bar with icons and labels",
                        checked = expressiveNav,
                        onToggle = { ThemePreferences.setExpressiveNav(context, it) }
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Theme Color", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (themeUnlocked) "Customize the app's primary color" else "Watch ad to unlock customization",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        if (themeUnlocked) { ColorPickerComponent() }
                        else {
                            Icon(
                                imageVector = Icons.Outlined.Lock, contentDescription = "Locked",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp).clickable { showThemeUnlockSheet = true }
                            )
                        }
                    }
                    HorizontalDivider()
                    if (themeUnlocked) {
                        ThemeSelectorRow(context)
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showThemeUnlockSheet = true }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("App Theme", style = MaterialTheme.typography.bodyLarge)
                                Text("Locked — Watch ad to unlock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                            Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                        }
                    }
                    HorizontalDivider()
                    SettingsToggleRow(
                        title = "Colorful Task Cards",
                        subtitle = "Give each task card a unique color tint",
                        checked = colorfulCards,
                        onToggle = { ThemePreferences.setColorfulCards(context, it) }
                    )
                }
            }

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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Update Frequency", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    when (notificationUpdateMode) {
                                        com.theblankstate.preamble.notification.NotificationUpdatePreference.AGGRESSIVE -> "Changes visible within 10 seconds (more battery usage)"
                                        com.theblankstate.preamble.notification.NotificationUpdatePreference.BALANCED -> "Changes visible within 30 seconds (recommended)"
                                        com.theblankstate.preamble.notification.NotificationUpdatePreference.BATTERY_SAVER -> "Updates every 2 min when no tasks (lowest battery use)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            com.theblankstate.preamble.notification.NotificationUpdatePreference.values().forEach { mode ->
                                FilterChip(
                                    selected = notificationUpdateMode == mode,
                                    onClick = {
                                        notificationUpdateMode = mode
                                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE).edit().putString("notification_update_mode", mode.name).apply()
                                    },
                                    label = { Text(mode.displayName) },
                                    modifier = Modifier.weight(1f)
                                )
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
                }
            }

            SectionTitle("AI")
            SettingsCard {
                Column {
                    var aiControlSheetEnabled by remember {
                        mutableStateOf(
                            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                .getBoolean("ai_control_task_sheet", true)
                        )
                    }
                    SettingsToggleRow(
                        title = "Let AI Control Task Sheet",
                        subtitle = "As you type, AI auto-fills time, date, priority, tags, and task type. Turn off for fully manual entry.",
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
                                .getInt("ai_subtask_intensity", 0)
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
                                Text("AI Edit from Notification", style = MaterialTheme.typography.bodyLarge)
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
                            Text("Type edit/delete commands in notification. AI may occasionally misidentify tasks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                                        Text("AI Edit from Notification", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text("What this feature does:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "\"gym cancel karo\" → deletes your Gym task",
                                        "\"meeting ko kal shift karo\" → moves Meeting to tomorrow",
                                        "\"hospital urgent kar do\" → sets Hospital task to High priority",
                                        "\"camping ka alarm 7:30 baje set karo\" → adds reminder to Camping task"
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

            SectionTitle("Personal Touches")
            SettingsCard {
                Column {
                    PersonalToggle(
                        label = "Time-aware greeting",
                        sub = "\"Good Morning, ${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName?.split(" ")?.firstOrNull() ?: "you"}\" — changes throughout the day",
                        checked = pmGreeting,
                        onToggle = { ThemePreferences.setPmGreeting(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Smart progress messages",
                        sub = "\"Gaining momentum!\" — adapts to your completion rate",
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
                        sub = "Unpredictable haptics and cheering messages when you complete tasks",
                        checked = pmVariableRewards,
                        onToggle = { ThemePreferences.setPmVariableRewards(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Contextual empty state",
                        sub = "\"Your day is a blank canvas\" — time-aware empty messages",
                        checked = pmSmartEmpty,
                        onToggle = { ThemePreferences.setPmSmartEmpty(context, it) }
                    )
                    HorizontalDivider()
                    PersonalToggle(
                        label = "Last-task amplification",
                        sub = "\"One task away from a perfect day\" — extra motivation at the finish",
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
                        sub = "\"New record!\" toast when you beat your all-time daily task count",
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
                        sub = "Subtle glow animation when you finish every task for the day",
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
                    SupportLink("✉ Email", "theblankstateteam@gmail.com") {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:theblankstateteam@gmail.com")))
                    }
                    SupportLink("🐙 GitHub", "Harry0M") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Harry0M")))
                    }
                    SupportLink("🌐 Website", "theblankstate.com") {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://theblankstate.com")))
                    }
                }
            }

            SectionTitle("Legal")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        "Preamble is fully open-source and respects your privacy. Tasks are always saved locally first; if you sign in, your tasks are securely synced to your own Firebase account for backup and realtime sync. We show ads (Google AdMob) to support development. No third-party analytics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Terms of Service",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                    Text(
                        "Preamble is provided as-is under the Apache License 2.0. You may use, modify, and distribute it freely. The developers are not liable for any damages arising from use of this software.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            SectionTitle("Open Source")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Licensed under Apache License 2.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    LibraryItem("Jetpack Compose", "UI Toolkit", "Apache 2.0")
                    LibraryItem("Room Database", "Local persistence", "Apache 2.0")
                    LibraryItem("Kotlin Coroutines", "Async operations", "Apache 2.0")
                    LibraryItem("Material Design 3", "Design system", "Apache 2.0")
                    LibraryItem("AndroidX Core", "Android extensions", "Apache 2.0")
                    LibraryItem("Navigation Compose", "Screen navigation", "Apache 2.0")
                }
            }

            Button(
                onClick = { showReviewSheet = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape
            ) { Text("⭐ Rate Preamble") }

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
                    Text("⭐", style = MaterialTheme.typography.displayMedium)
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
    }

    if (showThemeUnlockSheet && activity != null) {
        FeatureUnlockSheet(
            featureType = FeatureType.THEME,
            activity = activity,
            onDismiss = { showThemeUnlockSheet = false }
        )
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
    Column(modifier = modifier.fillMaxWidth(), content = content)
}

@Composable
private fun SupportLink(icon: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$icon  $label", style = MaterialTheme.typography.bodyLarge)
        Text("→", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
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
private fun ThemeSelectorRow(context: Context) {
    val currentMode by ThemePreferences.themeMode.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("App Theme", style = MaterialTheme.typography.bodyLarge)
            val displayName = if (currentMode == ThemePreferences.ThemeMode.AMOLED) "AMOLED Black" else currentMode.name.lowercase().replaceFirstChar { it.uppercase() }
            Text(
                displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    ThemePreferences.ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ThemePreferences.setThemeMode(context, mode)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentMode == mode,
                                onClick = {
                                    ThemePreferences.setThemeMode(context, mode)
                                    showDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val displayName = if (mode == ThemePreferences.ThemeMode.AMOLED) "AMOLED Black" else mode.name.lowercase().replaceFirstChar { it.uppercase() }
                            Text(displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
