package com.example

import android.app.Application
import android.util.Log
import com.example.data.FirebaseConfigManager
import com.example.data.SupabaseManager
import com.example.service.ScheduledBlockScheduler
import com.example.util.AiStudyGuardManager
import com.example.util.AppDailyLimitsManager

class FocuslyApplication : Application() {

    companion object {
        const val CRASH_LOG_FILE = "crash_log.txt"
    }

    override fun onCreate() {
        super.onCreate()
        installCrashReporter()

        // v1.4.3: initialize the theme manager FIRST, before anything reads it.
        // (Home greeting/dates read selectedTheme at frame one; late init made
        // text colors render with the wrong/default theme on cold start.)
        try {
            com.example.util.TimerThemeManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize TimerThemeManager: ${e.message}", e)
        }

        // v1.1: timer faces + mascot wardrobe — same init pattern.
        try {
            com.example.util.TimerFaceManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize TimerFaceManager: ${e.message}", e)
        }
        // v1.2.3: pomodoro design picker — same init pattern.
        try {
            com.example.util.PomodoroStyleManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize PomodoroStyleManager: ${e.message}", e)
        }
        try {
            com.example.util.MascotWardrobe.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize MascotWardrobe: ${e.message}", e)
        }

        try {
            SupabaseManager.initialize(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize SupabaseManager: ${e.message}", e)
        }

        try {
            FirebaseConfigManager.initializeFirebase(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize FirebaseConfig: ${e.message}", e)
        }

        try {
            AiStudyGuardManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize AiStudyGuardManager: ${e.message}", e)
        }

        try {
            AppDailyLimitsManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize AppDailyLimitsManager: ${e.message}", e)
        }

        try {
            com.example.util.StrictModeManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize StrictModeManager: ${e.message}", e)
        }

        try {
            com.example.util.ShortsLockManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize ShortsLockManager: ${e.message}", e)
        }

        try {
            com.example.util.YouTubeStudyGuardManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize YouTubeStudyGuardManager: ${e.message}", e)
        }

        try {
            com.example.util.StudyChannelsManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize StudyChannelsManager: ${e.message}", e)
        }

        try {
            com.example.util.BlockedChannelsManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize BlockedChannelsManager: ${e.message}", e)
        }

        try {
            com.example.util.DeepFocusManager.init(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize DeepFocusManager: ${e.message}", e)
        }

        try {
            ScheduledBlockScheduler.evaluateAndReschedule(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize ScheduledBlockScheduler: ${e.message}", e)
        }

        // v1.4.3: refresh the study-reminder + daily 19:00 streak-saver alarms
        // on every process start (evaluateAndReschedule covers this too; belt
        // and braces so the alarms exist even if evaluate throws early).
        // v1.1: + Sunday recap, update check, comeback — all survival alarms
        // re-armed idempotently here (and on boot).
        try {
            com.example.service.FocivoNotifications.scheduleAllSurvivalAlarms(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to initialize reminder alarms: ${e.message}", e)
        }

        // v1.1: stamp every app open (drives the 3-day comeback check; also
        // resets its once-per-absence dedupe flag).
        try {
            com.example.service.FocivoNotifications.stampAppOpen(this)
        } catch (e: Throwable) {
            Log.e("FocuslyApplication", "Failed to stamp app open: ${e.message}", e)
        }
    }

    /**
     * v1.1.2: remote-debugging aid. Any uncaught exception (which would kill the
     * app) is first written to a private file with device + version info. On the
     * NEXT launch the app shows the log in a dialog with a Copy button so the
     * owner can paste the exact crash. The handler itself can never throw, and
     * the previous system handler is always chained so the crash still behaves
     * normally for the OS.
     */
    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = java.io.StringWriter()
                try {
                    throwable.printStackTrace(java.io.PrintWriter(sw))
                } catch (_: Throwable) { }
                val stack = sw.toString().ifBlank {
                    try { android.util.Log.getStackTraceString(throwable) } catch (_: Throwable) { "n/a" }
                }
                val report = buildString {
                    appendLine("time=" + try {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date())
                    } catch (_: Throwable) { "n/a" })
                    appendLine("app=Focivo " + try {
                        "${com.example.BuildConfig.VERSION_NAME} (${com.example.BuildConfig.VERSION_CODE})"
                    } catch (_: Throwable) { "n/a" })
                    appendLine("device=" + try {
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} sdk=${android.os.Build.VERSION.SDK_INT}"
                    } catch (_: Throwable) { "n/a" })
                    appendLine("thread=" + try { thread.name } catch (_: Throwable) { "n/a" })
                    appendLine("---- stack ----")
                    appendLine(stack)
                }
                // Keep only the latest crash (overwrite).
                java.io.File(filesDir, CRASH_LOG_FILE).writeText(report.take(20000))
            } catch (_: Throwable) {
                // The reporter itself must never throw.
            }
            try {
                previous?.uncaughtException(thread, throwable)
            } catch (_: Throwable) { }
            if (previous == null) {
                try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) { }
            }
        }
    }
}
