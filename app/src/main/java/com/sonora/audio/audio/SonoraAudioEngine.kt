package com.sonora.audio.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.provider.Settings
import com.sonora.audio.model.DeviceAudioProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

data class AudioEngineStatus(
    val dspAttached: Boolean = false,
    val balanceSupported: Boolean = false,
    val spatialAudioAvailable: Boolean = false,
    val spatialAudioEnabled: Boolean = false,
    val headTrackingAvailable: Boolean = false,
    val systemMonoEnabled: Boolean = false,
    val volumePercent: Int = 50,
    val bandFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val findingChannel: String? = null, // null, "LEFT", "RIGHT", "BOTH"
    val statusNote: String = "Initializing DSP..."
)

object SonoraAudioEngine {

    private var audioManager: AudioManager? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    private var initialized = false
    private var findJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _status = MutableStateFlow(AudioEngineStatus())
    val status: StateFlow<AudioEngineStatus> = _status.asStateFlow()

    fun init(context: Context) {
        if (initialized) {
            refreshSystemAudioStatus(context)
            return
        }
        val appContext = context.applicationContext
        audioManager = appContext.getSystemService(AudioManager::class.java)

        var eqAttached = false
        var balanceReady = false
        val detectedFreqs = mutableListOf<Int>()

        runCatching {
            equalizer = Equalizer(0, 0).apply {
                enabled = true
                val bands = numberOfBands.toInt().coerceAtLeast(1)
                for (i in 0 until bands) {
                    detectedFreqs.add(getCenterFreq(i.toShort()) / 1000)
                }
            }
            eqAttached = true
        }

        runCatching {
            bassBoost = BassBoost(0, 0).apply { enabled = true }
        }

        runCatching {
            virtualizer = Virtualizer(0, 0).apply { enabled = true }
        }

        runCatching {
            loudnessEnhancer = LoudnessEnhancer(0).apply { enabled = false }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    false, 0,
                    false, 0,
                    false, 0,
                    false
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, 0, config).apply {
                    enabled = true
                }
                balanceReady = true
            }
        }

        initialized = true
        _status.value = _status.value.copy(
            dspAttached = eqAttached,
            balanceSupported = balanceReady,
            bandFrequenciesHz = if (detectedFreqs.size >= 5) detectedFreqs.take(5) else listOf(60, 230, 910, 3600, 14000),
            statusNote = if (eqAttached) {
                "Global Android DSP active (Session 0)"
            } else {
                "System restricted Session 0 DSP on this ROM"
            }
        )
        refreshSystemAudioStatus(appContext)
    }

    fun applyProfile(profile: DeviceAudioProfile) {
        val active = profile.eqEnabled

        runCatching {
            equalizer?.let { eq ->
                eq.enabled = active
                if (active) {
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()
                    val numBands = eq.numberOfBands.toInt()
                    for (i in 0 until numBands) {
                        val baseDb = profile.bandLevelsDb.getOrElse(i.coerceAtMost(4)) { 0f }
                        // Blend treble (top 2 bands), vocal (middle band), and gaming clarity
                        val trebleAdd = if (i >= numBands - 2) profile.trebleBoost * 5.0f else 0f
                        val vocalAdd = if (i == numBands / 2) profile.vocalBoost * 4.5f else 0f
                        val gamingAdd = if (profile.gamingMode && i >= numBands / 2) 1.5f else 0f
                        val totalMb = ((baseDb + trebleAdd + vocalAdd + gamingAdd) * 100f)
                            .roundToInt()
                            .coerceIn(minMb, maxMb)
                        eq.setBandLevel(i.toShort(), totalMb.toShort())
                    }
                }
            }
        }

        runCatching {
            bassBoost?.let { bb ->
                val enableBass = active && profile.bassBoost > 0.01f
                bb.enabled = enableBass
                if (enableBass && bb.strengthSupported) {
                    bb.setStrength((profile.bassBoost * 1000f).roundToInt().coerceIn(0, 1000).toShort())
                }
            }
        }

        runCatching {
            virtualizer?.let { virt ->
                // Disable virtualizer in Gaming Mode to keep audio latency minimal
                val enableVirt = active && !profile.gamingMode && profile.virtualizer > 0.01f
                virt.enabled = enableVirt
                if (enableVirt && virt.strengthSupported) {
                    virt.setStrength((profile.virtualizer * 1000f).roundToInt().coerceIn(0, 1000).toShort())
                }
            }
        }

        runCatching {
            loudnessEnhancer?.let { le ->
                val enableVocalGain = active && profile.vocalBoost > 0.05f
                le.enabled = enableVocalGain
                if (enableVocalGain) {
                    le.setTargetGain((profile.vocalBoost * 450f).roundToInt().coerceIn(0, 600))
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                dynamicsProcessing?.let { dp ->
                    val bal = profile.balance.coerceIn(-1f, 1f)
                    dp.enabled = kotlin.math.abs(bal) > 0.02f
                    val leftDb = if (bal > 0f) -24f * bal else 0f
                    val rightDb = if (bal < 0f) -24f * (-bal) else 0f
                    dp.setInputGainbyChannel(0, leftDb)
                    dp.setInputGainbyChannel(1, rightDb)
                }
            }
        }
    }

    fun setMediaVolumePercent(percent: Int) {
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = ((percent.coerceIn(0, 100) / 100f) * maxVol).roundToInt().coerceIn(0, maxVol)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        _status.value = _status.value.copy(volumePercent = percent.coerceIn(0, 100))
    }

    fun refreshSystemAudioStatus(context: Context) {
        val am = audioManager ?: context.applicationContext.getSystemService(AudioManager::class.java)
        val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
        val curVol = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
        val volPct = ((curVol.toFloat() / maxVol) * 100f).roundToInt()

        val mono = runCatching {
            Settings.System.getInt(context.contentResolver, "master_mono", 0) == 1
        }.getOrDefault(false)

        var spatialAvail = false
        var spatialOn = false
        var headTrack = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && am != null) {
            runCatching {
                val sp = am.spatializer
                spatialAvail = sp.isAvailable
                spatialOn = sp.isEnabled
                headTrack = sp.isHeadTrackerAvailable
            }
        }

        _status.value = _status.value.copy(
            volumePercent = volPct,
            systemMonoEnabled = mono,
            spatialAudioAvailable = spatialAvail,
            spatialAudioEnabled = spatialOn,
            headTrackingAvailable = headTrack
        )
    }

    /**
     * Real acoustic beacon for Find My Earbuds.
     * Routes a pulsing 3.2 kHz stereo chirp to LEFT, RIGHT, or BOTH channels.
     */
    fun toggleFindEarbuds(channel: String) {
        if (_status.value.findingChannel == channel) {
            stopFindEarbuds()
            return
        }
        stopFindEarbuds()
        _status.value = _status.value.copy(findingChannel = channel)

        findJob = scope.launch {
            val sampleRate = 44100
            val chirpSamples = (sampleRate * 0.28).toInt()
            val pcm = ShortArray(chirpSamples * 2) // Stereo L/R interleaved
            val leftGain = if (channel == "RIGHT") 0f else 1f
            val rightGain = if (channel == "LEFT") 0f else 1f

            for (i in 0 until chirpSamples) {
                val t = i.toDouble() / sampleRate
                val freq = if (i < chirpSamples / 2) 2800.0 else 3600.0
                val envelope = sin(PI * i / chirpSamples)
                val sample = (sin(2.0 * PI * freq * t) * envelope * 26000.0).toInt()
                pcm[i * 2] = (sample * leftGain).toInt().toShort()
                pcm[i * 2 + 1] = (sample * rightGain).toInt().toShort()
            }

            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(pcm.size * 2)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull()

            if (track == null) {
                _status.value = _status.value.copy(findingChannel = null)
                return@launch
            }

            try {
                track.play()
                var pulses = 0
                while (isActive && pulses < 16) {
                    track.write(pcm, 0, pcm.size)
                    delay(480)
                    pulses++
                }
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
                _status.value = _status.value.copy(findingChannel = null)
            }
        }
    }

    fun stopFindEarbuds() {
        findJob?.cancel()
        findJob = null
        _status.value = _status.value.copy(findingChannel = null)
    }
}
