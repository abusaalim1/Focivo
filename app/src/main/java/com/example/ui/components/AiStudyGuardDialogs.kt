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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ActiveAiBlock
import com.example.util.ActiveAiResolved
import com.example.util.ActiveAiWarning
import kotlinx.coroutines.delay

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

    val dialogBg = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val amberWarning = if (androidx.compose.material3.MaterialTheme.colorScheme.background != com.example.ui.theme.RegainBgTop) Color(0xFFFFB74D) else Color(0xFFD97706)
    val textPrimary = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val textSecondary = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(amberWarning.copy(alpha = 0.18f))
                        .border(1.5.dp, amberWarning, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = amberWarning,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Study Discipline Warning",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = warning.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = amberWarning
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "This looks like non-study usage. This is your official warning:",
                    fontSize = 14.sp,
                    color = textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Reason: ${warning.reason}",
                        fontSize = 12.sp,
                        color = textPrimary,
                        lineHeight = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "⚠️ Continuing casual or non-study activity will trigger an immediate 3+ HOUR LOCKOUT on ${warning.appName}.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFCA5A5),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Grace Countdown Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(amberWarning.copy(alpha = 0.2f))
                        .border(1.dp, amberWarning.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Grace Window Remaining: $timeFormatted",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = amberWarning
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onReturnToStudy,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I'll Return to Studying", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss", color = textSecondary)
            }
        }
    )
}

@Composable
fun AiStudyBlockedDialog(
    block: ActiveAiBlock,
    onDismiss: () -> Unit
) {
    val dialogBg = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val accentRed = if (androidx.compose.material3.MaterialTheme.colorScheme.background != com.example.ui.theme.RegainBgTop) Color(0xFFFF5252) else Color(0xFFD32F2F)
    val textPrimary = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val textSecondary = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accentRed.copy(alpha = 0.18f))
                        .border(1.5.dp, accentRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = accentRed,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "App Blocked for 3 Hours",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentRed,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = block.appName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Non-study activity continued after the warning was issued.",
                    fontSize = 14.sp,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = block.reason,
                    fontSize = 12.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF33161A))
                        .border(1.dp, accentRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔒 Block Active: 180 Minutes (3 Hours)\nFocus shield will intercept any attempt to open this app.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFCA5A5),
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = accentRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Understood", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}

@Composable
fun AiStudyResolvedDialog(
    resolved: ActiveAiResolved,
    onDismiss: () -> Unit
) {
    val dialogBg = androidx.compose.material3.MaterialTheme.colorScheme.surface
    val accentGreen = if (androidx.compose.material3.MaterialTheme.colorScheme.background != com.example.ui.theme.RegainBgTop) Color(0xFF69F0AE) else Color(0xFF2E7D32)
    val textPrimary = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val textSecondary = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogBg,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accentGreen.copy(alpha = 0.18f))
                        .border(1.5.dp, accentGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Resolved",
                        tint = accentGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Good — No Block Applied!",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentGreen,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = resolved.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary
                )
            }
        },
        text = {
            Text(
                text = "You corrected course and returned to study focus in time. No restriction was placed on ${resolved.appName}. Great discipline!",
                fontSize = 14.sp,
                color = textPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = accentGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Keep Studying 🎯", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    )
}
