package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.UserPreferencesEntity
import com.example.ui.theme.AppleLinearFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme

/**
 * SignOutConfirmationDialog
 *
 * Clean confirmation dialog that prompts the user before logging out,
 * ensuring all authentic data is saved to Supabase in real-time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOutConfirmationDialog(
    userPreferences: UserPreferencesEntity? = null,
    sessionsCount: Int = 0,
    isSyncing: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmSignOut: () -> Unit
) {
    val T = focivoTokens()
    val isDark = T.isDark

    BasicAlertDialog(
        onDismissRequest = {
            if (!isSyncing) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isSyncing,
            dismissOnClickOutside = !isSyncing
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(26.dp),
                    spotColor = if (isDark) Color(0x50000000) else Color(0x30000000)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(T.surface)
                .border(
                    1.dp,
                    T.line,
                    RoundedCornerShape(26.dp)
                )
                .padding(24.dp)
                .testTag("signout_confirmation_dialog")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(T.danger.copy(alpha = 0.14f))
                        .border(1.dp, T.danger.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSyncing) Icons.Default.CloudUpload else Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = T.danger,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSyncing) "Logging Out..." else "Confirm Log Out",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppleLinearFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = T.text,
                        letterSpacing = (-0.2).sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSyncing) {
                        "Saving your study data to the cloud..."
                    } else {
                        "Are you sure you want to log out of your account?"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = AppleLinearFontFamily,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        color = T.muted
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Area
                if (isSyncing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = T.danger,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Syncing & logging out...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = AppleLinearFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = T.muted,
                                fontSize = 13.5.sp
                            )
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel button
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(T.surface2)
                                .testTag("signout_cancel_button")
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = AppleLinearFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = T.muted
                                )
                            )
                        }

                        // Confirm Sign Out Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(T.danger)
                                .pressFeedback()
                                .testTag("signout_confirm_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = onConfirmSignOut,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Log Out",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = AppleLinearFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
