package com.tsutsen.platformplayer.feature.player.impl

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.model.VideoCard as VideoCardModel

/**
 * One row of the recommended videos grid: [cards] video cards side by side
 * (the grid-columns setting determines how many per row), 16.dp horizontal
 * padding like the rest of the details page.
 */
@Composable
internal fun RecommendedGridRow(
    cards: List<VideoCardModel>,
    onClick: (VideoCardModel) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
    ) {
        cards.forEachIndexed { index, card ->
            RecommendedGridCell(
                card = card,
                onClick = { onClick(card) },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = if (index == 0) 0.dp else 12.dp),
            )
        }
    }
}

@Composable
private fun RecommendedGridCell(
    card: VideoCardModel,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    VideoCard(
        card = card,
        onClick = onClick,
        modifier = modifier,
    )
}
