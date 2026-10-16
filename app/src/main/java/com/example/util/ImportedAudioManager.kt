package com.example.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * v1.2.3 (Task 12) — user-imported audio for the Session Soundscape (owner
 * order 2026-10-16).
 *
 * Import uses the Android document picker (SAF ACTION_OPEN_DOCUMENT,
 * audio/*) — no broad storage permission. We take a persistable read URI
 * permission so the track survives restarts, and store (uri, display name)
 * pairs as JSON in SharedPreferences.
 *
 * A URI whose permission was revoked (or file deleted) is reported as
 * unavailable — the UI greys it out and playback skips it instead of
 * crashing.
 */
object ImportedAudioManager {

    private const val TAG = "ImportedAudioManager"
    private const val PREFS_NAME = "focivo_imported_audio_prefs"
    private const val KEY_TRACKS = "key_imported_tracks_json"

    data class ImportedTrack(
        val uriString: String,
        val displayName: String
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTracks(context: Context): List<ImportedTrack> {
        return try {
            val json = getPrefs(context).getString(KEY_TRACKS, "[]") ?: "[]"
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri").ifBlank { return@mapNotNull null }
                val name = o.optString("name").ifBlank { "Imported audio" }
                ImportedTrack(uri, name)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getTracks failed: ${t.message}")
            emptyList()
        }
    }

    private fun saveTracks(context: Context, tracks: List<ImportedTrack>) {
        try {
            val arr = JSONArray()
            tracks.forEach { t ->
                arr.put(JSONObject().put("uri", t.uriString).put("name", t.displayName))
            }
            getPrefs(context).edit().putString(KEY_TRACKS, arr.toString()).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "saveTracks failed: ${t.message}")
        }
    }

    /**
     * Persists [uri] (takes persistable read permission) and stores the
     * entry. Returns false when the permission could not be persisted.
     */
    fun addTrack(context: Context, uri: Uri, displayName: String): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val tracks = getTracks(context).toMutableList()
            tracks.removeAll { it.uriString == uri.toString() }
            val cleanName = displayName
                .substringBeforeLast(".")
                .ifBlank { "Imported audio" }
                .take(48)
            tracks.add(ImportedTrack(uri.toString(), cleanName))
            saveTracks(context, tracks)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "addTrack failed: ${t.message}")
            false
        }
    }

    /** Removes the entry and releases its persisted URI permission. */
    fun removeTrack(context: Context, uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            saveTracks(context, getTracks(context).filterNot { it.uriString == uriString })
        } catch (t: Throwable) {
            Log.w(TAG, "removeTrack failed: ${t.message}")
        }
    }

    /**
     * True when we still hold a persisted read permission AND the document
     * can be opened. False -> UI shows the entry as unavailable.
     */
    fun isTrackAvailable(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
            if (!hasPermission) return false
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /** Best-effort display name for a freshly picked document. */
    fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
