package com.theblankstate.preamble.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.FileProvider
import com.theblankstate.preamble.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a generated shareable image to app cache and hands it to the Android
 * system Share_Sheet (Requirements 7.4, 8.3, 9.3).
 *
 * The PNG is written under `cacheDir/shared_images/` and exposed through the
 * `${applicationId}.fileprovider` FileProvider (registered in the manifest with
 * a matching `file_paths.xml` `cache-path`). The launch intent mirrors the
 * existing recap-share idiom: `ACTION_SEND`, `type="image/png"`, an
 * `EXTRA_STREAM` content `Uri`, the caption as `EXTRA_TEXT`, and a read-grant
 * flag. Any IO or provider error is captured into [Result.failure] rather than
 * thrown, so a failed share never crashes the caller.
 */
object ShareSheetLauncher {

    private const val TAG = "ShareSheetLauncher"
    private const val SHARED_DIR = "shared_images"
    private val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"

    /**
     * Persists [bitmap] as a PNG and presents the Share_Sheet carrying the image
     * and [caption].
     */
    suspend fun share(
        context: Context,
        bitmap: Bitmap,
        caption: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
            val file = File(directory, "preamble_share_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }

            val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        }.onFailure { Log.e(TAG, "Failed to launch share sheet", it) }
    }
}
