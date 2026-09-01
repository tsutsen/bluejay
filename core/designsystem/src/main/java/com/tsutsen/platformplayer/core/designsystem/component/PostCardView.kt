package com.tsutsen.platformplayer.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.ArticleCard
import com.tsutsen.platformplayer.core.model.PostCard
import com.tsutsen.platformplayer.core.ui.AsyncImage
import com.tsutsen.platformplayer.core.ui.RelativeTime

/**
 * Text-forward card for a post (thumbnail optional, right-aligned).
 */
@Composable
fun PostCardView(card: PostCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextContentCard(card.title, card.thumbnailUrl, card.author, card.publishedAt, onClick, modifier)
}

/**
 * Text-forward card for an article/web page (thumbnail optional, right-aligned).
 */
@Composable
fun ArticleCardView(card: ArticleCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextContentCard(card.title, card.thumbnailUrl, card.author, card.publishedAt, onClick, modifier)
}

@Composable
private fun TextContentCard(
    title: String,
    thumbnailUrl: String?,
    author: String?,
    publishedAt: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildString {
                    author?.let { append(it) }
                    publishedAt?.let {
                        if (isNotEmpty()) append(" • ")
                        append(RelativeTime.format(it))
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            thumbnailUrl?.let {
                AsyncImage(
                    url = it,
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp, 54.dp)
                        .clip(RoundedCornerShape(Tokens.RadiusSm)),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
