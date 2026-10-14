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
 * v1.4.3-widgets: owner-review screenshots for the redesigned home-screen widgets.
 * Inflates the REAL widget layouts at 4x2 widget size with realistic sample
 * data (same fields FocivoWidgetHelper binds) and captures them for review.
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
    fun widget_study_timer() {
        val v = inflate(R.layout.widget_study_timer)
            .tv(R.id.widget_timer_clock, "18:42")
            .tv(R.id.widget_timer_task_title, "Deep Work Session")
            .tv(R.id.widget_timer_status_badge, "FOCUSING")
            .tv(R.id.widget_timer_subtitle, "Deep focus session in progress · Stay on track")
        capture(v, "widget_timer")
    }

    @Test
    fun widget_streak_motivation() {
        val v = inflate(R.layout.widget_streak_motivation)
            .tv(R.id.widget_streak_title, "12 day streak")
            .tv(R.id.widget_streak_level, "Level 3 Student · 1,240 XP")
            .tv(R.id.widget_streak_quote, "Every page studied today is a step towards your dream.")
            .tv(R.id.widget_streak_goal_text, "120 / 360 min")
        v.findViewById<ProgressBar>(R.id.widget_streak_progress_bar).progress = 33
        capture(v, "widget_streak")
    }

    @Test
    fun widget_today_schedule() {
        val v = inflate(R.layout.widget_today_schedule)
            .tv(R.id.widget_schedule_date, "Wed, Oct 14")
            .tv(R.id.widget_schedule_next_time, "NEXT TARGET · 50 MIN · DEEP WORK")
            .tv(R.id.widget_schedule_next_title, "Physics numericals")
            .tv(R.id.widget_schedule_pending_count, "4 tasks remaining for today")
        capture(v, "widget_schedule")
    }

    @Test
    fun widget_focus_shield() {
        val v = inflate(R.layout.widget_focus_shield)
            .tv(R.id.widget_shield_status_badge, "ARMED")
            .tv(R.id.widget_shield_blocked_count, "8 apps blocked")
            .tv(R.id.widget_shield_subtext, "Shorts and Reels auto-intercept active")
            .tv(R.id.widget_btn_toggle_shield, "Armed")
        capture(v, "widget_shield")
    }

    @Test
    fun widget_zen_coach() {
        val v = inflate(R.layout.widget_zen_coach)
            .tv(R.id.widget_zen_user_points, "Level 3 · 1,240 XP")
            .tv(R.id.widget_zen_tip_text, "Clear your desk, take a sip of water, and conquer your next 25-minute target.")
        capture(v, "widget_zen")
    }
}
