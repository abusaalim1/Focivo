package com.example.screenshots.v12

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.LeaderboardUser
import com.example.data.model.FocusSessionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.ui.components.BuddyMascot
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.components.TimerThemePicker
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.MascotScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.MascotPose
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
 * v1.2 verify round (branch feat/v12-round, NEVER merged blindly):
 * - new 10-set wardrobe on the mascot (hood-cap, never bald) across
 *   Home / Focus study buddy / MascotScreen / buddy close-ups
 * - simplified leaderboard WITH avatars
 * - themes picker + timer faces ALL unlocked (ALL_CONTENT_UNLOCKED)
 * PNGs land in app/src/test/screenshots/v12/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V12ScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initManagers() {
        TimerThemeManager.init(context)
        TimerFaceManager.init(context)
        MascotWardrobe.init(context)
        // default equipped set is set02 (sage) — no override needed
    }

    private fun themed(dark: Boolean = true, content: @Composable () -> Unit) {
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ThemedScreenBackground()
                        content()
                    }
                }
            }
        }
    }

    private fun fakePrefs(): UserPreferencesEntity = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 360,
        focusPoints = 1250
    )

    private fun fakePriorities(): List<TaskEntity> = listOf(
        TaskEntity(id = 1L, title = "Physics: Units & Measurement", category = "Deep Work", priority = 1, isTopPriority = true),
        TaskEntity(id = 2L, title = "Maths: Quadratic Equations", category = "Deep Work", priority = 1, isTopPriority = true)
    )

    private fun fakeSessions(): List<FocusSessionEntity> = listOf(
        FocusSessionEntity(
            taskTitle = "Physics",
            mode = "Deep Work",
            durationSeconds = 3600,
            focusPointsEarned = 60,
            completedAt = System.currentTimeMillis()
        ),
        FocusSessionEntity(
            taskTitle = "Maths",
            mode = "Deep Work",
            durationSeconds = 1500,
            focusPointsEarned = 25,
            completedAt = System.currentTimeMillis() - 3_600_000L
        )
    )

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

    @Test
    fun v12_home_default_set02() {
        initManagers()
        TimerThemeManager.selectTheme(context, "midnight")
        themed(dark = true) {
            HomeScreen(
                topPriorities = fakePriorities(),
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                scheduleStatusSummary = "Evening Deep Focus · 6:00 PM",
                scheduledBlocksCount = 2,
                onToggleTask = {},
                onStartFocus = { _, _ -> },
                onCustomizeFocus = {},
                onOpenPlanner = {},
                onOpenTaskCreate = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v12/v12_home_default_set02.png")
    }

    @Test
    fun v12_mascot_screen_grid() {
        initManagers()
        themed(dark = true) {
            MascotScreen(
                focusPoints = 1250,
                onBack = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v12/v12_mascot_screen_grid.png")
    }

    @Test
    fun v12_buddy_set02_closeup_standing() {
        initManagers()
        themed(dark = true) {
            Box(
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101410)),
                contentAlignment = Alignment.Center
            ) {
                BuddyMascot(
                    modifier = Modifier.size(420.dp),
                    pose = MascotPose.STANDING
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v12/v12_buddy_set02_closeup_standing.png")
    }

    @Test
    fun v12_buddy_set02_closeup_celebrate() {
        initManagers()
        themed(dark = true) {
            Box(
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101410)),
                contentAlignment = Alignment.Center
            ) {
                BuddyMascot(
                    modifier = Modifier.size(420.dp),
                    pose = MascotPose.CELEBRATE
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v12/v12_buddy_set02_closeup_celebrate.png")
    }

    @Test
    fun v12_buddy_set04_preview_study() {
        initManagers()
        themed(dark = true) {
            Box(
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101410)),
                contentAlignment = Alignment.Center
            ) {
                // lavender LEARN+Lock set preview (pending-outfit path, like the MascotScreen preview)
                BuddyMascot(
                    modifier = Modifier.size(420.dp),
                    pose = MascotPose.STUDY,
                    pendingOutfit = "set04"
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v12/v12_buddy_set04_preview_study.png")
    }

    @Test
    fun v12_focus_study_buddy() {
        initManagers()
        TimerThemeManager.selectTheme(context, "midnight")
        themed(dark = true) {
            FocusScreen(
                remainingSeconds = 25 * 60,
                targetSeconds = 25 * 60,
                isRunning = false,
                currentMode = "Deep Work",
                currentTaskTitle = "Physics: Units & Measurement",
                distractionsCount = 0,
                ambientSound = "Rain",
                userStreak = 7,
                userPreferences = fakePrefs(),
                onStartTimer = {},
                onPauseTimer = {},
                onResumeTimer = {},
                onFinishEarly = {},
                onAddFiveMinutes = {},
                onSkipBreak = {},
                onSelectMode = { _, _ -> },
                onSelectAmbientSound = {},
                onLogDistraction = {},
                onUpdateTaskTitle = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v12/v12_focus_study_buddy.png")
    }

    @Test
    fun v12_leaderboard_avatars() {
        initManagers()
        TimerThemeManager.selectTheme(context, "midnight")
        val users = listOf(
            LeaderboardUser(id = "1", displayName = "Riya Sharma", todayStudySeconds = 7200L, weeklyStudySeconds = 374400L, studySeconds = 180000L, streak = 12, avatarUrl = fakeBitmapBase64("#8CE000")),
            LeaderboardUser(id = "2", displayName = "Arjun Mehta", todayStudySeconds = 5400L, weeklyStudySeconds = 316800L, studySeconds = 150000L, streak = 9),
            LeaderboardUser(id = "3", displayName = "Abu Saalim", todayStudySeconds = 3600L, weeklyStudySeconds = 270000L, studySeconds = 120000L, streak = 7, isCurrentUser = true, avatarUrl = fakeBitmapBase64("#FFB300")),
            LeaderboardUser(id = "4", displayName = "Priya Das", todayStudySeconds = 3000L, weeklyStudySeconds = 198000L, studySeconds = 90000L, streak = 5),
            LeaderboardUser(id = "5", displayName = "Karan Singh", todayStudySeconds = 2400L, weeklyStudySeconds = 144000L, studySeconds = 70000L, streak = 4, avatarUrl = fakeBitmapBase64("#40C4FF"))
        )
        themed(dark = true) {
            LeaderboardScreen(
                users = users,
                currentUserName = "Abu Saalim",
                currentUserPhotoUrl = null,
                currentUserPoints = 1250,
                currentUserStreak = 7,
                sessions = fakeSessions(),
                onRefresh = {}
            )
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v12/v12_leaderboard_avatars.png")
    }

    @Test
    fun v12_themes_picker_all_unlocked() {
        initManagers()
        TimerThemeManager.selectTheme(context, "midnight")
        themed(dark = true) {
            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                TimerThemePicker(
                    focusPoints = 0,
                    selectedThemeId = "midnight",
                    onThemeTap = { _, _ -> },
                    textPrimary = androidx.compose.ui.graphics.Color(0xFFF0F4ED),
                    cardBorder = androidx.compose.ui.graphics.Color(0xFFDCE6D2)
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v12/v12_themes_picker_all_unlocked.png")
    }

    @Test
    fun v12_customize_timer_templates_unlocked() {
        initManagers()
        TimerThemeManager.selectTheme(context, "midnight")
        themed(dark = true) {
            CustomizeTimerSheet(
                onDismiss = {},
                currentFocusMinutes = 25,
                currentSubject = "Physics",
                ambientSound = "Rain",
                onSelectAmbientSound = {},
                onSelectMode = { _, _ -> },
                onUpdateSubject = {}
            )
        }
        composeTestRule.onNodeWithTag("customize_timer_sheet", useUnmergedTree = true)
            .captureRoboImage("src/test/screenshots/v12/v12_customize_timer_templates_unlocked.png")
    }
}
