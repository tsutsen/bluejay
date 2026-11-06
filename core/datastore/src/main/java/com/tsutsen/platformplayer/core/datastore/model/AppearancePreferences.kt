package com.tsutsen.platformplayer.core.datastore.model

data class AppearancePreferences(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val fontFamily: FontFamily = FontFamily.DEFAULT,
    val iconStyle: IconStyle = IconStyle.ROUNDED,
    val fontSizeScale: Float = 1.0f,
    val dynamicColor: Boolean = true,
    /** 0..100 — 100 is the shipped rounding, 0 is sharp. */
    val uiRounding: Int = 100,
    /** User-created themes (Settings > Appearance > Custom themes). */
    val customThemes: List<CustomTheme> = emptyList(),
    /** Active custom theme id; null = default (Material You / brand). */
    val activeThemeId: String? = null,
    /** Thumbnail tint overlay (ARGB); null = none. */
    val thumbnailTintArgb: Long? = null,
    /**
     * Tint strength 0..1: the share of the tint mixed into every thumbnail.
     * The stored color is used opaque; its baked-in alpha (the old picker
     * stored a 30% wash) is ignored in favor of this.
     */
    val thumbnailTintStrength: Float = 0.35f,
    /** How the tint combines with the image ([TintBlendMode] name). */
    val thumbnailTintBlendMode: String = TintBlendMode.WASH.name,
    /** Thumbnail desaturation: 1 = full color, 0 = grayscale. */
    val thumbnailSaturation: Float = 1f,
)

/** How the thumbnail tint combines with the image (Appearance > Thumbnails). */
enum class TintBlendMode {
    /** Translucent color laid over the image (lightens toward the tint). */
    WASH,
    /** Image x tint: deepens with dark colors, white = no-op. */
    MULTIPLY,
    /** Inverse multiply: lightens, black = no-op. */
    SCREEN,
    /** Contrast-preserving mix: darks take the tint, highlights keep detail. */
    OVERLAY,
    /** Gentler overlay. */
    SOFT_LIGHT,
    /** Darkens with the tint while keeping highlights. */
    COLOR_BURN,
}

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class FontFamily { DEFAULT, INTER, ROBOTO, OPEN_SANS }

enum class IconStyle { ROUNDED, SHARP, OUTLINED }
