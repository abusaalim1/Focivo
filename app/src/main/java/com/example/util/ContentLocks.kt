package com.example.util

/**
 * v1.2 (owner-ordered TEST build): unlocks ALL Focus-Points / level-gated
 * content for free so the owner can try everything before release —
 * outfit sets (MascotBuddy.MascotOutfitSet), timer themes
 * (TimerThemeManager.TimerTheme) and timer faces (TimerFaceManager.TimerFace).
 *
 * All three isUnlocked() checks short-circuit on this flag, so every lock
 * badge, lock dialog and price label in the UI disappears automatically
 * (they all derive from isUnlocked()).
 *
 * Set to false to restore the normal points lock ladder.
 */
const val ALL_CONTENT_UNLOCKED = true
