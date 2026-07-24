package com.theblankstate.preamble.ui.components

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.theblankstate.preamble.ui.screens.TaskTimerScreen
import com.theblankstate.preamble.ui.viewmodels.TaskTimerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTimerSheet(
    onDismiss: () -> Unit,
    taskId: String? = null,
    taskTitle: String? = null
) {
    val context = LocalContext.current
    val timerViewModel: TaskTimerViewModel = viewModel(
        factory = TaskTimerViewModel.Factory(context.applicationContext as Application)
    )

    TaskTimerScreen(
        timerViewModel = timerViewModel,
        initialTaskId = taskId,
        initialTaskTitle = taskTitle,
        onBack = onDismiss
    )
}
