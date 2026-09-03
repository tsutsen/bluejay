package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import com.tsutsen.platformplayer.core.designsystem.theme.RadiusScale

/**
 * Position of an element inside a visually adjacent group (card group,
 * button group). Members stack flush; only [Single] keeps full rounding.
 */
enum class GroupPosition {
    Single, First, Middle, Last;

    companion object {
        /** Position for the element at [index] of [count] group members. */
        fun fromIndex(index: Int, count: Int): GroupPosition =
            when {
                count <= 1 -> Single
                index == 0 -> First
                index == count - 1 -> Last
                else -> Middle
            }
    }
}

/**
 * The three interaction states of a connected button: [shape] at rest,
 * [pressedShape] while pressed, [checkedShape] when toggled/active. M3's
 * [androidx.compose.material3.ButtonShapes] and
 * [androidx.compose.material3.ToggleButtonShapes] are built from these.
 */
data class GroupCornerShapes(
    val shape: Shape,
    val pressedShape: Shape,
    val checkedShape: Shape,
)

/**
 * The app's connected-button-group corner recipe (the like/dislike pill and
 * the download split group): the outer corners follow the user's largest
 * radius, the inner (seam) corners stay small and squish further on press,
 * and a checked button becomes a full pill — per the M3 Expressive
 * connected-button spec, derived from the user's rounding tokens.
 *
 * Percent corners for the pill, exactly like M3's own
 * [androidx.compose.material3.tokens.ShapeTokens.CornerFull]: a Dp-based
 * "500dp" pill would crash, because the buttons morph between shapes with a
 * spring that overshoots, and the corner lerp extrapolates past the target
 * — with 500dp deltas that lands on negative corner radii. Percent corners
 * resolve to half the button's shorter side at outline time, keeping the
 * lerp bounded no matter how far the spring bounces.
 *
 * Rounding ≈ 0: every state takes the same percent pill. With zero Dp
 * radii the shape morphs would spring from a 0 base and overshoot into
 * negative corners; a uniform pill has no morph at all and percent corners
 * can't go negative.
 */
fun connectedGroupShapes(
    position: GroupPosition,
    radius: RadiusScale,
): GroupCornerShapes {
    if (radius.lg.value < 1f) {
        val flat = RoundedCornerShape(CornerSize(100))
        return GroupCornerShapes(flat, flat, flat)
    }
    val outer = radius.lg
    val inner = radius.sm
    val pressedInner = radius.xs
    val pill = RoundedCornerShape(CornerSize(100))
    return when (position) {
        // First (left side): left corners rounded, right (seam) corners small.
        GroupPosition.First ->
            GroupCornerShapes(
                shape = RoundedCornerShape(outer, inner, inner, outer),
                pressedShape = RoundedCornerShape(outer, pressedInner, pressedInner, outer),
                checkedShape = pill,
            )

        // Last (right side): right corners rounded, left (seam) corners small.
        GroupPosition.Last ->
            GroupCornerShapes(
                shape = RoundedCornerShape(inner, outer, outer, inner),
                pressedShape = RoundedCornerShape(pressedInner, outer, outer, pressedInner),
                checkedShape = pill,
            )

        // Single / Middle: no seam, uniform rounding.
        else ->
            GroupCornerShapes(
                shape = RoundedCornerShape(outer),
                pressedShape = RoundedCornerShape(pressedInner),
                checkedShape = pill,
            )
    }
}

/**
 * The corner recipe for groups that live in the sheet's tile grid (the
 * download group): the OUTER corners follow the grid tiles' own states —
 * medium radius at rest, small while pressed, large when active — so the
 * group reads as a tile, not as a capsule. The inner (seam) corners stay
 * small, as in [connectedGroupShapes]. The same zero-rounding guard
 * applies: with Dp radii near 0 the spring-morphed corners overshoot into
 * negative values, so every state takes a percent pill instead.
 */
fun tileFlowGroupShapes(
    position: GroupPosition,
    radius: RadiusScale,
): GroupCornerShapes {
    if (radius.lg.value < 1f) {
        val flat = RoundedCornerShape(CornerSize(100))
        return GroupCornerShapes(flat, flat, flat)
    }
    val rest = radius.md
    val pressed = radius.sm
    val active = radius.lg
    val seamRest = radius.sm
    val seamPressed = radius.xs
    return when (position) {
        // First (left side): left (outer) corners follow the tile states.
        GroupPosition.First ->
            GroupCornerShapes(
                shape = RoundedCornerShape(rest, seamRest, seamRest, rest),
                pressedShape = RoundedCornerShape(pressed, seamPressed, seamPressed, pressed),
                checkedShape = RoundedCornerShape(active, seamRest, seamRest, active),
            )

        // Last (right side): right (outer) corners follow the tile states.
        GroupPosition.Last ->
            GroupCornerShapes(
                shape = RoundedCornerShape(seamRest, rest, rest, seamRest),
                pressedShape = RoundedCornerShape(seamPressed, pressed, pressed, seamPressed),
                checkedShape = RoundedCornerShape(seamRest, active, active, seamRest),
            )

        // Single / Middle: no seam, uniform rounding like a tile.
        else ->
            GroupCornerShapes(
                shape = RoundedCornerShape(rest),
                pressedShape = RoundedCornerShape(pressed),
                checkedShape = RoundedCornerShape(active),
            )
    }
}
