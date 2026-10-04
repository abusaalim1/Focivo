package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val iconBitmap: ImageBitmap? = null,
    val category: String,
    val isBlocked: Boolean
)

object InstalledAppsManager {

    @Volatile
    private var inMemoryCachedApps: List<DeviceAppInfo>? = null

    fun getCachedApps(): List<DeviceAppInfo>? = inMemoryCachedApps

    fun preloadApps(context: Context, blockedPackages: Set<String> = emptySet()) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                getInstalledApps(context.applicationContext, blockedPackages)
            } catch (_: Exception) {}
        }
    }

    suspend fun getInstalledAppsAsync(context: Context, blockedPackages: Set<String>): List<DeviceAppInfo> {
        return withContext(Dispatchers.IO) {
            getInstalledApps(context, blockedPackages)
        }
    }

    const val CATEGORY_SOCIAL = "Social Media"
    const val CATEGORY_GAMES = "Games"
    const val CATEGORY_ENTERTAINMENT = "Entertainment/Video"
    const val CATEGORY_MESSAGING = "Messaging"
    const val CATEGORY_OTHER = "Other"

    val ALL_CATEGORIES = listOf(
        CATEGORY_SOCIAL,
        CATEGORY_GAMES,
        CATEGORY_ENTERTAINMENT,
        CATEGORY_MESSAGING,
        CATEGORY_OTHER
    )

    private val SOCIAL_KEYWORDS = listOf(
        "instagram", "facebook", "twitter", "snapchat", "reddit", "tiktok", "musically",
        "pinterest", "threads", "linkedin", "sharechat", "moj", "josh", "tinder", "bumble", "badoo",
        "clubhouse", "tumblr", "weibo", "mastodon", "bluesky"
    )

    private val MESSAGING_KEYWORDS = listOf(
        "whatsapp", "telegram", "discord", "signal", "wechat", "viber", "line",
        "kik", "skype", "imo", "slack", "hike", "chat"
    )

    private val ENTERTAINMENT_KEYWORDS = listOf(
        "youtube", "netflix", "primevideo", "spotify", "twitch", "disney", "hotstar",
        "hulu", "crunchyroll", "hbo", "max", "zee5", "sonyliv", "voot", "mxplayer",
        "vlc", "audio", "music", "podcast", "video", "dailymotion", "soundcloud", "deezer",
        "apple.music", "jiosaavn", "gaana"
    )

    private val GAME_KEYWORDS = listOf(
        "game", "pubg", "imobile", "freefire", "candycrush", "roblox",
        "subway", "clash", "supercell", "genshin", "amongus", "ludo",
        "chess", "asphalt", "ea", "minecraft", "cod", "callofduty",
        "pokemon", "angrybirds", "brawlstars", "fortnite", "epicgames", "riotgames", "steam"
    )

    fun getInstalledApps(context: Context, blockedPackages: Set<String>): List<DeviceAppInfo> {
        inMemoryCachedApps?.let { cached ->
            if (cached.isNotEmpty()) {
                return cached.map { it.copy(isBlocked = blockedPackages.contains(it.packageName)) }
            }
        }

        val pm = context.packageManager
        val selfPackage = context.packageName
        val appMap = mutableMapOf<String, DeviceAppInfo>()

        try {
            // 1. Query all launcher apps (fastest and most relevant for distraction blocking)
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherActivities = pm.queryIntentActivities(launcherIntent, 0)
            for (resolveInfo in launcherActivities) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == selfPackage) continue

                // Exclude permanently protected essential apps
                if (EssentialAppsGuard.isEssentialApp(context, pkg)) continue

                val appName = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }
                val iconBitmap = try {
                    icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }

                val appInfo = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (_: Exception) {
                    null
                }

                val category = categorizeApp(pkg, appName, appInfo)
                val isBlocked = blockedPackages.contains(pkg)

                appMap[pkg] = DeviceAppInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    iconBitmap = iconBitmap,
                    category = category,
                    isBlocked = isBlocked
                )
            }

            // 2. Also check installed applications to catch user-installed apps without primary launcher tag
            val installedApps = pm.getInstalledApplications(0)
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName
                if (pkg == selfPackage || appMap.containsKey(pkg)) continue

                // Exclude permanently protected essential apps
                if (EssentialAppsGuard.isEssentialApp(context, pkg)) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isTarget = isDistractingApp(pkg, appInfo)

                if (!isSystem || isTarget) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (_: Exception) {
                        null
                    }
                    val iconBitmap = try {
                        icon?.toBitmap(width = 72, height = 72)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                    val category = categorizeApp(pkg, appName, appInfo)
                    val isBlocked = blockedPackages.contains(pkg)

                    appMap[pkg] = DeviceAppInfo(
                        packageName = pkg,
                        appName = appName,
                        icon = icon,
                        iconBitmap = iconBitmap,
                        category = category,
                        isBlocked = isBlocked
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback default list if emulator / limited test environment
        if (appMap.isEmpty()) {
            val defaults = listOf(
                "com.instagram.android" to ("Instagram" to CATEGORY_SOCIAL),
                "com.facebook.katana" to ("Facebook" to CATEGORY_SOCIAL),
                "com.twitter.android" to ("X (Twitter)" to CATEGORY_SOCIAL),
                "com.snapchat.android" to ("Snapchat" to CATEGORY_SOCIAL),
                "com.reddit.frontpage" to ("Reddit" to CATEGORY_SOCIAL),
                "com.zhiliaoapp.musically" to ("TikTok" to CATEGORY_SOCIAL),
                "com.whatsapp" to ("WhatsApp" to CATEGORY_MESSAGING),
                "org.telegram.messenger" to ("Telegram" to CATEGORY_MESSAGING),
                "com.discord" to ("Discord" to CATEGORY_MESSAGING),
                "com.google.android.youtube" to ("YouTube" to CATEGORY_ENTERTAINMENT),
                "com.netflix.mediaclient" to ("Netflix" to CATEGORY_ENTERTAINMENT),
                "com.spotify.music" to ("Spotify" to CATEGORY_ENTERTAINMENT),
                "com.dts.freefireth" to ("Free Fire" to CATEGORY_GAMES),
                "com.pubg.imobile" to ("BGMI / PUBG" to CATEGORY_GAMES),
                "com.roblox.client" to ("Roblox" to CATEGORY_GAMES)
            )
            for ((pkg, pair) in defaults) {
                if (!EssentialAppsGuard.isEssentialApp(context, pkg)) {
                    appMap[pkg] = DeviceAppInfo(
                        packageName = pkg,
                        appName = pair.first,
                        icon = null,
                        category = pair.second,
                        isBlocked = blockedPackages.contains(pkg)
                    )
                }
            }
        }

        val sortedList = appMap.values.sortedWith(
            compareByDescending<DeviceAppInfo> { it.isBlocked }
                .thenBy { it.appName.lowercase() }
        )
        inMemoryCachedApps = sortedList
        return sortedList
    }

    private fun isDistractingApp(pkg: String, appInfo: ApplicationInfo): Boolean {
        val lowerPkg = pkg.lowercase()
        return SOCIAL_KEYWORDS.any { lowerPkg.contains(it) } ||
                MESSAGING_KEYWORDS.any { lowerPkg.contains(it) } ||
                ENTERTAINMENT_KEYWORDS.any { lowerPkg.contains(it) } ||
                GAME_KEYWORDS.any { lowerPkg.contains(it) } ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME)
    }

    fun categorizeApp(pkg: String, name: String, appInfo: ApplicationInfo? = null): String {
        val lowerPkg = pkg.lowercase()
        val lowerName = name.lowercase()

        // 1. Check metadata category from Android OS (O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> return CATEGORY_GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> return CATEGORY_SOCIAL
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> return CATEGORY_ENTERTAINMENT
            }
        }

        // 2. Explicit Keyword / Package Categorization
        if (MESSAGING_KEYWORDS.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
            return CATEGORY_MESSAGING
        }
        if (SOCIAL_KEYWORDS.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
            return CATEGORY_SOCIAL
        }
        if (ENTERTAINMENT_KEYWORDS.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
            return CATEGORY_ENTERTAINMENT
        }
        if (GAME_KEYWORDS.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
            return CATEGORY_GAMES
        }

        return CATEGORY_OTHER
    }
}
