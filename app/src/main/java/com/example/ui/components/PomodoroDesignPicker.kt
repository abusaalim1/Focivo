package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.timer.FocivoPomodoro
import com.example.ui.timer.PomoPhase
import com.example.ui.timer.PomodoroStyle
import com.example.ui.timer.PomodoroUiState

/**
 * v1.2.3 — "Pomodoro design" picker row for the Customize Timer sheet.
 * Four lock-free style cards with small live previews (scaled real renders
 * of FocivoPomodoro, mid-focus running state). TICK_RING is the default.
 */
@Composable
fun PomodoroDesignPicker(
    selectedStyle: PomodoroStyle,
    onStyleTap: (PomodoroStyle) -> Unit,
    textPrimary: Color,
    cardBorder: Color,
    modifier: Modifier = Modifier
) {
    // Static preview state: first focus session, mid-way, running.
    val previewState = PomodoroUiState(
        phase = PomoPhase.FOCUS,
        remainingSec = 17 * 60,
        totalSec = 25 * 60,
        sessionIndex = 0,
        sessions = 4,
        running = true,
        nextPhase = PomoPhase.SHORT,
        nextMinutes = 5
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PomodoroStyle.values().forEach { style ->
            val isSelected = style == selectedStyle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onStyleTap(style) }
            ) {
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    // Real mini render: drawn at 360dp, scaled to the card.
                    Box(
                        modifier = Modifier
                            .size(360.dp, 450.dp)
                            .graphicsLayer(
                                scaleX = 112f / 360f,
                                scaleY = 112f / 360f,
                                transformOrigin = TransformOrigin(0f, 0f)
                            )
                    ) {
                        FocivoPomodoro(
                            state = previewState,
                            style = style,
                            onToggle = {},
                            onSkip = {},
                            onReset = {}
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                if (isSelected) 2.5.dp else 1.dp,
                                // v1.2.3 design-unification: owner clean-redesign accent.
                                if (isSelected) Color(0xFFB6F13A) else cardBorder,
                                RoundedCornerShape(16.dp)
                            )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = style.label,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.5.sp,
                    color = textPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
