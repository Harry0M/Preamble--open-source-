package com.theblankstate.preamble.util

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Handles checking for updates via Google Play and triggering Flexible updates.
 */
class InAppUpdateManager(private val activity: Activity) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val UPDATE_REQUEST_CODE = 1001

    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        Log.d("InAppUpdate", "InstallStateUpdatedListener status: ${state.installStatus()}")
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Log.d("InAppUpdate", "An update has been downloaded. Completing update...")
            activity.runOnUiThread {
                Toast.makeText(activity, "Update downloaded! Restarting to install...", Toast.LENGTH_LONG).show()
            }
            appUpdateManager.completeUpdate()
        }
    }

    fun checkForUpdate() {
        appUpdateManager.registerListener(installStateUpdatedListener)

        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            Log.d("InAppUpdate", "Check for update success. Availability: ${appUpdateInfo.updateAvailability()}")
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                // Request the update.
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                    UPDATE_REQUEST_CODE
                )
            }
        }.addOnFailureListener {
            Log.e("InAppUpdate", "Failed to check for update: ${it.message}")
        }
    }

    fun onResume() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            // If the update is downloaded but not installed,
            // notify the user to complete the update.
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                Log.d("InAppUpdate", "Update downloaded, completing now on resume...")
                appUpdateManager.completeUpdate()
            }
        }
    }

    fun onDestroy() {
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }
}
