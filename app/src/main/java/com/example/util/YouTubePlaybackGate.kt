package com.example.util

/**
 * v1.3.3 (ITEM 2 — YouTube guard over-blocking fix, owner order 2026-10-18).
 *
 * ROOT CAUSE of the reported bug ("opening YouTube shows the home feed and
 * the AI guard blocks INSTANTLY on feed titles"):
 *   1. The old gate, ShortsSurfaceDetector.isYouTubeVideoPlayerSurface(),
 *      treated ANY large (>= 55% display width) node whose class name
 *      contains "player"/"video" as playback evidence. On the YouTube home
 *      feed the muted inline AUTOPLAY PREVIEW is a real player_view node
 *      spanning ~full width — so the home feed satisfied the gate.
 *   2. With the gate open, extractPlayingYouTubeVideo() found no
 *      miniplayer / Shorts player on the feed and returned null, so the
 *      FALLBACK title scan (extractFallbackVideoTitle) walked the WHOLE
 *      tree and grabbed the longest prominent text — a feed card title
 *      (e.g. a MrBeast thumbnail) — and verdict-scored it.
 *   3. A non-study verdict then fired the discipline flow immediately.
 *
 * FIX: an explicit playback-detection gate. Enforcement is allowed ONLY
 * when ALL of:
 *   (a) the foreground package is YouTube (checked by the caller),
 *   (b) a REAL player surface is present — dedicated watch screen,
 *       miniplayer (a user-chosen video), or the Shorts player — AND
 *       playback is genuinely in progress (a "Pause" affordance on the
 *       player controls, or device audio actually playing). A large muted
 *       inline preview on a browse surface is demoted to INLINE_PREVIEW:
 *       it may be observed / pre-classified quietly, but it can NEVER
 *       satisfy mayEnforce().
 *   (c) the PLAYING video's title/channel classifies non-study (the
 *       existing combined-brain pipeline in YouTubeTitleJudge).
 *
 * This file is Android-free on purpose: the gate walks a minimal
 * [YtGateNode] view of the accessibility tree, so the whole decision is
 * unit-testable on the JVM (see YouTubePlaybackGateTest).
 *
 * KNOWN FAILURE MODES (honest):
 * - Pause-affordance detection relies on the player controls exposing a
 *   "Pause"/"Pause video" content description (YouTube build-dependent).
 *   A renamed control label closes the gate even while video plays.
 * - A user-PAUSED video is deliberately NOT "playback in progress": the
 *   gate stays closed until playback resumes (re-evaluated on the next
 *   accessibility event).
 * - The audio fallback (AudioManager.isMusicActive, injected by the
 *   caller) has device-dependent latency of a few seconds and can be fooled
 *   by background music from another app while a paused YouTube video is
 *   on screen. It is a FALLBACK only; the pause-affordance signal is
 *   preferred whenever present.
 * - Shorts rely mostly on the audio signal (Shorts have no persistent
 *   pause button); there is a short window on Shorts entry before audio
 *   starts where the gate stays closed.
 * - Picture-in-picture miniplayer viewIds may differ across builds; if the
 *   miniplayer ids are absent, PiP playback is observe-only.
 *
 * Contract: all YtGateNode string values are lowercased (Locale.ROOT) by
 * the adapter before the gate sees them — matching is case-insensitive by
 * construction.
 */
interface YtGateNode {
    val viewId: String
    val text: String
    val contentDescription: String
    val className: String
    val boundsWidthPx: Int
    val children: List<YtGateNode>
}

/** Player-grade surface currently visible inside YouTube. */
enum class YtPlayerSurface {
    /** Browse surfaces: home, search results, subscriptions, channel pages. */
    NONE,
    /**
     * Large muted inline player on a browse surface (feed autoplay
     * preview). OBSERVE-ONLY: pre-classification caching is allowed, but
     * mayEnforce() is always false here.
     */
    INLINE_PREVIEW,
    /** Dedicated watch screen (watch_fragment / watch_player / player_fragment). */
    WATCH_SCREEN,
    /** Miniplayer floating over any surface — a user-chosen video playing. */
    MINIPLAYER,
    /** Shorts player (reel_watch_fragment / shorts_player / ...). */
    SHORTS_PLAYER
}

data class YtPlaybackSignals(
    val surface: YtPlayerSurface,
    /** Positive evidence that video playback is actually in progress. */
    val playbackInProgress: Boolean,
    /** Compact Guard Hub evidence string. */
    val evidence: String
) {
    val enforceable: Boolean get() = YouTubePlaybackGate.mayEnforce(this)
}

object YouTubePlaybackGate {

    private val WATCH_IDS = listOf("watch_player", "watch_fragment", "player_fragment")
    private val MINIPLAYER_IDS = listOf("watch_while", "miniplayer", "mini_player")
    private val SHORTS_PLAYER_IDS = listOf(
        "reel_watch_fragment", "reel_watch", "shorts_player", "reel_player", "shorts_sound_metadata"
    )
    // Shelf/container ids (reel_recycler, shorts_container, reel_item_layout,
    // shorts_chip, reel_feed) are DELIBERATELY absent: they exist on the home
    // feed's Shorts shelf and must never prove the player.
    private val GENERIC_PLAYER_IDS = listOf("player_view", "video_player", "youtube_player", "player_container")
    private const val INLINE_PREVIEW_WIDTH_FRACTION = 0.55f
    private const val MAX_DEPTH = 14

    private class Found {
        var watch = false
        var mini = false
        var shorts = false
        var pauseAffordance = false
        var largeGenericPlayer = false
    }

    /**
     * Walks the node tree once and returns the playback signals.
     *
     * @param audioActive true when device audio is actually playing
     *   (AudioManager.isMusicActive — injected by the caller so the gate
     *   stays Android-free).
     * @param displayWidthPx display width for the inline-preview heuristic;
     *   pass <= 0 to skip it.
     */
    fun detect(root: YtGateNode, audioActive: Boolean, displayWidthPx: Int = 0): YtPlaybackSignals {
        val f = Found()
        val minW = if (displayWidthPx > 0) (displayWidthPx * INLINE_PREVIEW_WIDTH_FRACTION).toInt() else Int.MAX_VALUE
        fun walk(node: YtGateNode, depth: Int) {
            if (depth > MAX_DEPTH) return
            val viewId = node.viewId
            if (!f.watch && WATCH_IDS.any { viewId.contains(it) }) f.watch = true
            if (!f.mini && MINIPLAYER_IDS.any { viewId.contains(it) }) f.mini = true
            if (!f.shorts && SHORTS_PLAYER_IDS.any { viewId.contains(it) }) f.shorts = true
            if (!f.pauseAffordance && isPauseAffordance(viewId, node.text, node.contentDescription)) {
                f.pauseAffordance = true
            }
            if (!f.largeGenericPlayer && displayWidthPx > 0) {
                val looksLikePlayer = GENERIC_PLAYER_IDS.any { viewId.contains(it) } ||
                        node.className.contains("player") || node.className.contains("video")
                if (looksLikePlayer && node.boundsWidthPx >= minW) f.largeGenericPlayer = true
            }
            for (child in node.children) walk(child, depth + 1)
        }
        walk(root, 0)

        // Surface priority: the most player-grade surface wins. The Shorts
        // player and the miniplayer are full playback contexts; the watch
        // screen is the dedicated surface; a large muted preview on a browse
        // surface is observe-only.
        val surface = when {
            f.shorts -> YtPlayerSurface.SHORTS_PLAYER
            f.mini -> YtPlayerSurface.MINIPLAYER
            f.watch -> YtPlayerSurface.WATCH_SCREEN
            f.largeGenericPlayer -> YtPlayerSurface.INLINE_PREVIEW
            else -> YtPlayerSurface.NONE
        }
        val playbackInProgress = f.pauseAffordance || audioActive
        val evidence = buildString {
            append("surface=").append(surface.name)
            append(" playing=").append(playbackInProgress)
            append(" sig=")
            val sigs = mutableListOf<String>()
            if (f.pauseAffordance) sigs.add("pause-btn")
            if (audioActive) sigs.add("audio")
            if (f.largeGenericPlayer) sigs.add("inline-preview")
            if (sigs.isEmpty()) sigs.add("none")
            append(sigs.joinToString("+"))
        }
        return YtPlaybackSignals(surface, playbackInProgress, evidence)
    }

    /**
     * The enforcement gate. True ONLY when a real player surface is
     * present AND playback is in progress. Browse surfaces (NONE /
     * INLINE_PREVIEW) can NEVER satisfy this — this is what makes the
     * "home feed never blocks" guarantee provable.
     */
    fun mayEnforce(s: YtPlaybackSignals): Boolean {
        if (!s.playbackInProgress) return false
        return s.surface == YtPlayerSurface.WATCH_SCREEN ||
                s.surface == YtPlayerSurface.MINIPLAYER ||
                s.surface == YtPlayerSurface.SHORTS_PLAYER
    }

    /**
     * The player controls offer "Pause" while video is playing and "Play"
     * while paused — the most direct on-device playback-state signal.
     * Deliberately label-first: the toggle viewId ("...play_pause...") is
     * identical in both states, so the id alone can never prove playback.
     * The label must be EXACTLY "pause"/"pause video" — looser prefixes
     * ("pause download") live outside the player and must not count.
     */
    private fun isPauseAffordance(viewId: String, text: String, desc: String): Boolean {
        val saysPause = desc == "pause" || desc == "pause video" || text == "pause"
        if (!saysPause) return false
        // Scope to player controls when an id is exposed; some builds
        // expose no viewId on the control, which is still accepted.
        return viewId.isEmpty() || viewId.contains("play") || viewId.contains("pause") || viewId.contains("player")
    }
}
