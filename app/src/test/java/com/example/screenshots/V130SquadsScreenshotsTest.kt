package com.example.screenshots

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.squads.Squad
import com.example.data.squads.SquadMember
import com.example.data.squads.SquadStanding
import com.example.ui.screens.SquadInviteCardPng
import com.example.ui.screens.SquadMemberRow
import com.example.ui.screens.SquadStandingsCardPng
import com.example.ui.theme.FocuslyTheme
import com.example.ui.theme.LocalAppAccent
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
 * v1.3.0 — Squads share cards, rendered from the owner's Group Cards design.
 * Pure composables with fake-but-plausible data (no backend needed), so the
 * owner can review the card art before anything ships.
 * PNGs land in app/src/test/screenshots/v130-squads/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V130SquadsScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun fakeSquad() = Squad(
        id = "squad-1",
        name = "Night Owls",
        bio = "Late-night deep work crew.",
        pictureUrl = null,
        inviteCode = "ABU7K2",
        ownerUserId = "user-1",
        memberCount = 5,
    )

    private fun fakeStandings(): List<SquadStanding> {
        val names = listOf("Abu Saalim", "Riya", "Imran", "Sana", "Dev")
        val weeklyMin = listOf(320, 410, 210, 95, 40)
        val totalMin = listOf(2400, 3100, 1500, 700, 320)
        return names.mapIndexed { i, n ->
            SquadStanding(
                member = SquadMember(
                    id = "m-$i",
                    squadId = "squad-1",
                    userId = "user-$i",
                    displayName = n,
                    avatarUrl = null,
                ),
                weeklySeconds = weeklyMin[i] * 60L,
                allTimeSeconds = totalMin[i] * 60L,
                rank = 0,
                isMe = i == 0,
            )
        }.sortedByDescending { it.weeklySeconds }
            .mapIndexed { i, s -> s.copy(rank = i + 1) }
    }

    private fun themed(content: @Composable () -> Unit) {
        TimerThemeManager.init(context)
        TimerThemeManager.selectTheme(context, "midnight")
        val theme = TimerThemeManager.selectedTheme.value
        composeTestRule.setContent {
            FocuslyTheme(darkTheme = true) {
                CompositionLocalProvider(LocalAppAccent provides theme.accent) {
                    content()
                }
            }
        }
    }

    private fun capture(name: String) {
        composeTestRule.onRoot().captureRoboImage("src/test/screenshots/v130-squads/$name.png")
    }

    @Test
    fun squad_standings_card() {
        val squad = fakeSquad()
        val standings = fakeStandings()
        themed {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0C0B))
                    .padding(24.dp)
            ) {
                Box(Modifier.size(360.dp, 450.dp)) {
                    SquadStandingsCardPng(squad = squad, standings = standings)
                }
            }
        }
        capture("squad_standings_card")
    }

    @Test
    fun squad_invite_card() {
        val squad = fakeSquad()
        themed {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0C0B))
                    .padding(24.dp)
            ) {
                Box(Modifier.size(360.dp, 450.dp)) {
                    SquadInviteCardPng(
                        squad = squad,
                        inviterName = "Abu Saalim",
                        inviterWeeklyMin = 320,
                        memberNames = listOf("Abu Saalim", "Riya", "Imran", "Sana", "Dev"),
                    )
                }
            }
        }
        capture("squad_invite_card")
    }

    @Test
    fun squad_member_rows() {
        val standings = fakeStandings()
        val maxWeekly = standings.maxOf { it.weeklySeconds }
        themed {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0C0B))
                    .padding(24.dp)
            ) {
                Column {
                    standings.forEach { s ->
                        SquadMemberRow(standing = s, maxWeeklySeconds = maxWeekly)
                    }
                }
            }
        }
        capture("squad_member_rows")
    }
}
