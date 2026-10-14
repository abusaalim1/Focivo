package com.example.screenshots.v11

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.LeaderboardUser
import com.example.ui.components.MascotPose
import com.example.ui.components.SensitivePermission
import com.example.ui.components.TrustExplainerDialog
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.PermissionGrantContent
import com.example.ui.screens.PermissionSpec
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.MascotWardrobe
import com.example.util.TimerFaceManager
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * v1.1 final-touches: simplified leaderboard (with avatars) + new onboarding
 * permission steps + trust explainer card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V11FinalScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun themed(dark: Boolean = true, content: @Composable () -> Unit) {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.components.ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    private fun fakeBitmapBase64(colorHex: String): String {
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.parseColor(colorHex))
        val paint = Paint().apply { color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER }
        canvas.drawText("A", 32f, 44f, paint)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun fakeLeaderboardUsers(): List<LeaderboardUser> = listOf(
        LeaderboardUser(id = "1", displayName = "Riya Sharma", todayStudySeconds = 7200L, weeklyStudySeconds = 374400L, studySeconds = 180000L, streak = 12, avatarUrl = fakeBitmapBase64("#8CE000")),
        LeaderboardUser(id = "2", displayName = "Arjun Mehta", todayStudySeconds = 5400L, weeklyStudySeconds = 316800L, studySeconds = 150000L, streak = 9),
        LeaderboardUser(id = "3", displayName = "Abu Saalim", todayStudySeconds = 3600L, weeklyStudySeconds = 270000L, studySeconds = 120000L, streak = 7, isCurrentUser = true, avatarUrl = fakeBitmapBase64("#FFB300")),
        LeaderboardUser(id = "4", displayName = "Priya Das", todayStudySeconds = 3000L, weeklyStudySeconds = 198000L, studySeconds = 90000L, streak = 5),
        LeaderboardUser(id = "5", displayName = "Karan Singh", todayStudySeconds = 2400L, weeklyStudySeconds = 144000L, studySeconds = 70000L, streak = 4, avatarUrl = fakeBitmapBase64("#40C4FF"))
    )

    @Test
    fun v11final_leaderboard_simplified() {
        themed(dark = true) {
            LeaderboardScreen(
                users = fakeLeaderboardUsers(),
                currentUserName = "Abu Saalim",
                currentUserPhotoUrl = null,
                currentUserPoints = 320,
                currentUserStreak = 7,
                sessions = emptyList(),
                onRefresh = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v11-final/leaderboard_simplified.png")
    }

    private fun permissionStepTest(tag: String, spec: PermissionSpec, progress: Float) {
        themed(dark = true) {
            PermissionGrantContent(
                spec = spec,
                progress = progress,
                onAllow = {},
                onLater = {},
                onBack = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v11-final/$tag.png")
    }

    @Test
    fun v11final_onboarding_permission_exact_alarm() {
        permissionStepTest(
            tag = "onboarding_permission_exact_alarm",
            progress = 15f / 19f,
            spec = PermissionSpec(
                id = "exact_alarm",
                rowLabel = "Alarm permission",
                bubbleLead = "So your focus sessions and reminders ",
                bubbleAccent = "start on time",
                bubbleTail = ", we need exact alarm access.",
                privacyLine = "Your privacy matters to us. We never share your data.",
                whyBody = "Session end alarms, scheduled blocks and study reminders fire exactly when promised. Without it, the system may delay them.",
                pose = MascotPose.GUARD
            )
        )
    }

    @Test
    fun v11final_onboarding_permission_accessibility() {
        permissionStepTest(
            tag = "onboarding_permission_accessibility",
            progress = 16f / 19f,
            spec = PermissionSpec(
                id = "accessibility",
                rowLabel = "AI Study Guard",
                bubbleLead = "To ",
                bubbleAccent = "detect non-study apps during focus",
                bubbleTail = ", the AI Study Guard needs accessibility access.",
                privacyLine = "Detection happens on your phone. We never collect or share your data.",
                whyBody = "This is what powers the guard: when a blocked app opens during a focus session, Focivo notices and brings up the block screen.",
                pose = MascotPose.GUARD_WARNING
            )
        )
    }

    @Test
    fun v11final_onboarding_permission_device_admin() {
        permissionStepTest(
            tag = "onboarding_permission_device_admin",
            progress = 17f / 19f,
            spec = PermissionSpec(
                id = "device_admin",
                rowLabel = "Device admin",
                bubbleLead = "To ",
                bubbleAccent = "prevent uninstalls during strict sessions",
                bubbleTail = ", we need device admin access.",
                privacyLine = "Only active while a strict session runs. Removable anytime from settings.",
                whyBody = "This stops accidental (or tempted) uninstalls mid-session. You stay in control.",
                pose = MascotPose.STOP_SIGN
            )
        )
    }

    @Test
    fun v11final_trust_explainer_accessibility() {
        themed(dark = true) {
            TrustExplainerDialog(
                permission = SensitivePermission.ACCESSIBILITY,
                onContinue = {},
                onNotNow = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v11-final/trust_explainer_accessibility.png")
    }

    @Test
    fun v11final_trust_explainer_device_admin() {
        themed(dark = true) {
            TrustExplainerDialog(
                permission = SensitivePermission.DEVICE_ADMIN,
                onContinue = {},
                onNotNow = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v11-final/trust_explainer_device_admin.png")
    }
}
