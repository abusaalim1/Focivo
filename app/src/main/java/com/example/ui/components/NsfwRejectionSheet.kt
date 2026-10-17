package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoEyebrow
import com.example.ui.theme.focivoTokens

/**
 * Premium design-system bottom sheet shown when [com.example.util.NsfwScreenGate]
 * rejects a profile or group picture (Focivo 1.3.2, item 6).
 *
 * Design language: focivoTokens (dark #0A0A0B / lime #B6F13A), Poppins body,
 * Instrument Serif italic headline. English only, zero emojis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NsfwRejectionSheet(
    onDismiss: () -> Unit,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = focivoTokens()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokens.line)
            )
        },
        modifier = modifier.testTag("nsfw_rejection_sheet")
    ) {
        NsfwRejectionSheetContent(
            onDismiss = onDismiss,
            onChooseAnother = onChooseAnother
        )
    }
}

/**
 * The sheet body on its own — used by [NsfwRejectionSheet] and by the
 * Roborazzi screenshot test (test sources only).
 */
@Composable
fun NsfwRejectionSheetContent(
    onDismiss: () -> Unit,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = focivoTokens()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Shield icon tile — same language as the Focus Shield surfaces.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.softAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = tokens.accent,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PICTURE CHECK",
            style = focivoEyebrow(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This picture can't be used",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = SerifItalicFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                color = tokens.text
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "To keep Focivo safe for every student, profile and group " +
                "pictures need to stay appropriate. Please choose a different picture.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = tokens.muted
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = onChooseAnother,
            colors = ButtonDefaults.buttonColors(
                containerColor = tokens.accent,
                contentColor = tokens.onAccent
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("nsfw_choose_another")
        ) {
            Text(
                text = "Choose another picture",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("nsfw_dismiss")
        ) {
            Text(
                text = "Not now",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = tokens.muted
                )
            )
        }
    }
}
