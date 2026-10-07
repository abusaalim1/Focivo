package com.example.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.util.AppUpdateInfo

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDownloadClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val darkSurface = Color(0xFF0F1510)
    val cardBg = Color(0xFF182218)
    val limeAccent = RegainLimePrimary
    val textPrimary = Color(0xFFF2F7EE)
    val textSecondary = Color(0xFFA5B2A2)
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = {
            if (!updateInfo.isForceUpdate) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.isForceUpdate,
            dismissOnClickOutside = !updateInfo.isForceUpdate,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            limeAccent.copy(alpha = 0.5f),
                            Color(0x20FFFFFF),
                            Color(0x08FFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp), spotColor = limeAccent),
            color = darkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .testTag("app_update_dialog"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(limeAccent.copy(alpha = 0.18f))
                        .border(1.5.dp, limeAccent.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = "New Update",
                        tint = limeAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pill Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(limeAccent.copy(alpha = 0.14f))
                        .border(1.dp, limeAccent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = limeAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (updateInfo.isForceUpdate) "CRITICAL UPDATE REQUIRED" else "NEW UPDATE AVAILABLE",
                            color = limeAccent,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Update Focivo",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Version comparison strip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardBg)
                        .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "v${updateInfo.currentVersionName}",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = PoppinsFontFamily
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = limeAccent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "v${updateInfo.latestVersionName}",
                        color = limeAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PoppinsFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Release notes box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = "WHAT'S NEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = limeAccent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textSecondary,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Download / Update button
                Button(
                    onClick = {
                        onDownloadClick(updateInfo.downloadUrl)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("update_now_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = limeAccent,
                        contentColor = NearBlack
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = NearBlack
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download & Install Update",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = PoppinsFontFamily
                        )
                    }
                }

                if (!updateInfo.isForceUpdate) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary dismiss button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("remind_me_later_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textSecondary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0x28FFFFFF), Color(0x18FFFFFF))
                            )
                        )
                    ) {
                        Text(
                            text = "Remind Me Later",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = PoppinsFontFamily
                        )
                    }
                }
            }
        }
    }
}
