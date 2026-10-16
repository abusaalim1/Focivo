package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.util.ImportedAudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.sin

class AmbientAudioEngine {
    private var audioTrack: AudioTrack? = null
    private var generatorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var isPlaying = false
    private var currentSound = "Silent"

    /** Built-in file tracks (v1.2.3 Task 12 — DSP-generated OGG loops). */
    private val fileTracks: Map<String, Int> = mapOf(
        "Night Breeze" to R.raw.soundscape_night_breeze,
        "Ambient Pad" to R.raw.soundscape_ambient_pad,
        "Gentle Stream" to R.raw.soundscape_gentle_stream,
        "Distant Thunder" to R.raw.soundscape_distant_thunder
    )

    /** All built-in sound names for pickers (procedural + file tracks). */
    val builtInSounds: List<String> =
        listOf("Silent", "Rain", "White noise", "Cafe", "Forest", "Ocean") + fileTracks.keys

    private var filePlayer: MediaPlayer? = null
    private val filePlayerLock = Any()

    /**
     * Unified soundscape entry (v1.2.3 Task 12). Routes by id:
     *  - "Silent" -> stop
     *  - procedural names -> the code-generated AudioTrack path
     *  - built-in file tracks -> looping MediaPlayer from res/raw
     *  - content:// URIs (user imports) -> looping MediaPlayer from the URI;
     *    revoked/broken URIs stop quietly instead of crashing.
     *
     * Imported audio follows the same session rules as built-ins: it plays
     * under the timer and [stop] ends it with the session.
     */
    fun playSound(context: Context, soundId: String) {
        currentSound = soundId
        stop()

        if (soundId == "Silent") {
            isPlaying = false
            return
        }

        val fileResId = fileTracks[soundId]
        if (fileResId != null) {
            playRawLoop(context.applicationContext, fileResId)
            return
        }

        if (soundId.startsWith("content://")) {
            if (!ImportedAudioManager.isTrackAvailable(context.applicationContext, soundId)) {
                Log.w("AmbientAudioEngine", "Imported track unavailable, skipping: $soundId")
                isPlaying = false
                return
            }
            playUriLoop(context.applicationContext, Uri.parse(soundId))
            return
        }

        // Procedural path (existing behavior).
        play(soundId)
    }

    private fun playRawLoop(appContext: Context, resId: Int) {
        try {
            val afd = appContext.resources.openRawResourceFd(resId)
            startLoopPlayer(appContext) { mp ->
                try {
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } finally {
                    try { afd.close() } catch (_: Exception) {}
                }
            }
        } catch (t: Throwable) {
            Log.w("AmbientAudioEngine", "playRawLoop failed: ${t.message}")
        }
    }

    private fun playUriLoop(appContext: Context, uri: Uri) {
        try {
            startLoopPlayer(appContext) { mp -> mp.setDataSource(appContext, uri) }
        } catch (t: Throwable) {
            Log.w("AmbientAudioEngine", "playUriLoop failed: ${t.message}")
        }
    }

    private fun startLoopPlayer(appContext: Context, setSource: (MediaPlayer) -> Unit) {
        Thread {
            synchronized(filePlayerLock) {
                stopFilePlayerLocked()
                try {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    val player = MediaPlayer().apply {
                        setAudioAttributes(attrs)
                        setSource(this)
                        isLooping = true
                        setVolume(0.8f, 0.8f)
                        setOnCompletionListener { /* looping — not expected */ }
                        setOnErrorListener { mp, _, _ ->
                            try { mp.release() } catch (_: Exception) {}
                            synchronized(filePlayerLock) {
                                if (filePlayer === mp) filePlayer = null
                            }
                            true
                        }
                        prepare()
                        start()
                    }
                    filePlayer = player
                    isPlaying = true
                } catch (t: Throwable) {
                    Log.w("AmbientAudioEngine", "startLoopPlayer failed: ${t.message}")
                    isPlaying = false
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun stopFilePlayerLocked() {
        try {
            filePlayer?.let {
                try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        filePlayer = null
    }

    fun play(sound: String) {
        currentSound = sound
        stop()

        if (sound == "Silent") {
            isPlaying = false
            return
        }

        isPlaying = true
        generatorJob = scope.launch {
            try {
                val sampleRate = 22050
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                val buffer = ShortArray(1024)
                val random = Random()
                var brownianLast = 0.0
                var phase = 0.0

                while (isActive && isPlaying) {
                    for (i in buffer.indices) {
                        val sample: Double = when (currentSound) {
                            "White noise" -> {
                                (random.nextDouble() * 2.0 - 1.0) * 0.15
                            }
                            "Rain" -> {
                                val white = random.nextDouble() * 2.0 - 1.0
                                brownianLast = (brownianLast + (0.05 * white)) / 1.05
                                val drop = if (random.nextDouble() > 0.985) (random.nextDouble() * 0.4) else 0.0
                                (brownianLast * 0.25 + drop).coerceIn(-1.0, 1.0) * 0.3
                            }
                            "Ocean" -> {
                                phase += 0.0003
                                val waveMod = (sin(phase) + 1.0) / 2.0 // 0 to 1 swell
                                val white = random.nextDouble() * 2.0 - 1.0
                                brownianLast = (brownianLast + (0.03 * white)) / 1.03
                                brownianLast * (0.15 + 0.35 * waveMod)
                            }
                            "Forest", "Cafe" -> {
                                val white = random.nextDouble() * 2.0 - 1.0
                                brownianLast = (brownianLast + (0.025 * white)) / 1.025
                                brownianLast * 0.22
                            }
                            else -> 0.0
                        }

                        val pcmShort = (sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                        buffer[i] = pcmShort
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.w("AmbientAudioEngine", "Audio track error: ${e.message}")
            }
        }
    }

    fun playCompletionChime() {
        scope.launch {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * 1.5).toInt()
                val buffer = ShortArray(numSamples)
                val freq = 528.0 // Harmonic peaceful chime frequency (528 Hz)
                var phase = 0.0

                for (i in 0 until numSamples) {
                    val progress = i.toDouble() / numSamples
                    val envelope = (1.0 - progress) * (1.0 - progress) // decay
                    val sample = sin(phase) * envelope * 0.4
                    buffer[i] = (sample * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                    phase += 2.0 * Math.PI * freq / sampleRate
                }

                val chimeTrack = AudioTrack.Builder()
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
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                chimeTrack.write(buffer, 0, buffer.size)
                chimeTrack.play()
            } catch (e: Exception) {
                Log.w("AmbientAudioEngine", "Chime error: ${e.message}")
            }
        }
    }

    fun playShieldWarningTone() {
        // App intercept noise muted per user request
    }

    fun playShieldUnlockChime() {
        playCompletionChime()
    }

    fun stop() {
        isPlaying = false
        generatorJob?.cancel()
        generatorJob = null
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (_: Exception) {}
        audioTrack = null
        synchronized(filePlayerLock) { stopFilePlayerLocked() }
    }
}
