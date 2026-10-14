package com.example.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared layout constants (v1.4.0-round2).
 *
 * The floating bottom navigation is drawn as a Scaffold bottomBar while the
 * screen content intentionally ignores innerPadding (full-bleed design), so
 * every tab screen must reserve this much space at the bottom of its scroll
 * content. 144dp clears the pill + system gesture/3-button insets with margin.
 */
val BottomNavClearance = 144.dp

/** Standard screen horizontal padding. */
val ScreenHorizontalPadding = 20.dp

/** 8pt rhythm spacers. */
val Space8 = 8.dp
val Space12 = 12.dp
val Space16 = 16.dp
val Space24 = 24.dp
