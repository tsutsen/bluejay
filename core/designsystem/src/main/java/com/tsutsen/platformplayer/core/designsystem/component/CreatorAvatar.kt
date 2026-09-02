package com.tsutsen.platformplayer.core.designsystem.component

import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Creator avatar component for subscription strips.
 */
@Composable
fun CreatorAvatar(
    thumbnailUrl: String?,
    name: String,
    hasNewContent: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.expressiveClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = name,
            modifier = Modifier
                .size(Tokens.AvatarXl)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        if (hasNewContent) {
            Box(
                modifier = Modifier
                    .offset(x = 36.dp, y = (-4).dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .then(Modifier) // Red dot will be added with color
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Tokens.SpaceXs)
        )
    }
}
