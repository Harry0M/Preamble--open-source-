package com.theblankstate.preamble.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Detects manufacturer-specific aggressive background killers and exposes
 * intents to launch their autostart / battery-management screens so the user
 * can whitelist Preamble.
 *
 * Foreground services on these OEMs are killed within 10–15 minutes unless
 * the user explicitly enables Auto-launch / Background activity:
 *   Vivo (Funtouch / iManager), Oppo / Realme (ColorOS), Xiaomi / Redmi (MIUI),
 *   Huawei (EMUI), Honor, Samsung (newer One UI is milder but still aggressive).
 */
object OemHelper {

    private const val TAG = "OemHelper"

    val manufacturer: String
        get() = Build.MANUFACTURER.lowercase()

    val isVivo: Boolean get() = manufacturer == "vivo" || manufacturer == "iqoo"
    val isXiaomi: Boolean get() = manufacturer == "xiaomi" || manufacturer == "redmi" || manufacturer == "poco"
    val isOppo: Boolean get() = manufacturer == "oppo"
    val isRealme: Boolean get() = manufacturer == "realme"
    val isHuawei: Boolean get() = manufacturer == "huawei" || manufacturer == "honor"
    val isOneplus: Boolean get() = manufacturer == "oneplus"
    val isSamsung: Boolean get() = manufacturer == "samsung"

    /** True for OEMs known to kill background foreground services aggressively. */
    val isAggressiveKiller: Boolean
        get() = isVivo || isXiaomi || isOppo || isRealme || isHuawei || isOneplus

    /** Recommended keep-alive cadence in milliseconds for the current OEM. */
    val recommendedKeepAliveIntervalMs: Long
        get() = when {
            isVivo || isOppo || isRealme -> 5 * 60 * 1000L      // 5 min — most aggressive
            isXiaomi || isHuawei -> 7 * 60 * 1000L              // 7 min
            isOneplus -> 10 * 60 * 1000L                        // 10 min
            else -> 30 * 60 * 1000L                             // 30 min — stock Android default
        }

    /**
     * Whether the user has already whitelisted the app from battery optimisations.
     * On Android <M this returns true (concept doesn't exist).
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system "Ignore battery optimisations" prompt for our package.
     * Available on Android 6+. Caller should still gate the UI call behind
     * [isBatteryOptimizationIgnored] returning false.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "requestIgnoreBatteryOptimizations failed", e)
            // Fallback to general battery settings page
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.w(TAG, "Battery settings fallback also failed", e2)
            }
        }
    }

    /**
     * Opens the manufacturer-specific autostart / background-activity screen.
     * Returns true if an intent was successfully launched, false if no known
     * screen exists for this OEM (caller should then show generic guidance).
     *
     * The component names below are the public ones documented in the OEMs'
     * developer / consumer help pages; they have stayed stable across major
     * versions of Funtouch, ColorOS, MIUI etc.
     */
    fun openAutoStartSettings(context: Context): Boolean {
        val candidates = candidateAutoStartIntents()
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open ${intent.component}", e)
            }
        }
        return false
    }

    private fun candidateAutoStartIntents(): List<Intent> {
        val list = mutableListOf<Intent>()
        when {
            isVivo -> {
                // Funtouch OS 12+ (Vivo T1x, Y-series etc.) removed the standalone
                // "Auto-start" toggle from iManager. Actual control is:
                //   iManager → App Manager → Background App Cleanup → Protected apps
                // Try several known package/activity combos across Funtouch versions.
                list += Intent().setComponent(
                    ComponentName(
                        "com.bbk.security",                          // Funtouch 12+
                        "com.bbk.security.ui.AppManagerActivity"
                    )
                )
                list += Intent().setComponent(
                    ComponentName(
                        "com.iqoo.secure",                           // older Funtouch
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                    )
                )
                list += Intent().setComponent(
                    ComponentName(
                        "com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
                    )
                )
                list += Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                )
            }
            isXiaomi -> {
                list += Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            }
            isOppo -> {
                list += Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
                list += Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                )
            }
            isRealme -> {
                list += Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                )
            }
            isHuawei -> {
                list += Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                )
                list += Intent().setComponent(
                    ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"
                    )
                )
            }
            isOneplus -> {
                list += Intent().setComponent(
                    ComponentName(
                        "com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                    )
                )
            }
        }
        return list
    }
}
