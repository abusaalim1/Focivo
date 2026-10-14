package com.example.screenshots.v11

import android.content.Context
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * v1.1: renders the 5 pack widget layouts (actual XML, sample data) —
 * used for the widget picker previewImage assets AND the in-app page
 * verification. PNGs go to app/src/test/screenshots/v11/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class V11WidgetPreviewScreenshotsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun renderWidget(layoutRes: Int, fileName: String, bind: (android.view.View) -> Unit) {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        LayoutInflater.from(ctx).inflate(layoutRes, null, false).apply {
                            bind(this)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("app/src/test/screenshots/v11/$fileName")
    }

    @Test
    fun preview_focus_timer() {
        renderWidget(R.layout.widget_focus_timer, "widget_preview_focus_timer_raw.png") { v ->
            v.findViewById<TextView>(R.id.tv_title)?.text = "25:00"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Deep work session"
            v.findViewById<TextView>(R.id.tv_badge)?.text = "Ready"
        }
    }

    @Test
    fun preview_streak() {
        renderWidget(R.layout.widget_streak, "widget_preview_streak_raw.png") { v ->
            v.findViewById<TextView>(R.id.tv_title)?.text = "12 day streak"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Level 3 Student · 1,240 XP"
            v.findViewById<TextView>(R.id.tv_quote)?.text = "\u201CEvery page studied today is a step towards your dream.\u201D"
            v.findViewById<TextView>(R.id.tv_goal)?.text = "120 / 360 min"
            v.findViewById<ProgressBar>(R.id.pb_goal)?.progress = 33
        }
    }

    @Test
    fun preview_focus_shield() {
        renderWidget(R.layout.widget_focus_shield, "widget_preview_focus_shield_raw.png") { v ->
            v.findViewById<TextView>(R.id.tv_title)?.text = "8 apps blocked"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Shorts & Reels auto-intercept is on"
            v.findViewById<TextView>(R.id.tv_badge)?.text = "Armed"
        }
    }

    @Test
    fun preview_timetable() {
        renderWidget(R.layout.widget_timetable, "widget_preview_timetable_raw.png") { v ->
            v.findViewById<TextView>(R.id.tv_eyebrow)?.text = "Today · Wed, Oct 14"
            v.findViewById<TextView>(R.id.tv_title)?.text = "Physics numericals"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Next target · 50 min · 4 tasks remaining"
        }
    }

    @Test
    fun preview_zen_coach() {
        renderWidget(R.layout.widget_zen_coach, "widget_preview_zen_coach_raw.png") { v ->
            v.findViewById<TextView>(R.id.tv_title)?.text =
                "\u201CWhen your mind feels scattered, 5 minutes of box breathing restores clarity.\u201D"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Level 3 Student · 1,240 Focus XP"
        }
    }
}
