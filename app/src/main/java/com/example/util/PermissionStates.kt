package com.example.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.service.AiStudyAccessibilityService
import com.example.service.FocivoDeviceAdminReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * v1.3.3 — single source of truth for every permission / grant check in the
 * app. Every predicate below performs a LIVE system query on each call —
 * never a cached boolean, never a one-shot value read at composition.
 *
 * UI rule (owner report 2026-10-18): any screen/sheet that shows a permission
 * row, toggle or gate MUST re-run these predicates on ON_RESUME (see
 * [RefreshOnResume] / [observeResume]) so a permission granted in system
 * Settings is reflected the moment the user returns. Stale `remember {}`
 * one-shots are the bug this file exists to kill.
 */
object PermissionStates {

    private const val ACCESSIBILITY_SERVICE_CLASS = "com.example.service.AiStudyAccessibilityService"

    /** Usage Access (UsageStats): live AppOps query, no caching. */
    fun isUsageAccessGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Accessibility service: detects the service actually RUNNING for OUR
     * package, not merely a stale secure-settings flag.
     *
     * Order:
     * 1. In-process liveness flag ([AiStudyAccessibilityService.isServiceConnected]).
     * 2. [AccessibilityManager.getEnabledAccessibilityServiceList] — exact
     *    component match against our package + service class.
     * 3. Secure-settings fallback ([Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES])
     *    — exact component match ONLY (never a loose substring), because OEM
     *    ROMs can leave this flag stale briefly after the user disables the
     *    service.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        if (AiStudyAccessibilityService.isServiceConnected) return true
        val expectedComponent = "${context.packageName}/$ACCESSIBILITY_SERVICE_CLASS"
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabled = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabled != null) {
                for (info in enabled) {
                    if (info.id.equals(expectedComponent, ignoreCase = true)) return true
                    val serviceInfo = info.resolveInfo?.serviceInfo
                    if (serviceInfo != null &&
                        serviceInfo.packageName == context.packageName &&
                        serviceInfo.name == ACCESSIBILITY_SERVICE_CLASS
                    ) {
                        return true
                    }
                }
            }
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expectedComponent, ignoreCase = true)) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Draw-over-other-apps: live Settings query. */
    fun canDrawOverlays(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Notifications: runtime permission on 33+, app-level switch below. */
    fun isNotificationGranted(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                val nm = ContextCompat.getSystemService(context, NotificationManager::class.java)
                nm?.areNotificationsEnabled() ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Device admin: verifies OUR receiver is really an active admin. */
    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            dpm?.isAdminActive(FocivoDeviceAdminReceiver.getComponentName(context)) == true
        } catch (_: Exception) {
            false
        }
    }

    /** Battery-optimization exemption (background survival on OEM ROMs). */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Exact alarms (Android 12+). */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = ContextCompat.getSystemService(context, AlarmManager::class.java)
                am?.canScheduleExactAlarms() ?: true
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * The three permissions Focus Shield needs to actually block apps
     * (usage + overlay + accessibility). Home / Settings / Profile rows and
     * the Start-Study gate all read this.
     */
    fun hasCoreShieldPermissions(context: Context): Boolean =
        isUsageAccessGranted(context) &&
            canDrawOverlays(context) &&
            isAccessibilityEnabled(context)

    /** Immutable snapshot of every permission state at one moment. */
    data class PermissionGrantStates(
        val usageAccess: Boolean = false,
        val accessibility: Boolean = false,
        val overlay: Boolean = false,
        val notifications: Boolean = false,
        val deviceAdmin: Boolean = false,
        val batteryExemption: Boolean = false,
        val exactAlarms: Boolean = false
    )

    /** Live snapshot of every permission state. */
    fun snapshot(context: Context): PermissionGrantStates = PermissionGrantStates(
        usageAccess = isUsageAccessGranted(context),
        accessibility = isAccessibilityEnabled(context),
        overlay = canDrawOverlays(context),
        notifications = isNotificationGranted(context),
        deviceAdmin = isDeviceAdminActive(context),
        batteryExemption = isBatteryOptimizationDisabled(context),
        exactAlarms = canScheduleExactAlarms(context)
    )
}

/**
 * v1.3.3 — reactive holder a screen/sheet can observe: call [refresh] on
 * ON_RESUME (via [RefreshOnResume] or [observeResume]) and collectors get the
 * fresh live states. Unit-tested.
 */
class PermissionStateMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val _states = MutableStateFlow(PermissionStates.snapshot(appContext))
    val states: StateFlow<PermissionStates.PermissionGrantStates> = _states.asStateFlow()

    fun refresh() {
        _states.value = PermissionStates.snapshot(appContext)
    }
}

/**
 * Registers an ON_RESUME observer on [lifecycle] that runs [onResume] every
 * time the host returns to the foreground (e.g. back from system Settings).
 * Returns the observer so callers can remove it. Safe to call from
 * [DisposableEffect]; exceptions in [onResume] never crash the observer.
 */
fun observeResume(lifecycle: Lifecycle, onResume: () -> Unit): LifecycleEventObserver {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            try {
                onResume()
            } catch (_: Exception) {
            }
        }
    }
    lifecycle.addObserver(observer)
    return observer
}

/**
 * Composable version of [observeResume]: drop one line into any screen/sheet
 * that displays permission state and the UI re-reads the REAL system state on
 * every return from Settings.
 *
 * Usage:
 * ```
 * var granted by remember { mutableStateOf(PermissionStates.isNotificationGranted(context)) }
 * RefreshOnResume { granted = PermissionStates.isNotificationGranted(context) }
 * ```
 */
@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = observeResume(lifecycleOwner.lifecycle) { currentOnResume() }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
