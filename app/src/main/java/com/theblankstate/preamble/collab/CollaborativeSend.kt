package com.theblankstate.preamble.collab

/**
 * Pure, side-effect-free model and transition function for the durable
 * collaborative-send lifecycle (collaborative-tasks, Requirements 23, 24).
 *
 * Hosts the `Send_Status` model and the state transition so the send-state
 * logic can be unit- and property-tested independently of Android, WorkManager,
 * and Firestore.
 *
 * The machine encodes the requirement narrative: `PARSING` while awaiting the
 * AI_Parse_Phase before send (23.2); `QUEUED` while held offline/slow in the
 * Collaborative_Send_Queue (24.2); `SENDING` during an in-progress attempt
 * (24.4); `SENT` only after the canonical write completes, never before
 * (23.5, 24.5, 24.7); and `SEND_FAILED` only once retries are exhausted (24.6).
 * `SENT` is absorbing and is only reachable via `SendSucceeded`, which is what
 * guarantees a queued send is never reported delivered prematurely (24.7).
 */
object CollaborativeSend {
    /** User-visible Send_Status (Req 23.2/23.5, 24.2/24.4/24.5/24.6). */
    enum class SendStatus { PARSING, QUEUED, SENDING, SENT, SEND_FAILED }

    /** Device reachability classification (Iteration 3 glossary: Connectivity). */
    enum class Connectivity { ONLINE, SLOW, OFFLINE }

    /** Events that drive the send lifecycle, emitted by the confirm path and the workers. */
    sealed interface Event {
        data class Confirmed(val connectivity: Connectivity, val parsePending: Boolean) : Event
        data object ParseCompleted : Event        // AI_Parse_Phase finished (any outcome)
        data object ConnectivityOnline : Event    // connectivity returned while queued
        data object ConnectivityLost : Event      // connectivity dropped mid-flight
        data object SendStarted : Event           // a canonical-write attempt began
        data object SendSucceeded : Event         // canonical write completed
        data class SendFailed(val retriesRemaining: Boolean) : Event
        data object Retry : Event                 // manual retry of a send_failed task
    }

    /** Initial status when a collaborative task is confirmed (Req 23.1/23.2, 24.1/24.2). */
    fun initial(connectivity: Connectivity, parsePending: Boolean): SendStatus = when {
        connectivity == Connectivity.ONLINE && parsePending -> SendStatus.PARSING
        connectivity == Connectivity.ONLINE && !parsePending -> SendStatus.SENDING
        else -> SendStatus.QUEUED // offline / slow are enqueued (24.1)
    }

    /** Pure transition. SENT is absorbing; SEND_FAILED only leaves via an explicit Retry. */
    fun next(current: SendStatus, event: Event): SendStatus = when (current) {
        SendStatus.SENT -> SendStatus.SENT
        SendStatus.SEND_FAILED -> if (event is Event.Retry) SendStatus.QUEUED else SendStatus.SEND_FAILED
        else -> when (event) {
            is Event.SendSucceeded -> SendStatus.SENT
            is Event.SendFailed -> if (event.retriesRemaining) SendStatus.QUEUED else SendStatus.SEND_FAILED
            is Event.SendStarted, is Event.ConnectivityOnline -> SendStatus.SENDING
            is Event.ConnectivityLost -> SendStatus.QUEUED
            is Event.ParseCompleted -> if (current == SendStatus.PARSING) SendStatus.SENDING else current
            is Event.Confirmed -> initial(event.connectivity, event.parsePending)
            is Event.Retry -> current
        }
    }
}
