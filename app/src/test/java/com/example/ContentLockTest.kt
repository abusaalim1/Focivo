package com.example

import com.example.util.ALL_CONTENT_UNLOCKED
import com.example.util.ContentLocks
import com.example.util.MascotLevel
import com.example.util.OUTFIT_SETS
import com.example.util.TimerFaceManager
import com.example.util.TimerThemeManager
import com.example.util.levelForPoints
import com.example.ui.timer.PomodoroStyle
import org.junit.Assert.*
import org.junit.Test

/**
 * Content-lock regression tests (owner bug 2026-10-16: many themes and
 * clock faces rendered UNLOCKED when they should be point-locked).
 *
 * Root causes fixed:
 * - "mint" theme had unlockPoints = 0 (ladder typo) -> now 2750
 * - six v1.2.3 clock faces registered at 0 -> now on the price ladder
 *   (orbit 500, flip 1500, dial 2500, stack 3500, bars 5000, editorial 7000)
 *
 * Model: threshold-based, points never spent, same focusPoints source.
 */
class ContentLockTest {

    private fun theme(id: String) =
        TimerThemeManager.THEMES.first { it.id == id }

    private fun face(id: String) =
        TimerFaceManager.FACES.first { it.id == id }

    private fun set(id: String) =
        OUTFIT_SETS.first { it.id == id }

    @Test fun content_is_locked_by_default() {
        assertFalse(ALL_CONTENT_UNLOCKED)
    }

    @Test fun tier_boundaries() {
        assertEquals(MascotLevel.NOVICE, levelForPoints(0))
        assertEquals(MascotLevel.NOVICE, levelForPoints(999))
        assertEquals(MascotLevel.SCHOLAR, levelForPoints(1000))
        assertEquals(MascotLevel.SCHOLAR, levelForPoints(4999))
        assertEquals(MascotLevel.GRANDMASTER, levelForPoints(5000))
    }

    @Test fun free_themes_unlocked_at_zero() {
        assertTrue(theme("aurora").isUnlocked(0))
        assertTrue(theme("forest").isUnlocked(0))
    }

    @Test fun theme_ladder_thresholds() {
        assertFalse(theme("royal").isUnlocked(999))
        assertTrue(theme("royal").isUnlocked(1000))
        assertFalse(theme("coffee").isUnlocked(4999))
        assertTrue(theme("coffee").isUnlocked(5000))
        assertFalse(theme("sunset").isUnlocked(0))
        assertTrue(theme("sunset").isUnlocked(999))
    }

    @Test fun mint_theme_is_not_free() {
        // Regression: mint was registered at 0 (ladder typo).
        assertEquals(2750, theme("mint").unlockPoints)
        assertFalse(theme("mint").isUnlocked(0))
        assertFalse(theme("mint").isUnlocked(2749))
        assertTrue(theme("mint").isUnlocked(2750))
    }

    @Test fun ambient_themes_stay_locked() {
        assertFalse(theme("sakura_drift").isUnlocked(5000))
        assertTrue(theme("sakura_drift").isUnlocked(18000))
    }

    @Test fun free_faces_unlocked_at_zero() {
        // v1.3.0 — Aurora Ring removed; Minimal Digits is the free default.
        assertTrue(face("digits").isUnlocked(0))
    }

    @Test fun aurora_ring_face_removed_v130() {
        // Owner order 2026-10-17: the Aurora Ring face is gone completely.
        assertTrue(TimerFaceManager.FACES.none { it.id == "ring" })
        assertTrue(TimerFaceManager.FACES.none { it.name.contains("Aurora") })
        // Legacy persisted "ring" ids resolve to the new default face.
        assertEquals("digits", TimerFaceManager.getFace("ring").id)
        assertEquals("digits", TimerFaceManager.getFace(null).id)
        assertEquals("digits", TimerFaceManager.FACES.first().id)
    }

    @Test fun face_ladder_thresholds() {
        assertFalse(face("clock_liquid").isUnlocked(0))
        assertTrue(face("clock_liquid").isUnlocked(150))
        assertFalse(face("clock_breathe").isUnlocked(199))
        assertTrue(face("clock_breathe").isUnlocked(200))
        assertFalse(face("clock_horizon").isUnlocked(249))
        assertTrue(face("clock_horizon").isUnlocked(250))
        assertFalse(face("glass").isUnlocked(499))
        assertTrue(face("glass").isUnlocked(500))
        assertFalse(face("editorial").isUnlocked(1000))
        assertTrue(face("editorial").isUnlocked(3500))
    }

    @Test fun new_faces_not_free() {
        // Regression: six v1.2.3 faces were registered at 0.
        assertEquals(500, face("clock_orbit").unlockPoints)
        assertEquals(1500, face("clock_flip").unlockPoints)
        assertEquals(2500, face("clock_dial").unlockPoints)
        assertEquals(3500, face("clock_stack").unlockPoints)
        assertEquals(5000, face("clock_bars").unlockPoints)
        assertEquals(7000, face("clock_editorial").unlockPoints)
        for (id in listOf("clock_orbit", "clock_flip", "clock_dial", "clock_stack", "clock_bars", "clock_editorial")) {
            assertFalse("$id must be locked at 0 points", face(id).isUnlocked(0))
        }
        assertTrue(face("clock_orbit").isUnlocked(500))
        assertFalse(face("clock_bars").isUnlocked(4999))
        assertTrue(face("clock_bars").isUnlocked(5000))
    }

    @Test fun outfit_set_locks_match_spec() {        // Spec: 01/02/03 free, 05/06/07/08 Scholar-1000, 04/09/10 Grandmaster-5000.
        assertTrue(set("set01").isUnlocked(0))
        assertTrue(set("set02").isUnlocked(0))
        assertTrue(set("set03").isUnlocked(0))
        assertFalse(set("set05").isUnlocked(999))
        assertTrue(set("set05").isUnlocked(1000))
        assertFalse(set("set08").isUnlocked(999))
        assertTrue(set("set08").isUnlocked(1000))
        assertFalse(set("set04").isUnlocked(4999))
        assertTrue(set("set04").isUnlocked(5000))
        assertFalse(set("set09").isUnlocked(4999))
        assertTrue(set("set09").isUnlocked(5000))
        assertFalse(set("set10").isUnlocked(4999))
        assertTrue(set("set10").isUnlocked(5000))
    }

    // v1.2.4: Pomodoro designs are point-locked like timer faces
    // (TICK_RING free at 0; ACTIVITY_RINGS 1000; SESSION_STACK 2500;
    // TYPOGRAPHIC 5000). Regression: they were lock-free.
    @Test fun pomodoro_design_locks() {
        assertTrue(PomodoroStyle.TICK_RING.isUnlocked(0))
        assertEquals(1000, PomodoroStyle.ACTIVITY_RINGS.unlockPoints)
        assertEquals(2500, PomodoroStyle.SESSION_STACK.unlockPoints)
        assertEquals(5000, PomodoroStyle.TYPOGRAPHIC.unlockPoints)
        assertFalse(PomodoroStyle.ACTIVITY_RINGS.isUnlocked(999))
        assertTrue(PomodoroStyle.ACTIVITY_RINGS.isUnlocked(1000))
        assertFalse(PomodoroStyle.SESSION_STACK.isUnlocked(2499))
        assertTrue(PomodoroStyle.SESSION_STACK.isUnlocked(2500))
        assertFalse(PomodoroStyle.TYPOGRAPHIC.isUnlocked(4999))
        assertTrue(PomodoroStyle.TYPOGRAPHIC.isUnlocked(5000))
    }

    // v1.2.4: admin account (owner) bypasses every lock at any point level.
    @Test fun admin_email_matching() {
        assertTrue(ContentLocks.isAdminEmail("abusalim202322@gmail.com"))
        assertTrue(ContentLocks.isAdminEmail("ABUSALIM202322@GMAIL.COM"))
        assertTrue(ContentLocks.isAdminEmail("  abusalim202322@gmail.com  "))
        assertFalse(ContentLocks.isAdminEmail("abusalim202322@gmail.co"))
        assertFalse(ContentLocks.isAdminEmail("someone@gmail.com"))
        assertFalse(ContentLocks.isAdminEmail(null))
        assertFalse(ContentLocks.isAdminEmail(""))
    }

    @Test fun admin_unlocks_everything() {
        // Timer faces
        assertTrue(face("clock_editorial").isUnlocked(0, isAdmin = true))
        assertTrue(face("clock_bars").isUnlocked(0, isAdmin = true))
        // Timer themes
        assertTrue(theme("coffee").isUnlocked(0, isAdmin = true))
        assertTrue(theme("sakura_drift").isUnlocked(0, isAdmin = true))
        // Outfit sets
        assertTrue(set("set10").isUnlocked(0, isAdmin = true))
        // Pomodoro designs
        assertTrue(PomodoroStyle.TYPOGRAPHIC.isUnlocked(0, isAdmin = true))
    }

    @Test fun non_admin_keeps_ladder() {
        assertFalse(face("clock_editorial").isUnlocked(0, isAdmin = false))
        assertFalse(PomodoroStyle.TYPOGRAPHIC.isUnlocked(0, isAdmin = false))
        assertFalse(set("set10").isUnlocked(0, isAdmin = false))
        assertFalse(theme("coffee").isUnlocked(0, isAdmin = false))
    }
}

