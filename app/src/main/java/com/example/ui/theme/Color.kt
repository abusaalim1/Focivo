package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Regain Premium Pale Lime Theme Color Palette (Reference Design Match)

// 1. Base Gradient Background Colors
val RegainBgTop = Color(0xFFFFFFFF)       // Pure White at top
val RegainBgMiddle = Color(0xFFF7FDF1)    // Very soft pale lime tint
val RegainBgBottom = Color(0xFFEFF9E3)    // Gentle mint-lime tint

val RegainBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        RegainBgTop,
        RegainBgMiddle,
        RegainBgBottom
    )
)

// 2. Primary Accent (Vivid Lime-Green)
val RegainLimePrimary = Color(0xFF8CE000)      // Vivid energetic lime-green (#8CE000)
val RegainLimeLight = Color(0xFFA6EB38)        // Soft light lime
val RegainLimeDark = Color(0xFF72B800)         // Darker lime border
val RegainLimeDeepText = Color(0xFF4C8000)     // High-contrast green text
val RegainLimeContainer = Color(0xFFF0FCE1)    // Light pale lime container fill
val RegainLimeContainerBorder = Color(0xFFD4F59A)
val RegainLimeGlow = Color(0x338CE000)

// Apple & Linear Style Special Tokens
val LinearDarkCardBg = Color(0xF21C1C1E)
val LinearDarkCardBorder = Color(0x22FFFFFF)        // Apple dark hairline border (~13.5% white)
val LinearLightCardBg = Color(0xFFFFFFFF)
val LinearLightCardBorder = Color(0x12000000)       // Apple light hairline border (~7% black)
val AppleGlassHighlightLight = Color(0x99FFFFFF)
val AppleGlassHighlightDark = Color(0x25FFFFFF)
val ApplePillBgLight = Color(0xF2FFFFFF)
val ApplePillBgDark = Color(0xCC2C2C2E)

val AppleCardBgLight = Color(0xFFFFFFFF)
val AppleCardBgDark = Color(0xF21C1C1E)
val AppleCardBorderLight = Color(0x12000000)
val AppleCardBorderDark = Color(0x22FFFFFF)
val AppleCardHighlightLight = Color(0x99FFFFFF)
val AppleCardHighlightDark = Color(0x25FFFFFF)

val LinearLimeHeroGradient = Brush.horizontalGradient(
    listOf(Color(0xFF8CE000), Color(0xFFAEF72A))
)
val LinearDarkHeroGradient = Brush.verticalGradient(
    listOf(Color(0x308CE000), Color(0x058CE000))
)
val AppleGlassBorderGradient = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))
)

// 3. Text & Neutral Colors
val NearBlack = Color(0xFF1B1E1B)              // Near-black dark charcoal for titles
val DarkCharcoal = Color(0xFF2B2F2B)
val SecondaryTextLight = Color(0xFF6E756C)     // Medium gray for supporting labels
val MutedTextLight = Color(0xFF9DA49B)          // Disabled/placeholder text
val MutedBorderLight = Color(0x12000000)        // Apple clean subtle hairline
val MutedBorderDark = Color(0x22FFFFFF)         // Apple dark clean subtle hairline

// 4. Cards & Floating Surfaces
val PureWhite = Color(0xFFFFFFFF)
val GlassSurfaceLight = Color(0xFAFFFFFF)
val GlassCardLight = Color(0xF2FFFFFF)
val CardDropShadowColor = Color(0x0F000000)

// 5. Secondary Accents (Soft Muted Pastels)
val SoftPastelBlueBg = Color(0xFFEEF4FF)
val SoftPastelBlueText = Color(0xFF3B72E2)

val SoftPastelPurpleBg = Color(0xFFF4ECFF)
val SoftPastelPurpleText = Color(0xFF8B42E3)

val SoftPastelOrangeBg = Color(0xFFFFF3E6)
val SoftPastelOrangeText = Color(0xFFE0702B)

val SoftPastelTealBg = Color(0xFFE8F8F5)
val SoftPastelTealText = Color(0xFF1DA68A)

// Status & Priority Colors
val PriorityHigh = Color(0xFFEF5350)
val PriorityMedium = Color(0xFFFFA726)
val PriorityLow = Color(0xFF66BB6A)
val SuccessGreen = Color(0xFF8CE000)

// Backward-compatibility alias mappings so existing screens load smoothly
val WarmOffWhite = Color(0xFFF7FDF1)
val DarkSurface = Color(0xFFFFFFFF)
val DarkSurfaceElevated = Color(0xFFF4FAEE)
val DarkTextPrimary = NearBlack
val DarkTextSecondary = SecondaryTextLight
val GlassSurfaceDark = GlassSurfaceLight
val GlassCardDark = GlassCardLight

val VioletAccent = RegainLimePrimary
val LavenderSoft = RegainLimeContainer
val LavenderGradientEnd = RegainLimeLight
val VioletAccentDark = RegainLimePrimary

val RegainAuroraGreen = RegainLimePrimary
val RegainNeonEmerald = RegainLimeLight
val RegainDarkEmerald = RegainLimeDark
val RegainAuroraEmerald = RegainLimePrimary
val RegainDeepCosmic = RegainBgTop
val RegainNightSky = RegainBgTop
val RegainSpaceDark = RegainBgMiddle
val RegainCardBg = PureWhite
val RegainCardBorder = MutedBorderLight
val RegainMascotGreen = RegainLimePrimary
val RegainGoldPodium = Color(0xFFFFC107)
val RegainGoldStar = Color(0xFFFFB300)
val RegainSilverPodium = Color(0xFF90A4AE)
val RegainBronzePodium = Color(0xFFFF8A65)

val LuxuryAccentGradient = Brush.linearGradient(colors = listOf(RegainLimePrimary, RegainLimeLight))
val LuxuryAccentGradientDark = LuxuryAccentGradient
val AmbientGlowColor = Color(0x3B8CE000)
val AmbientGlowColorDark = Color(0x3B8CE000)
