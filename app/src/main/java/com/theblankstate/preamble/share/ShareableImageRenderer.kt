package com.theblankstate.preamble.share

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Renders a branded Composable to a [Bitmap] off-screen for sharing
 * (Requirements 7.2, 8.2, 9.2, 10.3).
 *
 * The primary path hosts the shareable in a [ComposeView] attached invisibly to
 * the activity window and captures it through a Compose `GraphicsLayer`
 * (`graphicsLayer.toImageBitmap().asAndroidBitmap()`) once a frame has been laid
 * out and drawn. If the graphics-layer capture is unavailable or yields nothing
 * at runtime, the fallback captures the same attached view via [PixelCopy]
 * (API 24+) or `view.draw(Canvas)` — mirroring the existing `RecapScreen`
 * approach.
 *
 * The function returns a [Result] and **never** throws into the caller
 * (Requirement 10.3); the caller (the ShareableViewModel) owns the overall
 * timeout (Requirement 10.4).
 */
object ShareableImageRenderer {

    private const val TAG = "ShareableImageRenderer"

    /** Default shareable canvas size (portrait card), used when the content is size-agnostic. */
    private const val DEFAULT_WIDTH_PX = 1080
    private const val DEFAULT_HEIGHT_PX = 1350

    /**
     * Renders [content] to a [Bitmap]. All view manipulation runs on the main
     * thread; the heavy PNG encoding happens later in [ShareSheetLauncher].
     *
     * @param activity host whose window the off-screen view is briefly attached to
     * @param widthPx target bitmap width in pixels
     * @param heightPx target bitmap height in pixels
     */
    suspend fun render(
        activity: Activity,
        widthPx: Int = DEFAULT_WIDTH_PX,
        heightPx: Int = DEFAULT_HEIGHT_PX,
        content: @Composable () -> Unit,
    ): Result<Bitmap> = withContext(Dispatchers.Main.immediate) {
        runCatching {
            val root = activity.window.decorView as ViewGroup
            val captured = CompletableDeferred<Bitmap>()

            val composeView = ComposeView(activity).apply {
                // Attached but invisible so it lays out and draws without disturbing the UI.
                alpha = 0f
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
                )
            }

            composeView.setContent {
                val graphicsLayer = rememberGraphicsLayer()
                Box(
                    modifier = Modifier.drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
                ) {
                    content()
                }

                LaunchedEffect(Unit) {
                    // Let one frame settle so layout + the recorded draw are ready.
                    withFrameNanos { }
                    if (captured.isCompleted) return@LaunchedEffect
                    val bitmap = runCatching {
                        graphicsLayer.toImageBitmap().asAndroidBitmap()
                    }.getOrNull()
                    if (bitmap != null) {
                        captured.complete(bitmap)
                    } else {
                        val fallback = captureViaView(activity, composeView)
                        if (fallback != null) {
                            captured.complete(fallback)
                        } else {
                            captured.completeExceptionally(
                                IllegalStateException("Shareable capture produced no bitmap")
                            )
                        }
                    }
                }
            }

            val params = FrameLayout.LayoutParams(widthPx, heightPx)
            root.addView(composeView, params)

            try {
                captured.await()
            } finally {
                root.removeView(composeView)
            }
        }.onFailure { Log.e(TAG, "Failed to render shareable image", it) }
    }

    /**
     * Fallback capture of an attached [view]: [PixelCopy] on API 24+ (the project
     * floor), otherwise a software `view.draw(Canvas)`. Returns `null` on any
     * failure so the caller can surface a single graceful error.
     */
    private suspend fun captureViaView(activity: Activity, view: View): Bitmap? {
        val width = view.width.takeIf { it > 0 } ?: return null
        val height = view.height.takeIf { it > 0 } ?: return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val region = android.graphics.Rect(
                location[0],
                location[1],
                location[0] + width,
                location[1] + height,
            )
            val pixelCopyResult = runCatching {
                suspendCancellableCoroutine { continuation ->
                    PixelCopy.request(
                        activity.window,
                        region,
                        bitmap,
                        { copyResult -> continuation.resume(copyResult) },
                        Handler(Looper.getMainLooper()),
                    )
                }
            }.getOrNull()
            if (pixelCopyResult == PixelCopy.SUCCESS) {
                return bitmap
            }
        }

        return runCatching {
            val softwareBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(softwareBitmap))
            softwareBitmap
        }.getOrNull()
    }
}
