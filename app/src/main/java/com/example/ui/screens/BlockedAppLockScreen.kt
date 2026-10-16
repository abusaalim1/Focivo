package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.example.MainActivity
import com.example.service.FocusShieldService
import com.example.ui.components.BlockedAppOverlay
import com.example.ui.theme.FocuslyTheme

class BlockedAppLockActivity : ComponentActivity() {

    private val blockedPackageState = mutableStateOf("")
    private val appNameState = mutableStateOf("")
    private val initialSecondsState = mutableIntStateOf(0)
    private val reasonState = mutableStateOf<String?>(null)
    private val isPunishmentState = mutableStateOf(false)
    private val guardVariantState = mutableStateOf<String?>(null)

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKED_NAME = "extra_blocked_name"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_IS_PUNISHMENT = "extra_is_punishment"
        const val EXTRA_GUARD_VARIANT = "extra_guard_variant"
        const val GUARD_VARIANT_WARNING = "warning"

        @Volatile
        var isCurrentlyShowing: Boolean = false

        /**
         * v1.2.3 — live instance for the braces dismiss path
         * ([BlockOverlayManager.dismissBracesActivity]).
         */
        @Volatile
        var currentInstance: BlockedAppLockActivity? = null
            private set

        fun createIntent(
            context: Context,
            packageName: String,
            appName: String,
            durationSec: Int = 0,
            reason: String? = null,
            isPunishment: Boolean = false,
            guardVariant: String? = null
        ): Intent {
            return Intent(context, BlockedAppLockActivity::class.java).apply {
                putExtra(EXTRA_BLOCKED_PACKAGE, packageName)
                putExtra(EXTRA_BLOCKED_NAME, appName)
                putExtra(EXTRA_DURATION_SECONDS, durationSec)
                putExtra(EXTRA_REASON, reason)
                putExtra(EXTRA_IS_PUNISHMENT, isPunishment)
                putExtra(EXTRA_GUARD_VARIANT, guardVariant)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        currentInstance = this
        parseIntentData(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToHomeScreen()
            }
        })

        setContent {
            FocuslyTheme {
                val mascotRes = when {
                    guardVariantState.value == GUARD_VARIANT_WARNING -> com.example.R.drawable.mascot_warning_transparent
                    isPunishmentState.value -> com.example.R.drawable.mascot_guard_transparent
                    else -> com.example.R.drawable.mascot_blocked
                }
                BlockedAppOverlay(
                    appName = appNameState.value,
                    blockedPackage = blockedPackageState.value,
                    initialDurationSec = initialSecondsState.intValue,
                    reason = reasonState.value,
                    isPunishment = isPunishmentState.value,
                    mascotRes = mascotRes,
                    onExitApp = {
                        exitToHomeScreen()
                    },
                    onOpenFocusly = {
                        val mainIntent = Intent(this@BlockedAppLockActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isCurrentlyShowing = true
    }

    override fun onResume() {
        super.onResume()
        isCurrentlyShowing = true
    }

    override fun onPause() {
        super.onPause()
        isCurrentlyShowing = false
    }

    override fun onStop() {
        super.onStop()
        isCurrentlyShowing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isCurrentlyShowing = false
        if (currentInstance === this) currentInstance = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseIntentData(intent)
    }

    private fun parseIntentData(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: ""
        val name = intent.getStringExtra(EXTRA_BLOCKED_NAME) ?: FocusShieldService.friendlyAppName(this, pkg)
        val sec = intent.getIntExtra(EXTRA_DURATION_SECONDS, 0)
        val rsn = intent.getStringExtra(EXTRA_REASON)
        val punishment = intent.getBooleanExtra(EXTRA_IS_PUNISHMENT, false)

        blockedPackageState.value = pkg
        appNameState.value = name
        initialSecondsState.intValue = sec
        reasonState.value = rsn
        isPunishmentState.value = punishment
        guardVariantState.value = intent.getStringExtra(EXTRA_GUARD_VARIANT)
    }

    private fun exitToHomeScreen() {
        isCurrentlyShowing = false
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        } catch (_: Exception) {}
        finish()
    }
}

