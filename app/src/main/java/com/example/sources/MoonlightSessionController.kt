package com.example.sources

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.limelight.binding.PlatformBinding
import com.limelight.binding.audio.AndroidAudioRenderer
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.MediaCodecHelper
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.GlPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.SecureRandom

/** Owns a single independent Moonlight stream and routes decoded frames into one SceneCast layer. */
class MoonlightSessionController(
    context: Context,
    private val videoSources: VideoSourceManager
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("scenecast_moonlight_hosts", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow("Not connected")
    val status: StateFlow<String> = _status.asStateFlow()
    private var connection: NvConnection? = null
    private var decoder: MediaCodecDecoderRenderer? = null
    private var audio: AndroidAudioRenderer? = null
    private var frameAdapter: MoonlightFrameAdapter? = null
    private var activeLayerId: String? = null
    private var activeHost: String? = null

    suspend fun pair(host: String, httpPort: Int = NvHTTP.DEFAULT_HTTP_PORT, pin: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(pin.length == 4 && pin.all(Char::isDigit)) { "Enter the 4-digit PIN shown by Sunshine" }
                _status.value = "Pairing with Sunshine…"
                val crypto = PlatformBinding.getCryptoProvider(appContext)
                val http = NvHTTP(ComputerDetails.AddressTuple(host, httpPort), 0, clientId(), null, crypto)
                val serverInfo = http.getServerInfo(true)
                when (http.getPairingManager().pair(serverInfo, pin)) {
                    PairingManager.PairState.PAIRED -> {
                        val cert = http.getPairingManager().getPairedCert() ?: error("Sunshine pairing did not return a certificate")
                        prefs.edit().putString("cert:$host", Base64.encodeToString(cert.encoded, Base64.NO_WRAP))
                            .putString("name:$host", host).putInt("port:$host", httpPort).apply()
                        _status.value = "Paired with $host"
                    }
                    PairingManager.PairState.PIN_WRONG -> error("Incorrect PIN. Generate a new PIN in Sunshine and try again.")
                    PairingManager.PairState.ALREADY_IN_PROGRESS -> error("Another pairing request is already in progress on the PC")
                    else -> error("Sunshine pairing failed")
                }
            }.onFailure { _status.value = it.message ?: "Pairing failed" }
        }

    fun pairedHosts(): List<String> = prefs.all.keys.filter { it.startsWith("cert:") }.map { it.removePrefix("cert:") }.sorted()

    fun savedPort(host: String): Int = prefs.getInt("port:$host", NvHTTP.DEFAULT_HTTP_PORT).coerceIn(1, 65535)

    fun forget(host: String) {
        prefs.edit().remove("cert:$host").remove("name:$host").remove("port:$host").apply()
        if (activeHost == host && connection != null) stop()
    }

    suspend fun connect(activity: Activity, host: String, layerId: String, httpPort: Int = prefs.getInt("port:$host", NvHTTP.DEFAULT_HTTP_PORT)): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            stop()
            _status.value = "Starting Moonlight decoder…"
            val certBytes = Base64.decode(prefs.getString("cert:$host", null) ?: error("Pair this PC first"), Base64.DEFAULT)
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            val crypto = PlatformBinding.getCryptoProvider(appContext)
            val http = NvHTTP(ComputerDetails.AddressTuple(host, httpPort), 0, clientId(), cert, crypto)
            val apps = http.appList
            val app = apps.firstOrNull { it.appName.equals("Desktop", true) } ?: apps.firstOrNull()
                ?: error("No streamable applications were returned by Sunshine")
            val serverInfo = http.getServerInfo(true)
            val httpsPort = http.getHttpsPort(serverInfo)
            val streamPrefs = PreferenceConfiguration.readPreferences(appContext).apply {
                width = 1920
                height = 1080
                fps = 60
                bitrate = 20000
            }
            // Moonlight's codec capability tables are process-global and MUST be initialized
            // before constructing MediaCodecDecoderRenderer or querying isAvcSupported().
            // The official Moonlight Game activity performs this exact initialization first.
            val glRenderer = GlPreferences.readPreferences(appContext).glRenderer ?: ""
            val (adapter, renderer) = withContext(Dispatchers.Main) {
                MediaCodecHelper.initialize(appContext, glRenderer)
                val frameOutput = MoonlightFrameAdapter(
                    { bitmap -> videoSources.publishMoonlightFrame(layerId, bitmap) },
                    streamPrefs.width,
                    streamPrefs.height
                )
                val decoderRenderer = MediaCodecDecoderRenderer(
                    activity,
                    streamPrefs,
                    { e -> _status.value = "Decoder error: ${e.message}" },
                    0,
                    false,
                    false,
                    glRenderer,
                    null
                )
                decoderRenderer.setRenderTarget(frameOutput.surfaceHolder)
                frameAdapter = frameOutput
                activeLayerId = layerId
                activeHost = host
                frameOutput to decoderRenderer
            }
            check(renderer.isAvcSupported()) {
                adapter.close()
                "This device has no compatible H.264 decoder. Check Android codec support."
            }
            val audioRenderer = AndroidAudioRenderer(appContext, false)
            val config = StreamConfiguration.Builder()
                .setResolution(streamPrefs.width, streamPrefs.height)
                .setLaunchRefreshRate(streamPrefs.fps)
                .setRefreshRate(streamPrefs.fps)
                .setBitrate(streamPrefs.bitrate)
                .setApp(app)
                .setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO)
                .setSupportedVideoFormats(MoonBridge.VIDEO_FORMAT_H264 or (if (renderer.isHevcSupported) MoonBridge.VIDEO_FORMAT_H265 else 0) or (if (renderer.isAv1Supported) MoonBridge.VIDEO_FORMAT_AV1_MAIN8 else 0))
                .setAudioConfiguration(MoonBridge.AUDIO_CONFIGURATION_STEREO)
                .setClientRefreshRateX100(streamPrefs.fps * 100)
                .setColorSpace(renderer.getPreferredColorSpace())
                .setColorRange(renderer.getPreferredColorRange())
                .enableLocalAudioPlayback(true)
                .build()
            val conn = NvConnection(appContext, ComputerDetails.AddressTuple(host, httpPort), httpsPort, clientId(), config, crypto, cert)
            connection = conn
            decoder = renderer
            audio = audioRenderer
            frameAdapter = adapter
            activeLayerId = layerId
            activeHost = host
            _status.value = "Connecting to ${app.appName} on $host…"
            conn.start(audioRenderer, renderer, object : NvConnectionListener {
                override fun stageStarting(stage: String) { _status.value = "Connecting: $stage" }
                override fun stageComplete(stage: String) { _status.value = "Ready: $stage" }
                override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) { _status.value = "Connection failed: $stage ($errorCode)" }
                override fun connectionStarted() { _status.value = "Streaming from $host" }
                override fun connectionTerminated(errorCode: Int) { _status.value = "Moonlight disconnected ($errorCode)"; releaseMedia() }
                override fun connectionStatusUpdate(connectionStatus: Int) { }
                override fun displayMessage(message: String) { _status.value = message }
                override fun displayTransientMessage(message: String) { _status.value = message }
                override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) { }
                override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) { }
                override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) { }
                override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) { }
                override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) { }
            })
        }.onFailure {
            try { connection?.stop() } catch (_: Exception) { }
            connection = null
            releaseMedia()
            _status.value = it.message ?: "Unable to connect"
        }
    }

    fun stop() {
        try { connection?.stop() } catch (_: Exception) { }
        connection = null
        releaseMedia()
        _status.value = "Not connected"
    }

    private fun releaseMedia() {
        // NvConnection/MoonBridge owns renderer and AudioTrack teardown after stream shutdown.
        frameAdapter?.close()
        activeLayerId?.let(videoSources::clearMoonlightFrame)
        decoder = null; audio = null; frameAdapter = null; activeLayerId = null; activeHost = null
    }

    private fun clientId(): String = prefs.getString("client_id", null) ?:
        String.format(java.util.Locale.US, "%016x", SecureRandom().nextLong()).uppercase().also {
            prefs.edit().putString("client_id", it).apply()
        }
}
