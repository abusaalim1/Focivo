package com.example.ui.components

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.widget.FocusShieldWidgetProvider
import com.example.widget.StreakMotivationWidgetProvider
import com.example.widget.StudyTimerWidgetProvider
import com.example.widget.TodayScheduleWidgetProvider
import com.example.widget.ZenStudyCoachWidgetProvider

/**
 * v1.1 — premium Widgets gallery.
 *
 * Sells the 5 home-screen widgets with faithful Compose replicas of the
 * real v143 RemoteViews layouts (the real RemoteViews can't be snapshotted
 * in Roborazzi, so each preview below mirrors its widget_*.xml layout:
 * same header/icon-tile/caption structure, same copy, same actions).
 * Each card has a benefit line + "Add to Home" (existing pin flow —
 * AppWidgetManager.requestPinAppWidget). Home-screen widget behavior is
 * untouched.
 */
private val GalleryLime = Color(0xFF8FD400)
private val GalleryCardBg = Color(0xFF151923)
private val GalleryCardBorder = Color(0x22FFFFFF)
private val GalleryTextPrimary = Color(0xFFF1F5F9)
private val GalleryTextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsShowcaseSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F1117),
        scrimColor = Color(0x99000000),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with mascot touch
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
                                color = GalleryTextMuted,
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp)
            ) {
                item {
                    WidgetGalleryCard(
                        title = "Focus Timer",
                        benefit = "Start a 25 or 50 minute session in one tap — without opening the app.",
                        onPin = { pinWidget(context, StudyTimerWidgetProvider::class.java) }
                    ) {
                        TimerWidgetReplica()
                    }
                }
                item {
                    WidgetGalleryCard(
                        title = "Daily Streak & Goal",
                        benefit = "Your streak flame and daily goal progress keep you accountable at a glance.",
                        onPin = { pinWidget(context, StreakMotivationWidgetProvider::class.java) }
                    ) {
                        StreakWidgetReplica()
                    }
                }
                item {
                    WidgetGalleryCard(
                        title = "Focus Shield",
                        benefit = "See shield status and blocked apps instantly — arm protection from the widget.",
                        onPin = { pinWidget(context, FocusShieldWidgetProvider::class.java) }
                    ) {
                        ShieldWidgetReplica()
                    }
                }
                item {
                    WidgetGalleryCard(
                        title = "Today's Schedule",
                        benefit = "Your next study target and remaining tasks, always one glance away.",
                        onPin = { pinWidget(context, TodayScheduleWidgetProvider::class.java) }
                    ) {
                        ScheduleWidgetReplica()
                    }
                }
                item {
                    WidgetGalleryCard(
                        title = "Zen Coach",
                        benefit = "Mindful tips, XP and 5-minute breath breaks from your AI study companion.",
                        onPin = { pinWidget(context, ZenStudyCoachWidgetProvider::class.java) }
                    ) {
                        ZenWidgetReplica()
                    }
                }
                item {
                    // How-to
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1A2030))
                            .border(1.dp, GalleryCardBorder, RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "HOW TO ADD",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                            color = GalleryLime
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            "Tap \"Add to Home\" on any widget below.",
                            "Confirm in the system dialog that appears.",
                            "Drag it where you want it on your home screen."
                        ).forEachIndexed { i, step ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(GalleryLime.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${i + 1}",
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = GalleryLime
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = step,
                                    fontFamily = PoppinsFontFamily,
                                    fontSize = 12.5.sp,
                                    color = GalleryTextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "On older Android versions, long-press your home screen, choose Widgets, then find Focivo.",
                            fontFamily = PoppinsFontFamily,
                            fontSize = 11.sp,
                            color = GalleryTextMuted,
                            lineHeight = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun WidgetGalleryCard(
    title: String,
    benefit: String,
    onPin: () -> Unit,
    preview: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(GalleryCardBg)
            .border(1.dp, GalleryCardBorder, RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = GalleryTextPrimary,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(GalleryLime)
                    .pressFeedback()
                    .clickable(onClick = onPin)
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = NearBlack,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add to Home",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = NearBlack
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = benefit,
            fontFamily = PoppinsFontFamily,
            fontSize = 12.sp,
            color = GalleryTextMuted,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Faithful widget replica (preview only — not interactive)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
        ) {
            preview()
        }
    }
}

// ---------------------------------------------------------------------------
// Faithful replicas of the v143 widget RemoteViews (widget_*.xml).
// Same structure, copy and actions as the real home-screen widgets.
// ---------------------------------------------------------------------------

@Composable
private fun WidgetReplicaHeader(
    iconRes: Int,
    caption: String,
    title: String,
    statusText: String,
    statusColor: Color = GalleryLime
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF232A3A))
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                .padding(7.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = caption,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = GalleryLime
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = GalleryTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(statusColor.copy(alpha = 0.14f))
                .border(0.8.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = statusText,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp,
                color = statusColor
            )
        }
    }
}

@Composable
private fun WidgetReplicaButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) GalleryLime else Color(0xFF232A3A))
            .border(
                1.dp,
                if (primary) GalleryLime else Color(0x14FFFFFF),
                RoundedCornerShape(10.dp)
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = if (primary) NearBlack else GalleryTextPrimary
        )
    }
}

@Composable
private fun TimerWidgetReplica() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10141D))
            .padding(14.dp)
    ) {
        WidgetReplicaHeader(
            iconRes = R.drawable.ic3d_timer,
            caption = "FOCUS TIMER",
            title = "Deep Work Session",
            statusText = "READY"
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A2030))
                .padding(12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "25:00",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    letterSpacing = 1.sp,
                    color = GalleryLime
                )
                Text(
                    text = "Tap to start a deep work session",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 10.sp,
                    color = GalleryTextMuted,
                    textAlign = TextAlign.Center
                )
            }
            Image(
                painter = painterResource(id = R.drawable.mascot_study_happy),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetReplicaButton(text = "25 min", primary = false, modifier = Modifier.weight(1f))
            WidgetReplicaButton(text = "50 min", primary = false, modifier = Modifier.weight(1f))
            WidgetReplicaButton(text = "Start", primary = true, modifier = Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun StreakWidgetReplica() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10141D))
            .padding(14.dp)
    ) {
        WidgetReplicaHeader(
            iconRes = R.drawable.emoji3d_flame,
            caption = "DAILY STREAK",
            title = "12 day streak",
            statusText = "ON FIRE",
            statusColor = Color(0xFFFF9F0A)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A2030))
                .padding(12.dp)
        ) {
            Text(
                text = "TODAY'S SPARK",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = Color(0xFFFF9F0A)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Every page studied today is a step towards your dream.",
                fontFamily = PoppinsFontFamily,
                fontSize = 11.5.sp,
                color = GalleryTextPrimary,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Today's study goal",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 10.5.sp,
                    color = GalleryTextMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "120 / 360 min",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = GalleryTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = 0.33f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFFF9F0A),
                trackColor = Color(0xFF2A2F3E)
            )
        }
    }
}

@Composable
private fun ShieldWidgetReplica() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10141D))
            .padding(14.dp)
    ) {
        WidgetReplicaHeader(
            iconRes = R.drawable.ic3d_shield,
            caption = "FOCUS SHIELD",
            title = "Distraction Blocker",
            statusText = "ARMED",
            statusColor = Color(0xFF30D158)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A2030))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "8 apps blocked",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = GalleryTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Shorts and reels auto-intercept active",
                fontFamily = PoppinsFontFamily,
                fontSize = 10.5.sp,
                color = GalleryTextMuted,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetReplicaButton(text = "Arm Shield", primary = true, modifier = Modifier.weight(1f))
            WidgetReplicaButton(text = "Shield Hub", primary = false, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ScheduleWidgetReplica() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10141D))
            .padding(14.dp)
    ) {
        WidgetReplicaHeader(
            iconRes = R.drawable.ic3d_calendar,
            caption = "TODAY'S TIMETABLE",
            title = "Wed, Oct 14",
            statusText = "PLANNER",
            statusColor = Color(0xFFBF5AF2)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A2030))
                .padding(12.dp)
        ) {
            Text(
                text = "NEXT TARGET · 50 MIN",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = Color(0xFFBF5AF2)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Physics numericals",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = GalleryTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "4 tasks remaining for today",
                fontFamily = PoppinsFontFamily,
                fontSize = 10.5.sp,
                color = GalleryTextMuted
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetReplicaButton(text = "Planner", primary = false, modifier = Modifier.weight(1f))
            WidgetReplicaButton(text = "Start Now", primary = true, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ZenWidgetReplica() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF10141D))
            .padding(14.dp)
    ) {
        WidgetReplicaHeader(
            iconRes = R.drawable.mascot_grateful,
            caption = "ZEN COACH",
            title = "Level 3 · 1,240 XP",
            statusText = "MINDFUL",
            statusColor = Color(0xFF64D2FF)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A2030))
                .padding(12.dp)
        ) {
            Text(
                text = "TODAY'S TIP",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = Color(0xFF64D2FF)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Clear your desk, take a sip of water, and conquer your next 25-minute target.",
                fontFamily = PoppinsFontFamily,
                fontSize = 11.5.sp,
                color = GalleryTextPrimary,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetReplicaButton(text = "5m Zen Break", primary = false, modifier = Modifier.weight(1f))
            WidgetReplicaButton(text = "AI Coach", primary = true, modifier = Modifier.weight(1f))
        }
    }
}

/** Existing add-to-home flow — AppWidgetManager pin request (unchanged). */
private fun pinWidget(context: Context, providerClass: Class<*>) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val myProvider = ComponentName(context, providerClass)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val successCallback: PendingIntent? = null
            appWidgetManager.requestPinAppWidget(myProvider, null, successCallback)
            Toast.makeText(context, "Adding widget to home screen...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Long-press your home screen to place this widget", Toast.LENGTH_LONG).show()
        }
    } else {
        Toast.makeText(context, "Long-press your home screen to place this widget", Toast.LENGTH_LONG).show()
    }
}
