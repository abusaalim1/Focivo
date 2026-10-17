package com.example.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.MemberProfileData
import com.example.ui.screens.MemberProfileSheetContent
import com.example.ui.theme.FocuslyTheme
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
 * v1.3.2 item 7: member profile view — dark card like the owner's Regain
 * reference. Eyeball EVERY PNG:
 * - public member: avatar+name, "This week's focus", "Rank N • Top X%",
 *   both session tiles, all readable on the dark card;
 * - public member, sessions unknown (column absent): tiles HIDDEN, never
 *   invented;
 * - private member (not self): ONLY name + avatar + lock + "This profile is
 *   private" — zero stats anywhere;
 * - own profile: full stats, "(you)" suffix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132MemberProfileScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun themed(dark: Boolean, content: @Composable () -> Unit) {
        TimerThemeManager.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    ThemedScreenBackground()
                    content()
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeTestRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/v132_memberprofile/${name}.png"
        )
    }

    private fun publicMember(sessions: Int?) = MemberProfileData(
        userId = "u2",
        displayName = "Atika Parbin",
        avatarUrl = null, // initial-letter fallback
        isMe = false,
        isPublic = true,
        weeklySeconds = 8100L, // 2h 15m
        weeklySessions = sessions,
        rank = 2,
        totalUsers = 30,
    )

    private val privateMember = MemberProfileData(
        userId = "u3",
        displayName = "Rahul Das",
        avatarUrl = null,
        isMe = false,
        isPublic = false,
        weeklySeconds = 99999L, // must NEVER render
        weeklySessions = 99,    // must NEVER render
        rank = 1,               // must NEVER render
        totalUsers = 30,
    )

    private val selfMember = MemberProfileData(
        userId = "u1",
        displayName = "Abu Saalim",
        avatarUrl = null,
        isMe = true,
        isPublic = true,
        weeklySeconds = 3600L,
        weeklySessions = 2,
        rank = 1,
        totalUsers = 30,
    )

    private fun shot(dark: Boolean, name: String, data: MemberProfileData) {
        themed(dark) {
            MemberProfileSheetContent(data = data, failed = false, onDismiss = {})
        }
        capture("${name}_${if (dark) "dark" else "light"}")
    }

    @Test fun public_dark() = shot(true, "public", publicMember(4))
    @Test fun public_light() = shot(false, "public", publicMember(4))
    @Test fun public_no_sessions_dark() = shot(true, "public_no_sessions", publicMember(null))
    @Test fun public_no_sessions_light() = shot(false, "public_no_sessions", publicMember(null))
    @Test fun private_dark() = shot(true, "private", privateMember)
    @Test fun private_light() = shot(false, "private", privateMember)
    @Test fun self_dark() = shot(true, "self", selfMember)
    @Test fun self_light() = shot(false, "self", selfMember)
}
