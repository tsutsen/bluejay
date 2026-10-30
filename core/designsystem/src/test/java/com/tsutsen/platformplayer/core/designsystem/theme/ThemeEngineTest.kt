package com.tsutsen.platformplayer.core.designsystem.theme

import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Hct
import com.tsutsen.platformplayer.core.datastore.model.PaletteStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeEngineTest {
    private val primary = 0xFF6750A4.toInt()

    @Test
    fun `styles produce distinct secondary and tertiary for the same primary`() {
        val light = PaletteStyle.entries.associateWith { ThemeEngine.generate(primary, null, null, it).light }

        val secondaries = light.values.map { it.secondary }
        val tertiaries = light.values.map { it.tertiary }

        assertEquals(secondaries.size, secondaries.distinct().size)
        assertEquals(tertiaries.size, tertiaries.distinct().size)

        // Styles must read as distinct in the swatch: secondary chroma spread
        // across styles is wide (muted tonal-spot vs vivid vibrant).
        val secChromas = light.values.map { Hct.fromInt(it.secondary.toArgb()).chroma }
        assertTrue(
            "secondary chroma spread too small: $secChromas",
            secChromas.max() - secChromas.min() >= 24.0,
        )
    }

    @Test
    fun `picked key colors stay verbatim in the scheme`() {
        val secondary = 0xFF00C853.toInt()
        val tertiary = 0xFFFF6D00.toInt()

        val scheme =
            ThemeEngine.generate(primary, secondary, tertiary, PaletteStyle.VIBRANT).light

        // secondary/tertiary roles read tones 80/90 of the picked palettes.
        assertEquals(
            Hct.fromInt(0xFF00C853.toInt()).hue,
            Hct.fromInt(scheme.secondary.toArgb()).hue,
            1.0,
        )
        assertEquals(
            Hct.fromInt(0xFFFF6D00.toInt()).hue,
            Hct.fromInt(scheme.tertiary.toArgb()).hue,
            1.0,
        )
    }

    @Test
    fun `background sets neutral hue at low chroma and distinct neutralVariant`() {
        val background = 0xFF0000FF.toInt() // vivid blue: full chroma must NOT reach surfaces
        val backgroundHue = Hct.fromInt(background).hue

        val schemes =
            ThemeEngine.generate(primary, null, null, PaletteStyle.TONAL_SPOT, background = background)

        val surface = Hct.fromInt(schemes.light.surface.toArgb())
        val surfaceVariant = Hct.fromInt(schemes.light.surfaceVariant.toArgb())

        // neutral + neutralVariant keep the background hue… (tolerance covers
        // 8-bit ARGB quantization at near-neutral chroma)
        assertEquals(backgroundHue, surface.hue, 5.0)
        assertEquals(backgroundHue, surfaceVariant.hue, 5.0)
        // …at low chroma (spec neutral 6 / neutralVariant 8), not the background's chroma…
        assertTrue(surface.chroma < 20.0)
        assertTrue(surfaceVariant.chroma < 20.0)
        // …and the two palettes stay distinct.
        assertTrue(surfaceVariant.chroma > surface.chroma)
        assertNotEquals(schemes.light.surface, schemes.light.surfaceVariant)
    }

    @Test
    fun `light and dark schemes differ`() {
        val schemes = ThemeEngine.generate(primary, null, null, PaletteStyle.TONAL_SPOT)

        assertNotEquals(schemes.light.primary, schemes.dark.primary)
        assertNotEquals(schemes.light.surface, schemes.dark.surface)
        assertNotEquals(schemes.light.onSurface, schemes.dark.onSurface)
    }
}
