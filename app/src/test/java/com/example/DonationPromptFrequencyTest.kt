package com.example

import com.example.data.model.UserPreferencesEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DonationPromptFrequencyTest {

    private fun isEligibleForPrompt(prefs: UserPreferencesEntity, nowMs: Long): Boolean {
        val requiredCooldownMs = if (prefs.donationPromptDismissedCount >= 3) {
            4 * 24 * 60 * 60 * 1000L
        } else {
            24 * 60 * 60 * 1000L
        }

        if (prefs.lastDonationPromptShownAt > 0L) {
            val timeSinceLastShown = nowMs - prefs.lastDonationPromptShownAt
            if (timeSinceLastShown < requiredCooldownMs) {
                return false
            }
        }
        return true
    }

    @Test
    fun testFirstTimeUserIsEligibleForCooldownCheck() {
        val prefs = UserPreferencesEntity(lastDonationPromptShownAt = 0L, donationPromptDismissedCount = 0)
        val now = System.currentTimeMillis()
        assertTrue("New user should be eligible for cooldown check", isEligibleForPrompt(prefs, now))
    }

    @Test
    fun test24HourCooldownAppliesWhenDismissedCountLessThan3() {
        val now = 1000000000000L
        val shown12HoursAgo = UserPreferencesEntity(
            lastDonationPromptShownAt = now - (12 * 60 * 60 * 1000L),
            donationPromptDismissedCount = 1
        )
        assertFalse("Prompt shown 12h ago should NOT be eligible under 24h cooldown", isEligibleForPrompt(shown12HoursAgo, now))

        val shown25HoursAgo = UserPreferencesEntity(
            lastDonationPromptShownAt = now - (25 * 60 * 60 * 1000L),
            donationPromptDismissedCount = 1
        )
        assertTrue("Prompt shown 25h ago SHOULD be eligible under 24h cooldown", isEligibleForPrompt(shown25HoursAgo, now))
    }

    @Test
    fun testExtended4DayCooldownAppliesWhenDismissedCount3OrMore() {
        val now = 1000000000000L
        val shown2DaysAgo = UserPreferencesEntity(
            lastDonationPromptShownAt = now - (2 * 24 * 60 * 60 * 1000L),
            donationPromptDismissedCount = 3
        )
        assertFalse("Prompt shown 2 days ago should NOT be eligible under 4 day extended cooldown", isEligibleForPrompt(shown2DaysAgo, now))

        val shown5DaysAgo = UserPreferencesEntity(
            lastDonationPromptShownAt = now - (5 * 24 * 60 * 60 * 1000L),
            donationPromptDismissedCount = 3
        )
        assertTrue("Prompt shown 5 days ago SHOULD be eligible under 4 day extended cooldown", isEligibleForPrompt(shown5DaysAgo, now))
    }
}
