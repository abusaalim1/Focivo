package com.example.ui.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme

/**
 * v1.1 — Trust explainer card shown BEFORE the system prompt for sensitive
 * permissions (Accessibility, Device Admin).
 *
 * Honest plain English: what the permission CAN do, plus the reassurance
 * that Focivo does not collect or send personal data anywhere.
 */
enum class SensitivePermission {
    ACCESSIBILITY,
    DEVICE_ADMIN
}

@Composable
fun TrustExplainerDialog(
    permission: SensitivePermission,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else Color(0xFF5A635A)
    val cardBg = if (isDark) Color(0xFF1A2030) else Color.White
    val cardBorder = if (isDark) Color(0x22FFFFFF) else Color(0xFFE2E8E0)

    val (title, whatItDoes) = when (permission) {
        SensitivePermission.ACCESSIBILITY -> Pair(
            "Accessibility Access",
            "This lets Focivo read screen content to detect non-study apps and content during your focus sessions, so the AI Study Guard can keep you on track."
        )
        SensitivePermission.DEVICE_ADMIN -> Pair(
            "Anti-Uninstall Shield",
            "This lets Focivo block uninstall while the shield is on, so a moment of weakness can't undo your focus. You can turn it off anytime in Settings to uninstall."
        )
    }

    Dialog(
        onDismissRequest = onNotNow,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                .padding(24.dp)
                .testTag("trust_explainer_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Before you continue",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = RegainLimePrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = whatItDoes,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Focivo does not collect or send your personal data anywhere. Everything stays on your phone.",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .pressFeedback()
                        .clickable(onClick = onNotNow)
                        .padding(vertical = 14.dp)
                        .testTag("trust_explainer_not_now"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Not now",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(RegainLimePrimary)
                        .pressFeedback()
                        .clickable(onClick = onContinue)
                        .padding(vertical = 14.dp)
                        .testTag("trust_explainer_continue"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Continue",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NearBlack
                    )
                }
            }
        }
    }
}
