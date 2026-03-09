package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.theblankstate.preamble.notification.TaskNotificationService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDebugScreen() {
    val context = LocalContext.current
    var serviceRunning by remember { mutableStateOf(false) }
    var lastCheckTime by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        while (true) {
            val am = context.getSystemService(android.app.ActivityManager::class.java)
            serviceRunning = am.getRunningServices(Integer.MAX_VALUE).any { 
                it.service.className == "com.theblankstate.preamble.notification.TaskNotificationService"
            }
            lastCheckTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            delay(2000)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Debug") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (serviceRunning) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Service Status",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (serviceRunning) "✅ Running" else "❌ Stopped",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Last check: $lastCheckTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Button(
                onClick = { TaskNotificationService.start(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start/Restart Service")
            }
            
            Button(
                onClick = { TaskNotificationService.stop(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Stop Service (Test)")
            }
            
            OutlinedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Notification Permissions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    val nm = context.getSystemService(android.app.NotificationManager::class.java)
                    val notificationsEnabled = nm.areNotificationsEnabled()
                    
                    Text(
                        text = if (notificationsEnabled) 
                            "✅ Notifications Enabled" 
                        else 
                            "❌ Notifications Disabled",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    if (!notificationsEnabled) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Notification Settings")
                        }
                    }
                }
            }
            
            OutlinedCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "How to Fix",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = """
                            1. Enable notifications in app settings
                            2. Tap "Start/Restart Service" button
                            3. Pull down notification tray
                            4. You should see "Preamble" notification
                            5. Try swiping it - it should NOT close
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
