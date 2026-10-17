package com.example.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.squads.Squad
import com.example.data.squads.SquadMember
import com.example.data.squads.SquadStanding
import com.example.ui.components.ThemedScreenBackground
import com.example.ui.screens.SquadDetailContent
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
 * v1.3.2 item 15: enriched group detail page — header (large picture,
 * serif-italic name, bio, code + copy), stats band (combined / avg / active),
 * top-3 podium (share-card aesthetic), invite + share section, member rows.
 * Pure content with fake-but-plausible standings (no backend needed).
 * PNGs land in app/src/test/screenshots/v132_groupdetail/.
 * Eyeball EVERY PNG: dark + light, 3 members and 8 members.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V132GroupDetailScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun themed(dark: Boolean, content: @Composable () -> Unit) {
        TimerThemeManager.init(ApplicationProvider.getApplicationContext())
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
            filePath = "src/test/screenshots/v132_groupdetail/${name}.png"
        )
    }

    private fun fakeSquad(memberCount: Int) = Squad(
        id = "squad-1",
        name = "Night Owls",
        bio = "Late-night deep work crew. We study together, win together.",
        pictureUrl = null, // initial-letter fallback
        inviteCode = "ABU7K2",
        ownerUserId = "user-0",
        memberCount = memberCount,
    )

    private fun member(i: Int, name: String, weeklyMin: Long, public: Boolean) = SquadStanding(
        member = SquadMember(
            id = "m-$i",
            squadId = "squad-1",
            userId = "user-$i",
            displayName = name,
            avatarUrl = null, // initial-letter fallback
        ),
        // Locked (private, not self) members' seconds are zeroed server-side —
        // mirror that in the fixture; the row must never show a time.
        weeklySeconds = weeklyMin * 60L,
        allTimeSeconds = weeklyMin * 300L,
        rank = 0,
        isMe = i == 0,
        isPublic = public,
    )

    private fun ranked(list: List<SquadStanding>) = list
        .sortedByDescending { it.weeklySeconds }
        .mapIndexed { i, s -> s.copy(rank = i + 1) }

    private fun standings3() = ranked(
        listOf(
            member(0, "Abu Saalim", 320, public = true),
            member(1, "Riya", 210, public = true),
            // Locked: visible name + avatar + lock, NO time (zeroed).
            member(2, "Imran", 0, public = false),
        )
    )

    private fun standings8() = ranked(
        listOf(
            member(0, "Abu Saalim", 320, public = true),
            member(1, "Riya", 410, public = true),
            member(2, "Imran", 210, public = true),
            member(3, "Sana", 95, public = true),
            member(4, "Dev", 40, public = true),
            member(5, "Noor", 30, public = true),
            member(6, "Arif", 22, public = true),
            // Locked: visible name + avatar + lock, NO time (zeroed).
            member(7, "Zara", 0, public = false),
        )
    )

    private fun shot(dark: Boolean, name: String, standings: List<SquadStanding>) {
        themed(dark) {
            SquadDetailContent(
                squad = fakeSquad(standings.size),
                standings = standings,
                myUserId = "user-0",
                onBack = {},
            )
        }
        capture("${name}_${if (dark) "dark" else "light"}")
    }

    @Test fun detail_3_dark() = shot(true, "detail_3", standings3())
    @Test fun detail_3_light() = shot(false, "detail_3", standings3())
    @Test fun detail_8_dark() = shot(true, "detail_8", standings8())
    @Test fun detail_8_light() = shot(false, "detail_8", standings8())
}
