package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.util.Log
import com.example.data.SupabaseManager
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
    val isUpdateAvailable: Boolean
)

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val PREFS_NAME = "focivo_app_update_prefs"
    private const val KEY_LAST_CHECK_TIMESTAMP = "last_update_check_time"
    private const val KEY_DISMISSED_VERSION = "dismissed_version_code"

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
            val fourHoursMs = 4 * 60 * 60 * 1000L
            if (System.currentTimeMillis() - lastCheck < fourHoursMs) {
                return@withContext null
            }
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIMESTAMP, System.currentTimeMillis()).apply()

        // 1. Try Supabase app_updates table
        var remoteDto: AppUpdateDto? = fetchUpdateFromSupabase()

        // 2. Fallback: Try public raw update metadata endpoint if Supabase is unavailable
        if (remoteDto == null) {
            remoteDto = fetchUpdateFromHttpFallback()
        }

        if (remoteDto == null) {
            return@withContext if (isManualCheck) {
                AppUpdateInfo(
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    latestVersionCode = currentCode,
                    latestVersionName = currentName,
                    releaseNotes = "You are on the latest version of Focivo.",
                    downloadUrl = "",
                    isForceUpdate = false,
                    isUpdateAvailable = false
                )
            } else null
        }

        val dismissedCode = prefs.getInt(KEY_DISMISSED_VERSION, 0)
        val isUpdateAvailable = remoteDto.versionCode > currentCode

        if (!isManualCheck && !remoteDto.isForceUpdate && remoteDto.versionCode <= dismissedCode) {
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
            val response = SupabaseManager.client
                .from("app_updates")
                .select(columns = Columns.ALL) {
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<AppUpdateDto>()

            response.firstOrNull()
        } catch (e: Exception) {
            Log.d(TAG, "Supabase app_updates query skipped/unavailable: ${e.message}")
            null
        }
    }

    private fun fetchUpdateFromHttpFallback(): AppUpdateDto? {
        return try {
            val supabaseUrl = SupabaseManager.SUPABASE_URL
            val supabaseKey = SupabaseManager.SUPABASE_KEY
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
            Log.d(TAG, "Http fallback app_updates query failed: ${e.message}")
            null
        }
    }

    fun dismissUpdate(context: Context, versionCode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DISMISSED_VERSION, versionCode).apply()
    }

    fun launchDownload(context: Context, downloadUrl: String) {
        try {
            if (downloadUrl.isNotBlank()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch update download URL: ${e.message}")
        }
    }
}
