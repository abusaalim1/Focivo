package com.example.screenshots.v123

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AndroidPreferenceSessionManager
import com.example.data.FocuslyRepository
import com.example.data.GuestDataStorageManager
import com.example.data.GuestProfileData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the "stuck guest" bug (owner report 2026-10-16 07:32 IST):
 * once a guest session was EVER created, FocuslyViewModel.restoreAuthSessionStandalone()
 * revived a phantom guest on EVERY cold start via GuestDataStorageManager's
 * guest_profile.json, which survived sign-out (only the session prefs were cleared).
 * Result: _currentUser was never null -> MainActivity always picked the RETURNING
 * splash -> straight into the app, and the NEW_USER splash / Create Account
 * screen could NEVER appear again.
 *
 * Contract tested here = the exact persistence inputs the restore decision reads:
 *  - TRUE first run (nothing persisted anywhere) -> _currentUser stays null ->
 *    NEW_USER splash -> AuthScreen with AuthMode.CreateAccount preselected.
 *  - FocuslyRepository.clearAllData() (the real sign-out path used by
 *    syncAndLogout()) must remove EVERY revival input: the session prefs
 *    (isGuest/userId) AND guest_profile.json. Afterwards the next launch
 *    behaves like a true first run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestSessionEscapeTest {

    private lateinit var context: Context

    private fun cleanSlate() {
        // Mirrors FocuslyRepository.clearAllData()'s sign-out clearing.
        AndroidPreferenceSessionManager.clearStoredSession(context)
        GuestDataStorageManager.getInstance(context).clearGuestData()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanSlate()
    }

    @After
    fun tearDown() {
        cleanSlate()
    }

    @Test
    fun `fresh install has no guest persistence - true first run reaches Create Account`() {
        assertFalse(AndroidPreferenceSessionManager.isGuestSession(context))
        assertNull(AndroidPreferenceSessionManager.getStoredUserId(context))
        assertNull(GuestDataStorageManager.getInstance(context).loadGuestProfile())
        // With all three absent, restoreAuthSessionStandalone() falls to its
        // else branch (_currentUser = null), MainActivity picks NEW_USER and
        // AuthScreen opens with AuthMode.CreateAccount preselected.
    }

    @Test
    fun `explicit guest sign-in persists all revival inputs - bug preconditions`() {
        // Mirrors FocuslyViewModel.signInAsGuest() persistence (the explicit path).
        AndroidPreferenceSessionManager.setStoredGuestSession(
            context, "guest_123", "Guest Scholar", "guest_123@focusly.app"
        )
        val saved = GuestDataStorageManager.getInstance(context).saveGuestProfile(
            GuestProfileData(
                guestId = "guest_123",
                guestName = "Guest Scholar",
                guestEmail = "guest_123@focusly.app"
            )
        )
        assertTrue(saved)
        assertTrue(AndroidPreferenceSessionManager.isGuestSession(context))
        assertEquals("guest_123", AndroidPreferenceSessionManager.getStoredUserId(context))
        assertNotNull(GuestDataStorageManager.getInstance(context).loadGuestProfile())
    }

    @Test
    fun `clearAllData removes every guest revival input - escape works`() {
        // Arrange: the stuck-guest state (prefs + guest_profile.json present,
        // exactly what survived sign-out before the fix).
        AndroidPreferenceSessionManager.setStoredGuestSession(
            context, "guest_123", "Guest Scholar", "guest_123@focusly.app"
        )
        GuestDataStorageManager.getInstance(context).saveGuestProfile(
            GuestProfileData(
                guestId = "guest_123",
                guestName = "Guest Scholar",
                guestEmail = "guest_123@focusly.app"
            )
        )
        assertNotNull(GuestDataStorageManager.getInstance(context).loadGuestProfile())

        // Act: the REAL sign-out path (FocuslyViewModel.syncAndLogout ->
        // FocuslyRepository.clearAllData).
        FocuslyRepository(context).clearAllData()

        // Assert: restore sees nothing on the next launch -> _currentUser stays
        // null -> NEW_USER splash -> Auth with Create Account preselected.
        assertFalse(AndroidPreferenceSessionManager.isGuestSession(context))
        assertNull(AndroidPreferenceSessionManager.getStoredUserId(context))
        assertNull(GuestDataStorageManager.getInstance(context).loadGuestProfile())
    }
}
