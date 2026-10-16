package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme

/**
 * v1.2.2 — Donation popup, now live.
 *
 * Owner spec: shows automatically after a session completes — right after a
 * new user's FIRST completed session, and at most once every 7 days for
 * everyone else (trigger lives in FocuslyViewModel's session-completion
 * path, never in a composable). "Donate" opens the owner's UPI ID
 * (aabu.x@fam) via an ACTION_VIEW chooser — the same proven intent pattern
 * as SupportLockZenSheet. "Maybe Later" dismisses and stamps the 7-day
 * timer (handled by the ViewModel's dismiss path).
 */
@Composable
fun DonationDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val T = focivoTokens()
    val isDark = T.isDark
    val textPrimary = T.text
    val textSecondary = T.muted
    val cardBg = T.surface
    val cardBorder = T.line

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

            // Buttons: Donate (primary, lime) + Maybe Later
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RegainLimePrimary)
                        .pressFeedback()
                        .clickable {
                            try {
                                val upiUri = "upi://pay?pa=aabu.x@fam&pn=Focivo&cu=INR"
                                val intent = Intent(Intent.ACTION_VIEW, upiUri.toUri())
                                val chooser = Intent.createChooser(intent, "Pay via UPI App")
                                context.startActivity(chooser)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "No UPI app found. UPI ID: aabu.x@fam",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
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
                Box(
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
        }
    }
}
