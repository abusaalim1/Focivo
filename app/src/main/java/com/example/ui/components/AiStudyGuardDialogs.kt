package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.LinearDarkCardBg
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.NearBlack
import com.example.ui.theme.RegainLimePrimary
import com.example.util.ActiveAiBlock
import com.example.util.ActiveAiResolved
import com.example.util.ActiveAiWarning
import kotlinx.coroutines.delay

/**
 * v1.2.2 (G) — premium minimal redesign of the AI Study Guard dialogs.
 *
 * Dark card, lime accent, big clear headline, ONE short line of explanation,
 * ONE primary button + one quiet text button, generous spacing. Copy kept to
 * the essentials: app name, the warning fact, and the grace countdown as a
 * small chip when active. No behaviour changes — the countdown logic,
 * callbacks and state flow are untouched.
 */
@Composable
fun AiStudyWarningDialog(
    warning: ActiveAiWarning,
    onReturnToStudy: () -> Unit,
    onDismiss: () -> Unit
) {
    var remainingSeconds by remember(warning.graceExpiresAtMillis) {
        val diff = (warning.graceExpiresAtMillis - System.currentTimeMillis()) / 1000L
        mutableStateOf(if (diff > 0) diff.toInt() else 0)
    }

    LaunchedEffect(warning.graceExpiresAtMillis) {
        while (remainingSeconds > 0) {
            delay(1000L)
            val diff = (warning.graceExpiresAtMillis - System.currentTimeMillis()) / 1000L
            remainingSeconds = if (diff > 0) diff.toInt() else 0
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = String.format("%d:%02d", minutes, seconds)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(focivoTokens().surface)
                .border(1.dp, focivoTokens().line, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RegainMascotView(
                width = 88.dp,
                height = 112.dp,
                pose = MascotPose.GUARD
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Study warning",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = focivoTokens().text,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = warning.appName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = focivoTokens().accentText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Non-study activity detected.",
                fontSize = 14.sp,
                color = focivoTokens().muted,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            if (remainingSeconds > 0) {
                Spacer(modifier = Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(RegainLimePrimary.copy(alpha = 0.14f))
                        .border(1.dp, RegainLimePrimary.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "$timeFormatted left to get back on track",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = focivoTokens().accentText
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = onReturnToStudy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = focivoTokens().accent,
                    contentColor = focivoTokens().onAccent
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(50),
                        spotColor = focivoTokens().accent.copy(alpha = 0.35f)
                    ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Return to study",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NearBlack
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Dismiss",
                    color = focivoTokens().muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AiStudyBlockedDialog(
    block: ActiveAiBlock,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(focivoTokens().surface)
                .border(1.dp, focivoTokens().line, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RegainMascotView(
                width = 88.dp,
                height = 112.dp,
                pose = MascotPose.GUARD
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "App blocked",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = focivoTokens().text,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = block.appName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = RegainLimePrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Locked for 3 hours.",
                fontSize = 14.sp,
                color = focivoTokens().muted,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = focivoTokens().accent,
                    contentColor = focivoTokens().onAccent
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(50),
                        spotColor = focivoTokens().accent.copy(alpha = 0.35f)
                    ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Understood",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NearBlack
                )
            }
        }
    }
}

@Composable
fun AiStudyResolvedDialog(
    resolved: ActiveAiResolved,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(focivoTokens().surface)
                .border(1.dp, focivoTokens().line, RoundedCornerShape(28.dp))
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RegainMascotView(
                width = 88.dp,
                height = 112.dp,
                pose = MascotPose.CELEBRATING
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No block applied",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = focivoTokens().text,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = resolved.appName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = RegainLimePrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Back on track. Keep going.",
                fontSize = 14.sp,
                color = focivoTokens().muted,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = focivoTokens().accent,
                    contentColor = focivoTokens().onAccent
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(50),
                        spotColor = focivoTokens().accent.copy(alpha = 0.35f)
                    ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Keep studying",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NearBlack
                )
            }
        }
    }
}
