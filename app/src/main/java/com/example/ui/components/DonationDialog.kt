package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme

/**
 * v1.1 — Donation popup.
 *
 * Tasteful, professional dialog with the cute mascot. Shows at most once
 * every 3 days (triggered on app open or after a session completes, never
 * mid-timer). The Donate button opens the URL from remote config
 * (app_config key "donate_url"); if empty/unset, the Donate button is hidden.
 */
@Composable
fun DonationDialog(
    donateUrl: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else Color(0xFF5A635A)
    val cardBg = if (isDark) Color(0xFF1A2030) else Color.White
    val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8E0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                .padding(24.dp)
                .testTag("donation_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cute mascot
            Image(
                painter = painterResource(id = R.drawable.mascot_celebration),
                contentDescription = null,
                modifier = Modifier.size(88.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Help Focivo Grow",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Focivo is built with care by an independent developer. If this app helps you focus better, consider supporting its journey. Your contribution helps us build more powerful AI features \u2014 and bring Focivo to the Play Store. Every bit of support truly matters.",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Buttons
            if (!donateUrl.isNullOrBlank()) {
                // Donate (primary, lime) + Maybe Later
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RegainLimePrimary)
                            .pressFeedback()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, donateUrl.toUri())
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                                onDismiss()
                            }
                            .padding(vertical = 14.dp)
                            .testTag("donation_donate_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Donate",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NearBlack
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .pressFeedback()
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 12.dp)
                            .testTag("donation_later_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Maybe Later",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = textSecondary
                        )
                    }
                }
            } else {
                // No URL: only "Maybe Later" (never a dead button)
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RegainLimePrimary)
                        .pressFeedback()
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp)
                        .testTag("donation_later_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Maybe Later",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NearBlack
                    )
                }
            }
        }
    }
}
