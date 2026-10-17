package com.example.service

import java.util.Locale

/**
 * v1.3.1 — pure, JVM-testable helpers for the floating timer pill's
 * drag + auto-dock behavior.
 *
 * Deliberately free of Android/Compose APIs so plain unit tests can
 * exercise every rule (nearest-edge choice, dock x, clamping, time text)
 * without Robolectric.
 */
object FloatingPillDocking {

    /** Idle time with no interaction before the pill docks itself. */
    const val AUTO_DOCK_DELAY_MS = 3000L

    /** Docked mini-pill diameter, in dp. */
    const val DOCKED_SIZE_DP = 64

    /** Gap between the docked mini-pill and the screen edge, in dp. */
    const val DOCK_MARGIN_DP = 10

    enum class DockEdge { LEFT, RIGHT }

    /**
     * Which screen edge is nearer to the pill's current horizontal span.
     * [currentXPx] is the window's absolute x (gravity LEFT). Ties go LEFT.
     */
    fun nearestEdge(currentXPx: Int, viewWidthPx: Int, screenWidthPx: Int): DockEdge {
        val centerPx = currentXPx + viewWidthPx / 2f
        return if (centerPx <= screenWidthPx / 2f) DockEdge.LEFT else DockEdge.RIGHT
    }

    /** Absolute x (gravity LEFT) for the docked mini-pill on [edge]. */
    fun dockedXPx(
        edge: DockEdge,
        screenWidthPx: Int,
        dockedWidthPx: Int,
        marginPx: Int
    ): Int = when (edge) {
        DockEdge.LEFT -> marginPx
        DockEdge.RIGHT -> (screenWidthPx - dockedWidthPx - marginPx).coerceAtLeast(0)
    }

    /** Keep the expanded pill's absolute x inside the screen bounds. */
    fun clampExpandedXPx(xPx: Int, viewWidthPx: Int, screenWidthPx: Int): Int {
        val max = (screenWidthPx - viewWidthPx).coerceAtLeast(0)
        return xPx.coerceIn(0, max)
    }

    /** Compact "MM:SS" text for the docked mini-pill; hours fold into minutes. */
    fun formatDockedCountdown(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val minutes = s / 60
        val seconds = s % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
