package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log

/**
 * v1.2.2 — tasteful sound effects at exactly two moments (owner spec: never
 * fill the app with effects):
 *  - [playComplete]: pleasant chime when a focus session completes.
 *  - [playTimerEnd]: clear-but-not-harsh alarm when the session finishes
 *    while the app is backgrounded (wired into FocusTimerService's
 *    completion path so it plays even with the UI dead).
 *
 * Audio can never crash the app: every entry point is wrapped in try/catch,
 * playback runs on a daemon thread, and the player always releases itself.
 * Volumes are modest and each sound uses its natural stream (notification /
 * alarm) so system volume and Do-Not-Disturb behavior are respected.
 */
object SoundFx {
    private const val TAG = "SoundFx"

    /** Pleasant session-complete chime on the notification stream. */
    fun playComplete(context: Context) {
        playOnce(
            appContext = context.applicationContext,
            resId = com.example.R.raw.session_complete_chime,
            usage = AudioAttributes.USAGE_NOTIFICATION,
            volume = 0.7f
        )
    }

    /**
     * Timer-end alarm on the alarm stream (modest volume). Plays the user's
     * selected alarm tone (v1.2.3 Task 11); the old Kenney timer_end_alarm
     * is retired.
     */
    fun playTimerEnd(context: Context) {
        val resId = try {
            AlarmToneManager.getSelectedTone(context.applicationContext).resId
        } catch (_: Exception) {
            com.example.R.raw.alarm_classic_clock
        }
        playOnce(
            appContext = context.applicationContext,
            resId = resId,
            usage = AudioAttributes.USAGE_ALARM,
            volume = 0.6f
        )
    }

    /** Preview player for the alarm-tone picker — only one preview at a time. */
    private var previewPlayer: MediaPlayer? = null
    private val previewLock = Any()

    /**
     * Plays [resId] once for picker preview. Any in-flight preview is
     * stopped first. Call [stopAlarmPreview] when the picker closes.
     */
    fun playAlarmPreview(context: Context, resId: Int) {
        synchronized(previewLock) {
            try {
                previewPlayer?.let {
                    try { it.stop() } catch (_: Exception) {}
                    try { it.release() } catch (_: Exception) {}
                }
                previewPlayer = null
            } catch (_: Exception) {}
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val afd = context.applicationContext.resources.openRawResourceFd(resId)
                val player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    try {
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    } finally {
                        try { afd.close() } catch (_: Exception) {}
                    }
                    setVolume(0.6f, 0.6f)
                    setOnCompletionListener { mp ->
                        synchronized(previewLock) {
                            if (previewPlayer === mp) previewPlayer = null
                        }
                        try { mp.release() } catch (_: Exception) {}
                    }
                    setOnErrorListener { mp, _, _ ->
                        synchronized(previewLock) {
                            if (previewPlayer === mp) previewPlayer = null
                        }
                        try { mp.release() } catch (_: Exception) {}
                        true
                    }
                    prepare()
                    start()
                }
                previewPlayer = player
            } catch (t: Throwable) {
                Log.w(TAG, "playAlarmPreview failed: ${t.message}")
            }
        }
    }

    /** Stops any in-flight picker preview. */
    fun stopAlarmPreview() {
        synchronized(previewLock) {
            try {
                previewPlayer?.let {
                    try { it.stop() } catch (_: Exception) {}
                    try { it.release() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            previewPlayer = null
        }
    }

    private fun playOnce(appContext: Context, resId: Int, usage: Int, volume: Float) {
        try {
            Thread {
                var player: MediaPlayer? = null
                try {
                    val attrs = AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    player = MediaPlayer().apply {
                        setAudioAttributes(attrs)
                        val afd = appContext.resources.openRawResourceFd(resId)
                        try {
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        } finally {
                            try {
                                afd.close()
                            } catch (_: Exception) {
                            }
                        }
                        setVolume(volume, volume)
                        setOnCompletionListener { mp ->
                            try {
                                mp.release()
                            } catch (_: Exception) {
                            }
                        }
                        setOnErrorListener { mp, _, _ ->
                            try {
                                mp.release()
                            } catch (_: Exception) {
                            }
                            true
                        }
                        prepare()
                        start()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "playOnce failed: ${t.message}")
                    try {
                        player?.release()
                    } catch (_: Exception) {
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "playOnce dispatch failed: ${t.message}")
        }
    }
}
