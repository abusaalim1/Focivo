package com.example.ui.timer

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.R

/* ================= SHARED (clock + pomodoro both use these) ================= */
/**
 * v1.2.3 — shared helpers for the integrated FocivoClock / FocivoPomodoro UI.
 * Renamed with the Ft prefix (and ftMmss/ftProg) to avoid collisions with
 * existing app declarations. FtFDisplay -> Instrument Serif Italic,
 * FtFBody -> Outfit (the app ships a single static Outfit file, so every
 * weight request maps to it — same honest limitation as the share-card round).
 */
internal val FtFDisplay: FontFamily = FontFamily(Font(R.font.instrument_serif_italic))
internal val FtFBody: FontFamily = FontFamily(Font(R.font.outfit))

internal val FtLime = Color(0xFF8CE000)
internal val FtMint = Color(0xFF4FE3B5)
internal val FtSand = Color(0xFFFFD66B)
internal val FtBgDark = Color(0xFF0D110E)

internal fun ftMmss(s: Int): String {
    val v = s.coerceAtLeast(0)
    return "%02d:%02d".format(v / 60, v % 60)
}

internal fun ftProg(rem: Int, total: Int): Float =
    if (total <= 0) 0f else (1f - rem.toFloat() / total).coerceIn(0f, 1f)

@Composable
internal fun FtT(
    text: String,
    size: Int,
    color: Color = Color.White,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily = FtFBody,
    spacing: Float = 0f,
    italic: Boolean = false,
    lh: Float = 0f,
    modifier: Modifier = Modifier
) = Text(
    text, modifier, style = TextStyle(
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = family,
        letterSpacing = spacing.sp,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.Center,
        lineHeight = if (lh > 0f) lh.sp else TextStyle.Default.lineHeight
    )
)
