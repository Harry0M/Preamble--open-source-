package com.theblankstate.preamble.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.theblankstate.preamble.collab.CollaborativeSend.Connectivity

/**
 * Small abstraction over device reachability for the durable collaborative-send pipeline
 * (collaborative-tasks, Requirements 23, 24).
 *
 * Kept behind an interface so the confirm path can stamp the initial Send_Status via the
 * pure [CollaborativeSend] state machine while remaining unit-testable (a fake probe can be
 * substituted without touching Android's [ConnectivityManager]).
 */
interface ConnectivityProbe {
    /** Classifies the current device connectivity for [CollaborativeSend.initial]. */
    fun current(): Connectivity
}

/**
 * Android implementation backed by [ConnectivityManager]'s active-network capabilities.
 *
 * Classification (kept deliberately simple — ONLINE/OFFLINE only):
 *  - No active network, or the active network lacks the INTERNET capability → [Connectivity.OFFLINE].
 *  - An active network advertising the INTERNET capability → [Connectivity.ONLINE].
 *
 * SLOW is intentionally not distinguished. The pure machine's [CollaborativeSend.initial]
 * already enqueues SLOW identically to OFFLINE, and the durable parse→send WorkManager chain
 * delivers regardless of the initial classification, so a separate SLOW bucket would add no
 * behavioral value. Classifying every metered/cellular network as SLOW would also wrongly
 * stamp normally-online mobile users as "queued"; using the INTERNET capability avoids that
 * while keeping the probe trivial.
 */
class AndroidConnectivityProbe(context: Context) : ConnectivityProbe {

    private val appContext = context.applicationContext

    override fun current(): Connectivity {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return Connectivity.OFFLINE
        val network = cm.activeNetwork ?: return Connectivity.OFFLINE
        val caps = cm.getNetworkCapabilities(network) ?: return Connectivity.OFFLINE
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            Connectivity.ONLINE
        } else {
            Connectivity.OFFLINE
        }
    }
}
