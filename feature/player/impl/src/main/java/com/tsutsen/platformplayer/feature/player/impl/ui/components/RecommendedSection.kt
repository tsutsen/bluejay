package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.model.VideoCard as VideoCardModel

/**
 * One row of the recommended videos grid: [cards] video cards side by side
 * (the grid-columns setting determines how many per row), 16.dp horizontal
 * padding like the rest of the details page.
 *
 * Every cell gets a FIXED width (row width / [gridColumns]) rather than a
 * weight, so a partial last row keeps the same card size as a full row
 * instead of stretching to fill the leftover space.
 */
@Composable
internal fun RecommendedGridRow(
    cards: List<VideoCardModel>,
    gridColumns: Int,
    onClick: (VideoCardModel) -> Unit,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
    ) {
        val gap = 12.dp
        val cellWidth = (maxWidth - gap * (gridColumns - 1)) / gridColumns
        Row {
            cards.forEachIndexed { index, card ->
                VideoCard(
                    card = card,
                    onClick = { onClick(card) },
                    modifier =
                        Modifier
                            .padding(start = if (index == 0) 0.dp else gap)
                            .width(cellWidth),
                )
            }
        }
    }
}
