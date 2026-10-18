package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.3.3 (ITEM 2 — YouTube guard over-blocking fix).
 *
 * Mocks the accessibility event sequence with fake node trees (see
 * FakeNode) and asserts the pure gate/judge/orchestrator decisions:
 *  - feed scroll (entertainment titles) -> NEVER enforces
 *  - playing study video -> allowed
 *  - playing entertainment video (above threshold, not doubt) -> Enforce
 *  - doubt phrasing -> doubt-gate preserved (no block)
 *  - whitelist toggle OFF -> filter fully inert
 *
 * The service's enforceIfPlayingYouTube() choke point performs the real
 * side effects (pause media, overlay, discipline flow) and is intentionally
 * NOT covered here; the guarantee under test is that the orchestrator can
 * only hand it Action.Enforce, and Action.Enforce is unreachable from
 * browse surfaces.
 */
class YouTubePlaybackGateTest {

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private data class FakeNode(
        override val viewId: String = "",
        override val text: String = "",
        override val contentDescription: String = "",
        override val className: String = "",
        override val boundsWidthPx: Int = 0,
        override val children: List<YtGateNode> = emptyList()
    ) : YtGateNode

    private class FakeDeps(
        val blocklisted: Boolean = false,
        val allowlisted: Boolean = false,
        val brain: YouTubeTitleJudge.BrainVerdict =
            YouTubeTitleJudge.BrainVerdict(YouTubeTitleJudge.BrainDecision.UNCERTAIN, "combined", 0.5),
        val knowledge: YouTubeTitleJudge.KnowledgeVerdict =
            YouTubeTitleJudge.KnowledgeVerdict(YouTubeTitleJudge.KnowledgeDecision.UNCERTAIN, 0.0, ""),
        val quick: YouTubeTitleJudge.QuickVerdict =
            YouTubeTitleJudge.QuickVerdict(true, false, 0.9f, "", "")
    ) : YouTubeTitleJudge.Deps {
        override fun isChannelBlocklisted(channel: String?) = blocklisted
        override fun isChannelAllowlisted(channel: String?) = allowlisted
        override fun judgeBrain(title: String, channel: String?) = brain
        override fun scoreKnowledge(title: String, channel: String?) = knowledge
        override fun quickEvaluate(title: String, channel: String?, metadata: String?) = quick
    }

    private val blockingBrain = YouTubeTitleJudge.BrainVerdict(
        YouTubeTitleJudge.BrainDecision.BLOCK, "combined", 0.12)
    private val studyBrain = YouTubeTitleJudge.BrainVerdict(
        YouTubeTitleJudge.BrainDecision.ALLOW, "combined", 0.93)

    private fun video(title: String, channel: String? = "mrbeast", source: String = "fallback-scan") =
        YouTubeGuardOrchestrator.ActiveVideo(title, channel, null, source)

    private fun inputs(
        signals: YtPlaybackSignals,
        video: YouTubeGuardOrchestrator.ActiveVideo?,
        deps: YouTubeTitleJudge.Deps,
        filterEnabled: Boolean = true
    ) = YouTubeGuardOrchestrator.Inputs(
        isYouTubeForeground = true,
        isStudyPeriod = true,
        filterEnabled = filterEnabled,
        signals = signals,
        video = video,
        judge = deps
    )

    /** Home feed: muted inline autoplay preview (full-width player_view) + Shorts shelf. */
    private fun homeFeedTree(): YtGateNode {
        val preview = FakeNode(
            viewId = "com.google.android.youtube:id/player_view",
            className = "com.google.android.apps.youtube.app.player.inline.inlineplayerview",
            boundsWidthPx = 1080
        )
        val card = FakeNode(
            viewId = "com.google.android.youtube:id/video_card",
            children = listOf(
                preview,
                FakeNode(text = "i survived 50 hours in a nuclear bunker"),
                FakeNode(text = "mrbeast")
            )
        )
        // The home feed's Shorts SHELF — its recycler/shelf ids must never
        // prove the Shorts player.
        val shelf = FakeNode(
            viewId = "com.google.android.youtube:id/reel_recycler",
            children = listOf(FakeNode(contentDescription = "shorts player"))
        )
        return FakeNode(children = listOf(card, shelf))
    }

    /** Dedicated watch screen; pauseDesc = what the play/pause control exposes. */
    private fun watchTree(pauseDesc: String = "pause video"): YtGateNode =
        FakeNode(children = listOf(
            FakeNode(
                viewId = "com.google.android.youtube:id/watch_fragment",
                children = listOf(
                    FakeNode(
                        viewId = "com.google.android.youtube:id/player_view",
                        className = "com.google.android.apps.youtube.app.player.playerview",
                        boundsWidthPx = 1080
                    ),
                    FakeNode(
                        viewId = "com.google.android.youtube:id/player_control_play_pause_replay_button",
                        contentDescription = pauseDesc
                    )
                )
            ),
            FakeNode(viewId = "com.google.android.youtube:id/video_title", text = "i built a working nuclear reactor"),
            FakeNode(viewId = "com.google.android.youtube:id/channel_name", text = "mrbeast")
        ))

    private fun miniplayerTree(): YtGateNode =
        FakeNode(children = listOf(
            FakeNode(
                viewId = "com.google.android.youtube:id/watch_while",
                children = listOf(
                    FakeNode(
                        viewId = "com.google.android.youtube:id/player_control_play_pause_replay_button",
                        contentDescription = "pause"
                    )
                )
            )
        ))

    private fun feedSignals(audioActive: Boolean = false): YtPlaybackSignals =
        YouTubePlaybackGate.detect(homeFeedTree(), audioActive = audioActive, displayWidthPx = 1080)

    private fun playingSignals(): YtPlaybackSignals =
        YouTubePlaybackGate.detect(watchTree(), audioActive = true, displayWidthPx = 1080)

    // ------------------------------------------------------------------
    // 1. Feed scroll (entertainment titles) -> NO block, ever
    // ------------------------------------------------------------------

    @Test
    fun `home feed autoplay preview is observe-only, not playback`() {
        val s = feedSignals()
        assertEquals(YtPlayerSurface.INLINE_PREVIEW, s.surface)
        assertFalse(YouTubePlaybackGate.mayEnforce(s))
    }

    @Test
    fun `feed scroll over many events never enforces`() {
        val deps = FakeDeps(brain = blockingBrain) // entertainment title, would block if playing
        repeat(20) { i ->
            val s = feedSignals()
            assertFalse("feed event $i must never be enforceable", YouTubePlaybackGate.mayEnforce(s))
            val action = YouTubeGuardOrchestrator.handle(
                inputs(s, video("i survived 50 hours in a nuclear bunker"), deps))
            assertTrue("feed event $i must be Observe, was $action", action is YouTubeGuardOrchestrator.Action.Observe)
        }
    }

    @Test
    fun `feed stays non-enforceable even with device audio active`() {
        // Belt-and-braces: a muted preview takes no audio focus, but even if
        // audioActive were true, a browse surface must never enforce.
        val s = feedSignals(audioActive = true)
        assertFalse(YouTubePlaybackGate.mayEnforce(s))
    }

    // ------------------------------------------------------------------
    // 2. Playing study video -> NO block
    // ------------------------------------------------------------------

    @Test
    fun `playing study video is allowed`() {
        val s = playingSignals()
        assertEquals(YtPlayerSurface.WATCH_SCREEN, s.surface)
        assertTrue(s.playbackInProgress)
        assertTrue(YouTubePlaybackGate.mayEnforce(s))
        val action = YouTubeGuardOrchestrator.handle(
            inputs(s, video("calculus limits explained step by step"), FakeDeps(brain = studyBrain)))
        assertTrue("study video must be Allowed, was $action", action is YouTubeGuardOrchestrator.Action.Allowed)
    }

    // ------------------------------------------------------------------
    // 3. Playing entertainment video (above threshold, not doubt) -> BLOCK
    // ------------------------------------------------------------------

    @Test
    fun `playing entertainment video enforces`() {
        val s = playingSignals()
        val action = YouTubeGuardOrchestrator.handle(
            inputs(s, video("i survived 50 hours in a nuclear bunker"), FakeDeps(brain = blockingBrain)))
        assertTrue("entertainment video must Enforce, was $action", action is YouTubeGuardOrchestrator.Action.Enforce)
        val enforce = action as YouTubeGuardOrchestrator.Action.Enforce
        assertFalse(enforce.viaBlocklist)
    }

    @Test
    fun `playing miniplayer video enforces on the playing video`() {
        val s = YouTubePlaybackGate.detect(miniplayerTree(), audioActive = true, displayWidthPx = 1080)
        assertEquals(YtPlayerSurface.MINIPLAYER, s.surface)
        assertTrue(YouTubePlaybackGate.mayEnforce(s))
        val action = YouTubeGuardOrchestrator.handle(
            inputs(s, video("i survived 50 hours in a nuclear bunker"), FakeDeps(brain = blockingBrain)))
        assertTrue(action is YouTubeGuardOrchestrator.Action.Enforce)
    }

    // ------------------------------------------------------------------
    // 4. Doubt-gate behaviour preserved
    // ------------------------------------------------------------------

    @Test
    fun `doubt phrasing never blocks even if brain mislabels it BLOCK`() {
        val doubtBlock = YouTubeTitleJudge.BrainVerdict(YouTubeTitleJudge.BrainDecision.BLOCK, "doubt", 0.5)
        val result = YouTubeTitleJudge.judge("maybe this is a prank?", "mrbeast", null, FakeDeps(brain = doubtBlock))
        assertFalse("doubt-tagged BLOCK must be refused, was ${result.outcome}", result.outcome is YouTubeTitleJudge.Outcome.Block)
    }

    @Test
    fun `doubt title on player goes to arbiter, never enforce`() {
        val doubt = YouTubeTitleJudge.BrainVerdict(YouTubeTitleJudge.BrainDecision.UNCERTAIN, "doubt", null)
        val action = YouTubeGuardOrchestrator.handle(
            inputs(playingSignals(), video("is this even real?"), FakeDeps(brain = doubt)))
        assertTrue("doubt title must be AwaitGemini, was $action", action is YouTubeGuardOrchestrator.Action.AwaitGemini)
    }

    // ------------------------------------------------------------------
    // 5. Whitelist toggle OFF -> filter inert
    // ------------------------------------------------------------------

    @Test
    fun `filter toggle off keeps everything inert`() {
        val exploding = object : YouTubeTitleJudge.Deps {
            override fun isChannelBlocklisted(channel: String?) = throw AssertionError("judge must not run")
            override fun isChannelAllowlisted(channel: String?) = throw AssertionError("judge must not run")
            override fun judgeBrain(title: String, channel: String?) = throw AssertionError("judge must not run")
            override fun scoreKnowledge(title: String, channel: String?) = throw AssertionError("judge must not run")
            override fun quickEvaluate(title: String, channel: String?, metadata: String?) = throw AssertionError("judge must not run")
        }
        val action = YouTubeGuardOrchestrator.handle(
            inputs(playingSignals(), video("i survived 50 hours in a nuclear bunker"), exploding, filterEnabled = false))
        assertTrue(action is YouTubeGuardOrchestrator.Action.Inert)
    }

    // ------------------------------------------------------------------
    // Gate edge cases
    // ------------------------------------------------------------------

    @Test
    fun `paused video is not playback in progress`() {
        val s = YouTubePlaybackGate.detect(watchTree(pauseDesc = "play"), audioActive = false, displayWidthPx = 1080)
        assertEquals(YtPlayerSurface.WATCH_SCREEN, s.surface)
        assertFalse(s.playbackInProgress)
        assertFalse(YouTubePlaybackGate.mayEnforce(s))
        val action = YouTubeGuardOrchestrator.handle(
            inputs(s, video("i survived 50 hours in a nuclear bunker"), FakeDeps(brain = blockingBrain)))
        assertTrue("paused video must only be Observed, was $action", action is YouTubeGuardOrchestrator.Action.Observe)
    }

    @Test
    fun `channel blocklist also needs playback`() {
        val deps = FakeDeps(blocklisted = true)
        val feedAction = YouTubeGuardOrchestrator.handle(
            inputs(feedSignals(), video("any title"), deps))
        assertTrue("blocklisted channel on feed must only be Observed, was $feedAction",
            feedAction is YouTubeGuardOrchestrator.Action.Observe)
        val playAction = YouTubeGuardOrchestrator.handle(
            inputs(playingSignals(), video("any title"), deps))
        assertTrue(playAction is YouTubeGuardOrchestrator.Action.Enforce)
        assertTrue((playAction as YouTubeGuardOrchestrator.Action.Enforce).viaBlocklist)
    }

    @Test
    fun `no title on screen fails open`() {
        val action = YouTubeGuardOrchestrator.handle(
            inputs(playingSignals(), null, FakeDeps(brain = blockingBrain)))
        assertTrue(action is YouTubeGuardOrchestrator.Action.NoVideo)
    }

    @Test
    fun `shorts shelf ids never prove the shorts player`() {
        // The home feed's Shorts shelf exists on browse surfaces; the gate
        // must not mistake it for the Shorts player.
        val s = feedSignals()
        assertFalse(s.surface == YtPlayerSurface.SHORTS_PLAYER)
    }
}
