package com.theblankstate.preamble

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.preamble.notification.TaskNotificationManager
import com.theblankstate.preamble.ui.screens.CalendarScreen
import com.theblankstate.preamble.ui.screens.HomeScreen
import com.theblankstate.preamble.ui.screens.OnboardingScreen
import com.theblankstate.preamble.ui.screens.SettingsScreen
import com.theblankstate.preamble.ui.screens.StatsScreen
import com.theblankstate.preamble.ui.theme.PreambleTheme
import com.theblankstate.preamble.ui.theme.ThemePreferences
import com.theblankstate.preamble.viewmodel.TaskViewModel
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.init(this)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("preamble_prefs", MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean("onboarding_done", false)

        setContent {
            PreambleTheme {
                var showOnboarding by remember { mutableStateOf(!onboardingDone) }

                if (showOnboarding) {
                    OnboardingScreen(
                        onComplete = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            showOnboarding = false
                            requestExactAlarmPermission()
                            postNotification()
                        }
                    )
                } else {
                    val app = application as PreambleApplication
                    val viewModel: TaskViewModel = viewModel(
                        factory = TaskViewModel.Factory(app.repository, app)
                    )
                    PreambleApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("preamble_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false)) {
            postNotification()
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) { }
            }
        }
    }

    private fun postNotification() {
        MainScope().launch {
            TaskNotificationManager.updateNotification(this@MainActivity)
        }
    }
}

@Composable
fun PreambleApp(viewModel: TaskViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tasks by viewModel.todayTasks.collectAsState()
    val pastTasks by viewModel.pastTasks.collectAsState()
    val stats by viewModel.statsState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                NavigationBar(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Tasks") },
                        label = { Text("Tasks") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            viewModel.refreshStats()
                        },
                        icon = { Icon(Icons.Filled.Analytics, contentDescription = "Stats") },
                        label = { Text("Stats") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                        label = { Text("Calendar") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> HomeScreen(
                tasks = tasks,
                pastTasks = pastTasks,
                streak = stats.streak,
                onAddTask = { title, date, deadlineTime -> viewModel.addTask(title, date, deadlineTime) },
                onToggleTask = { viewModel.toggleTask(it) },
                onDeleteTask = { viewModel.deleteTask(it) },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> StatsScreen(
                statsState = stats,
                modifier = Modifier.padding(innerPadding)
            )
            2 -> CalendarScreen(
                selectedDateTasks = viewModel.selectedDateTasks.collectAsState().value,
                heatMap = viewModel.calendarHeatMap.collectAsState().value,
                onDateSelected = { viewModel.selectDate(it) },
                onMonthChanged = { year, month -> viewModel.loadHeatMap(year, month) },
                modifier = Modifier.padding(innerPadding)
            )
            3 -> SettingsScreen(
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
