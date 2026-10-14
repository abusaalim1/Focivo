package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.data.FocuslyRepository
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SecondaryTextLight
import com.example.ui.theme.isAppInDarkTheme
import com.example.util.PomodoroConfig
import com.example.util.TimerFaceManager
import com.example.util.TimerTheme
import com.example.util.TimerThemeManager
import com.example.util.loadLastPomodoroPresetId
import com.example.util.loadPomodoroConfig

/**
 * v1.5.0 — Customize Timer sheet (replaces the old "Study Focus & Custom
 * Timer" dialog). Focus duration, short/long break durations, pomodoro
 * session count, subject, quick theme pick and sound pick — all wired to
 * the EXISTING settings/state (onSelectMode / onUpdateSubject /
 * onSelectAmbientSound / TimerThemeManager). No new backend fields.
 *
 * Breaks reuse the existing break-phase machinery: setting a "Short Break"
 * / "Long Break" mode drives the timer's break presentation (phase pill +
 * ring color shift); the user starts it with the normal Start button.
 */
private val CustomizeAccent = Color(0xFF8FD400)

private val CustomizeSounds = listOf("Silent", "Rain", "White noise", "Cafe", "Forest", "Ocean")

private val PresetSubjects = listOf(
    "Deep Study",
    "General Study",
    "Exam Prep",
    "Coding & Dev",
    "Reading & Research",
    "Maths & Logic",
    "Revision & Practice"
)

private fun modeLabelFor(mins: Int): String = when (mins) {
    15 -> "Sprint"
    25 -> "Pomodoro"
    50 -> "Deep Work"
    90 -> "Marathon"
    else -> "Custom"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomizeTimerSheet(
    onDismiss: () -> Unit,
    currentFocusMinutes: Int,
    currentSubject: String,
    ambientSound: String,
    onSelectAmbientSound: (String) -> Unit,
    onSelectMode: (mode: String, durationMinutes: Int) -> Unit,
    onUpdateSubject: (String) -> Unit,
    // v1.1: starts a pomodoro run (goes through the same session-start gate
    // as the classic timer — pre-study block sheet included).
    onStartPomodoro: (PomodoroConfig) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isDark = isAppInDarkTheme()
    val sheetBackground = if (isDark) Color(0xFF0F1410) else Color(0xFFFAFBF7)
    val textPrimary = if (isDark) Color(0xFFF0F4ED) else NearBlack
    val textSecondary = if (isDark) Color(0xFFA0A89E) else SecondaryTextLight
    val cardBg = if (isDark) Color(0xFF1D221C) else Color.White
    val cardBorder = if (isDark) Color(0x228CE000) else Color(0xFFDCE6D2)
    val selectedBg = if (isDark) Color(0xFF2A331F) else Color(0xFFF1F8E4)

    var selectedSubject by remember { mutableStateOf(currentSubject.ifBlank { "Deep Study" }) }
    var customSubjectInput by remember { mutableStateOf("") }
    var focusMins by remember { mutableStateOf(currentFocusMinutes.coerceAtLeast(1)) }
    var customFocusInput by remember { mutableStateOf("") }
    // v1.1: pomodoro showcase state — initialized from the last-used config
    // (persisted via the existing prefs mechanism), separate from the classic
    // focus duration above. Also drives the quick "Set break" buttons below.
    val savedPomo = remember(context) { loadPomodoroConfig(context) }
    var pomoFocusMins by remember { mutableStateOf(savedPomo.focusMinutes) }
    var pomoShortMins by remember { mutableStateOf(savedPomo.shortBreakMinutes) }
    var pomoLongMins by remember { mutableStateOf(savedPomo.longBreakMinutes) }
    var pomoCycles by remember { mutableStateOf(savedPomo.cycles) }
    var lastPresetId by remember { mutableStateOf(loadLastPomodoroPresetId(context)) }
    var lockedDialogTheme by remember { mutableStateOf<TimerTheme?>(null) }
    var lockedDialogFace by remember { mutableStateOf<com.example.util.TimerFace?>(null) }

    val selectedTheme by TimerThemeManager.selectedTheme.collectAsState()
    val selectedFace by TimerFaceManager.selectedFace.collectAsState()
    val prefsFlow = remember(context) { FocuslyRepository.getInstance(context).userPreferences }
    val prefs by prefsFlow.collectAsState(initial = null)
    val focusPoints = prefs?.focusPoints ?: 0

    lockedDialogTheme?.let { lockedTheme ->
        LockedThemeDialog(
            lockedTheme = lockedTheme,
            focusPoints = focusPoints,
            onDismiss = { lockedDialogTheme = null }
        )
    }
    lockedDialogFace?.let { lockedFace ->
        LockedTimerFaceDialog(
            lockedFace = lockedFace,
            focusPoints = focusPoints,
            onDismiss = { lockedDialogFace = null }
        )
    }

    val resolvedFocusMins = customFocusInput.toIntOrNull()?.takeIf { it in 1..480 } ?: focusMins

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("customize_timer_sheet"),
        containerColor = sheetBackground,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetDragHandle()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Customize timer",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = textPrimary
                    )
                    Text(
                        text = "Durations, pomodoro cycle and quick picks.",
                        fontFamily = PoppinsFontFamily,
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    CustomizeSectionLabel(text = "Subject", textSecondary = textSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetSubjects.forEach { subject ->
                            val isChosen = selectedSubject == subject && customSubjectInput.isBlank()
                            CustomizeChip(
                                text = subject,
                                selected = isChosen,
                                onClick = {
                                    selectedSubject = subject
                                    customSubjectInput = ""
                                },
                                textPrimary = textPrimary,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                selectedBg = selectedBg
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customSubjectInput,
                        onValueChange = {
                            customSubjectInput = it
                            if (it.isNotBlank()) selectedSubject = it
                        },
                        label = { Text("Or type a custom topic", fontFamily = PoppinsFontFamily) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    CustomizeSectionLabel(text = "Focus duration", textSecondary = textSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 50, 90).forEach { mins ->
                            val isChosen = resolvedFocusMins == mins && customFocusInput.isBlank()
                            CustomizeChip(
                                text = "${mins}m",
                                selected = isChosen,
                                onClick = {
                                    focusMins = mins
                                    customFocusInput = ""
                                },
                                textPrimary = textPrimary,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                selectedBg = selectedBg,
                                modifier = Modifier.weight(1f),
                                testTag = "customize_focus_chip_$mins"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customFocusInput,
                        onValueChange = { customFocusInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Custom minutes", fontFamily = PoppinsFontFamily) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // v1.1: aesthetic Pomodoro showcase — tilted phase cards,
                // editable durations, cycles, preset plans, real cycle engine.
                item {
                    PomodoroShowcaseSection(
                        focusMins = pomoFocusMins,
                        shortBreakMins = pomoShortMins,
                        longBreakMins = pomoLongMins,
                        sessionCount = pomoCycles,
                        lastPresetId = lastPresetId,
                        onFocusMinsChange = { pomoFocusMins = it },
                        onShortBreakMinsChange = { pomoShortMins = it },
                        onLongBreakMinsChange = { pomoLongMins = it },
                        onSessionCountChange = { pomoCycles = it },
                        onPresetTap = { preset ->
                            pomoFocusMins = preset.focus
                            pomoShortMins = preset.shortBreak
                            pomoLongMins = preset.longBreak
                            pomoCycles = preset.cycles
                            lastPresetId = preset.id
                            onStartPomodoro(preset.toConfig())
                            onDismiss()
                        },
                        onStartCustom = {
                            lastPresetId = null
                            onStartPomodoro(
                                PomodoroConfig(pomoFocusMins, pomoShortMins, pomoLongMins, pomoCycles, presetId = null)
                            )
                            onDismiss()
                        },
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        isDark = isDark
                    )
                }

                item {
                    CustomizeSectionLabel(text = "Quick picks", textSecondary = textSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sound",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CustomizeSounds.forEach { sound ->
                            val isChosen = sound == ambientSound
                            CustomizeChip(
                                text = sound,
                                selected = isChosen,
                                onClick = { onSelectAmbientSound(sound) },
                                textPrimary = textPrimary,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                selectedBg = selectedBg,
                                testTag = "customize_sound_${sound.lowercase().replace(" ", "_")}"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Theme",
                        fontFamily = PoppinsFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimerThemeManager.THEMES.forEach { theme ->
                            val isLocked = !theme.isUnlocked(focusPoints)
                            val isSelected = theme.id == selectedTheme.id
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) selectedBg else cardBg)
                                    .border(
                                        1.dp,
                                        if (isSelected) CustomizeAccent else cardBorder,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .pressFeedback()
                                    .clickable {
                                        if (isLocked) {
                                            lockedDialogTheme = theme
                                        } else {
                                            TimerThemeManager.trySelectTheme(context, theme.id, focusPoints)
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isLocked) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(13.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(theme.accent)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = theme.name,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = textPrimary
                                )
                                if (isLocked) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${theme.unlockPoints}",
                                        fontFamily = PoppinsFontFamily,
                                        fontSize = 10.sp,
                                        color = Color(0xFFFFD54F)
                                    )
                                }
                            }
                        }
                    }
                }

                // v1.1: Timer Style — switchable timer faces with live
                // previews, same lock model as themes (Focus Points balance
                // in the header, gold lock + cost, explainer dialog).
                item {
                    CustomizeSectionLabel(text = "Timer Style", textSecondary = textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = "Focus Points: ",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = textSecondary
                        )
                        Text(
                            text = "$focusPoints",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isDark) CustomizeAccent else Color(0xFF4A7A00)
                        )
                    }
                    TimerFacePicker(
                        focusPoints = focusPoints,
                        selectedFaceId = selectedFace.id,
                        onFaceTap = { face, isLocked ->
                            if (isLocked) {
                                lockedDialogFace = face
                            } else {
                                TimerFaceManager.trySelectFace(context, face.id, focusPoints)
                            }
                        },
                        textPrimary = textPrimary,
                        cardBorder = cardBorder
                    )
                }

                item {
                    Button(
                        onClick = {
                            val finalSubject = if (customSubjectInput.isNotBlank()) customSubjectInput.trim() else selectedSubject
                            onUpdateSubject(finalSubject)
                            onSelectMode(modeLabelFor(resolvedFocusMins), resolvedFocusMins)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CustomizeAccent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("customize_apply_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Apply timer",
                            fontFamily = PoppinsFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                onSelectMode("Short Break", pomoShortMins)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customize_set_short_break_btn")
                        ) {
                            Text(
                                text = "Set short break · ${pomoShortMins}m",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (isDark) CustomizeAccent else Color(0xFF4A7A00)
                            )
                        }
                        TextButton(
                            onClick = {
                                onSelectMode("Long Break", pomoLongMins)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customize_set_long_break_btn")
                        ) {
                            Text(
                                text = "Set long break · ${pomoLongMins}m",
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (isDark) CustomizeAccent else Color(0xFF4A7A00)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(28.dp)) }
            }
        }
    }
}

@Composable
private fun CustomizeSectionLabel(text: String, textSecondary: Color) {
    Text(
        text = text.uppercase(),
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        color = textSecondary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun CustomizeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    textPrimary: Color,
    cardBg: Color,
    cardBorder: Color,
    selectedBg: Color,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) selectedBg else cardBg)
            .border(
                1.dp,
                if (selected) CustomizeAccent else cardBorder,
                RoundedCornerShape(14.dp)
            )
            .pressFeedback()
            .clickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = PoppinsFontFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            color = textPrimary
        )
    }
}

@Composable
private fun CustomizeStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(cardBg)
            .border(1.dp, cardBorder, CircleShape)
            .pressFeedback()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = textPrimary,
            modifier = Modifier.size(16.dp)
        )
    }
}
