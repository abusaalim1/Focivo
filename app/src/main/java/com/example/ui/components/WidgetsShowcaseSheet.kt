package com.example.ui.components

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
        containerColor = Color(0xFF0F172A),
        scrimColor = Color(0x99000000),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(RegainLimePrimary.copy(alpha = 0.25f), Color(0x2000F0FF))
                                )
                            )
                            .border(1.dp, RegainLimePrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = RegainLimePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Apple-Style Home Widgets",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Frosted glassmorphism & Poppins typography",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Study Timer Widget
                item {
                    WidgetItemPreviewCard(
                        title = "Quick Study Timer",
                        description = "25m / 50m 1-tap sessions with live countdown and mascot state",
                        badge = "POPULAR",
                        badgeColor = RegainLimePrimary,
                        icon = Icons.Default.Timer,
                        mascotRes = R.drawable.mascot_studying,
                        accentGradient = listOf(Color(0xFF262D3D), Color(0xFF141A29)),
                        borderColor = RegainLimePrimary.copy(alpha = 0.45f),
                        onPin = { pinWidget(context, StudyTimerWidgetProvider::class.java) }
                    )
                }

                // 2. Streak & Motivation Widget
                item {
                    WidgetItemPreviewCard(
                        title = "Streak & Motivational Flame",
                        description = "Current streak count, daily study goal progress bar, and student quotes",
                        badge = "STREAK",
                        badgeColor = Color(0xFFFF9500),
                        icon = Icons.Default.LocalFireDepartment,
                        mascotRes = R.drawable.mascot_celebration,
                        accentGradient = listOf(Color(0xFF382215), Color(0xFF201524)),
                        borderColor = Color(0xFFFF9500).copy(alpha = 0.45f),
                        onPin = { pinWidget(context, StreakMotivationWidgetProvider::class.java) }
                    )
                }

                // 3. Focus Shield Widget
                item {
                    WidgetItemPreviewCard(
                        title = "Focus Shield & Blocker",
                        description = "Armed state status, blocked app counts, and instant shield hub control",
                        badge = "SECURITY",
                        badgeColor = Color(0xFF10B981),
                        icon = Icons.Default.Security,
                        mascotRes = R.drawable.mascot_blocked,
                        accentGradient = listOf(Color(0xFF1E3A2E), Color(0xFF10251E)),
                        borderColor = Color(0xFF10B981).copy(alpha = 0.45f),
                        onPin = { pinWidget(context, FocusShieldWidgetProvider::class.java) }
                    )
                }

                // 4. Today's Timetable Widget
                item {
                    WidgetItemPreviewCard(
                        title = "Today's Study Timetable",
                        description = "Next upcoming study block target, pending tasks, and planner shortcut",
                        badge = "SCHEDULE",
                        badgeColor = Color(0xFF818CF8),
                        icon = Icons.Default.Schedule,
                        mascotRes = R.drawable.mascot_studying,
                        accentGradient = listOf(Color(0xFF222B48), Color(0xFF141A2E)),
                        borderColor = Color(0xFF818CF8).copy(alpha = 0.45f),
                        onPin = { pinWidget(context, TodayScheduleWidgetProvider::class.java) }
                    )
                }

                // 5. Zen Study Companion Widget
                item {
                    WidgetItemPreviewCard(
                        title = "AI Companion & Zen Break",
                        description = "Mindful study tips, XP points, and 5-minute guided breath breaks",
                        badge = "ZEN",
                        badgeColor = Color(0xFFA855F7),
                        icon = Icons.Default.SelfImprovement,
                        mascotRes = R.drawable.mascot_grateful,
                        accentGradient = listOf(Color(0xFF331E48), Color(0xFF1E1430)),
                        borderColor = Color(0xFFA855F7).copy(alpha = 0.45f),
                        onPin = { pinWidget(context, ZenStudyCoachWidgetProvider::class.java) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetItemPreviewCard(
    title: String,
    description: String,
    badge: String,
    badgeColor: Color,
    icon: ImageVector,
    mascotRes: Int,
    accentGradient: List<Color>,
    borderColor: Color,
    onPin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(accentGradient))
            .border(1.5.dp, borderColor, RoundedCornerShape(22.dp))
            .shadow(12.dp, RoundedCornerShape(22.dp), spotColor = badgeColor)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = mascotRes),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.18f))
                                .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }

                Button(
                    onClick = onPin,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = badgeColor,
                        contentColor = NearBlack
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = NearBlack
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NearBlack
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle Description in Glass Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )
            }
        }
    }
}

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
