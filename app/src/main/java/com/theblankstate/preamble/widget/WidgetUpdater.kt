package com.theblankstate.preamble.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetUpdater {
    fun refresh(context: Context) {
        CoroutineScope(Dispatchers.Main).launch {
            TaskListWidget().updateAll(context)
        }
    }
}
