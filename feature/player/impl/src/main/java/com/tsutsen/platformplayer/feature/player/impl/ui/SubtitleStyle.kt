package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Styling of the in-player subtitle overlay.
 *
 * Fields are mutable `var`s on purpose: they are the single place the
 * subtitle look is defined today, and the intended hook for exposing
 * these as settings later (e.g. a [SubtitleStyle] read from
 * SettingsRepository at composition time).
 */
object SubtitleStyle {
    var fontFamily: FontFamily = FontFamily.Default
    var fontWeight: FontWeight = FontWeight.Medium
    var fontStyle: FontStyle = FontStyle.Normal
    var fontSize: Float = 16f
    var textColor: Color = Color.White

    /** Line height in sp; tighter than the font's default (~18.75 for 16sp). */
    var lineHeight: Float = 18f

    /** Gap between the video's bottom edge and the subtitle text. */
    var bottomPadding: Dp = 8.dp

    /** Crisp glyph outline, drawn as an offset ring of copies. */
    var outlineColor: Color = Color.Black
    var outlineWidth: Float = 1.5f

    /** Opaque plate behind the text. Disabled by default. */
    var backdropEnabled: Boolean = false
    var backdropColor: Color = Color(0x99000000)

    /**
     * Applies the user's subtitle settings (font family, size in pt,
     * bottom padding in dp). Called from the player ViewModel whenever
     * the preferences change.
     */
    fun applyPreferences(font: String, size: Int, padding: Int) {
        fontFamily =
            when (font) {
                "sans" -> FontFamily.SansSerif
                "serif" -> FontFamily.Serif
                "mono" -> FontFamily.Monospace
                else -> FontFamily.Default
            }
        fontSize = size.toFloat().coerceIn(8f, 32f)
        lineHeight = fontSize * 1.125f
        bottomPadding = padding.coerceIn(0, 80).dp
    }
}
