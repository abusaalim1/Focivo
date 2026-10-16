package com.example.util

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * v1.2.2 (C) — dedicated Shorts/Reels surface detector.
 *
 * Detects the SHORT-FORM PLAYER/FEED surfaces inside YouTube and Instagram
 * from the accessibility tree, so the Shorts & Reels lock can intercept them
 * while long-form content in the same app stays fully watchable.
 *
 * ROOT CAUSE of the v1.2.1 false positive (owner report): the old check
 * matched the bottom-navigation tab viewIds ("pivot_shorts" on YouTube,
 * "clips_tab" on Instagram). Those nodes exist on EVERY screen of the app,
 * so merely OPENING YouTube during a study session looked like "watching
 * Shorts" and fired the discipline flow. Those tab viewIds are DELIBERATELY
 * not matched here — only real player/viewer surfaces count.
 *
 * Also used by B2: [isYouTubeVideoPlayerSurface] is the "actual extracted
 * content evidence" gate for the YouTube AI content filter — recommendations
 * on the home feed or search results are never evaluated.
 */
object ShortsSurfaceDetector {

    enum class ShortsSurface {
        YOUTUBE_SHORTS_PLAYER,
        YOUTUBE_SHORTS_FEED,
        INSTAGRAM_REELS
    }

    // ------------------------------------------------------------------
    // YouTube Shorts — fullscreen vertical player surface.
    //
    // v1.2.3 (A1): two-tier signals. The v1.2.2 list treated SHELF ids
    // (reel_item_layout / shorts_container on the HOME FEED's Shorts shelf)
    // as player evidence — merely opening YouTube home during study fired the
    // Shorts lock with no video watched (owner report 2026-10-16). STRONG ids
    // prove the player; WEAK ids only count alongside a player-action signal.
    // ------------------------------------------------------------------
    private val YT_PLAYER_STRONG_IDS = listOf(
        "reel_watch_fragment", // strongest signal: the Shorts watch fragment
        "reel_watch",
        "shorts_player",
        "reel_player",
        "shorts_sound_metadata"
    )

    private val YT_PLAYER_WEAK_IDS = listOf(
        "shorts_container",
        "reel_viewer",
        "reel_item_layout",
        "shorts_chip",
        "reel_feed"
    )

    /** Class-name fragments of the Shorts player (version-robust). */
    private val YT_PLAYER_CLASS_SUBSTR = listOf(
        "reelwatchfragment",
        "shortsplayer",
        "reelplayer"
    )

    /** Player-only action text — these buttons exist only in the Shorts player UI. */
    private val YT_PLAYER_TEXT = listOf(
        "like this short",
        "dislike this short",
        "remix this short",
        "shorts player"
    )

    /** Selected-state description of the Shorts tab = the Shorts feed itself. */
    private const val YT_SHORTS_TAB_SELECTED_DESC = "shorts, selected"

    // ------------------------------------------------------------------
    // Instagram Reels — fullscreen reels viewer surface.
    // ------------------------------------------------------------------
    private val IG_VIEWER_VIEW_IDS = listOf(
        "clips_video_container",
        "reel_viewer_container",
        "clips_viewer_container",
        // v1.2.3: version-robust variants (viewIds change across IG builds).
        "reels_viewer",
        "clips_viewer",
        "reel_video_container",
        "clips_video"
    )

    /** Class-name fragments of the Reels viewer (version-robust). */
    private val IG_VIEWER_CLASS_SUBSTR = listOf(
        "reelsviewer",
        "clipsviewer",
        "reelunit"
    )

    private const val IG_REELS_TAB_SELECTED_DESC = "reels, selected"
    private const val IG_REELS_VIEWER_DESC = "reels viewer"

    // ------------------------------------------------------------------
    // Playing-video evidence (B2 gate for the YouTube AI content filter).
    //
    // v1.2.3 (A2): STRONG ids prove the dedicated watch screen (or the
    // miniplayer — a genuinely playing user-chosen video). Generic player ids
    // (inline autoplay PREVIEWS on the home feed) only count when the player
    // node is large (>= 55% of the display width) — a muted feed preview is
    // NOT "actual video playback".
    // ------------------------------------------------------------------
    private val YT_WATCH_STRONG_IDS = listOf(
        "watch_player",
        "watch_fragment",
        "player_fragment"
    )

    private val YT_MINIPLAYER_IDS = listOf(
        "watch_while",
        "miniplayer",
        "mini_player"
    )

    private val YT_WATCH_GENERIC_IDS = listOf(
        "player_view",
        "video_player",
        "youtube_player",
        "player_container"
    )

    private class YtSignals {
        var player = false
        var playerStrong = false
        var feed = false
        var watchStrong = false
        var miniplayer = false
    }

    private class IgSignals {
        var viewer = false
        var feed = false
    }

    private fun collectYouTube(node: AccessibilityNodeInfo, s: YtSignals, depth: Int) {
        if (depth > 12 || (s.player && s.feed && s.watchStrong && s.miniplayer)) return
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val cls = try { node.className?.toString()?.lowercase(Locale.ROOT) } catch (_: Exception) { null } ?: ""

        val hasActionText = YT_PLAYER_TEXT.any { text.contains(it) || desc.contains(it) }
        if (!s.playerStrong && (YT_PLAYER_STRONG_IDS.any { viewId.contains(it) } ||
                YT_PLAYER_CLASS_SUBSTR.any { cls.contains(it) })) {
            s.playerStrong = true
            s.player = true
        }
        // Weak (shelf/container) ids only prove the player with an action signal.
        if (!s.player && YT_PLAYER_WEAK_IDS.any { viewId.contains(it) } && hasActionText) {
            s.player = true
        }
        if (!s.player && hasActionText) s.player = true
        if (!s.feed && desc == YT_SHORTS_TAB_SELECTED_DESC) s.feed = true
        // The Shorts feed's recycler — only exists on the Shorts tab feed.
        if (!s.feed && viewId.contains("reel_recycler")) s.feed = true
        if (!s.watchStrong && YT_WATCH_STRONG_IDS.any { viewId.contains(it) }) s.watchStrong = true
        if (!s.miniplayer && YT_MINIPLAYER_IDS.any { viewId.contains(it) }) s.miniplayer = true

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectYouTube(child, s, depth + 1)
            try { child.recycle() } catch (_: Exception) {}
        }
    }

    private fun collectInstagram(node: AccessibilityNodeInfo, s: IgSignals, depth: Int) {
        if (depth > 10 || (s.viewer && s.feed)) return
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
        val cls = try { node.className?.toString()?.lowercase(Locale.ROOT) } catch (_: Exception) { null } ?: ""

        if (!s.viewer) {
            if (IG_VIEWER_VIEW_IDS.any { viewId.contains(it) } ||
                IG_VIEWER_CLASS_SUBSTR.any { cls.contains(it) } ||
                desc.contains(IG_REELS_VIEWER_DESC)) {
                s.viewer = true
            }
        }
        if (!s.feed && desc == IG_REELS_TAB_SELECTED_DESC) s.feed = true

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            collectInstagram(child, s, depth + 1)
            try { child.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Returns the Shorts surface currently on screen inside YouTube, or null
     * when the user is on a long-form / browse surface (home, search, watch).
     */
    fun detectYouTubeShorts(root: AccessibilityNodeInfo): ShortsSurface? {
        val s = YtSignals()
        collectYouTube(root, s, 0)
        return when {
            s.player -> ShortsSurface.YOUTUBE_SHORTS_PLAYER
            s.feed -> ShortsSurface.YOUTUBE_SHORTS_FEED
            else -> null
        }
    }

    /**
     * Returns the Reels surface currently on screen inside Instagram, or null
     * when the user is elsewhere (feed, DMs, profile).
     */
    fun detectInstagramReels(root: AccessibilityNodeInfo): ShortsSurface? {
        val s = IgSignals()
        collectInstagram(root, s, 0)
        return when {
            s.viewer || s.feed -> ShortsSurface.INSTAGRAM_REELS
            else -> null
        }
    }

    /**
     * B2 evidence gate: true only when a video is ACTUALLY playing — the
     * dedicated watch screen, the miniplayer (a real user-chosen video), the
     * Shorts player, or a large (≥55% display width) inline player. Muted
     * autoplay previews in feed cards and the Shorts shelf on home are NOT
     * playback: the AI filter must never verdict-score browse surfaces.
     *
     * @param displayWidthPx display width for the large-player heuristic;
     * pass <= 0 to skip the generic-player branch.
     */
    fun isYouTubeVideoPlayerSurface(root: AccessibilityNodeInfo, displayWidthPx: Int = 0): Boolean {
        val s = YtSignals()
        collectYouTube(root, s, 0)
        if (s.watchStrong || s.miniplayer || s.playerStrong) return true
        if (displayWidthPx > 0 && hasLargeGenericPlayer(root, displayWidthPx)) return true
        return false
    }

    private fun hasLargeGenericPlayer(root: AccessibilityNodeInfo, displayWidthPx: Int): Boolean {
        val minW = (displayWidthPx * 0.55f).toInt()
        val rect = android.graphics.Rect()
        var found = false
        fun traverse(node: AccessibilityNodeInfo, depth: Int) {
            if (found || depth > 12) return
            val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
            if (YT_WATCH_GENERIC_IDS.any { viewId.contains(it) }) {
                try {
                    node.getBoundsInScreen(rect)
                    if (rect.width() >= minW) found = true
                } catch (_: Exception) { }
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                traverse(child, depth + 1)
                try { child.recycle() } catch (_: Exception) {}
            }
        }
        traverse(root, 0)
        return found
    }

    /**
     * True when the dedicated watch screen (not just the miniplayer) is up.
     * Lets the title extractor require player-context evidence on browse
     * surfaces instead of verdict-scoring recommendation cards.
     */
    fun hasYouTubeWatchScreen(root: AccessibilityNodeInfo): Boolean {
        val s = YtSignals()
        collectYouTube(root, s, 0)
        return s.watchStrong
    }

    /**
     * v1.2.3 near-miss diagnostics: compact signal string for the Guard Hub
     * ("player=false feed=false watch=false mini=false") so the owner's next
     * device test shows exactly what the detector saw.
     */
    fun debugYouTubeSignals(root: AccessibilityNodeInfo): String {
        val s = YtSignals()
        collectYouTube(root, s, 0)
        return "player=${s.player} feed=${s.feed} watch=${s.watchStrong} mini=${s.miniplayer}"
    }

    fun debugInstagramSignals(root: AccessibilityNodeInfo): String {
        val s = IgSignals()
        collectInstagram(root, s, 0)
        return "viewer=${s.viewer} feed=${s.feed}"
    }
}
