package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.ui.geometry.Offset

enum class GestureRow { TOP, MIDDLE, BOTTOM }
enum class GestureColumn { LEFT, CENTER, RIGHT }

data class GestureZone(val row: GestureRow, val column: GestureColumn) {
    companion object {
        val ALL: List<GestureZone> =
            GestureRow.entries.flatMap { r -> GestureColumn.entries.map { c -> GestureZone(r, c) } }
    }
}

fun resolveGestureZone(position: Offset, areaWidth: Float, areaHeight: Float): GestureZone {
    val row = when {
        position.y < areaHeight / 3f -> GestureRow.TOP
        position.y > areaHeight * 2f / 3f -> GestureRow.BOTTOM
        else -> GestureRow.MIDDLE
    }
    val column = when {
        position.x < areaWidth / 3f -> GestureColumn.LEFT
        position.x > areaWidth * 2f / 3f -> GestureColumn.RIGHT
        else -> GestureColumn.CENTER
    }
    return GestureZone(row, column)
}
