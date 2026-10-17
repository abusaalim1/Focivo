package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens
import com.example.util.MascotPose

/**
 * v1.3.0 follow-up (owner order 2026-10-17, item 2) — premium design-system
 * sheets for the device-admin uninstall-protection layer (Regain parity).
 *
 * Standing rule: every popup is premium/design-system (tokens, Poppins +
 * serif-italic accents, lime accent, mascot, polished motion). The old plain
 * AlertDialog is gone.
 */

/** Eyebrow pill shared by both sheets. */
@Composable
private fun AdminEyebrow(text: String) {
    val T = focivoTokens()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(RegainLimePrimary.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RegainLimePrimary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = focivoEyebrow())
    }
}

/** One reassurance line: lime dot + text. */
@Composable
private fun AdminPoint(text: String) {
    val T = focivoTokens()
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(RegainLimePrimary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = T.muted
        )
    }
}

/**
 * The premium explainer shown when the user enables Strict Mode without the
 * device admin active. Honest copy: what the permission does, what it never
 * does, and that skipping keeps Strict Mode working (uninstall protection
 * simply shows OFF).
 *
 * onEnable -> launch the SYSTEM activation screen.
 * onOptOut  -> strict stays on, protection is marked off honestly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAdminExplainerSheet(
    onEnable: () -> Unit,
    onOptOut: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = T.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier.testTag("strict_admin_explainer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetDragHandle()
            Spacer(modifier = Modifier.height(14.dp))
            AdminEyebrow("STRICT MODE")
            Spacer(modifier = Modifier.height(14.dp))

            BuddyMascot(
                modifier = Modifier.size(104.dp),
                pose = MascotPose.STUDY,
                idleAnimation = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "One permission keeps your promise.",
                fontFamily = SerifItalicFontFamily,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                color = T.text
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Turn on device admin and Android itself will block " +
                    "Focivo's uninstall while a strict study session runs — " +
                    "so a weak moment can't undo your focus.",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                color = T.muted
            )
            Spacer(modifier = Modifier.height(8.dp))

            AdminPoint("Only blocks uninstall during strict sessions — nothing else changes.")
            AdminPoint("We never lock your screen, wipe data, or read anything.")
            AdminPoint("You can remove it anytime from Settings — Focivo can never trap you.")

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onEnable,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RegainLimePrimary,
                    contentColor = RegainLimeDeepText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("strict_admin_enable_cta")
            ) {
                Text(
                    text = "Protect my focus",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            TextButton(
                onClick = onOptOut,
                modifier = Modifier.testTag("strict_admin_opt_out")
            ) {
                Text(
                    text = "Not now — keep Strict Mode without it",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = T.muted
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

/**
 * Shown from the "Uninstall protection" row while protection is PROTECTED.
 * The disable path is the SYSTEM settings screen — Android gives apps no API
 * to deactivate their own admin, and we say so plainly. That is the user's
 * guarantee against trapping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictAdminDisableSheet(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = T.surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier.testTag("strict_admin_disable_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetDragHandle()
            Spacer(modifier = Modifier.height(14.dp))
            AdminEyebrow("PROTECTION ACTIVE")
            Spacer(modifier = Modifier.height(14.dp))

            BuddyMascot(
                modifier = Modifier.size(104.dp),
                pose = MascotPose.STUDY,
                idleAnimation = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your focus is guarded.",
                fontFamily = SerifItalicFontFamily,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
                color = T.text
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Android is blocking Focivo's uninstall right now. " +
                    "To remove this, deactivate Focivo in your phone's " +
                    "device-admin list — Focivo can't do it itself, and " +
                    "that's your guarantee we can never trap you.",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.5.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                color = T.muted
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RegainLimePrimary,
                    contentColor = RegainLimeDeepText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("strict_admin_open_settings_cta")
            ) {
                Text(
                    text = "Open device admin settings",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Keep protection on",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = T.muted
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}
