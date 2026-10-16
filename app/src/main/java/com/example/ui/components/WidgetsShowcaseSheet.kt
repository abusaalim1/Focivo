package com.example.ui.components

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.PoppinsFontFamily
import com.example.widget.FocusShieldWidgetProvider
import com.example.widget.StudyTimerWidgetProvider
import com.example.widget.TodayScheduleWidgetProvider
import com.example.widget.ZenStudyCoachWidgetProvider

/**
 * v1.1 — Widgets page (rebuilt per owner fix).
 *
 * Shows the ACTUAL home-screen widgets — each pack layout
 * (widget_{focus_timer,focus_shield,timetable,zen_coach}) inflated
 * for real via AndroidView with sample-but-realistic data, pixel-consistent
 * with what the launcher renders. One full widget preview per card, with a
 * "+ Add" action BELOW each preview that does the REAL pin flow
 * (AppWidgetManager.requestPinAppWidget + graceful fallback).
 */
private val PageLime = Color(0xFF8FD400)

private data class WidgetEntry(
    val title: String,
    val layoutRes: Int,
    val providerClass: Class<*>,
    val sampleData: (android.view.View) -> Unit
)

private fun widgetEntries(): List<WidgetEntry> = listOf(
    WidgetEntry(
        title = "Focus Timer",
        layoutRes = R.layout.widget_focus_timer,
        providerClass = StudyTimerWidgetProvider::class.java,
        sampleData = { v ->
            v.findViewById<TextView>(R.id.tv_title)?.text = "25:00"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Deep work session"
            v.findViewById<TextView>(R.id.tv_badge)?.text = "Ready"
        }
    ),
    WidgetEntry(
        title = "Focus Shield",
        layoutRes = R.layout.widget_focus_shield,
        providerClass = FocusShieldWidgetProvider::class.java,
        sampleData = { v ->
            v.findViewById<TextView>(R.id.tv_title)?.text = "8 apps blocked"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Shorts & Reels auto-intercept is on"
            v.findViewById<TextView>(R.id.tv_badge)?.text = "Armed"
        }
    ),
    WidgetEntry(
        title = "Timetable",
        layoutRes = R.layout.widget_timetable,
        providerClass = TodayScheduleWidgetProvider::class.java,
        sampleData = { v ->
            v.findViewById<TextView>(R.id.tv_eyebrow)?.text = "Today · Wed, Oct 14"
            v.findViewById<TextView>(R.id.tv_title)?.text = "Physics numericals"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Next target · 50 min · 4 tasks remaining"
        }
    ),
    WidgetEntry(
        title = "Zen Coach",
        layoutRes = R.layout.widget_zen_coach,
        providerClass = ZenStudyCoachWidgetProvider::class.java,
        sampleData = { v ->
            v.findViewById<TextView>(R.id.tv_title)?.text =
                "\u201CWhen your mind feels scattered, 5 minutes of box breathing restores clarity.\u201D"
            v.findViewById<TextView>(R.id.tv_sub)?.text = "Level 3 Student · 1,240 Focus XP"
        }
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsShowcaseSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val entries = widgetEntries()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = focivoTokens().bg,
        scrimColor = Color(0x99000000),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header — premium language with mascot touch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.mascot_celebration),
                        contentDescription = null,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Home Screen Widgets",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Your focus setup, right on your home screen",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = focivoTokens().muted,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                entries.forEach { entry ->
                    item(key = entry.title) {
                        ActualWidgetCard(
                            entry = entry,
                            onAdd = { pinWidget(context, entry.providerClass, entry.title) }
                        )
                    }
                }
                item {
                    // How-to fallback (also shown when pinning isn't supported)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(focivoTokens().surface)
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "CAN'T PIN AUTOMATICALLY?",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                            color = focivoTokens().accentText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Long-press your home screen, choose Widgets, then find Focivo and drag the widget you want into place.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 12.5.sp,
                            color = focivoTokens().text,
                            lineHeight = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * One full ACTUAL widget preview (the real pack layout, inflated) + the
 * "+ Add" action below it.
 */
@Composable
private fun ActualWidgetCard(
    entry: WidgetEntry,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(focivoTokens().surface2)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Text(
            text = entry.title,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = focivoTokens().text,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        // THE actual widget — same XML the launcher inflates.
        AndroidView(
            factory = { ctx ->
                LayoutInflater.from(ctx).inflate(entry.layoutRes, null, false).apply {
                    entry.sampleData(this)
                    // Preview only: disable clicks inside the inflated widget.
                    isClickable = false
                    isFocusable = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(focivoTokens().accent)
                .pressFeedback()
                .clickable(onClick = onAdd)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = focivoTokens().onAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = focivoTokens().onAccent
                )
            }
        }
    }
}

/**
 * REAL pin flow: requestPinAppWidget where the launcher supports it,
 * graceful in-context fallback otherwise.
 */
private fun pinWidget(context: Context, providerClass: Class<*>, title: String) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val myProvider = ComponentName(context, providerClass)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val successCallback: PendingIntent? = null
            appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            Toast.makeText(context, "Adding $title to home screen...", Toast.LENGTH_SHORT).show()
            return
        }
    }
    // Fallback: short in-context instructions.
    Toast.makeText(
        context,
        "Long-press your home screen → Widgets → find Focivo → drag \"$title\" into place.",
        Toast.LENGTH_LONG
    ).show()
}
