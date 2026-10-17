package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.2.4 — splash-variant regression tests (owner bug 2026-10-16 19:57:
 * signed-in users briefly saw the 8s new-user splash before the async
 * session restore flipped the variant).
 *
 * The variant is decided SYNCHRONOUSLY at first composition from local
 * persisted session state: any stored user id (signed-in account) or an
 * explicit guest session -> RETURNING; only a true first run (no stored
 * session at all) -> NEW_USER. No async flip may ever change it.
 */
@RunWith(RobolectricTestRunner::class)
class SplashVariantTest {

    private fun prefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("supabase_auth_session", Context.MODE_PRIVATE)

    private fun clear() {
        prefs().edit().clear().apply()
    }

    private fun appCtx(): Context = ApplicationProvider.getApplicationContext()

    @Test fun true_first_run_has_no_session() {
        clear()
        assertFalse(hasLocalSessionAtLaunch(appCtx()))
    }

    @Test fun stored_uid_means_returning() {
        clear()
        prefs().edit().putString("supabase_persisted_user_id", "user-123").apply()
        assertTrue(hasLocalSessionAtLaunch(appCtx()))
    }

    @Test fun guest_flag_means_returning() {
        clear()
        prefs().edit().putBoolean("supabase_persisted_is_guest", true).apply()
        assertTrue(hasLocalSessionAtLaunch(appCtx()))
    }

    @Test fun guest_uid_stored_means_returning() {
        clear()
        prefs().edit().putString("supabase_persisted_user_id", "guest_1690000000").apply()
        assertTrue(hasLocalSessionAtLaunch(appCtx()))
    }
}
