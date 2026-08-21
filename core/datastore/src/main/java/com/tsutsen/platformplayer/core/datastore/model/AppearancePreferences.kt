package com.tsutsen.platformplayer.core.datastore.model

data class AppearancePreferences(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val fontFamily: FontFamily = FontFamily.DEFAULT,
    val iconStyle: IconStyle = IconStyle.ROUNDED,
    val fontSizeScale: Float = 1.0f,
    val dynamicColor: Boolean = true,
)

enum class ThemeMode { AUTO, LIGHT, DARK }

enum class FontFamily { DEFAULT, INTER, ROBOTO, OPEN_SANS }

enum class IconStyle { ROUNDED, SHARP, OUTLINED }
