package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class AppUpdateDto(
    @SerialName("id") val id: String? = null,
    @SerialName("version_code") val versionCode: Int = 1,
    @SerialName("version_name") val versionName: String = "1.0.0",
    @SerialName("release_notes") val releaseNotes: String = "",
    @SerialName("download_url") val downloadUrl: String = "",
    @SerialName("is_force_update") val isForceUpdate: Boolean = false,
    @SerialName("published_at") val publishedAt: String = ""
)

data class AppUpdateInfo(
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isForceUpdate: Boolean,
    val isUpdateAvailable: Boolean,
    // v1.1.3: false when the remote check itself failed (network/DB error).
    // Callers must NEVER report "up to date" when this is false.
    val checkSucceeded: Boolean = true
)

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "focivo_app_update_prefs"
    private const val KEY_LAST_CHECK_TIMESTAMP = "last_update_check_time"
    private const val KEY_DISMISSED_VERSION = "dismissed_version_code"
    // v1.3.2: one notification per version, ever. Persisted last-notified
    // versionCode — a given version triggers AT MOST ONE status-bar
    // notification across every check path (app-open + scheduled). A
    // genuinely newer versionCode notifies once.
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version_code"
    // v1.1.3: dismissal only suppresses the auto dialog for 24h so it re-shows
    // on next launch/day instead of being hidden forever.
    private const val KEY_DISMISSED_AT = "dismissed_version_at"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    private const val DISMISS_SUPPRESS_MS = 24 * 60 * 60 * 1000L

    // v1.1.6: the app's main Supabase project (SupabaseManager) is the app-data backend
    // and does NOT host the app_updates table. Update metadata lives on the website
    // backend project — query it directly with its own public anon key.
    private const val UPDATES_SUPABASE_URL = "https://yuvbgmwkzjuvsukqdnyp.supabase.co"
    private const val UPDATES_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl1dmJnbXdremp1dnN1a3FkbnlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE1NjIwODIsImV4cCI6MjEwNzEzODA4Mn0.8tAjmP_IFFxuiRcW9wkf0GB2aJWsR4H8fFsv50rPeIw"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun getCurrentVersionInfo(context: Context): Pair<Int, String> {
        return try {
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            val name = pInfo.versionName ?: "1.0.0"
            Pair(code, name)
        } catch (_: Exception) {
            Pair(1, "1.0.0")
        }
    }

    suspend fun checkForUpdates(context: Context, isManualCheck: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val (currentCode, currentName) = getCurrentVersionInfo(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!isManualCheck) {
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIMESTAMP, 0L)
            if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                return@withContext null
            }
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIMESTAMP, System.currentTimeMillis()).apply()

        // 1. Primary: direct HTTPS to the updates backend project (proven reliable)
        var remoteDto: AppUpdateDto? = fetchUpdateFromHttpFallback()

        // 2. Fallback: supabase-kt SDK against the same updates project
        if (remoteDto == null) {
            Log.w(TAG, "HTTP app_updates query failed, trying Supabase SDK fallback")
            remoteDto = fetchUpdateFromSupabase()
        }

        if (remoteDto == null) {
            // v1.1.3: the check itself FAILED — never claim "up to date" here.
            Log.w(TAG, "Update check failed: both primary and fallback queries returned nothing")
            return@withContext if (isManualCheck) {
                AppUpdateInfo(
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    latestVersionCode = currentCode,
                    latestVersionName = currentName,
                    releaseNotes = "",
                    downloadUrl = "",
                    isForceUpdate = false,
                    isUpdateAvailable = false,
                    checkSucceeded = false
                )
            } else null
        }

        val dismissedCode = prefs.getInt(KEY_DISMISSED_VERSION, 0)
        val dismissedAt = prefs.getLong(KEY_DISMISSED_AT, 0L)
        // v1.2: a newer versionCode with a blank download URL is not actionable
        // (the dialog's Update button would silently do nothing) — treat it as
        // no update available instead of showing a dead dialog.
        val isUpdateAvailable = remoteDto.versionCode > currentCode &&
            remoteDto.downloadUrl.isNotBlank()
        if (!isUpdateAvailable && remoteDto.versionCode > currentCode) {
            Log.w(TAG, "Update ${remoteDto.versionCode} has a blank download_url; suppressing update UI")
        }

        // Dismissal suppresses the auto dialog only for 24h; force updates
        // always show; manual checks always report truthfully.
        if (!isManualCheck && !remoteDto.isForceUpdate &&
            remoteDto.versionCode <= dismissedCode &&
            System.currentTimeMillis() - dismissedAt < DISMISS_SUPPRESS_MS
        ) {
            return@withContext null
        }

        AppUpdateInfo(
            currentVersionCode = currentCode,
            currentVersionName = currentName,
            latestVersionCode = remoteDto.versionCode,
            latestVersionName = remoteDto.versionName,
            releaseNotes = remoteDto.releaseNotes.ifBlank { "Performance improvements and bug fixes for study sessions and app blocking." },
            downloadUrl = remoteDto.downloadUrl,
            isForceUpdate = remoteDto.isForceUpdate,
            isUpdateAvailable = isUpdateAvailable
        )
    }

    private suspend fun fetchUpdateFromSupabase(): AppUpdateDto? {
        return try {
            val response = updatesClient
                .from("app_updates")
                .select(columns = Columns.ALL) {
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<AppUpdateDto>()

            response.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Supabase SDK app_updates query failed", e)
            null
        }
    }

    // v1.1.6: dedicated client for the updates backend project (separate from the
    // app-data project in SupabaseManager, which has no app_updates table).
    private val updatesClient by lazy {
        io.github.jan.supabase.createSupabaseClient(
            supabaseUrl = UPDATES_SUPABASE_URL,
            supabaseKey = UPDATES_SUPABASE_KEY
        ) {
            install(io.github.jan.supabase.postgrest.Postgrest)
        }
    }

    private fun fetchUpdateFromHttpFallback(): AppUpdateDto? {
        return try {
            val supabaseUrl = UPDATES_SUPABASE_URL
            val supabaseKey = UPDATES_SUPABASE_KEY
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/app_updates?select=*&order=version_code.desc&limit=1")
                .addHeader("apikey", supabaseKey)
                .addHeader("Authorization", "Bearer $supabaseKey")
                .get()
                .build()

            // use{} closes the response (and recycles the pooled connection)
            // on every path, including non-2xx responses.
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank() && body.startsWith("[")) {
                        val array = org.json.JSONArray(body)
                        if (array.length() > 0) {
                            val obj = array.getJSONObject(0)
                            return AppUpdateDto(
                                id = obj.optString("id"),
                                versionCode = obj.optInt("version_code", 1),
                                versionName = obj.optString("version_name", "1.0.0"),
                                releaseNotes = obj.optString("release_notes", ""),
                                downloadUrl = obj.optString("download_url", ""),
                                isForceUpdate = obj.optBoolean("is_force_update", false),
                                publishedAt = obj.optString("published_at", "")
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Http fallback app_updates query failed", e)
            null
        }
    }

    fun dismissUpdate(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_DISMISSED_VERSION, versionCode)
            .putLong(KEY_DISMISSED_AT, System.currentTimeMillis())
            .apply()
    }

    // v1.4.2: Home update overlay dismissal is stored per versionCode and only
    // reappears when a NEWER versionCode arrives (no 24h re-show).
    private const val KEY_HOME_OVERLAY_DISMISSED_VERSION = "home_overlay_dismissed_version_code"

    fun isUpdateOverlayDismissed(context: Context, versionCode: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_HOME_OVERLAY_DISMISSED_VERSION, 0) >= versionCode
    }

    fun dismissUpdateOverlay(context: Context, versionCode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HOME_OVERLAY_DISMISSED_VERSION, versionCode)
            .apply()
    }

    /**
     * v1.3.2: notification gate. Returns true the FIRST time a version asks to
     * notify and persists it, so the same version can never notify twice —
     * no matter how many check paths fire (app open, daily alarm). A newer
     * versionCode returns true again (one notification for the new version).
     */
    fun claimNotifyForVersion(context: Context, versionCode: Int): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_LAST_NOTIFIED_VERSION, 0) >= versionCode) return false
            prefs.edit().putInt(KEY_LAST_NOTIFIED_VERSION, versionCode).apply()
            true
        } catch (_: Exception) {
            false // fail closed: never spam when storage is unavailable
        }
    }

    /**
     * v1.4.2: fetches the backend announcement (app_config key "announcement").
     * Returns the announcement text, or null when disabled/blank/unreachable.
     * Any failure is silent — callers must never block the app on this.
     */
    suspend fun fetchAnnouncement(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$UPDATES_SUPABASE_URL/rest/v1/app_config?select=value&key=eq.announcement")
                .addHeader("apikey", UPDATES_SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $UPDATES_SUPABASE_KEY")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank() && body.startsWith("[")) {
                        val array = JSONObject("{\"rows\":$body}").getJSONArray("rows")
                        if (array.length() > 0) {
                            val value = array.getJSONObject(0).optJSONObject("value")
                                ?: return@withContext null
                            if (!value.optBoolean("enabled", true)) return@withContext null
                            val text = value.optString("text", "").trim()
                            return@withContext text.ifBlank { null }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Announcement fetch failed", e)
            null
        }
    }

    /**
     * v1.1: fetches the donation URL (app_config key "donate_url").
     * Returns the URL, or null when unset/blank/unreachable.
     * Any failure is silent — callers must never block the app on this.
     */
    suspend fun fetchDonateUrl(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$UPDATES_SUPABASE_URL/rest/v1/app_config?select=value&key=eq.donate_url")
                .addHeader("apikey", UPDATES_SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $UPDATES_SUPABASE_KEY")
                .get()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank() && body.startsWith("[")) {
                        val array = JSONObject("{\"rows\":$body}").getJSONArray("rows")
                        if (array.length() > 0) {
                            val value = array.getJSONObject(0).optJSONObject("value")
                                ?: return@withContext null
                            // Support both {"url": "..."} and plain string values
                            val url = value.optString("url", "").trim()
                                .ifBlank { value.optString("text", "").trim() }
                            return@withContext url.ifBlank { null }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Donate URL fetch failed", e)
            null
        }
    }

    // ------------------------------------------------------------------
    // v1.2: REAL in-app update. Downloads the APK with OkHttp into
    // context.cacheDir and launches the system package installer through a
    // FileProvider URI (provider "${applicationId}.fileprovider" is declared
    // in the manifest; res/xml/file_paths.xml exposes the cache dir).
    // The browser is now only a last-resort fallback. Safe to call from the
    // main thread — all work happens on an IO coroutine.
    // ------------------------------------------------------------------
    sealed interface UpdateDownloadState {
        data object Idle : UpdateDownloadState
        data class Downloading(val percent: Int) : UpdateDownloadState
        data object Installing : UpdateDownloadState
        data class Failed(val reason: String) : UpdateDownloadState
    }

    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloadState =
        MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)

    /** Progress of the current in-app update download (Idle when none). */
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    fun resetDownloadState() {
        _downloadState.value = UpdateDownloadState.Idle
    }

    /**
     * v1.2: replaces the old browser-only behavior. Existing call sites
     * (AppUpdateDialog.onDownloadClick, HomeUpdateOverlay.onUpdateNow via
     * FocuslyViewModel.downloadAndInstallUpdate) keep working unchanged —
     * this now downloads and installs in-app instead of opening Chrome.
     */
    fun launchDownload(context: Context, downloadUrl: String) {
        if (downloadUrl.isBlank()) return
        val appContext = context.applicationContext

        // Android 8+: installing an APK from the app requires the
        // "install unknown apps" grant for our own package. Route the user to
        // that settings page first; they tap Update again afterwards.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            Log.i(TAG, "Install-unknown-apps not granted; routing to settings")
            try {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}")
                ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                appContext.startActivity(settingsIntent)
                _downloadState.value = UpdateDownloadState.Failed(
                    "Please allow \"Install unknown apps\" for Focivo, then tap Update again."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open unknown-app-sources settings", e)
                openInBrowser(appContext, downloadUrl)
            }
            return
        }

        _downloadState.value = UpdateDownloadState.Downloading(0)
        updateScope.launch {
            try {
                val apkFile = downloadApk(appContext, downloadUrl) { pct ->
                    _downloadState.value = UpdateDownloadState.Downloading(pct)
                }
                _downloadState.value = UpdateDownloadState.Installing
                installApk(appContext, apkFile)
                // The system installer takes over from here; reset so a later
                // dialog never shows a stale state if the user backs out.
                _downloadState.value = UpdateDownloadState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "In-app update failed; falling back to browser", e)
                _downloadState.value = UpdateDownloadState.Failed(
                    e.message ?: "Download failed"
                )
                openInBrowser(appContext, downloadUrl)
            }
        }
    }

    @Throws(Exception::class)
    private fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // One file per URL so a newer build never collides with a stale one.
        val safeHash = downloadUrl.hashCode().toString().replace("-", "n")
        val outFile = File(dir, "focivo-update-$safeHash.apk")
        if (outFile.exists()) outFile.delete()

        val request = Request.Builder().url(downloadUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code} while downloading update")
            }
            val body = response.body ?: throw java.io.IOException("Empty update download")
            val total = body.contentLength()
            var written = 0L
            var lastPct = -1
            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val pct = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (outFile.length() == 0L) throw java.io.IOException("Downloaded update is empty")
        }
        onProgress(100)
        Log.i(TAG, "Update APK downloaded: ${outFile.absolutePath} (${outFile.length()} bytes)")
        return outFile
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }

    /** Last-resort fallback: open the download URL in the browser. */
    private fun openInBrowser(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch update download URL: ${e.message}")
        }
    }
}
