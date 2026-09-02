package com.tsutsen.platformplayer.core.designsystem.component

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
