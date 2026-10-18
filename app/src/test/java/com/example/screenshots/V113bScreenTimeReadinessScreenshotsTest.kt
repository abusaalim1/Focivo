package com.example.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.ui.components.ScreenTimeReadinessCard
import com.example.ui.components.StudyTopStatsHeader
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.focivoTokens
import com.example.util.ScreenTimeReadiness
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1.3 (FIX 2) — the "Screen time" readiness card in the Block hub, plus
 * the HONESTY-rule state of the Study header's Usage card.
 *
 * - readiness card, granted state: three green checks + fresh timestamp
 * - readiness card, missing state: one-tap Grant / Request pills + "Never"
 * - study header, missing usage access: value "--" + "Usage access needed"
 *   (never a fabricated number)
 *
 * Captured dark + light at real phone size. These run on CI via the
 * screenshots workflow (recordRoborazziDebug); the VM has no JVM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V113bScreenTimeReadinessScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun themed(dark: Boolean, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ThemedScreenBackground()
                    content()
                }
            }
        }
    }

    private fun capture(name: String) {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v113b_readiness/$name.png"
        )
    }

    private val grantedInput = ScreenTimeReadiness.ReadinessInput(
        usageAccessGranted = true,
        batteryExemptionGranted = true,
        lastComputedAtMs = System.currentTimeMillis() - 3 * 60_000L
    )

    private val missingInput = ScreenTimeReadiness.ReadinessInput(
        usageAccessGranted = false,
        batteryExemptionGranted = false,
        lastComputedAtMs = null
    )

    @Composable
    private fun PaddedCard(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(focivoTokens().bg)
                .padding(22.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            content()
        }
    }

    // ---------- readiness card: granted (dark + light) ----------------------

    @Test
    fun readinessCardGranted_dark() {
        themed(dark = true) {
            PaddedCard {
                ScreenTimeReadinessCard(
                    readiness = grantedInput,
                    onGrantUsageAccess = {},
                    onRequestBatteryExemption = {},
                    onRefresh = {}
                )
            }
        }
        capture("readiness_granted_dark")
    }

    @Test
    fun readinessCardGranted_light() {
        themed(dark = false) {
            PaddedCard {
                ScreenTimeReadinessCard(
                    readiness = grantedInput,
                    onGrantUsageAccess = {},
                    onRequestBatteryExemption = {},
                    onRefresh = {}
                )
            }
        }
        capture("readiness_granted_light")
    }

    // ---------- readiness card: missing (dark + light) ----------------------

    @Test
    fun readinessCardMissing_dark() {
        themed(dark = true) {
            PaddedCard {
                ScreenTimeReadinessCard(
                    readiness = missingInput,
                    onGrantUsageAccess = {},
                    onRequestBatteryExemption = {},
                    onRefresh = {}
                )
            }
        }
        capture("readiness_missing_dark")
    }

    @Test
    fun readinessCardMissing_light() {
        themed(dark = false) {
            PaddedCard {
                ScreenTimeReadinessCard(
                    readiness = missingInput,
                    onGrantUsageAccess = {},
                    onRequestBatteryExemption = {},
                    onRefresh = {}
                )
            }
        }
        capture("readiness_missing_light")
    }

    // ---------- study header: honest missing state (dark + light) -----------

    @Test
    fun studyHeaderMissingUsageAccess_dark() {
        themed(dark = true) {
            PaddedCard {
                StudyTopStatsHeader(
                    usageMillis = null,
                    hasUsagePermission = false,
                    focusMinutesToday = 45,
                    onUsageCardClick = {}
                )
            }
        }
        capture("study_header_missing_dark")
    }

    @Test
    fun studyHeaderMissingUsageAccess_light() {
        themed(dark = false) {
            PaddedCard {
                StudyTopStatsHeader(
                    usageMillis = null,
                    hasUsagePermission = false,
                    focusMinutesToday = 45,
                    onUsageCardClick = {}
                )
            }
        }
        capture("study_header_missing_light")
    }
}
