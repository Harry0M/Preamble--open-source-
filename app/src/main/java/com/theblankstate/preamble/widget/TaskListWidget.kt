package com.theblankstate.preamble.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.theblankstate.preamble.MainActivity
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.R
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

class TaskListWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PreambleApplication
        val today = TaskRepository.todayString()
        val tasksFlow = app.database.taskDao().observePendingTasksForDate(today)

        provideContent {
            GlanceTheme {
                TaskWidgetContent(tasksFlow)
            }
        }
    }

    @Composable
    private fun TaskWidgetContent(tasksFlow: Flow<List<Task>>) {
        val tasks by tasksFlow.collectAsState(initial = emptyList())

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.surface)
        ) {
            // Header row
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Tasks",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Image(
                    provider = ImageProvider(R.drawable.ic_add_widget),
                    contentDescription = "Add Task",
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickable(
                            actionStartActivity(
                                Intent(LocalContext.current, MainActivity::class.java)
                            )
                        )
                )
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "You're all caught up",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 14.sp
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(tasks, itemId = { it.id.hashCode().toLong() }) { task ->
                        WidgetTaskRow(task)
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetTaskRow(task: Task) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckBox(
                checked = false,
                onCheckedChange = actionRunCallback<ToggleTaskAction>(
                    actionParametersOf(TaskIdKey to task.id)
                ),
                modifier = GlanceModifier.padding(end = 8.dp)
            )

            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = task.title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    maxLines = 1
                )
                if (task.deadlineTime != null) {
                    Text(
                        text = task.deadlineTime,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.primary
                        )
                    )
                }
            }

            if (task.priority > 0) {
                Spacer(modifier = GlanceModifier.width(4.dp))
                Box(
                    modifier = GlanceModifier
                        .size(8.dp)
                        .cornerRadius(4.dp)
                        .background(
                            when (task.priority) {
                                3 -> ColorProvider(ComposeColor(0xFFEF4444))
                                2 -> ColorProvider(ComposeColor(0xFFF97316))
                                1 -> ColorProvider(ComposeColor(0xFF3B82F6))
                                else -> ColorProvider(ComposeColor.Transparent)
                            }
                        )
                ) {}
            }
        }
    }
}
