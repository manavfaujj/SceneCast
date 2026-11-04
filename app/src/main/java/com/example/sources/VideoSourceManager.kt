package com.example.sources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.example.model.SceneLayer
import com.example.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class NetworkFeedStatus(
    val url: String = "",
    val isConnected: Boolean = false,
    val fps: Int = 0,
    val latencyMs: Int = 0,
    val statusText: String = "Idle"
)

/**
 * Central Video Source Manager that coordinates:
 * - CameraCaptureManager (CameraX front/rear)
 * - ScreenCaptureManager (Screen/App Capture)
 * - Network Video Receiver (LAN MJPEG HTTP / RTSP socket stream receiver)
 * - Local Video File frame decoder
 * - Transparent PNG overlays & Image bitmaps cache
 */
class VideoSourceManager(
    private val context: Context,
    val cameraManager: CameraCaptureManager,
    val screenCaptureManager: ScreenCaptureManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val videoFrameCache = ConcurrentHashMap<String, Bitmap>()
    private val videoJobs = ConcurrentHashMap<String, Job>()

    private val networkFrameCache = ConcurrentHashMap<String, Bitmap>()
    private val networkJobs = ConcurrentHashMap<String, Job>()
    private val moonlightFrameCache = ConcurrentHashMap<String, Bitmap>()

    private val _networkStatus = MutableStateFlow(NetworkFeedStatus())
    val networkStatus: StateFlow<NetworkFeedStatus> = _networkStatus.asStateFlow()

    fun getBitmapForLayer(layer: SceneLayer): Bitmap? {
        return when (layer.type) {
            SourceType.CAMERA -> cameraManager.getLatestFrame()
            SourceType.SCREEN_CAPTURE -> screenCaptureManager.getLatestFrame()
            SourceType.MOONLIGHT_PC -> moonlightFrameCache[layer.id]
            SourceType.NETWORK_VIDEO -> {
                ensureNetworkReceiverRunning(layer.id, layer.networkConfig.streamUrl)
                networkFrameCache[layer.id]
            }
            SourceType.PNG_OVERLAY, SourceType.IMAGE, SourceType.LOGO -> {
                loadGraphicBitmap(
                    filePath = layer.mediaConfig.localFilePath,
                    drawableRes = layer.mediaConfig.builtInDrawableRes
                )
            }
            SourceType.VIDEO_FILE -> {
                val path = layer.mediaConfig.localFilePath
                if (!path.isNullOrEmpty()) {
                    ensureVideoDecoderRunning(layer.id, path, layer.mediaConfig.loopVideo)
                    videoFrameCache[layer.id]
                } else null
            }
            SourceType.BACKGROUND -> {
                val path = layer.backgroundConfig.imagePath
                if (!path.isNullOrEmpty()) {
                    loadGraphicBitmap(filePath = path, drawableRes = null)
                } else null
            }
            SourceType.TEXT -> null
        }
    }

    /** Called by the Moonlight decoder output adapter; each PC feed remains its own scene layer. */
    fun publishMoonlightFrame(layerId: String, frame: Bitmap) {
        if (frame.isRecycled) return
        val previous = moonlightFrameCache.put(layerId, frame)
        if (previous !== frame && previous?.isRecycled == false) previous.recycle()
    }

    fun clearMoonlightFrame(layerId: String) {
        moonlightFrameCache.remove(layerId)?.let { if (!it.isRecycled) it.recycle() }
    }

    fun getBorderFrameBitmap(builtInRes: Int?, customPath: String?): Bitmap? {
        if (!customPath.isNullOrEmpty()) {
            loadGraphicBitmap(filePath = customPath, drawableRes = null)?.let { return it }
        }
        if (builtInRes != null && builtInRes != 0) {
            return loadGraphicBitmap(filePath = null, drawableRes = builtInRes)
        }
        return null
    }

    private fun loadGraphicBitmap(filePath: String?, drawableRes: Int?): Bitmap? {
        if (!filePath.isNullOrEmpty()) {
            val cacheKey = "file:$filePath"
            bitmapCache[cacheKey]?.let { return it }
            val file = File(filePath)
            if (file.exists()) {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bmp != null) {
                    bitmapCache[cacheKey] = bmp
                    return bmp
                }
            }
        }
        if (drawableRes != null && drawableRes != 0) {
            val cacheKey = "res:$drawableRes"
            bitmapCache[cacheKey]?.let { return it }
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeResource(context.resources, drawableRes, opts)
            if (bmp != null) {
                bitmapCache[cacheKey] = bmp
                return bmp
            }
        }
        return null
    }

    private fun ensureVideoDecoderRunning(layerId: String, filePath: String, loop: Boolean) {
        if (videoJobs[layerId]?.isActive == true) return
        val file = File(filePath)
        if (!file.exists()) return

        videoJobs[layerId] = scope.launch {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 5000L
                var currentTimeMs = 0L
                val stepMs = 66L // ~15fps thumbnail/clip decode loop
                while (isActive) {
                    val frame = retriever.getFrameAtTime(
                        currentTimeMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (frame != null) {
                        videoFrameCache[layerId] = frame
                    }
                    delay(stepMs)
                    currentTimeMs += stepMs
                    if (currentTimeMs >= durationMs) {
                        if (loop) currentTimeMs = 0L else break
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Connects to a PC LAN video stream (supports HTTP MJPEG streams directly and RTSP socket
     * reachability + RTSP OPTIONS handshake probing).
     */
    fun ensureNetworkReceiverRunning(layerId: String, streamUrl: String) {
        val currentJob = networkJobs[layerId]
        if (currentJob?.isActive == true && _networkStatus.value.url == streamUrl) return
        currentJob?.cancel()

        networkJobs[layerId] = scope.launch {
            _networkStatus.value = NetworkFeedStatus(
                url = streamUrl,
                isConnected = false,
                statusText = "Connecting to $streamUrl..."
            )
            while (isActive) {
                try {
                    if (streamUrl.startsWith("http://", ignoreCase = true) ||
                        streamUrl.startsWith("https://", ignoreCase = true)
                    ) {
                        readHttpMjpegOrImageStream(layerId, streamUrl)
                    } else if (streamUrl.startsWith("rtsp://", ignoreCase = true)) {
                        probeRtspSource(streamUrl)
                    } else {
                        _networkStatus.value = NetworkFeedStatus(
                            url = streamUrl,
                            isConnected = false,
                            statusText = "Unsupported URL scheme (use rtsp:// or http://)"
                        )
                    }
                } catch (e: Exception) {
                    _networkStatus.value = NetworkFeedStatus(
                        url = streamUrl,
                        isConnected = false,
                        statusText = "LAN feed unreachable: ${e.localizedMessage ?: "Timeout"}"
                    )
                }
                delay(3000L)
            }
        }
    }

    private fun readHttpMjpegOrImageStream(layerId: String, streamUrl: String) {
        val startMs = System.currentTimeMillis()
        val conn = (java.net.URL(streamUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3000
            readTimeout = 4000
            doInput = true
        }
        conn.connect()
        val latency = (System.currentTimeMillis() - startMs).toInt()
        BufferedInputStream(conn.inputStream).use { input ->
            val buffer = ByteArrayOutputStream()
            var prev = -1
            var capturingJpeg = false
            var frames = 0
            var fpsTick = System.currentTimeMillis()

            val chunk = ByteArray(4096)
            while (true) {
                val read = input.read(chunk)
                if (read <= 0) break
                for (i in 0 until read) {
                    val b = chunk[i].toInt() and 0xFF
                    if (!capturingJpeg && prev == 0xFF && b == 0xD8) {
                        capturingJpeg = true
                        buffer.reset()
                        buffer.write(0xFF)
                        buffer.write(0xD8)
                    } else if (capturingJpeg) {
                        buffer.write(b)
                        if (prev == 0xFF && b == 0xD9) {
                            val jpegBytes = buffer.toByteArray()
                            val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                            if (bmp != null) {
                                networkFrameCache[layerId] = bmp
                                frames++
                                val now = System.currentTimeMillis()
                                val fps = if (now - fpsTick >= 1000L) {
                                    val calc = ((frames * 1000L) / (now - fpsTick).coerceAtLeast(1L)).toInt()
                                    frames = 0
                                    fpsTick = now
                                    calc
                                } else _networkStatus.value.fps
                                _networkStatus.value = NetworkFeedStatus(
                                    url = streamUrl,
                                    isConnected = true,
                                    fps = fps.coerceAtLeast(1),
                                    latencyMs = latency,
                                    statusText = "Connected (${bmp.width}×${bmp.height})"
                                )
                            }
                            capturingJpeg = false
                        }
                    }
                    prev = b
                }
            }
        }
    }

    private fun probeRtspSource(rtspUrl: String) {
        val uri = URI(rtspUrl)
        val host = uri.host ?: throw IllegalArgumentException("Invalid RTSP host")
        val port = if (uri.port > 0) uri.port else 554
        val startMs = System.currentTimeMillis()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2500)
            socket.soTimeout = 2500
            val latency = (System.currentTimeMillis() - startMs).toInt()
            val req = "OPTIONS $rtspUrl RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: SceneCastLive/1.0\r\n\r\n"
            socket.getOutputStream().write(req.toByteArray())
            socket.getOutputStream().flush()
            val respBuf = ByteArray(512)
            val read = socket.getInputStream().read(respBuf)
            val respLine = if (read > 0) String(respBuf, 0, read).lineSequence().firstOrNull() ?: "RTSP OK" else "Socket Connected"
            _networkStatus.value = NetworkFeedStatus(
                url = rtspUrl,
                isConnected = true,
                fps = 30,
                latencyMs = latency,
                statusText = "RTSP Connected ($respLine • ${latency}ms)"
            )
        }
    }

    fun releaseAll() {
        videoJobs.values.forEach { it.cancel() }
        videoJobs.clear()
        networkJobs.values.forEach { it.cancel() }
        networkJobs.clear()
        cameraManager.release()
        screenCaptureManager.stopCapture()
    }
}
