package com.tsutsen.platformplayer.feature.settings.impl

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.groupShape
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.designsystem.theme.surfaceBorder
import com.tsutsen.platformplayer.core.designsystem.theme.SurfaceKeys
import com.tsutsen.platformplayer.core.ui.R
import com.tsutsen.platformplayer.core.ui.rememberThumbnailColorFilter
import com.tsutsen.platformplayer.core.ui.thumbnailTintBlend
import com.tsutsen.platformplayer.core.ui.thumbnailTintOverlay
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import com.tsutsen.platformplayer.core.ui.LocalThumbnailTint
import com.tsutsen.platformplayer.core.ui.LocalThumbnailTintBlendMode
import com.tsutsen.platformplayer.core.ui.LocalThumbnailTintStrength

/**
 * Appearance → Thumbnails: a regular settings row whose right side shows a
 * small sample thumbnail rendered through the live pipeline (tint overlay,
 * desaturation, contrast — all via the composition locals), so the sliders
 * below update it in real time.
 */
@Composable
internal fun ThumbnailPreviewRow(
    modifier: Modifier = Modifier,
    groupPosition: GroupPosition = GroupPosition.Single,
) {
    val samplePainter = painterResource(R.drawable.sample_video_thumbnail)
    // Recomputed on every locals change (reads happen in the composition).
    val colorFilter = rememberThumbnailColorFilter()
    val tint = LocalThumbnailTint.current
    val tintStrength = LocalThumbnailTintStrength.current
    val tintBlend = thumbnailTintBlend(LocalThumbnailTintBlendMode.current)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = groupShape(groupPosition),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = surfaceBorder(SurfaceKeys.SETTINGS_ROW),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Tokens.SpaceMd),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_thumbnail_preview),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.settings_thumbnail_preview_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .width(96.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(BluejayTokens().radius.sm))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Image(
                        painter = samplePainter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .thumbnailTintOverlay(
                                    tint = tint,
                                    strength = tintStrength,
                                    blend = tintBlend,
                                ),
                    )
                }
            }
        }
    }
}
