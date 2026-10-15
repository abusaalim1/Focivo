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
    // ------------------------------------------------------------------
    private val YT_PLAYER_VIEW_IDS = listOf(
        "reel_watch_fragment", // strongest signal: the Shorts watch fragment
        "shorts_player",
        "reel_player",
        "shorts_container",
        "reel_viewer",
        "reel_item_layout",
        "shorts_sound_metadata",
        "shorts_chip"
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
        "clips_viewer_container"
    )

    private const val IG_REELS_TAB_SELECTED_DESC = "reels, selected"
    private const val IG_REELS_VIEWER_DESC = "reels viewer"

    // ------------------------------------------------------------------
    // Playing-video evidence (B2 gate for the YouTube AI content filter).
    // ------------------------------------------------------------------
    private val YT_WATCH_PLAYER_IDS = listOf(
        "watch_player",
        "player_view",
        "video_player",
        "youtube_player",
        "player_container",
        "watch_while"
    )

    private class YtSignals {
        var player = false
        var feed = false
        var watchPlayer = false
    }

    private class IgSignals {
        var viewer = false
        var feed = false
    }

    private fun collectYouTube(node: AccessibilityNodeInfo, s: YtSignals, depth: Int) {
        if (depth > 12 || (s.player && s.feed && s.watchPlayer)) return
        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""

        if (!s.player) {
            if (YT_PLAYER_VIEW_IDS.any { viewId.contains(it) }) {
                s.player = true
            } else if (YT_PLAYER_TEXT.any { text.contains(it) || desc.contains(it) }) {
                s.player = true
            }
        }
        if (!s.feed && desc == YT_SHORTS_TAB_SELECTED_DESC) s.feed = true
        // The Shorts feed's recycler — only exists on the Shorts tab feed.
        if (!s.feed && viewId.contains("reel_recycler")) s.feed = true
        if (!s.watchPlayer && (YT_WATCH_PLAYER_IDS + YT_PLAYER_VIEW_IDS).any { viewId.contains(it) }) {
            s.watchPlayer = true
        }

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

        if (!s.viewer) {
            if (IG_VIEWER_VIEW_IDS.any { viewId.contains(it) } || desc.contains(IG_REELS_VIEWER_DESC)) {
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
     * B2 evidence gate: true only when a video player surface (long-form watch
     * player OR shorts player) is actually on screen. The YouTube AI content
     * filter must never evaluate home-feed recommendations or search results.
     */
    fun isYouTubeVideoPlayerSurface(root: AccessibilityNodeInfo): Boolean {
        val s = YtSignals()
        collectYouTube(root, s, 0)
        return s.watchPlayer
    }
}
