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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.ui.viewmodel.RecoveryCodeFlow

/** v1.3.3 (ITEM 10): which flow the OTP code sheet serves. */
enum class CodeSheetMode { RECOVERY, SIGNUP }

/**
 * v1.3.3 (ITEM 10): premium bottom sheet for the 6-digit email OTP code.
 * Used by BOTH password recovery and signup confirmation (copy differs by [mode]).
 *
 * UX: a single hidden [BasicTextField] drives six digit boxes - pasting a
 * code fills all boxes at once, typing auto-advances, reaching 6 digits
 * auto-submits. Digits clear + refocus on wrong/expired errors. English only,
 * FocivoTokens colors, Poppins body / Instrument Serif italic display.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryCodeSheet(
    email: String,
    mode: CodeSheetMode,
    codeState: RecoveryCodeFlow.CodeState,
    resendCooldownSeconds: Long,
    onSubmitCode: (String) -> Unit,
    onResend: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
) {
    val T = focivoTokens()
    var digits by remember { mutableStateOf("") }
    var submittedFor by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    val verifying = codeState is RecoveryCodeFlow.CodeState.Verifying

    fun submit(code: String) {
        if (code.length == 6 && submittedFor != code && !verifying) {
            submittedFor = code
            onSubmitCode(code)
        }
    }

    val errorText: String? = when (codeState) {
        is RecoveryCodeFlow.CodeState.WrongCode ->
            "That code isn't right \u2014 check and try again."
        is RecoveryCodeFlow.CodeState.ExpiredCode ->
            "This code has expired \u2014 tap Resend for a new one."
        is RecoveryCodeFlow.CodeState.Error -> codeState.message
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = T.bg,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(T.accent.copy(alpha = 0.5f))
            )
        },
        modifier = Modifier.testTag("recovery_code_sheet")
    ) {
        // Focus effects live inside the sheet content: the FocusRequester is
        // attached by the field below in THIS composition, so requesting
        // focus here can never hit "FocusRequester is not initialized".
        LaunchedEffect(codeState) {
            if (codeState is RecoveryCodeFlow.CodeState.WrongCode ||
                codeState is RecoveryCodeFlow.CodeState.ExpiredCode ||
                codeState is RecoveryCodeFlow.CodeState.Error
            ) {
                digits = ""
                submittedFor = null
                focusRequester.requestFocus()
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(T.accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Email,
                        contentDescription = null,
                        tint = T.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = T.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (mode == CodeSheetMode.SIGNUP) "Confirm your email" else "Check your email",
                fontFamily = SerifItalicFontFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 26.sp,
                color = T.text,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (mode == CodeSheetMode.SIGNUP) {
                    "Enter the 6-digit code we sent to your email."
                } else {
                    "Enter the 6-digit code we sent to $email."
                },
                fontFamily = PoppinsFontFamily,
                fontSize = 14.sp,
                color = T.muted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            BasicTextField(
                value = digits,
                onValueChange = { raw ->
                    val d = raw.filter { it.isDigit() }.take(RecoveryCodeFlow.CODE_LENGTH)
                    digits = d
                    if (d.length == RecoveryCodeFlow.CODE_LENGTH) submit(d)
                },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .testTag("recovery_code_field"),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit(digits) }
                ),
                textStyle = TextStyle(color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                decorationBox = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(RecoveryCodeFlow.CODE_LENGTH) { i ->
                            val ch = digits.getOrNull(i)
                            val active = i == digits.length && !verifying
                            val filled = ch != null
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(T.surface2)
                                    .border(
                                        width = if (filled || active) 2.dp else 1.dp,
                                        color = when {
                                            errorText != null -> T.danger
                                            filled || active -> T.accent
                                            else -> T.muted.copy(alpha = 0.35f)
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .testTag("recovery_code_box_$i"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (ch != null) {
                                    Text(
                                        text = ch.toString(),
                                        fontFamily = PoppinsFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                        color = T.text
                                    )
                                } else if (active) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(24.dp)
                                            .background(T.accent)
                                    )
                                }
                            }
                        }
                    }
                }
            )

            if (errorText != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorText,
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = T.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("recovery_code_error")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            PremiumButton(
                text = if (verifying) "Verifying..." else "Verify",
                onClick = { submit(digits) },
                enabled = digits.length == RecoveryCodeFlow.CODE_LENGTH && !verifying,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recovery_code_verify")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Didn't get the code?",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 13.sp,
                    color = T.muted
                )
                val canResend = resendCooldownSeconds <= 0L && !verifying
                TextButton(
                    onClick = onResend,
                    enabled = canResend,
                    modifier = Modifier.testTag("recovery_code_resend")
                ) {
                    Text(
                        text = if (resendCooldownSeconds > 0L) {
                            val s = resendCooldownSeconds
                            "Resend in 0:${s.toString().padStart(2, '0')}"
                        } else {
                            "Resend code"
                        },
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (canResend) T.accent else T.muted
                    )
                }
            }
        }
    }
}
