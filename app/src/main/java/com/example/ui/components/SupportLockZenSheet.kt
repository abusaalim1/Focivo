package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MutedBorderLight
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.RegainLimeContainer
import com.example.ui.theme.RegainLimeDeepText
import com.example.ui.theme.RegainLimePrimary
import com.example.ui.theme.SecondaryTextLight

data class PresetTier(
    val amount: Int,
    val label: String,
    val icon: String
)

val PRESET_TIERS = listOf(
    PresetTier(49, "Chai", "☕"),
    PresetTier(99, "Supporter", "🌟"),
    PresetTier(199, "Hero", "🚀")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportLockZenSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var selectedAmount by remember { mutableStateOf(99) }
    var customAmountText by remember { mutableStateOf("99") }
    var isCustomSelected by remember { mutableStateOf(false) }
    var showThankYouDialog by remember { mutableStateOf(false) }

    val bgSurface = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight
    val cardBorder = if (isDark) MaterialTheme.colorScheme.outlineVariant else MutedBorderLight

    val effectiveAmount = if (isCustomSelected) {
        customAmountText.toIntOrNull() ?: 99
    } else {
        selectedAmount
    }

    fun launchUpiPayment() {
        val upiUri = "upi://pay?pa=7002395406@fam&pn=Focivo&am=$effectiveAmount&cu=INR"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
        try {
            val chooser = Intent.createChooser(intent, "Pay via UPI App")
            context.startActivity(chooser)
            showThankYouDialog = true
        } catch (_: Exception) {
            Toast.makeText(context, "No UPI app found. Please copy 7002395406@fam or use PayPal.", Toast.LENGTH_LONG).show()
        }
    }

    fun launchPaypalPayment() {
        val paypalUrl = "https://www.paypal.com/paypalme/abusaalim/$effectiveAmount"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paypalUrl))
        try {
            context.startActivity(intent)
            showThankYouDialog = true
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open browser for PayPal.", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgSurface,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .testTag("support_lockzen_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(RegainLimeContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Support",
                            tint = RegainLimeDeepText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Support Focivo",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Voluntary Contribution · Keep Focivo Free",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = PoppinsFontFamily,
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Human developer message card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Hey — if Focivo has helped you stay focused and reclaim your time, it'd mean a lot if you considered supporting its growth. I built this solo, and every bit helps keep it alive and free for everyone. Totally optional, no pressure at all 🌱",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = PoppinsFontFamily,
                            color = textPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Preset Tiers Choice
            Text(
                text = "SELECT AMOUNT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 11.sp
                ),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PRESET_TIERS.forEach { tier ->
                    val isSelected = !isCustomSelected && selectedAmount == tier.amount
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) RegainLimeContainer else (if (isDark) Color(0x1AFFFFFF) else Color(0xFFF4F6F0)))
                            .border(
                                1.5.dp,
                                if (isSelected) RegainLimePrimary else cardBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                isCustomSelected = false
                                selectedAmount = tier.amount
                                customAmountText = tier.amount.toString()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${tier.icon} ₹${tier.amount}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) RegainLimeDeepText else textPrimary,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tier.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = PoppinsFontFamily,
                                    color = if (isSelected) RegainLimeDeepText.copy(alpha = 0.8f) else textSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Custom Amount Input Field
            OutlinedTextField(
                value = customAmountText,
                onValueChange = { text ->
                    val filtered = text.filter { it.isDigit() }.take(5)
                    customAmountText = filtered
                    isCustomSelected = true
                },
                label = {
                    Text(
                        "Custom Amount (₹)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = PoppinsFontFamily)
                    )
                },
                prefix = {
                    Text("₹ ", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold, color = RegainLimeDeepText))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RegainLimePrimary,
                    unfocusedBorderColor = cardBorder,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Payment Methods Buttons
            Text(
                text = "CHOOSE PAYMENT METHOD",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = textSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontSize = 11.sp
                ),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // UPI Button
            GlassButton(
                text = "Pay ₹$effectiveAmount via UPI (GPay/PhonePe)",
                onClick = { launchUpiPayment() },
                isPrimary = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = NearBlack,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // PayPal Button
            GlassButton(
                text = "Pay via PayPal (International)",
                onClick = { launchPaypalPayment() },
                isPrimary = false,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        tint = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Direct UPI ID: 7002395406@fam · PayPal: abusaalim.design@gmail.com",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = PoppinsFontFamily,
                    color = textSecondary,
                    fontSize = 10.sp
                )
            )
        }
    }

    if (showThankYouDialog) {
        AlertDialog(
            onDismissRequest = {
                showThankYouDialog = false
                onDismiss()
            },
            title = {
                Text(
                    text = "Thank You! 💚",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    text = "Thank you so much for your support! Every contribution helps keep Focivo free, independent, and continuously improving for everyone.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = PoppinsFontFamily
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showThankYouDialog = false
                        onDismiss()
                    }
                ) {
                    Text("You're Welcome", style = MaterialTheme.typography.labelLarge.copy(fontFamily = PoppinsFontFamily, color = RegainLimeDeepText, fontWeight = FontWeight.Bold))
                }
            },
            containerColor = bgSurface
        )
    }
}

@Composable
fun SupportLockZenMilestoneDialog(
    onSupportClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bgSurface = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onSurface else NearBlack
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else SecondaryTextLight

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(RegainLimeContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RegainLimeDeepText,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = "Support Focivo 🌱",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            )
        },
        text = {
            Text(
                text = "Hey — if Focivo has helped you stay focused, it'd mean a lot if you considered supporting its growth. I built this solo, and every bit helps keep it alive. Totally optional, no pressure at all 🌱",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    color = textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onSupportClick()
                }
            ) {
                Text(
                    text = "Support Focivo",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        color = RegainLimeDeepText,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Not now",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = PoppinsFontFamily,
                        color = textSecondary
                    )
                )
            }
        },
        containerColor = bgSurface
    )
}
