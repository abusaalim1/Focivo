package com.example.util

/**
 * v1.2.1 (owner-ordered RE-LOCK — the v1.2 test-unlock is over): Focus-Points
 * / level-gated content is locked again — outfit sets
 * (MascotBuddy.MascotOutfitSet), timer themes (TimerThemeManager.TimerTheme)
 * and timer faces (TimerFaceManager.TimerFace).
 *
 * All three isUnlocked() checks short-circuit on this flag, so every lock
 * badge, lock dialog and price label in the UI reappears automatically
 * (they all derive from isUnlocked()). Threshold model: points are never
 * spent, a tier unlocks permanently once lifetime Focus Points reach it.
 */
const val ALL_CONTENT_UNLOCKED = false
