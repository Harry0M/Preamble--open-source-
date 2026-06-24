package com.theblankstate.preamble.ai

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.theblankstate.preamble.PreambleApplication
import com.theblankstate.preamble.collab.CollaborativeSend
import com.theblankstate.preamble.collab.CollaborativeSend.SendStatus
import com.theblankstate.preamble.data.Task
import com.theblankstate.preamble.repository.Friend
import com.theblankstate.preamble.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable second link of the parse→send WorkManager chain (collaborative-tasks,
 * Requirements 23, 24).
 *
 * Where [AiParsingWorker] only refines and persists attributes for the AI_Parse_Phase,
 * this worker performs the actual Collaborative_Send: it writes the canonical
 * `/collaborativeTasks/{taskId}` document so assignees receive the task. Because it lives
 * in WorkManager's persisted queue, the send survives backgrounding, process death, and
 * reboot, and is retried when connectivity returns (Requirements 24.3, 24.4, 24.5).
 *
 * The send reads the *current* task from Room, so it always sends the AI-refined
 * attributes when parsing succeeded (Requirement 23.3) and the as-entered attributes when
 * parsing produced nothing (Requirement 23.4). The send no longer depends on the parse
 * step producing tool calls — that is the change that closes the original "offline ⇒ never
 * sent" bug.
 *
 * The worker is idempotent: a missing task, a task that is no longer collaborative, a task
 * the current user does not administer, or a task already `sent` short-circuits to
 * [Result.success]. This makes WorkManager re-runs after a restart safe (Requirement 24.7).
 *
 * Status is tracked through [CollaborativeSend] and persisted to [Task.collabSendStatus]:
 * `sending` before the attempt; `sent` (with `isSyncing = false`) on success; the status is
 * kept `queued` on a transient (network/IO/Firestore-unavailable) failure while
 * [Result.retry] schedules exponential backoff; and `send_failed` once retries are
 * exhausted ([MAX_SEND_ATTEMPTS]), retaining the local copy unchanged and surfacing a
 * message (Requirement 24.6).
 */
class CollaborativeSendWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as PreambleApplication
        val taskId = inputData.getString("taskId") ?: return Result.failure()

        val task = app.repository.getTaskById(taskId)
        if (task == null) {
            // Task was deleted by the user in the meantime; nothing to send.
            return Result.success()
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid

        // Idempotent guards (Requirement 24.7): only the admin of a still-collaborative,
        // not-yet-sent task performs the send. Everything else is a no-op success.
        if (task.collabAdminUid == null || task.collabAdminUid != currentUid) {
            return Result.success()
        }
        if (task.collabSendStatus == STATUS_SENT) {
            return Result.success()
        }

        // Build the assignee Friend list from the task's collaborators, excluding the admin
        // and any blank/duplicate uids (Requirement 23.6).
        val assignees: List<Friend> = task.collabAssignees
            .filter { it.uid.isNotBlank() && it.uid != currentUid }
            .map { Friend(uid = it.uid, name = it.name, photoUrl = it.photoUrl) }
            .distinctBy(Friend::uid)
        if (assignees.isEmpty()) {
            // No real assignees: the task is effectively non-collaborative, so there is
            // nothing to share. Clear the send status and finish.
            app.repository.updateTask(task.copy(collabSendStatus = null, isSyncing = false))
            return Result.success()
        }

        // Mark the attempt as in-progress before writing (Requirement 24.4).
        val sendingStatus = CollaborativeSend.next(currentSendStatus(task), CollaborativeSend.Event.SendStarted)
        app.repository.updateTask(task.copy(collabSendStatus = sendingStatus.toPersistedValue()))

        val workspaceRepository = WorkspaceRepository()
        val result = workspaceRepository.writeFinalizedCollaborativeAttributes(
            task = task,
            assignees = assignees,
            adminPhotoUrl = com.theblankstate.preamble.data.UserProfileStore.load(applicationContext).photoUrl
        )

        return result.fold(
            onSuccess = {
                // The canonical write completed; only now is the send `sent`
                // (Requirements 23.5, 24.5, 24.7).
                val sentStatus = CollaborativeSend.next(SendStatus.SENDING, CollaborativeSend.Event.SendSucceeded)
                app.repository.updateTask(
                    task.copy(
                        collabSendStatus = sentStatus.toPersistedValue(),
                        isSyncing = false
                    )
                )
                Log.d(TAG, "Collaborative send succeeded for task $taskId")
                Result.success()
            },
            onFailure = { error ->
                val transient = isTransientFailure(error)
                if (transient && runAttemptCount < MAX_SEND_ATTEMPTS) {
                    // Transient failure with attempts remaining: keep the task QUEUED in the
                    // durable Collaborative_Send_Queue and let WorkManager retry with
                    // exponential backoff (Requirements 24.2, 24.6).
                    val queuedStatus = CollaborativeSend.next(
                        SendStatus.SENDING,
                        CollaborativeSend.Event.SendFailed(retriesRemaining = true)
                    )
                    app.repository.updateTask(task.copy(collabSendStatus = queuedStatus.toPersistedValue()))
                    Log.w(TAG, "Collaborative send transient failure for task $taskId (attempt $runAttemptCount); will retry", error)
                    Result.retry()
                } else {
                    // Terminal failure: either non-transient, or retries are exhausted. Move to
                    // SEND_FAILED, retain the local copy unchanged, and surface a message
                    // (Requirement 24.6).
                    val failedStatus = CollaborativeSend.next(
                        SendStatus.SENDING,
                        CollaborativeSend.Event.SendFailed(retriesRemaining = false)
                    )
                    app.repository.updateTask(task.copy(collabSendStatus = failedStatus.toPersistedValue()))
                    Log.e(TAG, "Collaborative send failed permanently for task $taskId (attempt $runAttemptCount)", error)
                    surfaceMessage("Couldn't share \"${task.title}\" with collaborators. Your copy is saved.")
                    Result.failure()
                }
            }
        )
    }

    /** Reads the persisted send status, defaulting to QUEUED when absent/unrecognized. */
    private fun currentSendStatus(task: Task): SendStatus =
        task.collabSendStatus?.let { raw ->
            runCatching { SendStatus.valueOf(raw.uppercase()) }.getOrNull()
        } ?: SendStatus.QUEUED

    /** Surfaces a short user-facing message from the background worker. */
    private suspend fun surfaceMessage(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "CollaborativeSendWorker"

        /** Persisted form of [SendStatus.SENT] in [Task.collabSendStatus]. */
        private const val STATUS_SENT = "sent"

        /**
         * Maximum number of WorkManager attempts before the send is treated as a terminal
         * failure and moved to `send_failed` (Requirement 24.6).
         */
        const val MAX_SEND_ATTEMPTS = 5

        /**
         * Unique-work name for the parse→send chain of a given task (Requirement 24.3).
         * Enqueuing with this name and [androidx.work.ExistingWorkPolicy.REPLACE] guarantees
         * a single durable send chain per task.
         */
        fun sendWorkName(taskId: String): String = "collab-send-$taskId"

        /** Maps a [SendStatus] to its persisted lowercase string in [Task.collabSendStatus]. */
        private fun SendStatus.toPersistedValue(): String = name.lowercase()

        /**
         * Classifies a [writeFinalizedCollaborativeAttributes] failure as transient
         * (worth retrying) vs. terminal. Network/IO problems and a temporarily unavailable
         * Firestore backend are transient; anything else (e.g. permission denied, invalid
         * argument) is terminal.
         */
        private fun isTransientFailure(error: Throwable): Boolean = when (error) {
            is java.net.UnknownHostException,
            is java.net.SocketTimeoutException,
            is java.io.IOException -> true
            is FirebaseFirestoreException -> error.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                error.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
            else -> false
        }
    }
}
