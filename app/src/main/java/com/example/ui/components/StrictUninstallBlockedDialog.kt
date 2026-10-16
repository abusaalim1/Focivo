package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme

/**
 * v1.2.2 — in-app popup shown when an uninstall is attempted while strict
 * mode is active (owner spec). The host Activity shows this when it receives
 * [com.example.util.StrictUninstallGuard.ACTION_STRICT_UNINSTALL_BLOCKED].
 *
 * SUPERSEDED (v1.2.3, owner 2026-10-16): production now shows
 * [StrictUninstallBlockedOverlay] instead. This dialog is kept only so the
 * v122 historical screenshot test still compiles; do not wire it into new
 * flows.
 *
 * Wiring (for the host — MainActivity owns its composition):
 * ```
 * var showStrictUninstallBlocked by remember { mutableStateOf(false) }
 * // in handleActivityIntent / onNewIntent:
 * if (intent?.action == StrictUninstallGuard.ACTION_STRICT_UNINSTALL_BLOCKED)
 *     showStrictUninstallBlocked = true
 * // in composition:
 * if (showStrictUninstallBlocked) {
 *     StrictUninstallBlockedDialog(onDismiss = { showStrictUninstallBlocked = false })
 * }
 * ```
 * And from the uninstaller-interception path (accessibility), call
 * `StrictUninstallGuard.notifyStrictUninstallBlocked(context)` when strict
 * mode is active.
 */
@Composable
fun StrictUninstallBlockedDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isAppInDarkTheme()
    val T = focivoTokens()
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
                .testTag("strict_uninstall_blocked_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.mascot_blocked),
                contentDescription = null,
                modifier = Modifier.size(88.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Strict Mode is ON",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Focivo can't be uninstalled during an active focus lock. Turn off Strict Mode in Settings after your session to uninstall.",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RegainLimePrimary)
                    .pressFeedback()
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp)
                    .testTag("strict_uninstall_blocked_ok"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Got it",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NearBlack
                )
            }
        }
    }
}
