package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimePrimary
import com.example.util.AppUpdateInfo

private val OverlayCardBg = Color(0xF21D221C)
private val OverlayTitle = Color(0xFFF7F8F4)
private val OverlayBody = Color(0xFFB9C2B6)
private val OverlayButtonText = Color(0xFF061505)

@Composable
private fun OverlayShell(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(OverlayCardBg)
                .border(1.dp, RegainLimePrimary.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 24.dp, vertical = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
private fun OverlayMascot() {
    Image(
        painter = painterResource(id = R.drawable.mascot_study_happy),
        contentDescription = "Focivo mascot",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(120.dp)
    )
}

@Composable
private fun OverlayPrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RegainLimePrimary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                color = OverlayButtonText,
                fontSize = 15.sp
            )
        )
    }
}

@Composable
private fun OverlayGhostButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = OverlayBody,
                fontSize = 14.sp
            )
        )
    }
}

/**
 * v1.4.2: premium Home-screen update overlay. Shown when a newer versionCode
 * exists on the backend. "Later" dismisses per versionCode — the overlay only
 * reappears for a NEWER version.
 */
@Composable
fun HomeUpdateOverlay(
    updateInfo: AppUpdateInfo,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    OverlayShell(onDismiss = onLater) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            OverlayMascot()
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "A new update is available",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = OverlayTitle,
                    fontSize = 20.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "v${updateInfo.latestVersionName}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = RegainLimePrimary,
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = updateInfo.releaseNotes.take(160),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    color = OverlayBody,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 5
            )
            Spacer(modifier = Modifier.height(20.dp))
            OverlayPrimaryButton(text = "Update Now", onClick = onUpdateNow)
            Spacer(modifier = Modifier.height(4.dp))
            OverlayGhostButton(text = "Later", onClick = onLater)
        }
    }
}

/**
 * v1.4.2: backend notice overlay. Shown once per announcement text change
 * with a "Got It" acknowledgement.
 */
@Composable
fun HomeNoticeOverlay(
    announcementText: String,
    onGotIt: () -> Unit
) {
    OverlayShell(onDismiss = onGotIt) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            OverlayMascot()
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Heads up",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = OverlayTitle,
                    fontSize = 20.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = announcementText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    color = OverlayBody,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.width(24.dp))
                Box(modifier = Modifier.weight(1f)) {
                    OverlayPrimaryButton(text = "Got It", onClick = onGotIt)
                }
                Spacer(modifier = Modifier.width(24.dp))
            }
        }
    }
}
