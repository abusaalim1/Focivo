package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object RingtoneCatalog {
    data class RingtoneInfo(
        val id: String,
        val title: String,
        val subtitle: String,
        val iconType: String
    )

    val RINGTONES = listOf(
        RingtoneInfo("Zen Bell", "Zen Bell", "Harmonic singing bowl with rich decay", "bell"),
        RingtoneInfo("Morning Birds", "Morning Birds", "Melodic high-frequency bird warble", "nature"),
        RingtoneInfo("Digital Pulse", "Digital Pulse", "Crisp rhythmic electronic cadence", "digital"),
        RingtoneInfo("Celestial Harp", "Celestial Harp", "Ascending shimmer arpeggio", "music"),
        RingtoneInfo("Cosmic Chime", "Cosmic Chime", "Deep ethereal dual-harmonic resonance", "space"),
        RingtoneInfo("Gentle Kalimba", "Gentle Kalimba", "Warm African wooden thumb piano", "acoustic"),
        RingtoneInfo("Nordic Dawn", "Nordic Dawn", "Warm ambient harmonic swell pad", "ambient"),
        RingtoneInfo("Crystal Drops", "Crystal Drops", "Bright resonant glass droplet cascade", "crystal"),
        RingtoneInfo("Subtle Radar", "Subtle Radar", "Sonar acoustic pulse with harmonic echo", "radar"),
        RingtoneInfo("Marimba Sunrise", "Marimba Sunrise", "Vibrant wooden marimba triplet run", "marimba")
    )
}

class AlarmAudioEngine(private val context: Context? = null) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var activeTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var isAlarmActive = false
    private var currentlyPreviewing: String? = null

    private val vibrator: Vibrator? by lazy {
        context?.let { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }

    fun isPreviewing(ringtone: String): Boolean = currentlyPreviewing == ringtone

    fun togglePreview(ringtone: String, onStateChanged: (String?) -> Unit = {}) {
        if (currentlyPreviewing == ringtone) {
            stopPreview()
            onStateChanged(null)
        } else {
            stop()
            currentlyPreviewing = ringtone
            onStateChanged(ringtone)
            playRingtoneInternal(ringtone, loop = false) {
                currentlyPreviewing = null
                onStateChanged(null)
            }
        }
    }

    fun stopPreview() {
        currentlyPreviewing = null
        stop()
    }

    fun startAlarm(ringtone: String) {
        stop()
        isAlarmActive = true
        startVibration()
        playRingtoneInternal(ringtone, loop = true)
    }

    fun stopAlarm() {
        isAlarmActive = false
        stopVibration()
        stop()
    }

    fun stop() {
        currentlyPreviewing = null
        playbackJob?.cancel()
        playbackJob = null
        try {
            activeTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w("AlarmAudioEngine", "Error releasing track: ${e.message}")
        }
        activeTrack = null
    }

    private fun startVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 400, 200, 400, 600)
                val amplitudes = intArrayOf(0, 220, 0, 240, 0)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 400, 200, 400, 600), 0)
            }
        } catch (e: Exception) {
            Log.w("AlarmAudioEngine", "Vibration error: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun playRingtoneInternal(ringtone: String, loop: Boolean, onComplete: () -> Unit = {}) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val sampleRate = 44100
                val pcmData = generateRingtonePcm(ringtone, sampleRate)
                val bufferSizeBytes = pcmData.size * 2

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSizeBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                activeTrack = track
                track.play()

                while (isActive) {
                    track.write(pcmData, 0, pcmData.size)
                    if (!loop) {
                        break
                    }
                }

                // If not looped, allow tail to play out
                if (!loop) {
                    kotlinx.coroutines.delay(200L)
                }
            } catch (e: Exception) {
                Log.w("AlarmAudioEngine", "Playback error for $ringtone: ${e.message}")
            } finally {
                onComplete()
            }
        }
    }

    /**
     * Synthesizes 10 rich acoustic / electronic procedural ringtones.
     */
    private fun generateRingtonePcm(ringtone: String, sampleRate: Int): ShortArray {
        return when (ringtone) {
            "Zen Bell" -> generateZenBell(sampleRate)
            "Morning Birds" -> generateMorningBirds(sampleRate)
            "Digital Pulse" -> generateDigitalPulse(sampleRate)
            "Celestial Harp" -> generateCelestialHarp(sampleRate)
            "Cosmic Chime" -> generateCosmicChime(sampleRate)
            "Gentle Kalimba" -> generateGentleKalimba(sampleRate)
            "Nordic Dawn" -> generateNordicDawn(sampleRate)
            "Crystal Drops" -> generateCrystalDrops(sampleRate)
            "Subtle Radar" -> generateSubtleRadar(sampleRate)
            "Marimba Sunrise" -> generateMarimbaSunrise(sampleRate)
            else -> generateZenBell(sampleRate)
        }
    }

    // 1. Zen Bell (432Hz deep singing bowl)
    private fun generateZenBell(sampleRate: Int): ShortArray {
        val durationSec = 2.4
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val f1 = 432.0
        val f2 = 864.0
        val f3 = 1296.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = exp(-1.8 * t)
            val shimmer = sin(2.0 * PI * 3.5 * t) * 0.15
            val sample = (sin(2.0 * PI * f1 * t) * 0.65 +
                    sin(2.0 * PI * f2 * t) * 0.25 +
                    sin(2.0 * PI * f3 * t) * 0.1) * (decay + shimmer * decay)
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000.0).toInt().toShort()
        }
        return buffer
    }

    // 2. Morning Birds (High FM chirping sequence)
    private fun generateMorningBirds(sampleRate: Int): ShortArray {
        val durationSec = 2.0
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)

        val chirps = listOf(
            Triple(0.05, 0.22, 2600.0),
            Triple(0.30, 0.48, 2950.0),
            Triple(0.55, 0.72, 3300.0),
            Triple(0.85, 1.15, 2750.0),
            Triple(1.25, 1.55, 3100.0)
        )

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            for (chirp in chirps) {
                val start = chirp.first
                val end = chirp.second
                val baseFreq = chirp.third

                if (t in start..end) {
                    val dt = t - start
                    val dur = end - start
                    val env = sin(PI * (dt / dur)) // bell envelope
                    val modFreq = baseFreq + sin(2.0 * PI * 28.0 * dt) * 350.0 + (dt / dur) * 500.0
                    sample += sin(2.0 * PI * modFreq * dt) * env * 0.65
                }
            }
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 30000.0).toInt().toShort()
        }
        return buffer
    }

    // 3. Digital Pulse (Precision modern electronic double-beep)
    private fun generateDigitalPulse(sampleRate: Int): ShortArray {
        val durationSec = 1.4
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val f1 = 880.0 // A5
        val f2 = 1320.0 // E6

        // Pulse 1: 0.05 to 0.18 (880Hz), Pulse 2: 0.24 to 0.40 (1320Hz)
        // Pulse 3: 0.55 to 0.68 (880Hz), Pulse 4: 0.74 to 0.90 (1320Hz)
        val pulses = listOf(
            Triple(0.05, 0.18, f1),
            Triple(0.24, 0.40, f2),
            Triple(0.55, 0.68, f1),
            Triple(0.74, 0.90, f2)
        )

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            for (p in pulses) {
                if (t in p.first..p.second) {
                    val dt = t - p.first
                    val dur = p.second - p.first
                    val attack = (dt / 0.01).coerceAtMost(1.0)
                    val decay = ((p.second - t) / 0.02).coerceAtMost(1.0)
                    val env = attack * decay
                    val tone = sin(2.0 * PI * p.third * dt)
                    // gentle harmonic
                    val harmonic = sin(2.0 * PI * (p.third * 2.0) * dt) * 0.25
                    sample += (tone + harmonic) * env * 0.75
                }
            }
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000.0).toInt().toShort()
        }
        return buffer
    }

    // 4. Celestial Harp (Arpeggio sweep C5, E5, G5, B5, D6)
    private fun generateCelestialHarp(sampleRate: Int): ShortArray {
        val durationSec = 2.4
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val notes = listOf(523.25, 659.25, 783.99, 987.77, 1174.66) // C, E, G, B, D

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            notes.forEachIndexed { idx, freq ->
                val start = idx * 0.14
                if (t >= start) {
                    val dt = t - start
                    val decay = exp(-2.2 * dt)
                    val tone = sin(2.0 * PI * freq * dt) + sin(2.0 * PI * (freq * 2.0) * dt) * 0.25
                    sample += tone * decay * 0.35
                }
            }
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000.0).toInt().toShort()
        }
        return buffer
    }

    // 5. Cosmic Chime (Dual harmonic detuned shimmer)
    private fun generateCosmicChime(sampleRate: Int): ShortArray {
        val durationSec = 2.2
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val fA = 587.33 // D5
        val fB = 590.50 // 3.17 Hz acoustic beating

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val decay = exp(-1.5 * t)
            val sub = sin(2.0 * PI * (fA * 0.5) * t) * 0.3
            val main = (sin(2.0 * PI * fA * t) + sin(2.0 * PI * fB * t)) * 0.4
            val overtone = sin(2.0 * PI * (fA * 2.5) * t) * 0.15
            val sample = (main + sub + overtone) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000.0).toInt().toShort()
        }
        return buffer
    }

    // 6. Gentle Kalimba (Warm African thumb piano motif)
    private fun generateGentleKalimba(sampleRate: Int): ShortArray {
        val durationSec = 2.0
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val notes = listOf(
            Pair(0.00, 698.46), // F5
            Pair(0.25, 880.00), // A5
            Pair(0.50, 1046.50), // C6
            Pair(0.75, 1318.51)  // E6
        )

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            for (n in notes) {
                val start = n.first
                val freq = n.second
                if (t >= start) {
                    val dt = t - start
                    val decay = exp(-3.8 * dt)
                    val wood = sin(2.0 * PI * (freq * 2.75) * dt) * exp(-12.0 * dt) * 0.4
                    val tone = sin(2.0 * PI * freq * dt) + sin(2.0 * PI * (freq * 2.0) * dt) * 0.2
                    sample += (tone * 0.5 + wood) * decay
                }
            }
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000.0).toInt().toShort()
        }
        return buffer
    }

    // 7. Nordic Dawn (Warm ambient fifth pad harmonic swell)
    private fun generateNordicDawn(sampleRate: Int): ShortArray {
        val durationSec = 2.2
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val root = 440.0
        val fifth = 660.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Swell envelope: slow rise (0.4s), sustain, gentle fall
            val env = if (t < 0.4) (t / 0.4) else exp(-1.2 * (t - 0.4))
            val tremolo = 1.0 + sin(2.0 * PI * 4.0 * t) * 0.15
            val s1 = sin(2.0 * PI * root * t) * 0.5
            val s2 = sin(2.0 * PI * fifth * t) * 0.35
            val s3 = sin(2.0 * PI * (root * 2.0) * t) * 0.15
            val sample = (s1 + s2 + s3) * env * tremolo * 0.7
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 30000.0).toInt().toShort()
        }
        return buffer
    }

    // 8. Crystal Drops (High resonant glass water droplets)
    private fun generateCrystalDrops(sampleRate: Int): ShortArray {
        val durationSec = 2.0
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val drops = listOf(
            Pair(0.05, 1760.0),
            Pair(0.30, 2093.0),
            Pair(0.60, 2637.0),
            Pair(0.95, 3136.0),
            Pair(1.30, 2637.0)
        )

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            for (d in drops) {
                val start = d.first
                val freq = d.second
                if (t >= start) {
                    val dt = t - start
                    val decay = exp(-5.0 * dt)
                    val tone = sin(2.0 * PI * freq * dt) + sin(2.0 * PI * (freq * 1.5) * dt) * 0.18
                    sample += tone * decay * 0.55
                }
            }
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 30000.0).toInt().toShort()
        }
        return buffer
    }

    // 9. Subtle Radar (Sonar acoustic ping with reverberant tail)
    private fun generateSubtleRadar(sampleRate: Int): ShortArray {
        val durationSec = 1.6
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val freq = 1046.50 // C6

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Main Ping
            val dt1 = t - 0.05
            val ping1 = if (dt1 >= 0) {
                sin(2.0 * PI * freq * dt1) * exp(-2.8 * dt1) * 0.7
            } else 0.0

            // Echo ping
            val dt2 = t - 0.45
            val ping2 = if (dt2 >= 0) {
                sin(2.0 * PI * freq * dt2) * exp(-4.0 * dt2) * 0.28
            } else 0.0

            val sample = ping1 + ping2
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000.0).toInt().toShort()
        }
        return buffer
    }

    // 10. Marimba Sunrise (Fast wooden triplet run)
    private fun generateMarimbaSunrise(sampleRate: Int): ShortArray {
        val durationSec = 1.8
        val numSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(numSamples)
        val notes = listOf(
            Pair(0.00, 523.25), // C5
            Pair(0.14, 783.99), // G5
            Pair(0.28, 880.00), // A5
            Pair(0.42, 1046.50), // C6
            Pair(0.56, 1174.66), // D6
            Pair(0.70, 1567.98)  // G6
        )

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0

            for (n in notes) {
                val start = n.first
                val freq = n.second
                if (t >= start) {
                    val dt = t - start
                    val decay = exp(-4.2 * dt)
                    // Marimba strike punch
                    val strike = sin(2.0 * PI * (freq * 3.8) * dt) * exp(-18.0 * dt) * 0.35
                    val body = sin(2.0 * PI * freq * dt) + sin(2.0 * PI * (freq * 2.0) * dt) * 0.25
                    sample += (body * 0.6 + strike) * decay
                }
            }
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000.0).toInt().toShort()
        }
        return buffer
    }
}
