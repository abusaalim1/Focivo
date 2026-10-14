package com.example.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1: owner-review screenshots for the home-screen widgets, now using the
 * owner-supplied aesthetic pack layouts (widget_focus_timer / widget_streak /
 * widget_timetable / widget_focus_shield / widget_zen_coach) with the pack's
 * IDs (tv_title, tv_sub, tv_badge, tv_eyebrow, tv_quote, pb_goal, ...).
 * Inflates the REAL widget layouts at 4x2 widget size with realistic sample
 * data (same fields the widget providers bind) and captures them for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class WidgetScreenshotsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // MeasureSpec-free: avoids a compile quirk in the unit-test source set.
    // Encoding is the stable Android API: EXACTLY = 1 shl 30, spec = size | mode.
    private fun exactSpec(size: Int): Int = size or (1 shl 30)

    private fun inflate(layout: Int, width: Int = 1000, height: Int = 520): View {
        val view = LayoutInflater.from(context).inflate(layout, null)
        view.measure(exactSpec(width), exactSpec(height))
        view.layout(0, 0, width, height)
        return view
    }

    private fun View.tv(id: Int, text: String): View {
        findViewById<TextView>(id).text = text
        return this
    }

    private fun capture(view: View, name: String) {
        // View.captureRoboImage needs an Activity context; render to a bitmap instead.
        val bitmap = Bitmap.createBitmap(
            view.measuredWidth.coerceAtLeast(1),
            view.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        view.draw(Canvas(bitmap))
        bitmap.captureRoboImage("src/test/screenshots/round5/$name.png")
    }

    @Test
    fun widget_focus_timer() {
        val v = inflate(R.layout.widget_focus_timer)
            .tv(R.id.tv_eyebrow, "FOCUS TIMER")
            .tv(R.id.tv_badge, "READY")
            .tv(R.id.tv_title, "25:00")
            .tv(R.id.tv_sub, "Deep work session")
            .tv(R.id.btn_25, "25")
            .tv(R.id.btn_50, "50")
            .tv(R.id.btn_primary, "START")
        capture(v, "widget_timer")
    }

    @Test
    fun widget_streak_motivation() {
        val v = inflate(R.layout.widget_streak)
            .tv(R.id.tv_eyebrow, "DAILY STREAK")
            .tv(R.id.tv_badge, "ON FIRE")
            .tv(R.id.tv_title, "12 day streak")
            .tv(R.id.tv_sub, "Level 3 Student · 1,240 XP")
            .tv(R.id.tv_quote, "Every page studied today is a step towards your dream.")
            .tv(R.id.tv_goal, "120 / 360 min")
        v.findViewById<ProgressBar>(R.id.pb_goal).progress = 33
        capture(v, "widget_streak")
    }

    @Test
    fun widget_today_schedule() {
        val v = inflate(R.layout.widget_timetable)
            .tv(R.id.tv_eyebrow, "TODAY'S SCHEDULE")
            .tv(R.id.tv_badge, "WED, OCT 14")
            .tv(R.id.tv_title, "Physics numericals")
            .tv(R.id.tv_sub, "Next target · 50 min · 4 tasks remaining")
            .tv(R.id.btn_primary, "PLANNER")
            .tv(R.id.btn_secondary, "VIEW")
        capture(v, "widget_schedule")
    }

    @Test
    fun widget_focus_shield() {
        val v = inflate(R.layout.widget_focus_shield)
            .tv(R.id.tv_eyebrow, "FOCUS SHIELD")
            .tv(R.id.tv_badge, "ARMED")
            .tv(R.id.tv_title, "8 apps blocked")
            .tv(R.id.tv_sub, "Shorts & Reels auto-intercept is on")
            .tv(R.id.btn_primary, "SHIELD HUB")
            .tv(R.id.btn_secondary, "PAUSE")
        capture(v, "widget_shield")
    }

    @Test
    fun widget_zen_coach() {
        val v = inflate(R.layout.widget_zen_coach)
            .tv(R.id.tv_eyebrow, "ZEN COACH")
            .tv(R.id.tv_badge, "ZEN")
            .tv(R.id.tv_title, "Level 3 · 1,240 XP")
            .tv(R.id.tv_sub, "Clear your desk, take a sip of water, and conquer your next 25-minute target.")
            .tv(R.id.btn_primary, "BREATHE")
            .tv(R.id.btn_secondary, "TIPS")
        capture(v, "widget_zen")
    }
}
