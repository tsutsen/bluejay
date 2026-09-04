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
)

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class FontFamily { DEFAULT, INTER, ROBOTO, OPEN_SANS }

enum class IconStyle { ROUNDED, SHARP, OUTLINED }
