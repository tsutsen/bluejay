package com.tsutsen.platformplayer.feature.library.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tsutsen.platformplayer.core.designsystem.component.EmptyState
import com.tsutsen.platformplayer.core.designsystem.component.VideoCard
import com.tsutsen.platformplayer.core.designsystem.component.VideoCardSkeleton
import com.tsutsen.platformplayer.core.model.Card
import com.tsutsen.platformplayer.core.model.LibrarySection
import com.tsutsen.platformplayer.core.model.PlaylistCard
import com.tsutsen.platformplayer.core.model.VideoCard as CoreVideoCard
import com.tsutsen.platformplayer.core.navigation.Navigator

/**
 * Library screen composable.
 * Shows three vertical-scroll sections: History, Watch Later, Playlists.
 * Each section has a clickable header that opens the detail page.
 */
@Composable
fun LibraryScreen(
    navigator: Navigator,
    viewModel: LibraryViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val sections by viewModel.sections.collectAsState()
    val refreshingState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    // Reset refresh state when loading completes
    LaunchedEffect(sections) {
        val allLoaded = sections.all { !it.isLoading }
        if (allLoaded && sections.isNotEmpty()) {
            isRefreshing = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            state = refreshingState,
            onRefresh = {
                isRefreshing = true
            },
            content = {
                if (sections.isEmpty()) {
                    // Show skeletons while loading
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { LibrarySectionSkeleton(title = "History") }
                        item { LibrarySectionSkeleton(title = "Watch Later") }
                        item { LibrarySectionSkeleton(title = "Playlists") }
                    }
                } else {
                    // Render sections vertically
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(sections, key = { it.id }) { section ->
                            LibrarySectionCard(
                                section = section,
                                onClick = { navigator.navigateToLibrarySectionDetail(section.id) }
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * Library section card with vertical scroll.
 * Header is clickable to open detail page.
 */
@Composable
private fun LibrarySectionCard(
    section: LibrarySection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section header - clickable
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View all",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section items - vertical scroll
        if (section.isLoading) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) {
                    VideoCardSkeleton()
                }
            }
        } else if (section.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nothing yet!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                section.items.forEach { card ->
                    LibraryCard(card = card)
                }
            }
        }
    }
}

/**
 * Card for library section (uses same VideoCard as Home/Subscription tabs).
 */
@Composable
private fun LibraryCard(
    card: Card,
    modifier: Modifier = Modifier
) {
    when (card) {
        is CoreVideoCard -> {
            VideoCard(
                card = card,
                onClick = { /* Navigate to video */ }
            )
        }
        is PlaylistCard -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { /* Navigate to playlist */ }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2
                    )
                }
            }
        }
        else -> {
            // Fallback for other card types
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/**
 * Skeleton for library section (shimmer effect).
 */
@Composable
private fun LibrarySectionSkeleton(
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (title != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) {
                VideoCardSkeleton()
            }
        }
    }
}
