package com.example.sources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.CameraFacing
import com.example.model.CameraSourceConfig
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CameraHardwareState(
    val isBound: Boolean = false,
    val activeFacing: CameraFacing = CameraFacing.FRONT,
    val resolutionLabel: String = "1920×1080",
    val targetFps: Int = 30,
    val measuredFps: Int = 0,
    val hasFlashUnit: Boolean = false,
    val torchEnabled: Boolean = false,
    val minZoom: Float = 1f,
    val maxZoom: Float = 4f,
    val currentZoom: Float = 1f,
    val exposureRangeMin: Int = -4,
    val exposureRangeMax: Int = 4,
    val exposureIndex: Int = 0,
    val errorMessage: String? = null
)

/**
 * High-quality, zero-leak CameraX capture manager.
 *
 * Uses a double-buffered reusable Bitmap pool (`poolBitmaps[0]` and `poolBitmaps[1]`)
 * and a reusable raw pixel buffer Bitmap so that 30/60 FPS 1080p camera capture NEVER allocates
 * heap/native Bitmaps per frame—eliminating OutOfMemoryError / GC stalls during long livestreams.
 */
class CameraCaptureManager(private val context: Context) {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var currentConfig: CameraSourceConfig = CameraSourceConfig()

    private val latestFrameRef = AtomicReference<Bitmap?>(null)
    private val _hardwareState = MutableStateFlow(CameraHardwareState())
    val hardwareState: StateFlow<CameraHardwareState> = _hardwareState.asStateFlow()

    // Reusable double-buffered bitmap pool to prevent per-frame Bitmap allocations
    private var rawBufferBitmap: Bitmap? = null
    private val outputPool = arrayOfNulls<Bitmap>(2)
    private val outputCanvases = arrayOfNulls<Canvas>(2)
    private var poolIndex = 0
    private val transformMatrix = Matrix()
    private val highQualityPaint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
    )

    private var frameCounter = 0
    private var lastFpsTimestampMs = System.currentTimeMillis()

    fun getLatestFrame(): Bitmap? = latestFrameRef.get()

    @OptIn(ExperimentalCamera2Interop::class)
    fun startOrUpdateCamera(
        lifecycleOwner: LifecycleOwner,
        config: CameraSourceConfig
    ) {
        currentConfig = config
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                provider.unbindAll()

                val selector = when (config.facing) {
                    CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraFacing.REAR -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                val activeSelector = if (provider.hasCamera(selector)) {
                    selector
                } else if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    _hardwareState.value = _hardwareState.value.copy(
                        isBound = false,
                        errorMessage = "No physical camera sensor detected on device"
                    )
                    return@addListener
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(config.targetWidth, config.targetHeight),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val analysisBuilder = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)

                val interop = Camera2Interop.Extender(analysisBuilder)
                val fps = config.targetFps.coerceIn(30, 60)
                interop.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(24, fps)
                )
                interop.setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    CameraCharacteristics.EDGE_MODE_HIGH_QUALITY
                )
                interop.setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY
                )

                val imageAnalysis = analysisBuilder.build()
                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        val planes = imageProxy.planes
                        if (planes.isNotEmpty()) {
                            val srcW = imageProxy.width
                            val srcH = imageProxy.height
                            val plane = planes[0]
                            val rowStride = plane.rowStride
                            val pixelStride = plane.pixelStride.coerceAtLeast(4)
                            val paddedW = (rowStride / pixelStride).coerceAtLeast(srcW)

                            var rawBmp = rawBufferBitmap
                            if (rawBmp == null || rawBmp.isRecycled || rawBmp.width != paddedW || rawBmp.height != srcH) {
                                rawBmp = Bitmap.createBitmap(paddedW, srcH, Bitmap.Config.ARGB_8888)
                                rawBufferBitmap = rawBmp
                            }
                            val buf = plane.buffer
                            buf.rewind()
                            rawBmp.copyPixelsFromBuffer(buf)

                            val rotation = imageProxy.imageInfo.rotationDegrees
                            val outW = if (rotation == 90 || rotation == 270) srcH else srcW
                            val outH = if (rotation == 90 || rotation == 270) srcW else srcH

                            val nextIdx = (poolIndex + 1) % 2
                            var outBmp = outputPool[nextIdx]
                            var outCanvas = outputCanvases[nextIdx]
                            if (outBmp == null || outBmp.isRecycled || outBmp.width != outW || outBmp.height != outH) {
                                outBmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                                outCanvas = Canvas(outBmp)
                                outputPool[nextIdx] = outBmp
                                outputCanvases[nextIdx] = outCanvas
                            }

                            val canvas = outCanvas ?: Canvas(outBmp).also { outputCanvases[nextIdx] = it }
                            canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)

                            transformMatrix.reset()
                            transformMatrix.postTranslate(-srcW / 2f, -srcH / 2f)
                            if (rotation != 0) {
                                transformMatrix.postRotate(rotation.toFloat())
                            }
                            val sx = if (currentConfig.mirrorHorizontally) -1f else 1f
                            val sy = if (currentConfig.flipVertically) -1f else 1f
                            if (sx != 1f || sy != 1f) {
                                transformMatrix.postScale(sx, sy)
                            }
                            transformMatrix.postTranslate(outW / 2f, outH / 2f)

                            canvas.drawBitmap(rawBmp, transformMatrix, highQualityPaint)

                            poolIndex = nextIdx
                            latestFrameRef.set(outBmp)

                            frameCounter++
                            val now = System.currentTimeMillis()
                            if (now - lastFpsTimestampMs >= 1000L) {
                                val measured = ((frameCounter * 1000L) / (now - lastFpsTimestampMs).coerceAtLeast(1L)).toInt()
                                frameCounter = 0
                                lastFpsTimestampMs = now
                                _hardwareState.value = _hardwareState.value.copy(
                                    measuredFps = measured,
                                    resolutionLabel = "${outW}×${outH}"
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Frame skipped safely
                    } finally {
                        imageProxy.close()
                    }
                }

                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    activeSelector,
                    imageAnalysis
                )
                activeCamera = camera

                applyControlSettings(camera, config)
            } catch (e: Exception) {
                _hardwareState.value = _hardwareState.value.copy(
                    isBound = false,
                    errorMessage = e.localizedMessage ?: "Camera initialization error"
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun updateLiveControls(config: CameraSourceConfig) {
        val rebindNeeded = config.facing != currentConfig.facing ||
            config.targetWidth != currentConfig.targetWidth ||
            config.targetHeight != currentConfig.targetHeight ||
            config.targetFps != currentConfig.targetFps
        currentConfig = config
        if (!rebindNeeded) {
            activeCamera?.let { applyControlSettings(it, config) }
        }
    }

    private fun applyControlSettings(camera: Camera, config: CameraSourceConfig) {
        val info = camera.cameraInfo
        val control = camera.cameraControl

        val hasFlash = info.hasFlashUnit()
        if (hasFlash) {
            control.enableTorch(config.torchEnabled)
        }

        val zoomState = info.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 1f
        val maxZoom = zoomState?.maxZoomRatio ?: 4f
        val clampedZoom = config.zoomRatio.coerceIn(minZoom, maxZoom)
        control.setZoomRatio(clampedZoom)

        val exposureState = info.exposureState
        val expRange = exposureState.exposureCompensationRange
        val clampedExp = config.exposureIndex.coerceIn(expRange.lower, expRange.upper)
        if (exposureState.isExposureCompensationSupported) {
            control.setExposureCompensationIndex(clampedExp)
        }

        _hardwareState.value = CameraHardwareState(
            isBound = true,
            activeFacing = config.facing,
            resolutionLabel = "${config.targetWidth}×${config.targetHeight}",
            targetFps = config.targetFps,
            measuredFps = _hardwareState.value.measuredFps,
            hasFlashUnit = hasFlash,
            torchEnabled = hasFlash && config.torchEnabled,
            minZoom = minZoom,
            maxZoom = maxZoom,
            currentZoom = clampedZoom,
            exposureRangeMin = expRange.lower,
            exposureRangeMax = expRange.upper,
            exposureIndex = clampedExp,
            errorMessage = null
        )
    }

    fun release() {
        try {
            cameraProvider?.unbindAll()
            activeCamera = null
            _hardwareState.value = _hardwareState.value.copy(isBound = false)
        } catch (_: Exception) {
        }
    }
}
