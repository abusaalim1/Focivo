package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class DeviceAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val category: String,
    val isBlocked: Boolean
)

object InstalledAppsManager {

    private val SOCIAL_KEYWORDS = listOf(
        "instagram", "youtube", "tiktok", "musically", "twitter", "facebook",
        "snapchat", "reddit", "whatsapp", "telegram", "discord", "netflix",
        "twitch", "pinterest", "threads", "linkedin", "sharechat", "moj",
        "josh", "mx", "tinder", "bumble", "badoo", "hike"
    )

    private val GAME_KEYWORDS = listOf(
        "game", "pubg", "imobile", "freefire", "candycrush", "roblox",
        "subway", "clash", "supercell", "genshin", "amongus", "ludo",
        "chess", "asphalt", "ea", "minecraft", "cod", "callofduty"
    )

    fun getInstalledApps(context: Context, blockedPackages: Set<String>): List<DeviceAppInfo> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val appMap = mutableMapOf<String, DeviceAppInfo>()

        try {
            // 1. Query all launcher apps
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launcherActivities = pm.queryIntentActivities(launcherIntent, 0)
            for (resolveInfo in launcherActivities) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == selfPackage) continue

                val appName = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (_: Exception) {
                    null
                }

                val category = categorizeApp(pkg, appName)
                val isBlocked = blockedPackages.contains(pkg)

                appMap[pkg] = DeviceAppInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    category = category,
                    isBlocked = isBlocked
                )
            }

            // 2. Also check installed applications to catch any user installed apps without standard launcher
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName
                if (pkg == selfPackage || appMap.containsKey(pkg)) continue

                // Check if it's user installed or popular target
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isSocialOrGame = SOCIAL_KEYWORDS.any { pkg.contains(it, ignoreCase = true) } ||
                    GAME_KEYWORDS.any { pkg.contains(it, ignoreCase = true) }

                if (!isSystem || isSocialOrGame) {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (_: Exception) {
                        null
                    }
                    val category = categorizeApp(pkg, appName)
                    val isBlocked = blockedPackages.contains(pkg)

                    appMap[pkg] = DeviceAppInfo(
                        packageName = pkg,
                        appName = appName,
                        icon = icon,
                        category = category,
                        isBlocked = isBlocked
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback default list if emulator / limited test environment
        if (appMap.isEmpty()) {
            val defaults = listOf(
                "com.instagram.android" to "Instagram & Reels",
                "com.google.android.youtube" to "YouTube & Shorts",
                "com.zhiliaoapp.musically" to "TikTok",
                "com.twitter.android" to "X (Twitter)",
                "com.facebook.katana" to "Facebook",
                "com.snapchat.android" to "Snapchat",
                "com.reddit.frontpage" to "Reddit",
                "com.pubg.imobile" to "BGMI / PUBG",
                "com.dts.freefireth" to "Free Fire"
            )
            for ((pkg, name) in defaults) {
                appMap[pkg] = DeviceAppInfo(
                    packageName = pkg,
                    appName = name,
                    icon = null,
                    category = categorizeApp(pkg, name),
                    isBlocked = blockedPackages.contains(pkg)
                )
            }
        }

        return appMap.values.sortedWith(
            compareByDescending<DeviceAppInfo> { it.isBlocked }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun categorizeApp(pkg: String, name: String): String {
        val lowerPkg = pkg.lowercase()
        val lowerName = name.lowercase()

        if (SOCIAL_KEYWORDS.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
            return "Social & Media"
        }
        if (GAME_KEYWORDS.any { lowerPkg.contains(it) || lowerName.contains(it) }) {
            return "Games"
        }
        return "Other Apps"
    }
}
