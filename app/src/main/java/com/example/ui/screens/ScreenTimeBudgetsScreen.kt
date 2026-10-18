package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SerifItalicFontFamily
import com.example.ui.theme.focivoTokens
import com.example.util.ScreenTimeBudgetStore

/**
 * v1.3.3 (ITEM 5 — Study-page guard control cluster): the real entry point
 * for route id "screen_time_budgets" ([ScreenTimeBudgetStore.SCREEN_TIME_BUDGETS_ROUTE_ID]).
 *
 * Reached from the Study screen's guard control cluster ("Limits" icon).
 * Drives [ScreenTimeBudgetStore] for the GLOBAL daily screen-time limit
 * (enabled flag + minutes) — no parallel state.
 *
 * HANDOFF — screen-time budget settings editor (another worker, same round):
 * the full editor (per-app limits + richer global-limit UI) slots in at
 * [ScreenTimeBudgetEditorSlot] below. It must keep driving
 * [ScreenTimeBudgetStore] for the global limit so both surfaces stay in
 * sync. Per-app limits continue to live in AppDailyLimitsManager /
 * FocuslyRepository (AppLimitsScreen).
 */
@Composable
fun ScreenTimeBudgetsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val T = focivoTokens()

    LaunchedEffect(Unit) { ScreenTimeBudgetStore.init(context) }
    val globalEnabled by ScreenTimeBudgetStore.globalLimitEnabledState.collectAsState()
    val globalMinutes by ScreenTimeBudgetStore.globalLimitMinutesState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(T.bg)
            .statusBarsPadding()
            .testTag("screen_time_budgets_screen")
    ) {
        // Header.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(T.surface)
                    .border(1.dp, T.line, CircleShape)
                    .clickable(onClick = onBack)
                    .testTag("budgets_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = T.text,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Screen-time budgets",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = T.text
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Spend it where it counts.",
                    fontFamily = SerifItalicFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    color = T.text,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "One daily ceiling for your whole phone, plus limits " +
                        "for individual apps. Focivo nudges you when a budget runs out.",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = T.muted
                )
            }

            // Global daily limit card — real, wired to ScreenTimeBudgetStore.
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(T.surface)
                        .border(1.dp, T.line, RoundedCornerShape(22.dp))
                        .padding(18.dp)
                        .testTag("budgets_global_limit_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(T.softAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = T.accentText,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Daily screen-time limit",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = T.text
                            )
                            Text(
                                text = "A single ceiling for total phone use each day.",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                color = T.muted
                            )
                        }
                        Switch(
                            checked = globalEnabled,
                            onCheckedChange = { enabled ->
                                ScreenTimeBudgetStore.setGlobalLimitEnabled(context, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = T.onAccent,
                                checkedTrackColor = T.accent,
                                uncheckedThumbColor = T.thumbOff,
                                uncheckedTrackColor = T.trackOff,
                                uncheckedBorderColor = T.line
                            ),
                            modifier = Modifier.testTag("budgets_global_limit_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (globalEnabled) {
                        Text(
                            text = if (globalMinutes > 0)
                                "Your day ends after ${formatBudgetMinutes(globalMinutes)} of screen time."
                            else
                                "Pick how much screen time your day gets.",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = T.accentText,
                            modifier = Modifier.testTag("budgets_global_limit_summary")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        BudgetMinuteChips(
                            selectedMinutes = globalMinutes,
                            onSelect = { mins -> ScreenTimeBudgetStore.setGlobalLimitMinutes(context, mins) }
                        )
                    } else {
                        Text(
                            text = "Turn this on to cap your total daily screen time.",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.5.sp,
                            color = T.muted
                        )
                    }
                }
            }

            // Extension slot for the full editor (per-app limits + global UI).
            item {
                ScreenTimeBudgetEditorSlot()
            }
        }
    }
}

/**
 * HANDOFF — screen-time budget settings editor (another worker, same round).
 * Replace this placeholder's body with the full editor (per-app limits +
 * global daily limit UI). It MUST keep driving [ScreenTimeBudgetStore] for
 * the global limit so the Study-screen cluster entry and this screen stay
 * in sync. Route id stays "screen_time_budgets".
 */
@Composable
fun ScreenTimeBudgetEditorSlot(
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, T.chipBorder, RoundedCornerShape(22.dp))
            .background(T.surface2)
            .padding(18.dp)
            .testTag("budgets_editor_slot")
    ) {
        Text(
            text = "PER-APP BUDGETS",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            color = T.muted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Per-app daily limits are being added here — the full budget " +
                "editor plugs into this spot and shares the same store.",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = T.muted,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun BudgetMinuteChips(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val T = focivoTokens()
    val presets = listOf(60, 120, 180, 240, 360)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { mins ->
            val selected = selectedMinutes == mins
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) T.accent else T.surface2)
                    .border(
                        1.dp,
                        if (selected) T.accent else T.chipBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(mins) }
                    .padding(vertical = 10.dp)
                    .testTag("budgets_chip_${mins}m"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatBudgetMinutes(mins),
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = if (selected) T.onAccent else T.muted
                )
            }
        }
    }
}

private fun formatBudgetMinutes(mins: Int): String {
    if (mins <= 0) return "Off"
    val h = mins / 60
    val m = mins % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
