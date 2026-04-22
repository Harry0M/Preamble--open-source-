package com.theblankstate.preamble.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object WeeklyWrappedScheduler {

    private const val REQUEST_CODE = 2101
    private const val PREFS = "preamble_weekly_wrapped"
    private const val K_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(K_ENABLED, enabled).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) {
            cancel(appContext)
            return
        }

        val triggerAt = nextSundayEveningMillis()
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pi = buildPendingIntent(appContext, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pi = buildPendingIntent(appContext, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    private fun buildPendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context, WeeklyWrappedReceiver::class.java).apply {
            action = WeeklyWrappedReceiver.ACTION_WEEKLY_WRAPPED
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun nextSundayEveningMillis(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) {
            target.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}
