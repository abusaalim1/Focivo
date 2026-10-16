package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.isAppInDarkTheme
import androidx.compose.ui.graphics.Color

/**
 * v1.3.1 — strict-mode end-early confirmation (Regain parity: "you cannot end
 * your session early"). Ending early discards the session (no record, no
 * points), so strict mode forces an explicit confirmation first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrictEndSessionDialog(
    onDismiss: () -> Unit,
    onConfirmEnd: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val T = focivoTokens()
    val textPrimary = T.text
    val textSecondary = T.muted

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("strict_end_session_dialog")
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = focivoTokens().surface
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = RegainLimePrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Strict mode is on",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 18.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Ending now discards this session — no record, no points, no streak. Are you sure?",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PoppinsFontFamily,
                        color = textSecondary,
                        fontSize = 14.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onConfirmEnd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RegainLimePrimary,
                        contentColor = NearBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("End session anyway", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismiss) {
                    Text("Keep focusing", color = textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
