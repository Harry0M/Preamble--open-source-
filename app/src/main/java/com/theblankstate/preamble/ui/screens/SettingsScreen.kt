package com.theblankstate.preamble.ui.screens

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.ui.components.ColorPickerComponent
import com.theblankstate.preamble.ui.theme.ThemePreferences
import com.theblankstate.preamble.auth.AuthManager
import com.theblankstate.preamble.sync.GoogleCalendarManager
import com.theblankstate.preamble.sync.GoogleTasksManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as PreambleApplication
    val scope = app.appScope

    var hasSystemNotificationPermission by remember { mutableStateOf(areNotificationsEnabled(context)) }
    var notificationPrefEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                .getBoolean("notification_enabled", true)
        )
    }

    var alarmToneName by remember { mutableStateOf(getCurrentAlarmToneName(context)) }
    var showReviewSheet by remember { mutableStateOf(false) }

    // Auth state
    val currentUser by AuthManager.currentUser.collectAsState()
    var signInLoading by remember { mutableStateOf(false) }
    var signOutLoading by remember { mutableStateOf(false) }

    // Google Calendar & Tasks state (unified)
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

    // Google sign-in launcher (grants Calendar + Tasks scopes together)
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
                // Sync both Calendar + Tasks
                scope.launch {
                    try {
                        val app = context.applicationContext as PreambleApplication
                        val events = GoogleCalendarManager.fetchCalendarEvents(context)
                        app.repository.syncCalendarEvents(events)
                        val gTasks = GoogleTasksManager.fetchGoogleTasks(context)
                        app.repository.syncGoogleTasks(gTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                        Toast.makeText(context, "Synced ${events.size} events + ${gTasks.size} tasks", Toast.LENGTH_SHORT).show()
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    googleLinkLoading = false
                }
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

    // Check for review prompt (after 2 days)
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
            // ── Account ──
            SectionTitle("Account")
            SettingsCard {
                if (currentUser != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentUser?.displayName ?: "User",
                                style = MaterialTheme.typography.bodyLarge
                            )
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
                                        val app = context.applicationContext as PreambleApplication
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


            // ── Google Calendar & Tasks (unified) ──
            SectionTitle("Google Calendar & Tasks")
            SettingsCard {
                Column {
                    if (googleLinked) {
                        // Linked state — show account info + Sync Now + Unlink
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Linked",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                                        val app = context.applicationContext as PreambleApplication
                                        app.repository.clearCalendarEvents()
                                        app.repository.clearGoogleTasks()
                                        GoogleCalendarManager.unlink(context)
                                        GoogleTasksManager.unlink(context)
                                        Toast.makeText(context, "Google Calendar & Tasks unlinked", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = CircleShape
                            ) {
                                Text("Unlink")
                            }
                        }

                        HorizontalDivider()

                        // Sync Now — both Calendar + Tasks
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !googleSyncing) {
                                    scope.launch {
                                        try {
                                            val app = context.applicationContext as PreambleApplication
                                            val events = GoogleCalendarManager.fetchCalendarEvents(context)
                                            app.repository.syncCalendarEvents(events)
                                            val gTasks = GoogleTasksManager.fetchGoogleTasks(context)
                                            app.repository.syncGoogleTasks(gTasks, GoogleTasksManager.autoDeleteGoogleTasks.value)
                                            Toast.makeText(context, "Synced ${events.size} events + ${gTasks.size} tasks", Toast.LENGTH_SHORT).show()
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

                        // Voice tasks sync toggle
                        val syncVoice by GoogleTasksManager.syncVoiceTasks.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sync Voice Tasks", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Auto-add voice tasks to Google Tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = syncVoice,
                                onCheckedChange = {
                                    GoogleTasksManager.setSyncVoiceTasks(context, it)
                                }
                            )
                        }

                        HorizontalDivider()

                        // Auto-delete from app when deleted from Google
                        val autoDelete by GoogleTasksManager.autoDeleteGoogleTasks.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-delete synced tasks", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Delete from app when removed from Google Tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Switch(
                                checked = autoDelete,
                                onCheckedChange = {
                                    GoogleTasksManager.setAutoDeleteGoogleTasks(context, it)
                                }
                            )
                        }
                    } else {
                        // Not linked — single link button for both
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

            // ── Appearance ──
            SectionTitle("Appearance")
            SettingsCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Theme Color", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Customize the app's primary color",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        ColorPickerComponent()
                    }

                    HorizontalDivider()

                    ThemeSelectorRow(context)
                }
            }

            // ── Notifications ──
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
                            Text(
                                "Quick Add & Voice Task from notification bar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = notificationPrefEnabled && hasSystemNotificationPermission,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (!hasSystemNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        notificationPrefEnabled = true
                                        context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                            .edit().putBoolean("notification_enabled", true).apply()
                                        com.theblankstate.preamble.notification.TaskNotificationService.start(context)
                                    }
                                } else {
                                    notificationPrefEnabled = false
                                    context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                        .edit().putBoolean("notification_enabled", false).apply()
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
                    
                }
            }

            // ── Alarms ──
            SectionTitle("Alarms")
            SettingsCard(
                modifier = Modifier.clickable {
                    ringtonePickerLauncher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Tone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            val saved = context.getSharedPreferences("preamble_prefs", Context.MODE_PRIVATE)
                                .getString("alarm_tone_uri", null)
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
                        Text(
                            alarmToneName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── Support ──
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

            // ── Legal ──
            SectionTitle("Legal")
            SettingsCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { /* In a real app, open URL */ }
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        "Preamble is fully open-source and respects your privacy. Tasks are always saved locally first; if you sign in, your tasks are securely synced to your own Firebase account for backup and realtime sync. We do not run ads or third-party analytics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        "Terms of Service",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        "Preamble is provided as-is under the Apache License 2.0. You may use, modify, and distribute it freely. The developers are not liable for any damages arising from use of this software.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // ── Open Source ──
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

            // ── Rate Us ──
            Button(
                onClick = { showReviewSheet = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape
            ) { Text("⭐ Rate Preamble") }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Review popup
    if (showReviewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showReviewSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("⭐", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Enjoying Preamble?",
                    style = MaterialTheme.typography.headlineSmall
                )
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
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        content = content
    )
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
    val colorfulCards by ThemePreferences.colorfulCards.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Column {
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
                Text(
                    currentMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        if (currentMode == ThemePreferences.ThemeMode.LIGHT || (currentMode == ThemePreferences.ThemeMode.SYSTEM && !androidx.compose.foundation.isSystemInDarkTheme())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Colorful Task Cards", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Use different light shaded backgrounds for task cards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = colorfulCards,
                    onCheckedChange = { ThemePreferences.setColorfulCards(context, it) }
                )
            }
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
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
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

