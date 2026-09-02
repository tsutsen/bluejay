package com.tsutsen.platformplayer.core.datastore.model

import kotlinx.serialization.Serializable

/**
 * A user-created theme: a few key colors that [com.tsutsen.platformplayer.core.designsystem.theme.ThemeEngine]
 * expands into a full light + dark Material 3 [androidx.compose.material3.ColorScheme].
 *
 * Stored compactly (name + source colors + palette style) — the scheme is
 * regenerated on demand, so themes stay a small list, not a blob cache.
 */
@Serializable
data class CustomTheme(
    val id: String,
    val name: String,
    val primary: Int,
    val secondary: Int? = null,
    val tertiary: Int? = null,
    /** Optional surface tint: when set, light+dark neutrals come from this color. */
    val background: Int? = null,
    val paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
    /**
     * Contrast refinement, -1..1 per the material-color-utilities spec:
     * -1 = minimum, 0 = standard (as specced), 1 = maximum. Adjusts the
     * per-role contrast curves, so text-on-container pairs get bolder or
     * softer while the palette itself stays the same.
     */
    val contrast: Float = 0f,
)

/**
 * Which algorithm derives the 40 scheme roles from the source palettes —
 * same family as the Material color-utilities generators (PixelPlayer uses
 * the same set).
 */
@Serializable
enum class PaletteStyle {
    TONAL_SPOT,
    VIBRANT,
    EXPRESSIVE,
    FRUIT_SALAD,
}
