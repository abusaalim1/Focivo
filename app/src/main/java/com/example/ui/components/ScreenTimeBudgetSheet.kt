package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.util.ScreenTimeBudgetManager

/**
 * v1.3.3 (item 8b) — premium sheet to set the GLOBAL daily screen-time
 * budget. Preset chips (Off / 1h / 2h / 3h / 4h / 6h / 8h) + custom minutes.
 *
 * Copy explains the mechanic: past the budget, every non-essential app
 * locks for a 3-hour break; essentials always stay available. All
 * user-facing text English, no emojis, design-system tokens.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScreenTimeBudgetSheet(
    currentBudgetMinutes: Int,
    onBudgetSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedMinutes by remember(currentBudgetMinutes) {
        mutableStateOf(currentBudgetMinutes)
    }
    var customText by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    val presets = listOf(0, 60, 120, 180, 240, 360, 480)

    fun applyCustom() {
        val mins = customText.trim().toIntOrNull()
        customError = when {
            mins == null -> "Enter minutes, e.g. 150"
            mins < 30 -> "Minimum 30 minutes"
            mins > 960 -> "Maximum 16 hours"
            else -> null
        }
        if (customError == null && mins != null) {
            selectedMinutes = mins
            customText = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = T.bg,
        dragHandle = null,
        modifier = modifier.testTag("screen_time_budget_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetDragHandle()

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(T.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = T.accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Daily Screen-Time Limit",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SerifItalicFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = T.text
                ),
                modifier = Modifier.testTag("screen_time_budget_title")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "When your total screen time today passes this limit, every " +
                    "non-essential app locks for a 3-hour break. Phone, Camera, " +
                    "Maps, YouTube, Chrome and other essentials always stay open.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = PoppinsFontFamily,
                    color = T.muted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("screen_time_budget_chips")
            ) {
                presets.forEach { mins ->
                    val isSelected = selectedMinutes == mins
                    val label = if (mins == 0) "Off"
                    else ScreenTimeBudgetManager.budgetLabel(mins)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) T.accent else T.surface)
                            .border(
                                1.dp,
                                if (isSelected) T.accent else T.line,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                selectedMinutes = mins
                                customError = null
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp)
                            .testTag("budget_chip_$mins"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelected && mins != 0) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = T.bg,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) T.bg else T.text
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Custom minutes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = customText,
                    onValueChange = {
                        customText = it.filter { c -> c.isDigit() }.take(3)
                        customError = null
                    },
                    placeholder = {
                        Text(
                            text = "Custom minutes",
                            fontFamily = PoppinsFontFamily,
                            color = T.muted,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = customError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = T.surface,
                        unfocusedContainerColor = T.surface,
                        focusedBorderColor = T.accent,
                        unfocusedBorderColor = T.line,
                        focusedTextColor = T.text,
                        unfocusedTextColor = T.text,
                        cursorColor = T.accent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("budget_custom_input")
                )
                Spacer(modifier = Modifier.width(10.dp))
                GlassButton(
                    text = "Set",
                    onClick = ::applyCustom,
                    isPrimary = true,
                    minHeight = 48.dp,
                    horizontalPadding = 20.dp,
                    verticalPadding = 10.dp,
                    fontSize = 13.sp,
                    modifier = Modifier.testTag("budget_custom_set_button")
                )
            }

            if (customError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = customError!!,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = PoppinsFontFamily,
                        color = T.danger,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            GlassButton(
                text = if (selectedMinutes == 0) "Turn Off Limit"
                else "Set Limit: ${ScreenTimeBudgetManager.budgetLabel(selectedMinutes)} / day",
                onClick = {
                    onBudgetSelected(selectedMinutes)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_confirm_button"),
                isPrimary = true,
                minHeight = 54.dp,
                fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Cancel",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = T.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = PoppinsFontFamily
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}
