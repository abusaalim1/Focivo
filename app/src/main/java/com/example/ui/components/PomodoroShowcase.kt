package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NearBlack
import com.example.ui.theme.PoppinsFontFamily
import com.example.util.PomodoroConfig

/**
 * v1.1 — aesthetic Pomodoro showcase (Apple/Notion minimal, dark+lime).
 *
 * Tilted preset phase cards (Focus / Short Break / Long Break) with big
 * clean numbers and the mascot accent, editable durations, cycles stepper,
 * and 10 ready-made preset plans. One tap on a preset selects AND starts.
 *
 * Presets are DATA-ONLY — a static list in the UI layer, no backend.
 */
data class PomodoroPreset(
    val id: String,
    val name: String,
    val focus: Int,
    val shortBreak: Int,
    val longBreak: Int,
    val cycles: Int
) {
    fun toConfig() = PomodoroConfig(focus, shortBreak, longBreak, cycles, presetId = id)
    fun specLine() = "$focus·$shortBreak·$longBreak ×$cycles"
}

val POMODORO_PRESETS = listOf(
    PomodoroPreset("classic", "Classic", 25, 5, 15, 4),
    PomodoroPreset("deep-work", "Deep Work", 50, 10, 30, 3),
    PomodoroPreset("exam-mode", "Exam Mode", 90, 15, 30, 2),
    PomodoroPreset("sprint", "Sprint", 15, 3, 10, 6),
    PomodoroPreset("micro-focus", "Micro Focus", 10, 2, 5, 8),
    PomodoroPreset("marathon", "Study Marathon", 45, 10, 20, 4),
    PomodoroPreset("night-owl", "Night Owl", 30, 5, 20, 4),
    PomodoroPreset("quick-review", "Quick Review", 20, 5, 10, 4),
    PomodoroPreset("ultra-deep", "Ultra Deep", 120, 20, 40, 2),
    PomodoroPreset("beginner", "Beginner", 15, 5, 10, 4)
)

private val ShowcaseLime = Color(0xFF8FD400)
private val ShowcaseMint = Color(0xFF69F0AE)

@Composable
fun PomodoroShowcaseSection(
    focusMins: Int,
    shortBreakMins: Int,
    longBreakMins: Int,
    sessionCount: Int,
    lastPresetId: String?,
    onFocusMinsChange: (Int) -> Unit,
    onShortBreakMinsChange: (Int) -> Unit,
    onLongBreakMinsChange: (Int) -> Unit,
    onSessionCountChange: (Int) -> Unit,
    onPresetTap: (PomodoroPreset) -> Unit,
    onStartCustom: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    cardBorder: Color,
    isDark: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header with mascot accent
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.mascot_studying),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pomodoro",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = textPrimary
                )
                Text(
                    text = "Automatic focus → break cycles. Start it, stay with it.",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.5.sp,
                    color = textSecondary,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tilted phase cards — the showcase hero
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PomodoroPhaseCard(
                label = "FOCUS",
                minutes = focusMins,
                tilt = -4f,
                accent = ShowcaseLime,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
            PomodoroPhaseCard(
                label = "SHORT BREAK",
                minutes = shortBreakMins,
                tilt = 3f,
                accent = ShowcaseMint,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
            PomodoroPhaseCard(
                label = "LONG BREAK",
                minutes = longBreakMins,
                tilt = -3f,
                accent = ShowcaseMint,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                isDark = isDark,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tune durations — compact steppers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PomodoroDurationStepper(
                label = "Focus",
                value = focusMins,
                range = 5..180,
                step = 5,
                accent = ShowcaseLime,
                onChange = onFocusMinsChange,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                cardBorder = cardBorder,
                modifier = Modifier.weight(1f)
            )
            PomodoroDurationStepper(
                label = "Short",
                value = shortBreakMins,
                range = 1..60,
                step = 1,
                accent = ShowcaseMint,
                onChange = onShortBreakMinsChange,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                cardBorder = cardBorder,
                modifier = Modifier.weight(1f)
            )
            PomodoroDurationStepper(
                label = "Long",
                value = longBreakMins,
                range = 5..120,
                step = 5,
                accent = ShowcaseMint,
                onChange = onLongBreakMinsChange,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardBg = cardBg,
                cardBorder = cardBorder,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cycles stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Focus sessions per cycle",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                color = textPrimary,
                modifier = Modifier.weight(1f)
            )
            PomodoroSmallStepperButton(
                icon = Icons.Default.Remove,
                contentDescription = "Fewer sessions",
                onClick = { onSessionCountChange((sessionCount - 1).coerceAtLeast(2)) },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary
            )
            Text(
                text = "$sessionCount",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = textPrimary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            PomodoroSmallStepperButton(
                icon = Icons.Default.Add,
                contentDescription = "More sessions",
                onClick = { onSessionCountChange((sessionCount + 1).coerceAtMost(8)) },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cycle summary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isDark) Color(0xFF2A331F) else Color(0xFFF1F8E4)
                )
                .border(1.dp, ShowcaseLime.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "$sessionCount × ${focusMins}m focus · ${shortBreakMins}m short breaks · ${longBreakMins}m long break",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset plans
        Text(
            text = "PRESET PLANS",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = textSecondary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "One tap selects a plan and starts it.",
            fontFamily = PoppinsFontFamily,
            fontSize = 11.sp,
            color = textSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            POMODORO_PRESETS.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { preset ->
                        val isLast = preset.id == lastPresetId
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(cardBg)
                                .border(
                                    1.5.dp,
                                    if (isLast) ShowcaseLime else cardBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .pressFeedback()
                                .clickable { onPresetTap(preset) }
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = preset.name,
                                    fontFamily = PoppinsFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                if (isLast) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(ShowcaseLime)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = preset.specLine(),
                                fontFamily = PoppinsFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (isDark) ShowcaseLime else Color(0xFF4A7A00)
                            )
                            Text(
                                text = "Focus · Short · Long × cycles",
                                fontFamily = PoppinsFontFamily,
                                fontSize = 9.5.sp,
                                color = textSecondary
                            )
                        }
                    }
                    // Keep the grid even when the last row has one card
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Start with the custom-tuned config
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(ShowcaseLime)
                .pressFeedback()
                .clickable(onClick = onStartCustom)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = NearBlack,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Start Pomodoro",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NearBlack
                )
            }
        }
    }
}

@Composable
private fun PomodoroPhaseCard(
    label: String,
    minutes: Int,
    tilt: Float,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.graphicsLayer { rotationZ = tilt }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardBg)
                .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$minutes",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                letterSpacing = (-1).sp,
                color = textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "min",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = textSecondary
            )
        }
    }
}

@Composable
private fun PomodoroDurationStepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    accent: Color,
    onChange: (Int) -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    cardBg: Color,
    cardBorder: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.8.sp,
            color = textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PomodoroSmallStepperButton(
                icon = Icons.Default.Remove,
                contentDescription = "Decrease $label",
                onClick = { onChange((value - step).coerceIn(range)) },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                small = true
            )
            Text(
                text = "$value",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = textPrimary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            PomodoroSmallStepperButton(
                icon = Icons.Default.Add,
                contentDescription = "Increase $label",
                onClick = { onChange((value + step).coerceIn(range)) },
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                small = true
            )
        }
    }
}

@Composable
private fun PomodoroSmallStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    small: Boolean = false
) {
    val size = if (small) 28.dp else 34.dp
    Box(
        modifier = Modifier
            .size(size)
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
            modifier = Modifier.size(if (small) 13.dp else 16.dp)
        )
    }
}
