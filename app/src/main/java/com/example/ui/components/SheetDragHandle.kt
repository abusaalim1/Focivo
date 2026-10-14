package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Final phase UI touch: shared bottom-sheet top drag handle with a soft
 * 1dp top-border highlight. Render as the first element inside any sheet.
 */
@Composable
fun SheetDragHandle(
    modifier: Modifier = Modifier,
    handleColor: Color = Color(0xFF3A443B).copy(alpha = 0.55f),
    highlightColor: Color = Color.White.copy(alpha = 0.35f)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(handleColor)
                .border(1.dp, highlightColor, RoundedCornerShape(3.dp))
        )
    }
}
