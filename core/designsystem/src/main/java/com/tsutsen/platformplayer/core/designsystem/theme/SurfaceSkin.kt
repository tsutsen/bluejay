package com.tsutsen.platformplayer.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Resolved skin for one named surface: user overrides layered over the
 * active theme. Null = fall through to whatever the component already uses
 * (MaterialTheme.colorScheme). Provided by [BluejayTheme] from the active
 * theme's Custom tab (Settings > Themes > editor).
 */
data class SurfaceSkin(
    val color: Color? = null,
    val outlineColor: Color? = null,
    val outlineWidth: Dp? = null,
    /** Texture (decoded from the user-uploaded file); drawn tiled over the color. */
    val texture: ImageBitmap? = null,
    /** Text/icon color override; null = theme default. */
    val contentColor: Color? = null,
) {
    val hasOutline: Boolean
        get() = outlineColor != null && outlineWidth != null && outlineWidth > 0.dp
}

val LocalSurfaceSkins = staticCompositionLocalOf<Map<String, SurfaceSkin>> { emptyMap() }

/** Named surfaces a skin can be attached to (theme editor > Custom tab). */
object SurfaceKeys {
    const val DOCK = "dock"
    const val TILE = "tile"
    const val VIDEO_CARD = "videoCard"
    const val SETTINGS_ROW = "settingsRow"
    const val SHEET = "sheet"
    const val VIDEO_PAGE = "videoPage"

    val ALL = listOf(DOCK, TILE, VIDEO_CARD, SETTINGS_ROW, SHEET, VIDEO_PAGE)

    fun label(key: String): String =
        when (key) {
            DOCK -> "Dock"
            TILE -> "Tiles"
            VIDEO_CARD -> "Video cards"
            SETTINGS_ROW -> "Settings rows"
            SHEET -> "Sheets"
            VIDEO_PAGE -> "Video page"
            else -> key
        }
}

/** The user skin for [key], or the empty skin (everything falls through). */
@Composable
fun surfaceSkin(key: String): SurfaceSkin =
    LocalSurfaceSkins.current[key] ?: SurfaceSkin()

/** Container color for a surface: the skin override, or [fallback]. */
@Composable
fun surfaceColor(
    key: String,
    fallback: Color,
): Color = LocalSurfaceSkins.current[key]?.color ?: fallback

/**
 * Tiles the surface's texture over whatever is drawn behind this modifier
 * (place it after the color background). No-op when the skin has no texture.
 */
@Composable
fun Modifier.surfaceTexture(key: String): Modifier {
    val texture = LocalSurfaceSkins.current[key]?.texture ?: return this
    return this.drawBehind {
        var y = 0
        while (y < size.height.roundToInt()) {
            var x = 0
            while (x < size.width.roundToInt()) {
                drawImage(texture, dstOffset = IntOffset(x, y))
                x += texture.width
            }
            y += texture.height
        }
    }
}

/**
 * Text/icon color for a surface: the skin override, or [fallback].
 */
@Composable
fun surfaceContentColor(
    key: String,
    fallback: Color,
): Color = LocalSurfaceSkins.current[key]?.contentColor ?: fallback

/**
 * The surface's outline as an M3 Card [BorderStroke] (Cards draw their own
 * background over a modifier-drawn border, so they need the param), or null.
 */
@Composable
fun surfaceBorder(key: String): BorderStroke? {
    val skin = surfaceSkin(key)
    return if (skin.hasOutline) BorderStroke(skin.outlineWidth!!, skin.outlineColor!!) else null
}

/**
 * Draws the surface's user outline (when one is set) with the same [shape]
 * the surface clips itself to. No-op on the default skin.
 */
@Composable
fun Modifier.surfaceOutline(
    key: String,
    shape: Shape,
): Modifier {
    val skin = LocalSurfaceSkins.current[key] ?: return this
    if (!skin.hasOutline) return this
    return this.border(skin.outlineWidth!!, skin.outlineColor!!, shape)
}



/**
 * Everything a surface needs, resolved once: call [surfaceTheme] at the top
 * of a skinned component instead of composing 3–5 helpers at each usage.
 */
data class SurfaceTheme(
    val color: Color,
    val contentColor: Color,
    val outlineWidth: Dp,
    val outlineColor: Color,
    val texture: ImageBitmap?,
) {
    val hasOutline: Boolean get() = outlineWidth > 0.dp

    companion object {
        @Composable
        fun of(
            key: String,
            fallbackColor: Color,
            fallbackContent: Color,
        ): SurfaceTheme {
            val skin = surfaceSkin(key)
            return SurfaceTheme(
                color = skin.color ?: fallbackColor,
                contentColor = skin.contentColor ?: fallbackContent,
                outlineWidth = skin.outlineWidth ?: 0.dp,
                outlineColor = skin.outlineColor ?: Color.Unspecified,
                texture = skin.texture,
            )
        }
    }
}

@Composable
fun surfaceTheme(
    key: String,
    fallbackColor: Color,
    fallbackContent: Color,
): SurfaceTheme = SurfaceTheme.of(key, fallbackColor, fallbackContent)
