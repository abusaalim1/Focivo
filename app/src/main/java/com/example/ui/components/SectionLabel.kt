package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Final phase UI touch: consistent section-label style used across the
 * hub sheets (Guard Hub, Alarm Studio) — 11sp letterspaced caps + steady
 * spacing. Bounded polish only, no behavior changes.
 */
@Composable
fun SectionLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            color = color,
            letterSpacing = 1.3.sp,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = modifier.padding(top = 6.dp, bottom = 8.dp)
    )
}
