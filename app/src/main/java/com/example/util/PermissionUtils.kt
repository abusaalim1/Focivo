package com.example.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtils {

    /**
     * v1.2.1 — the 7 focus permissions: the exact set onboarding requests.
     * Used by the Start-Study permission gate (FocuslyViewModel) and the
     * onboarding permission steps — one shared notion of "required for focus".
     */
    data class FocusPermission(
        val id: String,
        val title: String,
        val why: String,
        val sensitive: Boolean = false
    )

    val FOCUS_PERMISSIONS: List<FocusPermission> = listOf(
        FocusPermission(
            id = "notifications",
            title = "Notifications",
            why = "Session chimes, break reminders and the guard status need to reach you."
        ),
        FocusPermission(
            id = "usage",
            title = "Usage Access",
            why = "Detects which app is in the foreground so blocked apps can be intercepted."
        ),
        FocusPermission(
            id = "overlay",
            title = "Display Over Other Apps",
            why = "Draws the block screen above blocked apps — including floating windows."
        ),
        FocusPermission(
            id = "battery",
            title = "Battery Exemption",
            why = "Keeps the focus timer and guard alive when the screen is off."
        ),
        FocusPermission(
            id = "exact_alarm",
            title = "Exact Alarms",
            why = "Fires study reminders and session timers at the exact minute."
        ),
        FocusPermission(
            id = "accessibility",
            title = "Accessibility Service",
            why = "Sees app windows (fullscreen, split-screen and floating) to block distractions instantly.",
            sensitive = true
        ),
        FocusPermission(
            id = "device_admin",
            title = "Device Admin",
            why = "Prevents Focivo being uninstalled mid-session when Strict Mode is on.",
            sensitive = true
        )
    )

    fun focusPermissionById(id: String): FocusPermission? =
        FOCUS_PERMISSIONS.firstOrNull { it.id == id }

    /** True when every one of the 7 focus permissions is granted. */
    fun hasAllFocusPermissions(context: Context): Boolean =
        missingFocusPermissionIds(context).isEmpty()

    /** Ids of the focus permissions that are still missing. Empty = all granted. */
    fun missingFocusPermissionIds(context: Context): List<String> =
        FOCUS_PERMISSIONS.mapNotNull { perm ->
            if (isFocusPermissionGranted(context, perm.id)) null else perm.id
        }

    fun isFocusPermissionGranted(context: Context, id: String): Boolean = when (id) {
        "notifications" -> hasNotificationPermission(context)
        "usage" -> hasUsageStatsPermission(context)
        "overlay" -> hasOverlayPermission(context)
        "battery" -> isBatteryOptimizationDisabled(context)
        "exact_alarm" -> hasExactAlarmPermission(context)
        "accessibility" -> AiStudyGuardManager.isAccessibilityPermissionGranted(context)
        "device_admin" -> {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? android.app.DevicePolicyManager
            dpm?.isAdminActive(com.example.service.FocivoDeviceAdminReceiver.getComponentName(context)) == true
        }
        else -> false
    }

    /**
     * v1.2.1 — fires the system dialog / settings intent for one focus
     * permission id. Returns true when an intent was launched, false when
     * nothing could be opened (caller should toast + suggest manual grant).
     * Shared by onboarding and the Start-Study permission gate.
     */
    fun fireFocusPermissionIntent(context: Context, id: String): Boolean {
        return try {
            when (id) {
                "notifications" -> openAppNotificationSettings(context)
                "usage" -> openUsageStatsSettings(context)
                "overlay" -> openOverlaySettings(context)
                "battery" -> openBatteryOptimizationSettings(context)
                "exact_alarm" -> openExactAlarmSettings(context)
                "accessibility" -> AiStudyGuardManager.openAccessibilitySettings(context)
                "device_admin" -> {
                    try {
                        val intent = android.content.Intent(
                            android.app.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                        ).apply {
                            putExtra(
                                android.app.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                com.example.service.FocivoDeviceAdminReceiver.getComponentName(context)
                            )
                            putExtra(
                                android.app.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                "Prevents uninstalling Focivo during strict focus sessions."
                            )
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        openAppSettings(context)
                    }
                }
                else -> return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val notificationManager = ContextCompat.getSystemService(
                context,
                NotificationManager::class.java
            )
            notificationManager?.areNotificationsEnabled() ?: false
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else false
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    openAppSettings(context)
                }
            }
        }
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = ContextCompat.getSystemService(
                context,
                AlarmManager::class.java
            )
            alarmManager?.canScheduleExactAlarms() ?: true
        } else {
            true
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
    }

    fun openAppNotificationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                openAppSettings(context)
            }
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
