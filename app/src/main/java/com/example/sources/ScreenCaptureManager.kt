package com.example.sources

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import com.example.model.ScreenCaptureMode
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScreenCaptureState(
    val isCapturing: Boolean = false,
    val captureWidth: Int = 1920,
    val captureHeight: Int = 1080,
    val measuredFps: Int = 0,
    val captureMode: ScreenCaptureMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ScreenCaptureMode.ANDROID_14_SINGLE_APP
    } else {
        ScreenCaptureMode.ANDROID_13_SPLIT_CROP
    },
    val statusMessage: String = "Tap 'Grant Screen Capture' to capture screen or app window"
)

/**
 * Manages Android MediaProjection for Screen capture using a zero-allocation
 * double-buffered Bitmap pool so continuous 1080p screen capture never triggers OutOfMemoryError.
 */
class ScreenCaptureManager(private val context: Context) {

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val latestFrameRef = AtomicReference<Bitmap?>(null)
    private val _state = MutableStateFlow(ScreenCaptureState())
    val state: StateFlow<ScreenCaptureState> = _state.asStateFlow()

    // Reusable double-buffered bitmap pool for zero-GC 1080p screen capture
    private var rawStrideBitmap: Bitmap? = null
    private val outputPool = arrayOfNulls<Bitmap>(2)
    private val outputCanvases = arrayOfNulls<Canvas>(2)
    private var poolIndex = 0
    private val copySrcRect = Rect()
    private val copyDstRect = Rect()
    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var frameCount = 0
    private var lastFpsMs = System.currentTimeMillis()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture("Screen capture stopped by system")
        }
    }

    fun getMediaProjection(): MediaProjection? = mediaProjection

    fun getLatestFrame(): Bitmap? = latestFrameRef.get()

    fun createCaptureConsentIntent(preferredMode: ScreenCaptureMode): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val config = when (preferredMode) {
                ScreenCaptureMode.ANDROID_14_SINGLE_APP ->
                    MediaProjectionConfig.createConfigForUserChoice()
                ScreenCaptureMode.ANDROID_13_SPLIT_CROP ->
                    MediaProjectionConfig.createConfigForDefaultDisplay()
            }
            projectionManager.createScreenCaptureIntent(config)
        } else {
            projectionManager.createScreenCaptureIntent()
        }
    }

    fun startCapture(
        resultCode: Int,
        resultData: Intent,
        width: Int = 1920,
        height: Int = 1080,
        densityDpi: Int = context.resources.displayMetrics.densityDpi,
        mode: ScreenCaptureMode = _state.value.captureMode
    ) {
        stopCapture()
        try {
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                _state.value = _state.value.copy(
                    isCapturing = false,
                    statusMessage = "MediaProjection permission was not granted"
                )
                return
            }
            mediaProjection = projection

            val thread = HandlerThread("SceneCastScreenCapture").apply { start() }
            captureThread = thread
            val handler = Handler(thread.looper)
            captureHandler = handler

            // Mandatory on Android 14+: register callback BEFORE createVirtualDisplay
            projection.registerCallback(projectionCallback, handler)

            val safeWidth = (width / 2) * 2
            val safeHeight = (height / 2) * 2
            val reader = ImageReader.newInstance(safeWidth, safeHeight, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            reader.setOnImageAvailableListener({ ir ->
                val image = try {
                    ir.acquireLatestImage()
                } catch (_: Exception) {
                    null
                } ?: return@setOnImageAvailableListener

                try {
                    val planes = image.planes
                    if (planes.isNotEmpty()) {
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride.coerceAtLeast(4)
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * safeWidth
                        val paddedWidth = (safeWidth + rowPadding / pixelStride).coerceAtLeast(safeWidth)

                        val nextIdx = (poolIndex + 1) % 2
                        var outBmp = outputPool[nextIdx]
                        var outCanvas = outputCanvases[nextIdx]
                        if (outBmp == null || outBmp.isRecycled || outBmp.width != safeWidth || outBmp.height != safeHeight) {
                            outBmp = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
                            outCanvas = Canvas(outBmp)
                            outputPool[nextIdx] = outBmp
                            outputCanvases[nextIdx] = outCanvas
                        }

                        buffer.rewind()
                        if (rowPadding <= 0) {
                            outBmp.copyPixelsFromBuffer(buffer)
                        } else {
                            var strideBmp = rawStrideBitmap
                            if (strideBmp == null || strideBmp.isRecycled || strideBmp.width != paddedWidth || strideBmp.height != safeHeight) {
                                strideBmp = Bitmap.createBitmap(paddedWidth, safeHeight, Bitmap.Config.ARGB_8888)
                                rawStrideBitmap = strideBmp
                            }
                            strideBmp.copyPixelsFromBuffer(buffer)
                            copySrcRect.set(0, 0, safeWidth, safeHeight)
                            copyDstRect.set(0, 0, safeWidth, safeHeight)
                            outCanvas?.drawBitmap(strideBmp, copySrcRect, copyDstRect, filterPaint)
                        }

                        poolIndex = nextIdx
                        latestFrameRef.set(outBmp)

                        frameCount++
                        val now = System.currentTimeMillis()
                        if (now - lastFpsMs >= 1000L) {
                            val fps = ((frameCount * 1000L) / (now - lastFpsMs).coerceAtLeast(1L)).toInt()
                            frameCount = 0
                            lastFpsMs = now
                            _state.value = _state.value.copy(measuredFps = fps)
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    image.close()
                }
            }, handler)

            virtualDisplay = projection.createVirtualDisplay(
                "SceneCastScreenDisplay",
                safeWidth,
                safeHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            _state.value = ScreenCaptureState(
                isCapturing = true,
                captureWidth = safeWidth,
                captureHeight = safeHeight,
                measuredFps = 30,
                captureMode = mode,
                statusMessage = "Capturing Screen / App (${safeWidth}×${safeHeight})"
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isCapturing = false,
                statusMessage = "Capture start failed: ${e.localizedMessage ?: "Ensure foreground service is active"}"
            )
        }
    }

    fun stopCapture(reason: String = "Screen capture stopped") {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        try {
            captureThread?.quitSafely()
        } catch (_: Exception) {
        }
        captureThread = null
        captureHandler = null

        _state.value = _state.value.copy(
            isCapturing = false,
            measuredFps = 0,
            statusMessage = reason
        )
    }
}
