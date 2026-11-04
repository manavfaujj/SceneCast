package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.os.Build
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
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioChannelConfig(
    val id: String,
    val name: String,
    val subtitle: String,
    val muted: Boolean = false,
    val volume: Float = 1.0f, // 0.0f .. 1.5f
    val peakLevel: Float = 0f, // 0.0f .. 1.0f
    val dbFs: Float = -60f,
    val isAvailable: Boolean = true,
    val statusNote: String = "Ready"
)

data class AudioMixerState(
    val micChannel: AudioChannelConfig = AudioChannelConfig(
        id = "mic",
        name = "Phone Microphone",
        subtitle = "Hardware Mic Input (44.1 kHz AAC)"
    ),
    val devicePlaybackChannel: AudioChannelConfig = AudioChannelConfig(
        id = "device_playback",
        name = "Screen / App Audio",
        subtitle = "AudioPlaybackCapture via MediaProjection",
        statusNote = "Active with Screen Capture"
    ),
    val networkAudioChannel: AudioChannelConfig = AudioChannelConfig(
        id = "network_audio",
        name = "Network Stream Audio",
        subtitle = "Network Stream Embedded Audio",
        muted = true
    ),
    val masterMuted: Boolean = false,
    val masterVolume: Float = 1.0f,
    val masterPeakLevel: Float = 0f,
    val micMonitoringEnabled: Boolean = false,
    val noiseSuppressionEnabled: Boolean = true,
    val echoCancellationEnabled: Boolean = true,
    val avSyncOffsetMs: Int = 0, // -250ms .. +250ms
    val micOnlyMode: Boolean = false,
    val duplicateCapturePrevented: Boolean = true
)

/**
 * Real-time Audio Mixer for SceneCast Live:
 * - Captures Phone Microphone via AudioRecord
 * - Captures Screen / App Audio playback via AudioPlaybackCaptureConfiguration
 * - Applies hardware NoiseSuppressor & AcousticEchoCanceler where supported
 * - Mixes PCM 16-bit 44.1kHz samples with independent volumes, mute states,
 *   A/V synchronization offset, and feeds mixed PCM directly to the RTMPS AAC encoder.
 */
class AudioMixerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null

    private var micRecord: AudioRecord? = null
    private var playbackRecord: AudioRecord? = null
    private var monitorTrack: AudioTrack? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    private val _state = MutableStateFlow(AudioMixerState())
    val state: StateFlow<AudioMixerState> = _state.asStateFlow()

    var onMixedPcmFrame: ((ShortArray, Int, Long) -> Unit)? = null

    fun setChannelVolume(channelId: String, volume: Float) {
        val clamped = volume.coerceIn(0f, 1.5f)
        val s = _state.value
        _state.value = when (channelId) {
            "mic" -> s.copy(micChannel = s.micChannel.copy(volume = clamped))
            "device_playback" -> s.copy(devicePlaybackChannel = s.devicePlaybackChannel.copy(volume = clamped))
            "network_audio" -> s.copy(networkAudioChannel = s.networkAudioChannel.copy(volume = clamped))
            "master" -> s.copy(masterVolume = clamped)
            else -> s
        }
    }

    fun toggleChannelMute(channelId: String) {
        val s = _state.value
        _state.value = when (channelId) {
            "mic" -> s.copy(micChannel = s.micChannel.copy(muted = !s.micChannel.muted))
            "device_playback" -> s.copy(devicePlaybackChannel = s.devicePlaybackChannel.copy(muted = !s.devicePlaybackChannel.muted))
            "network_audio" -> s.copy(networkAudioChannel = s.networkAudioChannel.copy(muted = !s.networkAudioChannel.muted))
            "master" -> s.copy(masterMuted = !s.masterMuted)
            else -> s
        }
    }

    fun setMasterMute(muted: Boolean) {
        _state.value = _state.value.copy(masterMuted = muted)
    }

    fun setMicOnlyMode(enabled: Boolean) {
        _state.value = _state.value.copy(
            micOnlyMode = enabled,
            devicePlaybackChannel = _state.value.devicePlaybackChannel.copy(
                muted = if (enabled) true else _state.value.devicePlaybackChannel.muted
            )
        )
    }

    fun setNoiseSuppression(enabled: Boolean) {
        _state.value = _state.value.copy(noiseSuppressionEnabled = enabled)
        try {
            noiseSuppressor?.enabled = enabled
        } catch (_: Exception) {
        }
    }

    fun setEchoCancellation(enabled: Boolean) {
        _state.value = _state.value.copy(echoCancellationEnabled = enabled)
        try {
            echoCanceler?.enabled = enabled
        } catch (_: Exception) {
        }
    }

    fun setMicMonitoring(enabled: Boolean) {
        _state.value = _state.value.copy(
            micMonitoringEnabled = enabled,
            duplicateCapturePrevented = true
        )
    }

    fun setAvSyncOffsetMs(offsetMs: Int) {
        _state.value = _state.value.copy(avSyncOffsetMs = offsetMs.coerceIn(-250, 250))
    }

    @SuppressLint("MissingPermission")
    fun startAudioEngine(mediaProjection: MediaProjection? = null) {
        if (captureJob?.isActive == true) {
            if (mediaProjection != null && playbackRecord == null) {
                initPlaybackCapture(mediaProjection)
            }
            return
        }

        val sampleRate = SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
            .coerceAtLeast(2048)

        try {
            val audioSource = if (_state.value.echoCancellationEnabled) {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            } else {
                MediaRecorder.AudioSource.MIC
            }
            val record = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                encoding,
                minBuf * 2
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                micRecord = record
                attachAudioEffects(record.audioSessionId)
                record.startRecording()
                _state.value = _state.value.copy(
                    micChannel = _state.value.micChannel.copy(
                        isAvailable = true,
                        statusNote = "Capturing 44.1kHz PCM"
                    )
                )
            } else {
                record.release()
                _state.value = _state.value.copy(
                    micChannel = _state.value.micChannel.copy(
                        isAvailable = false,
                        statusNote = "Grant microphone permission to capture voice"
                    )
                )
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                micChannel = _state.value.micChannel.copy(
                    isAvailable = false,
                    statusNote = "Mic unavailable: ${e.localizedMessage ?: "Permission required"}"
                )
            )
        }

        if (mediaProjection != null) {
            initPlaybackCapture(mediaProjection)
        }

        captureJob = scope.launch {
            val frameSamples = 1024
            val micBuf = ShortArray(frameSamples)
            val pbBuf = ShortArray(frameSamples)
            val mixBuf = ShortArray(frameSamples)

            while (isActive) {
                val currentState = _state.value
                var micRead = 0
                val activeMic = micRecord
                if (activeMic != null && activeMic.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    micRead = activeMic.read(micBuf, 0, frameSamples)
                }

                var pbRead = 0
                val activePb = playbackRecord
                if (activePb != null &&
                    activePb.recordingState == AudioRecord.RECORDSTATE_RECORDING &&
                    !currentState.micOnlyMode
                ) {
                    pbRead = activePb.read(pbBuf, 0, frameSamples)
                }

                if (micRead <= 0 && pbRead <= 0) {
                    delay(20L)
                    _state.value = currentState.copy(
                        micChannel = currentState.micChannel.copy(
                            peakLevel = currentState.micChannel.peakLevel * 0.7f
                        ),
                        devicePlaybackChannel = currentState.devicePlaybackChannel.copy(
                            peakLevel = currentState.devicePlaybackChannel.peakLevel * 0.7f
                        ),
                        masterPeakLevel = currentState.masterPeakLevel * 0.7f
                    )
                    continue
                }

                val count = maxOf(micRead, pbRead, frameSamples)
                val micGain = if (currentState.micChannel.muted || currentState.masterMuted) 0f else currentState.micChannel.volume
                val pbGain = if (currentState.devicePlaybackChannel.muted ||
                    currentState.masterMuted ||
                    currentState.micOnlyMode ||
                    currentState.micMonitoringEnabled
                ) {
                    0f
                } else {
                    currentState.devicePlaybackChannel.volume
                }
                val masterGain = if (currentState.masterMuted) 0f else currentState.masterVolume

                var sumMicSq = 0.0
                var sumPbSq = 0.0
                var sumMixSq = 0.0

                for (i in 0 until count) {
                    val mSample = if (i < micRead) micBuf[i].toFloat() else 0f
                    val pSample = if (i < pbRead) pbBuf[i].toFloat() else 0f

                    sumMicSq += mSample * mSample
                    sumPbSq += pSample * pSample

                    val mixed = ((mSample * micGain) + (pSample * pbGain)) * masterGain
                    val clamped = mixed.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    mixBuf[i] = clamped
                    sumMixSq += clamped.toFloat() * clamped.toFloat()
                }

                val micLevel = rmsToNormalizedLevel(sumMicSq, count)
                val pbLevel = rmsToNormalizedLevel(sumPbSq, count)
                val masterLevel = rmsToNormalizedLevel(sumMixSq, count)

                _state.value = currentState.copy(
                    micChannel = currentState.micChannel.copy(
                        peakLevel = if (currentState.micChannel.muted) 0f else micLevel,
                        dbFs = levelToDb(micLevel)
                    ),
                    devicePlaybackChannel = currentState.devicePlaybackChannel.copy(
                        peakLevel = if (currentState.devicePlaybackChannel.muted || currentState.micOnlyMode) 0f else pbLevel,
                        dbFs = levelToDb(pbLevel)
                    ),
                    masterPeakLevel = if (currentState.masterMuted) 0f else masterLevel
                )

                if (currentState.micMonitoringEnabled && micRead > 0 && !currentState.micChannel.muted) {
                    playMonitorSamples(micBuf, micRead)
                }

                val ptsUs = (System.nanoTime() / 1000L) + (currentState.avSyncOffsetMs * 1000L)
                onMixedPcmFrame?.invoke(mixBuf, count, ptsUs)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun initPlaybackCapture(mediaProjection: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _state.value = _state.value.copy(
                devicePlaybackChannel = _state.value.devicePlaybackChannel.copy(
                    isAvailable = false,
                    statusNote = "Playback capture requires Android 10+"
                )
            )
            return
        }
        try {
            playbackRecord?.release()
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            val record = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            if (record.state == AudioRecord.STATE_INITIALIZED) {
                record.startRecording()
                playbackRecord = record
                _state.value = _state.value.copy(
                    devicePlaybackChannel = _state.value.devicePlaybackChannel.copy(
                        isAvailable = true,
                        statusNote = "Capturing App Audio"
                    )
                )
            } else {
                record.release()
                _state.value = _state.value.copy(
                    devicePlaybackChannel = _state.value.devicePlaybackChannel.copy(
                        isAvailable = false,
                        statusNote = "Blocked by source app policy or permission"
                    )
                )
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                devicePlaybackChannel = _state.value.devicePlaybackChannel.copy(
                    isAvailable = false,
                    statusNote = "Playback capture unavailable: ${e.localizedMessage ?: "Restricted"}"
                )
            )
        }
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = _state.value.noiseSuppressionEnabled
                }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = _state.value.echoCancellationEnabled
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun playMonitorSamples(samples: ShortArray, count: Int) {
        try {
            var track = monitorTrack
            if (track == null) {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track.play()
                monitorTrack = track
            }
            track.write(samples, 0, count)
        } catch (_: Exception) {
        }
    }

    private fun rmsToNormalizedLevel(sumSquares: Double, count: Int): Float {
        if (count <= 0) return 0f
        val rms = sqrt(sumSquares / count) / 32768.0
        val db = 20.0 * log10(rms.coerceAtLeast(1e-4))
        return ((db.toFloat() + 55f) / 55f).coerceIn(0f, 1f)
    }

    private fun levelToDb(level: Float): Float {
        return (-60f + (level * 60f)).coerceIn(-60f, 0f)
    }

    fun stopAudioEngine() {
        captureJob?.cancel()
        captureJob = null
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
        }
        noiseSuppressor = null
        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        }
        echoCanceler = null
        try {
            micRecord?.stop()
            micRecord?.release()
        } catch (_: Exception) {
        }
        micRecord = null
        try {
            playbackRecord?.stop()
            playbackRecord?.release()
        } catch (_: Exception) {
        }
        playbackRecord = null
        try {
            monitorTrack?.stop()
            monitorTrack?.release()
        } catch (_: Exception) {
        }
        monitorTrack = null
    }

    companion object {
        const val SAMPLE_RATE = 44100
    }
}
