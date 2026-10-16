package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset

/**
 * Motion Lab #7 — "Number roll" (1.1s).
 *
 * Stats digits roll like an odometer: each digit slides vertically
 * (450ms) with a 40ms stagger from the right (least-significant digit
 * animates first). Works when the value goes up AND down, and when the
 * digit count grows (new digits fade/slide in from the left, keyed by
 * position-from-right so the number stays right-aligned).
 *
 * Poppins numerals come from the passed [style]; "tnum" is merged in so
 * digits keep a stable width while rolling. English digits only — the
 * [formatter] controls the text, and this composable never localizes
 * digit shapes.
 */
@Composable
fun RollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    formatter: (Int) -> String = { it.toString() }
) {
    RollingText(
        text = formatter(value),
        modifier = modifier,
        style = style
    )
}

/**
 * Mixed-string variant — rolls digit characters, renders everything else
 * (letters, punctuation, spaces) as static text in the same style.
 * Trivial example: RollingText("2h 30m") rolls the digits, "h"/"m" stay.
 */
@Composable
fun RollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    // Tabular figures keep digit widths stable so the row doesn't jitter
    // while digits roll. The passed style (incl. Poppins) is otherwise kept.
    val rolledStyle = style.merge(TextStyle(fontFeatureSettings = "tnum"))
    val chars = text.toCharArray()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        chars.forEachIndexed { i, ch ->
            val digitsToRight = chars.drop(i + 1).count { it.isDigit() }
            if (ch.isDigit()) {
                // Key by position-from-right so the number stays right-aligned
                // when the digit count grows; stagger 40ms per digit from right.
                key(chars.size - 1 - i) {
                    RollingDigit(
                        digit = ch,
                        style = rolledStyle,
                        delayMillis = digitsToRight * ROLL_STAGGER_MS
                    )
                }
            } else {
                Text(text = ch.toString(), style = rolledStyle)
            }
        }
    }
}

/** One odometer digit: vertical slide, direction follows increase/decrease. */
@Composable
private fun RollingDigit(
    digit: Char,
    style: TextStyle,
    delayMillis: Int
) {
    AnimatedContent(
        targetState = digit,
        transitionSpec = {
            val goingUp = targetState >= initialState
            val spec = tween<IntOffset>(
                durationMillis = ROLL_DURATION_MS,
                delayMillis = delayMillis
            )
            (slideInVertically(spec) { fullHeight -> if (goingUp) fullHeight else -fullHeight } togetherWith
                    slideOutVertically(spec) { fullHeight -> if (goingUp) -fullHeight else fullHeight })
                .using(SizeTransform(clip = false))
        },
        label = "rolling_digit"
    ) { d ->
        Text(text = d.toString(), style = style)
    }
}

private const val ROLL_DURATION_MS = 450
private const val ROLL_STAGGER_MS = 40
