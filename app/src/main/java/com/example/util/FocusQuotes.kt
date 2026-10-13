package com.example.util

/**
 * v1.3.1 — rotating motivational quote lines for the session-complete share card.
 * English only. Static copy (not social proof) — safe to show on share cards.
 * Quote for a session is picked deterministically from the session id so the
 * card is stable across re-renders but varies session to session.
 */
object FocusQuotes {

    private val QUOTES = listOf(
        "Small focus, big future. Keep going.",
        "You just proved your attention is yours.",
        "Deep work is a superpower. You used it today.",
        "One focused minute beats ten distracted ones.",
        "Discipline is choosing what you want most.",
        "Your future self says thanks.",
        "Focus is the new IQ.",
        "You didn't just study — you trained your mind.",
        "Distractions knocked. You didn't answer.",
        "Consistency compounds. This session counts.",
        "The doomscroll has been defeated today.",
        "Calm mind, sharp focus, steady progress.",
        "Every focused session rewires your brain for depth.",
        "You showed up. That's the whole game.",
        "Attention is currency. You just got richer.",
        "Study now, shine later."
    )

    /** Deterministic per-session quote (stable for the same session id). */
    fun forSession(sessionId: Long): String {
        if (QUOTES.isEmpty()) return ""
        val idx = (sessionId % QUOTES.size).toInt().let { if (it < 0) it + QUOTES.size else it }
        return QUOTES[idx]
    }
}
