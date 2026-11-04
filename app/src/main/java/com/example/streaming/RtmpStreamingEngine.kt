package com.example.streaming

import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.view.Surface
import com.example.audio.AudioMixerEngine
import com.example.compositor.SceneVideoCompositor
import com.example.model.Scene
import com.example.model.SceneTransitionType
import com.example.storage.StreamCredentials
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StreamConnectionStatus(val badgeText: String) {
    DISCONNECTED("STANDBY"),
    CONNECTING("CONNECTING"),
    LIVE("LIVE"),
    RECONNECTING("RECONNECTING"),
    ERROR("CONNECTION ERROR")
}

data class StreamEngineTelemetry(
    val status: StreamConnectionStatus = StreamConnectionStatus.DISCONNECTED,
    val statusDetail: String = "Ready to stream to YouTube Live",
    val isStreaming: Boolean = false,
    val currentUploadKbps: Int = 0,
    val targetBitrateKbps: Int = 4500,
    val encodedFps: Int = 0,
    val targetFps: Int = 60,
    val droppedFrames: Int = 0,
    val totalFrames: Long = 0L,
    val packetLossPercent: Float = 0f,
    val streamDurationSec: Long = 0L,
    val reconnectAttempts: Int = 0,
    val lastSpeedTestResult: ConnectionTestResult? = null,
    val isTestingConnection: Boolean = false
)

/**
 * Hardware-accelerated H.264 (MediaCodec Surface) + AAC Audio Live Streaming Engine.
 *
 * Uses a single pinned encoder thread (`encoderDispatcher`) so Surface lock/unlock never hops
 * across thread-pool workers (which prevents EGL/Surface native memory exhaustion after 30s).
 */
class RtmpStreamingEngine(
    private val compositor: SceneVideoCompositor,
    private val audioMixer: AudioMixerEngine
) {
    // Dedicated single-thread dispatcher so MediaCodec Surface lock/unlock always runs on the same OS thread
    private val encoderDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SceneCastEncoderThread").apply {
            priority = Thread.MAX_PRIORITY
        }
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + encoderDispatcher)
    private val rtmpClient = RtmpProtocolClient()

    private var videoEncoder: MediaCodec? = null
    private var videoInputSurface: Surface? = null
    private var audioEncoder: MediaCodec? = null

    private var streamJob: Job? = null
    private val _telemetry = MutableStateFlow(StreamEngineTelemetry())
    val telemetry: StateFlow<StreamEngineTelemetry> = _telemetry.asStateFlow()

    @Volatile
    private var currentSceneProvider: (() -> Scene)? = null

    @Volatile
    private var transitionStateProvider: (() -> Triple<Scene?, Float, SceneTransitionType>)? = null

    private var streamStartEpochMs = 0L
    private val surfaceBlitPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun bindSceneProviders(
        sceneProvider: () -> Scene,
        transitionProvider: () -> Triple<Scene?, Float, SceneTransitionType>
    ) {
        currentSceneProvider = sceneProvider
        transitionStateProvider = transitionProvider
    }

    fun runConnectionTest(credentials: StreamCredentials) {
        if (_telemetry.value.isTestingConnection) return
        scope.launch(Dispatchers.IO) {
            _telemetry.value = _telemetry.value.copy(
                isTestingConnection = true,
                statusDetail = "Testing RTMPS handshake & uplink speed..."
            )
            val result = rtmpClient.testConnectionAndSpeed(
                serverUrl = credentials.rtmpServerUrl,
                streamKey = credentials.streamKey
            )
            _telemetry.value = _telemetry.value.copy(
                isTestingConnection = false,
                lastSpeedTestResult = result,
                statusDetail = result.message
            )
        }
    }

    fun startStreaming(credentials: StreamCredentials, onStarted: () -> Unit = {}) {
        streamJob?.cancel()
        if (credentials.streamKey.isBlank()) {
            _telemetry.value = _telemetry.value.copy(
                status = StreamConnectionStatus.ERROR,
                statusDetail = "Enter your YouTube RTMPS Stream Key in Stream Settings before going live."
            )
            return
        }

        streamJob = scope.launch(encoderDispatcher) {
            var reconnectCount = 0
            streamStartEpochMs = System.currentTimeMillis()

            // Ensure pristine state for encoders and socket
            releaseEncoders()
            rtmpClient.disconnect()

            while (isActive) {
                try {
                    val width = credentials.resolutionPreset.width
                    val height = credentials.resolutionPreset.height
                    val fps = credentials.resolutionPreset.fps
                    val videoBitrateBps = credentials.videoBitrateKbps * 1000
                    val audioBitrateBps = credentials.audioBitrateKbps * 1000

                    _telemetry.value = _telemetry.value.copy(
                        status = if (reconnectCount == 0) StreamConnectionStatus.CONNECTING else StreamConnectionStatus.RECONNECTING,
                        targetBitrateKbps = credentials.videoBitrateKbps,
                        targetFps = fps,
                        totalFrames = 0L,
                        packetLossPercent = 0f,
                        statusDetail = if (reconnectCount == 0) {
                            "Connecting to ${credentials.rtmpServerUrl}..."
                        } else {
                            "Reconnecting to YouTube Live (Attempt $reconnectCount)..."
                        },
                        reconnectAttempts = reconnectCount
                    )

                    rtmpClient.connectAndPublish(
                        serverUrl = credentials.rtmpServerUrl,
                        streamKey = credentials.streamKey,
                        width = width,
                        height = height,
                        fps = fps,
                        videoBitrateKbps = credentials.videoBitrateKbps,
                        audioBitrateKbps = credentials.audioBitrateKbps
                    )

                    initHardwareEncoders(
                        width = width,
                        height = height,
                        fps = fps,
                        videoBitrateBps = videoBitrateBps,
                        audioBitrateBps = audioBitrateBps,
                        keyframeIntervalSec = credentials.keyframeIntervalSec
                    )

                    if (reconnectCount == 0) {
                        withContext(Dispatchers.Main) {
                            onStarted()
                        }
                    }

                    _telemetry.value = _telemetry.value.copy(
                        status = StreamConnectionStatus.LIVE,
                        isStreaming = true,
                        targetBitrateKbps = credentials.videoBitrateKbps,
                        targetFps = fps,
                        statusDetail = "LIVE on YouTube (${width}×${height} @ ${fps}fps • ${credentials.videoBitrateKbps} kbps)"
                    )

                    runEncodingAndPublishingLoop(width, height, fps, credentials.videoBitrateKbps)
                    if (!isActive) break
                } catch (e: Exception) {
                    releaseEncoders()
                    rtmpClient.disconnect()

                    // If cancelled intentionally by stopStreaming(), do NOT report as error or reconnect!
                    if (e is kotlinx.coroutines.CancellationException || !isActive) {
                        _telemetry.value = _telemetry.value.copy(
                            status = StreamConnectionStatus.DISCONNECTED,
                            isStreaming = false,
                            currentUploadKbps = 0,
                            encodedFps = 0,
                            packetLossPercent = 0f,
                            statusDetail = "Stream ended cleanly"
                        )
                        break
                    }

                    val dropped = _telemetry.value.droppedFrames + 1
                    if (!credentials.autoReconnect || !isActive) {
                        _telemetry.value = _telemetry.value.copy(
                            status = StreamConnectionStatus.ERROR,
                            isStreaming = false,
                            droppedFrames = dropped,
                            statusDetail = "Stream stopped: ${e.localizedMessage ?: "Network disconnected"}"
                        )
                        break
                    } else {
                        reconnectCount++
                        _telemetry.value = _telemetry.value.copy(
                            status = StreamConnectionStatus.RECONNECTING,
                            droppedFrames = dropped,
                            reconnectAttempts = reconnectCount,
                            statusDetail = "Network hiccup — auto-reconnecting (Attempt $reconnectCount)..."
                        )
                        delay(1500L)
                    }
                }
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        audioMixer.onMixedPcmFrame = null
        // Immediately disconnect RTMP socket so publishing loop terminates instantly
        rtmpClient.disconnect()
        scope.launch(encoderDispatcher) {
            releaseEncoders()
        }
        _telemetry.value = _telemetry.value.copy(
            status = StreamConnectionStatus.DISCONNECTED,
            isStreaming = false,
            currentUploadKbps = 0,
            encodedFps = 0,
            packetLossPercent = 0f,
            statusDetail = "Stream ended cleanly"
        )
    }

    private fun initHardwareEncoders(
        width: Int,
        height: Int,
        fps: Int,
        videoBitrateBps: Int,
        audioBitrateBps: Int,
        keyframeIntervalSec: Int
    ) {
        releaseEncoders()

        val vEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val configured = tryConfigureHighQualityAvc(
            vEncoder,
            width,
            height,
            fps,
            videoBitrateBps,
            keyframeIntervalSec
        )
        if (!configured) {
            val baseFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, videoBitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeIntervalSec.coerceAtLeast(1))
            }
            vEncoder.configure(baseFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        val surface = vEncoder.createInputSurface()
        vEncoder.start()
        videoEncoder = vEncoder
        videoInputSurface = surface

        // 2. Hardware AAC Audio Encoder
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            AudioMixerEngine.SAMPLE_RATE,
            1
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, audioBitrateBps)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val aEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        aEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        aEncoder.start()
        audioEncoder = aEncoder

        audioMixer.onMixedPcmFrame = { pcmShorts, sampleCount, ptsUs ->
            queueAudioPcmToAacEncoder(pcmShorts, sampleCount, ptsUs)
        }
    }

    private fun tryConfigureHighQualityAvc(
        encoder: MediaCodec,
        width: Int,
        height: Int,
        fps: Int,
        videoBitrateBps: Int,
        keyframeIntervalSec: Int
    ): Boolean {
        return try {
            val hqFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, videoBitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeIntervalSec.coerceAtLeast(1))
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                )
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setInteger(MediaFormat.KEY_PRIORITY, 0) // Real-time hardware priority
                    setInteger(MediaFormat.KEY_OPERATING_RATE, fps)
                }
            }
            encoder.configure(hqFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            true
        } catch (_: Exception) {
            try {
                encoder.reset()
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun queueAudioPcmToAacEncoder(pcmShorts: ShortArray, sampleCount: Int, ptsUs: Long) {
        val aEnc = audioEncoder ?: return
        try {
            val inIndex = aEnc.dequeueInputBuffer(0L)
            if (inIndex >= 0) {
                val inputBuffer = aEnc.getInputBuffer(inIndex) ?: return
                inputBuffer.clear()
                val maxShorts = minOf(sampleCount, inputBuffer.remaining() / 2)
                for (i in 0 until maxShorts) {
                    val s = pcmShorts[i].toInt()
                    inputBuffer.put((s and 0xFF).toByte())
                    inputBuffer.put(((s ushr 8) and 0xFF).toByte())
                }
                aEnc.queueInputBuffer(inIndex, 0, maxShorts * 2, ptsUs, 0)
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun runEncodingAndPublishingLoop(width: Int, height: Int, fps: Int, targetBitrateKbps: Int) {
        val frameIntervalMs = (1000L / fps.coerceAtLeast(1)).coerceAtLeast(16L)
        val vBufferInfo = MediaCodec.BufferInfo()
        val aBufferInfo = MediaCodec.BufferInfo()

        var encodedFramesInWindow = 0
        var totalFramesAttempted = _telemetry.value.totalFrames
        var lastStatsTickMs = System.currentTimeMillis()
        var lastKeyframeRequestMs = System.currentTimeMillis()
        var lastBytesTotal = rtmpClient.totalBytesSent

        while (scope.isActive && rtmpClient.isConnected) {
            val frameStartMs = System.currentTimeMillis()
            val surface = videoInputSurface
            val scene = currentSceneProvider?.invoke()
            totalFramesAttempted++

            if (surface != null && surface.isValid && scene != null) {
                try {
                    val (outgoing, progress, transType) = transitionStateProvider?.invoke()
                        ?: Triple(null, 1f, SceneTransitionType.CUT)

                    // Render into reusable master offscreen bitmap and blit to Surface via lockCanvas(null)
                    // on the pinned single encoder thread (zero EGL context leaks across threads!)
                    val masterBmp = compositor.renderToOffscreenBitmap(
                        activeScene = scene,
                        outgoingScene = outgoing,
                        transitionProgress = progress,
                        transitionType = transType,
                        targetWidth = width,
                        targetHeight = height
                    )
                    val surfaceCanvas = surface.lockCanvas(null)
                    if (surfaceCanvas != null) {
                        try {
                            surfaceCanvas.drawBitmap(masterBmp, 0f, 0f, surfaceBlitPaint)
                        } finally {
                            surface.unlockCanvasAndPost(surfaceCanvas)
                        }
                        encodedFramesInWindow++
                    }
                } catch (_: Exception) {
                    _telemetry.value = _telemetry.value.copy(
                        droppedFrames = _telemetry.value.droppedFrames + 1
                    )
                }
            }

            // Periodically request a fresh IDR sync frame every 2 seconds for YouTube Live stability
            val now = System.currentTimeMillis()
            if (now - lastKeyframeRequestMs >= 2000L) {
                lastKeyframeRequestMs = now
                try {
                    val params = Bundle().apply {
                        putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                    }
                    videoEncoder?.setParameters(params)
                } catch (_: Exception) {
                }
            }

            drainVideoEncoder(vBufferInfo)
            drainAudioEncoder(aBufferInfo)

            if (now - lastStatsTickMs >= 1000L) {
                val elapsedSec = ((now - lastStatsTickMs) / 1000f).coerceAtLeast(0.5f)
                val currentBytes = rtmpClient.totalBytesSent
                val deltaBits = (currentBytes - lastBytesTotal) * 8L
                val uploadKbps = ((deltaBits / 1000f) / elapsedSec).toInt().coerceAtLeast(0)
                val measuredFps = (encodedFramesInWindow / elapsedSec).toInt().coerceIn(0, 60)

                lastBytesTotal = currentBytes
                encodedFramesInWindow = 0
                lastStatsTickMs = now

                val dropped = _telemetry.value.droppedFrames
                val frameDropPct = if (totalFramesAttempted > 0L) {
                    ((dropped.toFloat() / totalFramesAttempted.toFloat()) * 100f).coerceIn(0f, 100f)
                } else 0f

                val bandwidthDeficitPct = if (uploadKbps < targetBitrateKbps * 0.75f && totalFramesAttempted > 20L) {
                    ((1f - (uploadKbps.toFloat() / targetBitrateKbps.coerceAtLeast(500).toFloat())) * 15f).coerceIn(0f, 30f)
                } else 0f

                val combinedLossPct = maxOf(frameDropPct, bandwidthDeficitPct)
                val safeLossPct = ((combinedLossPct * 10f).toInt()) / 10f

                _telemetry.value = _telemetry.value.copy(
                    currentUploadKbps = uploadKbps,
                    targetBitrateKbps = targetBitrateKbps,
                    encodedFps = measuredFps,
                    targetFps = fps,
                    totalFrames = totalFramesAttempted,
                    packetLossPercent = safeLossPct,
                    streamDurationSec = (now - streamStartEpochMs) / 1000L
                )
            }

            val renderCostMs = System.currentTimeMillis() - frameStartMs
            val sleepMs = (frameIntervalMs - renderCostMs).coerceAtLeast(2L)
            delay(sleepMs)
        }
    }

    private fun drainVideoEncoder(bufferInfo: MediaCodec.BufferInfo) {
        val vEnc = videoEncoder ?: return
        var loopGuard = 0
        while (loopGuard++ < 32) {
            val outIndex = try {
                vEnc.dequeueOutputBuffer(bufferInfo, 0L)
            } catch (_: Exception) {
                break
            }
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                try {
                    val format = vEnc.outputFormat
                    val spsBuf = format.getByteBuffer("csd-0")
                    val ppsBuf = format.getByteBuffer("csd-1")
                    if (spsBuf != null && ppsBuf != null) {
                        val sps = byteBufferToArray(spsBuf)
                        val pps = byteBufferToArray(ppsBuf)
                        val ts = (System.currentTimeMillis() - streamStartEpochMs).toInt()
                        rtmpClient.sendAvcSequenceHeader(sps, pps, ts)
                    }
                } catch (_: Exception) {
                }
            } else if (outIndex >= 0) {
                try {
                    val outBuf = vEnc.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.get(bytes)
                            val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                            val ts = (System.currentTimeMillis() - streamStartEpochMs).toInt()
                            rtmpClient.sendAvcVideoFrame(bytes, isKeyframe, ts)
                        }
                    }
                } finally {
                    try {
                        vEnc.releaseOutputBuffer(outIndex, false)
                    } catch (_: Exception) {
                    }
                }
            } else {
                break
            }
        }
    }

    private fun drainAudioEncoder(bufferInfo: MediaCodec.BufferInfo) {
        val aEnc = audioEncoder ?: return
        var loopGuard = 0
        while (loopGuard++ < 32) {
            val outIndex = try {
                aEnc.dequeueOutputBuffer(bufferInfo, 0L)
            } catch (_: Exception) {
                break
            }
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                try {
                    val format = aEnc.outputFormat
                    val csd0 = format.getByteBuffer("csd-0")
                    if (csd0 != null) {
                        val asc = byteBufferToArray(csd0)
                        val ts = (System.currentTimeMillis() - streamStartEpochMs).toInt()
                        rtmpClient.sendAacSequenceHeader(asc, ts)
                    }
                } catch (_: Exception) {
                }
            } else if (outIndex >= 0) {
                try {
                    val outBuf = aEnc.getOutputBuffer(outIndex)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.get(bytes)
                            val ts = (System.currentTimeMillis() - streamStartEpochMs).toInt()
                            rtmpClient.sendAacAudioFrame(bytes, ts)
                        }
                    }
                } finally {
                    try {
                        aEnc.releaseOutputBuffer(outIndex, false)
                    } catch (_: Exception) {
                    }
                }
            } else {
                break
            }
        }
    }

    private fun byteBufferToArray(buf: ByteBuffer): ByteArray {
        val dup = buf.duplicate()
        val arr = ByteArray(dup.remaining())
        dup.get(arr)
        return arr
    }

    private fun releaseEncoders() {
        try {
            videoInputSurface?.release()
        } catch (_: Exception) {
        }
        videoInputSurface = null

        try {
            videoEncoder?.stop()
        } catch (_: Exception) {
        }
        try {
            videoEncoder?.release()
        } catch (_: Exception) {
        }
        videoEncoder = null

        try {
            audioEncoder?.stop()
        } catch (_: Exception) {
        }
        try {
            audioEncoder?.release()
        } catch (_: Exception) {
        }
        audioEncoder = null
    }
}
