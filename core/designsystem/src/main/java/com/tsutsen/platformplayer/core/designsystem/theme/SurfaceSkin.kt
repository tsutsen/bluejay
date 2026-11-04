package com.tsutsen.platformplayer.core.designsystem.theme

import androidx.compose.foundation.background
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

    val ALL = listOf(DOCK, TILE, VIDEO_CARD, SETTINGS_ROW, SHEET)

    fun label(key: String): String =
        when (key) {
            DOCK -> "Dock"
            TILE -> "Tiles"
            VIDEO_CARD -> "Video cards"
            SETTINGS_ROW -> "Settings rows"
            SHEET -> "Sheets"
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


