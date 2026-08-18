package com.tsutsen.platformplayer.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Bluejay brand colors
val GrayjayBlue = Color(0xFF1A73E8)
val GrayjayBlueDark = Color(0xFF1557B0)
val GrayjayBlueLight = Color(0xFF4A90E2)
val GrayjayDark = Color(0xFF1A1A2E)
val GrayjayDarker = Color(0xFF16213E)
val GrayjayLight = Color(0xFFE8EAF6)

// Semantic roles M3 does not define. M3 already covers danger
// (error / errorContainer) and highlight (primaryContainer); warning
// (yellow) is the only one we need to provide ourselves.
data class SemanticColors(
    val warning: Color,
    val onWarning: Color,
)

val SemanticColorsLight =
    SemanticColors(
        warning = Color(0xFFFFE082),
        onWarning = Color(0xFF3E2C00),
    )

val SemanticColorsDark =
    SemanticColors(
        warning = Color(0xFF443B00),
        onWarning = Color(0xFFFFE082),
    )

// Contrast level color schemes
val LightColorScheme =
    androidx.compose.material3.lightColorScheme(
        primary = GrayjayBlue,
        onPrimary = Color.White,
        primaryContainer = GrayjayBlueLight,
        onPrimaryContainer = Color.Black,
        secondary = Color(0xFF546E7A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFB0BEC5),
        onSecondaryContainer = Color.Black,
        background = Color(0xFFF5F5F5),
        onBackground = Color(0xFF1C1B1F),
        surface = Color.White,
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFE0E0E0),
        onSurfaceVariant = Color(0xFF49454F),
        error = Color(0xFFB3261E),
        onError = Color.White,
        outline = Color(0xFF79747E),
    )

val DarkColorScheme =
    androidx.compose.material3.darkColorScheme(
        primary = GrayjayBlueLight,
        onPrimary = Color.Black,
        primaryContainer = GrayjayBlueDark,
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF90A4AE),
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF37474F),
        onSecondaryContainer = Color.White,
        background = GrayjayDark,
        onBackground = GrayjayLight,
        surface = GrayjayDarker,
        onSurface = GrayjayLight,
        surfaceVariant = Color(0xFF2D2D44),
        onSurfaceVariant = Color(0xFFCAC4D0),
        error = Color(0xFFF28B82),
        onError = Color.Black,
        outline = Color(0xFF938F99),
    )

// High contrast variants
val HighContrastLightScheme =
    androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF000080),
        onPrimary = Color.White,
        background = Color.White,
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
    )

val HighContrastDarkScheme =
    androidx.compose.material3.darkColorScheme(
        primary = Color(0xFFFFBF00),
        onPrimary = Color.Black,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
    )
