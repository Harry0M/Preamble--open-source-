package com.theblankstate.preamble.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.data.ProblemReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProblemReportRepository {
    const val MAX_ATTACHMENT_BYTES: Long = 50L * 1024L * 1024L
    const val MAX_UNRESOLVED_REPORTS = 2

    private const val FIRESTORE_DB_ID = "preamble"
    private const val COLLECTION = "problemReports"
    private const val FUNCTIONS_BASE_URL = "https://us-central1-preambl-fbea6.cloudfunctions.net"
    private val activeStatuses = listOf(
        ProblemReport.STATUS_OPEN,
        ProblemReport.STATUS_IN_PROGRESS,
    )

    private val firestore by lazy { FirebaseFirestore.getInstance(FIRESTORE_DB_ID) }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class DraftMedia(
        val uri: Uri,
        val name: String,
        val contentType: String,
        val sizeBytes: Long,
    )

    suspend fun loadMyReports(): List<ProblemReport> = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: return@withContext emptyList()
        val snapshot = firestore.collection(COLLECTION)
            .whereEqualTo("uid", user.uid)
            .get()
            .await()

        snapshot.documents
            .mapNotNull { doc -> doc.data?.let { ProblemReport.fromMap(doc.id, it) } }
            .sortedByDescending { it.createdAt }
    }

    suspend fun inspectMedia(context: Context, uris: List<Uri>): List<DraftMedia> = withContext(Dispatchers.IO) {
        uris.map { uri ->
            val contentType = context.contentResolver.getType(uri).orEmpty()
            if (!contentType.startsWith("image/") && !contentType.startsWith("video/")) {
                throw IllegalArgumentException("Only image and video files can be attached.")
            }

            val (name, sizeBytes) = readDisplayNameAndSize(context, uri)
            if (sizeBytes > MAX_ATTACHMENT_BYTES) {
                throw IllegalArgumentException("${name.ifBlank { "Selected file" }} is larger than 50 MB.")
            }

            DraftMedia(
                uri = uri,
                name = name.ifBlank { "attachment_${System.currentTimeMillis()}" },
                contentType = contentType,
                sizeBytes = sizeBytes,
            )
        }
    }

    suspend fun submitReport(
        context: Context,
        title: String,
        description: String,
        media: List<DraftMedia>,
    ): ProblemReport = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Please sign in before reporting a problem.")

        val userReportsSnapshot = firestore.collection(COLLECTION)
            .whereEqualTo("uid", user.uid)
            .get()
            .await()
        val activeReportCount = userReportsSnapshot.documents.count { doc ->
            val status = doc.getString("status") ?: ProblemReport.STATUS_OPEN
            status in activeStatuses
        }

        if (activeReportCount >= MAX_UNRESOLVED_REPORTS) {
            throw IllegalStateException("You already have 2 reports in review. Please wait until one is resolved.")
        }
        if (activeReportCount > 0) {
            throw IllegalStateException("Your current report is still in review. You can send another once it is resolved.")
        }

        val reportRef = firestore.collection(COLLECTION).document()
        val reportId = reportRef.id

        val attachments = media.mapIndexed { index, item ->
            if (item.sizeBytes > MAX_ATTACHMENT_BYTES) {
                throw IllegalArgumentException("${item.name} is larger than 50 MB.")
            }
            val storagePath = "users/${user.uid}/problem_reports/$reportId/media/${index}_${sanitizeFileName(item.name)}"
            val metadata = StorageMetadata.Builder()
                .setContentType(item.contentType)
                .build()

            storage.reference.child(storagePath)
                .putFile(item.uri, metadata)
                .await()

            AttachmentPayload(
                name = item.name,
                contentType = item.contentType,
                sizeBytes = item.sizeBytes,
                storagePath = storagePath,
            )
        }

        createReportOnServer(
            reportId = reportId,
            title = title,
            description = description,
            attachments = attachments,
        )
    }

    private data class AttachmentPayload(
        val name: String,
        val contentType: String,
        val sizeBytes: Long,
        val storagePath: String,
    )

    private suspend fun createReportOnServer(
        reportId: String,
        title: String,
        description: String,
        attachments: List<AttachmentPayload>,
    ): ProblemReport {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("Please sign in before reporting a problem.")
        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException("Could not verify your sign-in session.")

        val body = JSONObject().apply {
            put("reportId", reportId)
            put("title", title.trim())
            put("description", description.trim())
            put("attachments", JSONArray().apply {
                attachments.forEach { item ->
                    put(JSONObject().apply {
                        put("name", item.name)
                        put("contentType", item.contentType)
                        put("sizeBytes", item.sizeBytes)
                        put("storagePath", item.storagePath)
                    })
                }
            })
            put("appVersionName", BuildConfig.VERSION_NAME)
            put("appVersionCode", BuildConfig.VERSION_CODE)
            put("device", listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim())
            put("androidSdk", Build.VERSION.SDK_INT)
        }

        val request = Request.Builder()
            .url("$FUNCTIONS_BASE_URL/submitProblemReport")
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful) {
                throw IllegalStateException(json?.optString("error")?.takeIf { it.isNotBlank() } ?: "Could not send report.")
            }

            val reportJson = json?.optJSONObject("report")
            val reportMap = buildMap<String, Any?> {
                put("uid", user.uid)
                put("userEmail", reportJson?.optString("userEmail")?.takeIf { it.isNotBlank() } ?: user.email)
                put("userName", reportJson?.optString("userName")?.takeIf { it.isNotBlank() } ?: user.displayName)
                put("title", title.trim())
                put("description", description.trim())
                put("status", ProblemReport.STATUS_OPEN)
                put("createdAt", reportJson?.optLong("createdAt") ?: System.currentTimeMillis())
                put("updatedAt", reportJson?.optLong("updatedAt") ?: System.currentTimeMillis())
                put("attachments", attachments.map {
                    mapOf(
                        "name" to it.name,
                        "contentType" to it.contentType,
                        "sizeBytes" to it.sizeBytes,
                        "storagePath" to it.storagePath,
                    )
                })
            }
            return ProblemReport.fromMap(reportId, reportMap)
        }
    }

    private fun readDisplayNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = ""
        var size = 0L

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }

        if (name.isBlank()) name = uri.lastPathSegment.orEmpty()
        return name to size
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .ifBlank { "attachment" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(80)
    }
}
