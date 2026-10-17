package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.MascotPose
import kotlinx.coroutines.delay

/**
 * v1.2.3 (Task 10) — strict-mode FULL lock-in (owner order 2026-10-16,
 * Regain parity: "you cannot quit until the focus timer ends").
 *
 * When Strict Mode is ON and a session is running, tapping Stop no longer
 * offers an "end anyway" escape — the session cannot be stopped or restarted
 * until the timer finishes. This dialog explains why, with an aesthetic
 * word-by-word animated text treatment and a small cute BuddyMascot (the
 * equipped outfit, idle breathing) beside the headline.
 *
 * Replaces StrictEndSessionDialog (deleted): strict lock-in has no
 * early-exit path by design.
 *
 * v1.3.0 (owner order 2026-10-17): [mode] adds the PAUSE variant — Pause
 * joins Stop and Restart as forbidden under strict. Same premium animated
 * explanation (small mascot + animated words), pause-specific copy.
 */
enum class StrictLockMode { STOP, PAUSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictLockDialog(
    onDismiss: () -> Unit,
    mode: StrictLockMode = StrictLockMode.STOP
) {
    val isDark = isAppInDarkTheme()
    val T = focivoTokens()
    val textPrimary = T.text
    val textSecondary = T.muted

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(
            if (mode == StrictLockMode.PAUSE) "strict_pause_dialog" else "strict_lock_dialog"
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = T.surface
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BuddyMascot(
                        modifier = Modifier.size(84.dp),
                        pose = MascotPose.STUDY,
                        idleAnimation = true
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Locked in.",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = SerifItalicFontFamily,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            color = textPrimary,
                            fontSize = 26.sp,
                            lineHeight = 30.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                AnimatedWordsText(
                    text = if (mode == StrictLockMode.PAUSE) {
                        // v1.3.0: Pause is forbidden under strict — same
                        // first-person, warm-but-firm voice as the Stop copy.
                        "Strict Mode is on, so pausing is off too — just like Stop and Restart. " +
                            "The timer keeps running while you're away, and your locked apps stay locked. " +
                            "Take a breath and finish strong."
                    } else {
                        "You asked me to be strict with you, so I am being strict. " +
                            "This session cannot be stopped or restarted until the timer finishes, " +
                            "and your blocked apps stay locked the whole way. " +
                            "Take a breath. Keep going."
                    },
                    textColor = textSecondary
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegainLimePrimary,
                        contentColor = NearBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Keep focusing",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Aesthetic animated text treatment: words fade + rise in one by one
 * (staggered), design-system Poppins. transform-only per word — the dialog
 * never reflows once the words are in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimatedWordsText(
    text: String,
    textColor: androidx.compose.ui.graphics.Color
) {
    val words = remember(text) { text.split(" ") }
    val visibleCount = remember { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        visibleCount.intValue = 0
        words.forEachIndexed { index, _ ->
            delay(55L)
            visibleCount.intValue = index + 1
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        words.forEachIndexed { index, word ->
            AnimatedVisibility(
                visible = index < visibleCount.intValue,
                enter = fadeIn(animationSpec = tween(320)) +
                    slideInVertically(animationSpec = tween(320)) { it / 2 },
                exit = fadeOut()
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Normal,
                        color = textColor,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Start
                    )
                )
            }
        }
    }
}
