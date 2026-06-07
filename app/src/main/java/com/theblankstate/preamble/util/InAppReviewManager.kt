package com.theblankstate.preamble.util

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.theblankstate.preamble.BuildConfig

class InAppReviewManager(private val activity: Activity) {

    private val manager: ReviewManager by lazy {
        if (BuildConfig.DEBUG) {
            FakeReviewManager(activity)
        } else {
            ReviewManagerFactory.create(activity)
        }
    }

    fun launchReviewFlow(forceFake: Boolean = false, onComplete: (() -> Unit)? = null) {
        val activeManager = if (forceFake) FakeReviewManager(activity) else manager
        
        Log.d("InAppReview", "Requesting review flow (isFake = ${activeManager is FakeReviewManager})...")
        val request = activeManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                Log.d("InAppReview", "Launching review flow...")
                val flow = activeManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    Log.d("InAppReview", "Review flow completed.")
                    if (activeManager is FakeReviewManager) {
                        Toast.makeText(activity, "Debug: Fake Review Flow Completed successfully!", Toast.LENGTH_SHORT).show()
                    }
                    onComplete?.invoke()
                }
            } else {
                Log.w("InAppReview", "Failed to request review info: ${task.exception?.message}")
                onComplete?.invoke()
            }
        }
    }
}
