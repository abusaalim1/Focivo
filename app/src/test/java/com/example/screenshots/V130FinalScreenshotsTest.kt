package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.FocusSessionEntity
import com.example.data.model.TaskEntity
import com.example.data.model.UserPreferencesEntity
import com.example.data.squads.Squad
import com.example.ui.components.CustomizeTimerSheet
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.FocusScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SessionCompleteScreen
import com.example.ui.screens.SquadCreateScreen
import com.example.ui.screens.SquadDetailScreen
import com.example.ui.screens.SquadInviteScreen
import com.example.ui.screens.SquadJoinScreen
import com.example.ui.screens.SquadsFlowScreen
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
import com.example.util.MascotWardrobe
import com.example.util.TimerThemeManager
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.3.0 FINAL assembly verify (branch feat/v130-squads):
 * every screen touched by the mega-round rendered in BOTH dark and light
 * theme at Pixel 8 size so the assembler can eyeball each PNG:
 * Home (Squads card), squads hub/create/invite/join/detail, session-complete
 * flow, customize timer (Aurora face removed), profile + edit profile
 * (worker C), Focus screen.
 * PNGs land in app/src/test/screenshots/v130-final/.
 *
 * Squads backend is absent server-side (DDL 403-blocked) — the flow screens
 * render their graceful "setting up"/unavailable states, which is exactly
 * what the owner will see until the SQL is pasted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V130FinalScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun initAll() {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "midnight")
        MascotWardrobe.init(context)
        // Squads screens hit SquadRepository -> SupabaseManager.client (lazy).
        // Initialize so the client builds properly in tests; the absent
        // tables then resolve to the graceful SquadsUnavailable state.
        com.example.data.SupabaseManager.initialize(context)
    }

    /** Same stack as MainActivity: theme backdrop behind everything. */
    private fun themed(dark: Boolean, content: @Composable () -> Unit) {
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

    private fun shot(dark: Boolean, name: String, content: @Composable () -> Unit) {
        initAll()
        themed(dark, content)
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v130-final/$name.png")
    }

    /**
     * Squad flow screens fetch from SquadRepository on Dispatchers.IO, which
     * waitForIdle() does not track. Give the call time to resolve to the
     * graceful unavailable/error state before capturing.
     */
    private fun shotSquads(dark: Boolean, name: String, content: @Composable () -> Unit) {
        initAll()
        themed(dark, content)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(5_000)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v130-final/$name.png")
    }

    private fun fakePrefs() = UserPreferencesEntity(
        currentUserName = "Abu Saalim",
        currentUserEmail = "scholar@example.com",
        dailyGoalMinutes = 240,
        focusPoints = 1500,
        primaryStudyGoal = "Crack JEE with daily deep work",
        studentClassLevel = "Class 11",
        targetExam = "JEE",
        isProfilePublic = true,
    )

    private fun fakePriorities() = listOf(
        TaskEntity(id = 1L, title = "Physics: Units & Measurement", category = "Deep Work", priority = 1, isTopPriority = true),
        TaskEntity(id = 2L, title = "Maths: Quadratic Equations", category = "Deep Work", priority = 1, isTopPriority = true)
    )

    private fun fakeSessions() = listOf(
        FocusSessionEntity(
            id = 101L,
            taskTitle = "Physics",
            mode = "Deep Work",
            durationSeconds = 3600,
            focusPointsEarned = 60,
            completedAt = System.currentTimeMillis()
        ),
        FocusSessionEntity(
            id = 102L,
            taskTitle = "Maths",
            mode = "Deep Work",
            durationSeconds = 1500,
            focusPointsEarned = 25,
            completedAt = System.currentTimeMillis() - 3_600_000L
        )
    )

    private fun fakeSquad() = Squad(
        id = "squad-1",
        name = "Night Owls",
        bio = "Late-night deep work crew.",
        pictureUrl = null,
        inviteCode = "ABU7K2",
        ownerUserId = "user-1",
        memberCount = 5,
    )

    // ── Home (Squads card) ──────────────────────────────────────────────
    @Test
    fun v130final_home_dark() = shot(true, "v130final_home_dark") {
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
            onOpenTaskCreate = {},
            onOpenSquads = {},
        )
    }

    @Test
    fun v130final_home_light() = shot(false, "v130final_home_light") {
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
            onOpenTaskCreate = {},
            onOpenSquads = {},
        )
    }

    // ── Squads hub ──────────────────────────────────────────────────────
    @Test
    fun v130final_squads_hub_dark() = shotSquads(true, "v130final_squads_hub_dark") {
        SquadsFlowScreen(
            onBack = {},
            myUserId = "user-1",
            myDisplayName = "Abu Saalim",
            myAvatarUrl = null,
            myWeeklyMinutes = 320,
        )
    }

    @Test
    fun v130final_squads_hub_light() = shotSquads(false, "v130final_squads_hub_light") {
        SquadsFlowScreen(
            onBack = {},
            myUserId = "user-1",
            myDisplayName = "Abu Saalim",
            myAvatarUrl = null,
            myWeeklyMinutes = 320,
        )
    }

    // ── Squads create ───────────────────────────────────────────────────
    @Test
    fun v130final_squads_create_dark() = shot(true, "v130final_squads_create_dark") {
        SquadCreateScreen(
            myUserId = "user-1",
            myDisplayName = "Abu Saalim",
            myAvatarUrl = null,
            onBack = {},
            onCreated = {},
        )
    }

    @Test
    fun v130final_squads_create_light() = shot(false, "v130final_squads_create_light") {
        SquadCreateScreen(
            myUserId = "user-1",
            myDisplayName = "Abu Saalim",
            myAvatarUrl = null,
            onBack = {},
            onCreated = {},
        )
    }

    // ── Squads invite ───────────────────────────────────────────────────
    @Test
    fun v130final_squads_invite_dark() = shot(true, "v130final_squads_invite_dark") {
        SquadInviteScreen(
            squad = fakeSquad(),
            inviterName = "Abu Saalim",
            inviterWeeklyMin = 320,
            memberNames = listOf("Abu Saalim"),
            onBack = {},
            onOpenSquad = {},
        )
    }

    @Test
    fun v130final_squads_invite_light() = shot(false, "v130final_squads_invite_light") {
        SquadInviteScreen(
            squad = fakeSquad(),
            inviterName = "Abu Saalim",
            inviterWeeklyMin = 320,
            memberNames = listOf("Abu Saalim"),
            onBack = {},
            onOpenSquad = {},
        )
    }

    // ── Squads join ─────────────────────────────────────────────────────
    @Test
    fun v130final_squads_join_dark() = shotSquads(true, "v130final_squads_join_dark") {
        SquadJoinScreen(
            prefillCode = "ABU7K2",
            myUserId = "user-1",
            myDisplayName = "Abu Saalim",
            myAvatarUrl = null,
            onBack = {},
            onJoined = {},
        )
    }

    @Test
    fun v130final_squads_join_light() = shotSquads(false, "v130final_squads_join_light") {
        SquadJoinScreen(
            prefillCode = "ABU7K2",
            myUserId = "user-1",
            myDisplayName = "Abu Saalim",
            myAvatarUrl = null,
            onBack = {},
            onJoined = {},
        )
    }

    // ── Squads detail ───────────────────────────────────────────────────
    @Test
    fun v130final_squads_detail_dark() = shotSquads(true, "v130final_squads_detail_dark") {
        SquadDetailScreen(
            squadId = "squad-1",
            myUserId = "user-1",
            myDisplayName = "Abu Saalim",
            onBack = {},
        )
    }

    @Test
    fun v130final_squads_detail_light() = shotSquads(false, "v130final_squads_detail_light") {
        SquadDetailScreen(
            squadId = "squad-1",
            myUserId = "user-1",
            myDisplayName = "Abu Saalim",
            onBack = {},
        )
    }

    // ── Session complete flow (owner HTML alignment; animateEntrance=false
    // goes straight to the SHARE stage for a deterministic capture) ──────
    @Test
    fun v130final_session_complete_dark() = shot(true, "v130final_session_complete_dark") {
        SessionCompleteScreen(
            session = FocusSessionEntity(
                id = 201L,
                taskTitle = "Physics",
                mode = "Deep Work",
                durationSeconds = 1500,
                focusPointsEarned = 25,
                distractionsCount = 1,
                completedAt = System.currentTimeMillis()
            ),
            onDismiss = {},
            animateEntrance = false
        )
    }

    @Test
    fun v130final_session_complete_light() = shot(false, "v130final_session_complete_light") {
        SessionCompleteScreen(
            session = FocusSessionEntity(
                id = 201L,
                taskTitle = "Physics",
                mode = "Deep Work",
                durationSeconds = 1500,
                focusPointsEarned = 25,
                distractionsCount = 1,
                completedAt = System.currentTimeMillis()
            ),
            onDismiss = {},
            animateEntrance = false
        )
    }

    // ── Customize timer (Aurora face removed by worker B) ───────────────
    @Test
    fun v130final_customize_dark() = shot(true, "v130final_customize_dark") {
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

    @Test
    fun v130final_customize_light() = shot(false, "v130final_customize_light") {
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

    // ── Profile (worker C: goal/class/exam header, privacy pill, no email) ──
    @Test
    fun v130final_profile_dark() = shot(true, "v130final_profile_dark") {
        ProfileScreen(
            userPreferences = fakePrefs(),
            sessions = fakeSessions(),
            onOpenSettings = {}
        )
    }

    @Test
    fun v130final_profile_light() = shot(false, "v130final_profile_light") {
        ProfileScreen(
            userPreferences = fakePrefs(),
            sessions = fakeSessions(),
            onOpenSettings = {}
        )
    }

    // ── Edit Profile dialog (worker C: name/goal/class/exam/email/switch) ───
    // NOTE: captured without waitForIdle() — the dialog (activity-result
    // launcher + AlertDialog window) never settles Robolectric's idling
    // resource; v123 precedent uses mainClock.advanceTimeBy for such UI.
    @Test
    fun v130final_profile_edit_dark() {
        initAll()
        themed(true) {
            ProfileScreen(
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                onOpenSettings = {}
            )
        }
        composeTestRule.onNodeWithContentDescription("Edit Profile").performClick()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v130-final/v130final_profile_edit_dark.png")
    }

    @Test
    fun v130final_profile_edit_light() {
        initAll()
        themed(false) {
            ProfileScreen(
                userPreferences = fakePrefs(),
                sessions = fakeSessions(),
                onOpenSettings = {}
            )
        }
        composeTestRule.onNodeWithContentDescription("Edit Profile").performClick()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v130-final/v130final_profile_edit_light.png")
    }

    // ── Focus screen ────────────────────────────────────────────────────
    @Test
    fun v130final_focus_dark() = shot(true, "v130final_focus_dark") {
        FocusScreen(
            remainingSeconds = 12 * 60 + 34,
            targetSeconds = 25 * 60,
            isRunning = true,
            currentMode = "Deep Work",
            currentTaskTitle = "Physics: Units & Measurement",
            distractionsCount = 2,
            ambientSound = "Rain",
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
            onUpdateTaskTitle = {},
        )
    }

    @Test
    fun v130final_focus_light() = shot(false, "v130final_focus_light") {
        FocusScreen(
            remainingSeconds = 12 * 60 + 34,
            targetSeconds = 25 * 60,
            isRunning = true,
            currentMode = "Deep Work",
            currentTaskTitle = "Physics: Units & Measurement",
            distractionsCount = 2,
            ambientSound = "Rain",
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
            onUpdateTaskTitle = {},
        )
    }
}
