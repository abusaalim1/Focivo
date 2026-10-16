package com.example.ui.screens

import android.content.Context
import com.example.R
import com.example.data.model.FocusSessionEntity
import com.example.service.SessionBlockRecorder
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Data — everything real, from the user's own device data
// ---------------------------------------------------------------------------

data class ShareBlockedApp(
    val packageName: String,
    val appName: String,
    /** Optional drawable fallback for the icon (used in screenshot tests where
     * PackageManager lookups fail; in prod the real launcher icon is used). */
    val fallbackRes: Int? = null
)

data class ShareScreenData(
    val minutes: Int,
    val distractions: Int,
    /** "Oct 15" (en-US, like the HTML). */
    val dateLabel: String,
    /** Null when unknown — card renders "Well done." instead. */
    val userName: String?,
    val todayTotalMinutes: Int,
    val todaySessions: Int,
    /** 7 entries, oldest..today. */
    val weekMinutes: List<Int>,
    /** 7 single-letter weekday initials, matching weekMinutes. */
    val weekLabels: List<String>,
    /** Apps the shield really intercepted during this session (deduped). */
    val blockedApps: List<ShareBlockedApp>
)

private const val SHARE_PREFS = "focusly_local_data"

private data class LocalSession(val id: Long, val completedAt: Long, val durationSeconds: Int)

private fun readLocalSessions(prefs: android.content.SharedPreferences): List<LocalSession> {
    val out = mutableListOf<LocalSession>()
    try {
        val arr = JSONArray(prefs.getString("focus_sessions", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                LocalSession(
                    id = o.optLong("id", 0L),
                    completedAt = o.optLong("completedAt", 0L),
                    durationSeconds = o.optInt("durationSeconds", 0)
                )
            )
        }
    } catch (_: Exception) { }
    return out
}

private fun startOfDay(millis: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/**
 * Builds the share data from real device state. The just-finished [session]
 * is merged in when it is not yet persisted (so "Today" includes it).
 */
fun loadShareScreenData(context: Context, session: FocusSessionEntity): ShareScreenData {
    val prefs = context.getSharedPreferences(SHARE_PREFS, Context.MODE_PRIVATE)
    val minutes = (session.durationSeconds / 60).coerceAtLeast(0)
    val distractions = session.distractionsCount.coerceAtLeast(0)

    val dateLabel = try {
        SimpleDateFormat("MMM d", Locale.US).format(Date(session.completedAt))
    } catch (_: Exception) { "" }

    // Name: same resolution as ProfileScreen (currentUserName -> email prefix),
    // but "Student"/blank means "no name" for the card headline.
    var userName: String? = null
    try {
        val obj = prefs.getString("user_preferences", null)?.let { JSONObject(it) }
        val raw = obj?.optString("currentUserName", "")?.trim().orEmpty()
        val email = obj?.optString("currentUserEmail", "")?.trim().orEmpty()
        userName = when {
            raw.isNotBlank() && raw != "Deep Worker" && raw != "Student" -> raw
            email.isNotBlank() -> email.substringBefore("@").takeIf { it.isNotBlank() }
            else -> null
        }
    } catch (_: Exception) { }

    val sessions = readLocalSessions(prefs).toMutableList()
    val hasCurrent = sessions.any { it.id == session.id }
    if (!hasCurrent) {
        sessions.add(LocalSession(session.id, session.completedAt, session.durationSeconds))
    }

    val now = System.currentTimeMillis()
    val todayStart = startOfDay(now)
    val todaySessions = sessions.filter { it.completedAt >= todayStart }
    val todayTotalMinutes = todaySessions.sumOf { it.durationSeconds } / 60

    val weekMinutes = mutableListOf<Int>()
    val weekLabels = mutableListOf<String>()
    val dayNames = arrayOf("S", "M", "T", "W", "T", "F", "S") // Calendar.SUNDAY=1
    for (back in 6 downTo 0) {
        val dayStart = todayStart - back * 24L * 3600 * 1000
        val dayEnd = dayStart + 24L * 3600 * 1000
        val mins = sessions.filter { it.completedAt in dayStart until dayEnd }
            .sumOf { it.durationSeconds } / 60
        weekMinutes.add(mins)
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        weekLabels.add(dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1])
    }

    // Apps the shield intercepted inside this session's window.
    val endMs = if (session.endTime > 0) session.endTime else session.completedAt
    val startMs = when {
        session.startTime > 0 -> session.startTime
        else -> endMs - session.durationSeconds * 1000L
    }
    val blockedApps = SessionBlockRecorder.forWindow(context, startMs, endMs)
        .map { ShareBlockedApp(it.packageName, it.appName) }

    return ShareScreenData(
        minutes = minutes,
        distractions = distractions,
        dateLabel = dateLabel,
        userName = userName,
        todayTotalMinutes = todayTotalMinutes,
        todaySessions = todaySessions.size,
        weekMinutes = weekMinutes,
        weekLabels = weekLabels,
        blockedApps = blockedApps
    )
}

/**
 * Curated fallback icons for the most common distracting apps. The REAL
 * launcher icon via PackageManager is always tried first; these official
 * app icons (already in the app for the splash) are used only when that
 * lookup fails (e.g. screenshot tests), so a tile never renders blank.
 */
/** Shared with FocivoShareScreen.kt's shield tiles. */
internal fun knownAppIconRes(packageName: String): Int? = when (packageName) {
    "com.instagram.android" -> R.drawable.ic_instagram
    "com.google.android.youtube", "com.google.android.youtube.tv" -> R.drawable.ic_youtube
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> R.drawable.ic_tiktok
    "com.twitter.android" -> R.drawable.ic_x
    "com.snapchat.android" -> R.drawable.ic_snapchat
    "com.reddit.frontpage" -> R.drawable.ic_reddit
    else -> null
}
