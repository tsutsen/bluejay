package com.futo.platformplayer.compose.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.futo.platformplayer.api.media.models.playlists.IPlatformPlaylist
import com.futo.platformplayer.api.media.models.post.IPlatformPost
import com.futo.platformplayer.api.media.models.video.IPlatformVideo
import com.futo.platformplayer.api.media.structures.IRefreshPager
import com.futo.platformplayer.api.media.structures.ReusableRefreshPager
import com.futo.platformplayer.fragment.mainactivity.main.ArticleDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment
import com.futo.platformplayer.fragment.mainactivity.main.PostDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.RemotePlaylistFragment
import com.futo.platformplayer.fragment.mainactivity.main.VideoDetailFragment
import com.futo.platformplayer.fragment.mainactivity.main.WebDetailFragment
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.launch

class FeedFragment : MainFragment() {
    override val isMainView: Boolean = true
    override val isOverlay: Boolean = false
    override val isHistory: Boolean = false
    override val hasBottomBar: Boolean = true
    override val isComposeMode: Boolean = true

    @Composable
    override fun ComposeContent() {
        var uiState by remember { mutableStateOf(FeedUiState(isLoading = true)) }
        var pager by remember { mutableStateOf<ReusableRefreshPager<com.futo.platformplayer.api.media.models.contents.IPlatformContent>?>(null) }
        var items by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
        var contentList by remember { mutableStateOf<List<com.futo.platformplayer.api.media.models.contents.IPlatformContent>>(emptyList()) }

        // Initialize pager
        DisposableEffect(Unit) {
            lifecycleScope.launch {
                try {
                    val p = StatePlatform.instance.getHomeRefresh(lifecycleScope)
                    if (p is IRefreshPager) {
                        val rp = ReusableRefreshPager(p)
                        pager = rp
                        rp.nextPage()
                        val loaded = rp.getResults()
                        val feedItems = loaded.map { toFeedItem(it) }
                        contentList = loaded
                        items = feedItems
                        uiState = FeedUiState(isLoading = false, items = feedItems)
                    }
                } catch (e: Exception) {
                    uiState = uiState.copy(isLoading = false, error = e.message)
                }
            }
            onDispose {
                pager = null
                items = emptyList()
                contentList = emptyList()
            }
        }

        FeedScreen(
            state = uiState,
            bottomBarHeight = 0.dp,
            onRefresh = {
                pager?.let { p ->
                    p.nextPage()
                    val loaded = p.getResults()
                    val feedItems = loaded.map { toFeedItem(it) }
                    contentList = loaded
                    items = feedItems
                    uiState = uiState.copy(items = feedItems)
                }
            },
            onLoadMore = {
                pager?.let { p ->
                    if (p.hasMorePages()) {
                        p.nextPage()
                        val loaded = p.getResults()
                        val feedItems = loaded.map { toFeedItem(it) }
                        contentList = loaded
                        items = feedItems
                        uiState = uiState.copy(items = feedItems)
                    }
                }
            },
            onItemClicked = { id ->
                val content = contentList.find { it.id?.value == id }
                val ma = activity as? com.futo.platformplayer.activities.MainActivity ?: return@FeedScreen
                when (content) {
                    is IPlatformVideo -> {
                        // Use MainActivity.navigate() which handles container visibility and maximization
                        ma.navigate(ma._fragVideoDetail, content, true, false)
                    }
                    is IPlatformPlaylist -> {
                        ma.navigate(RemotePlaylistFragment.newInstance(), content, true, false)
                    }
                    is IPlatformPost -> {
                        ma.navigate(PostDetailFragment.newInstance(), content, true, false)
                    }
                    is com.futo.platformplayer.api.media.models.article.IPlatformArticle -> {
                        ma.navigate(ArticleDetailFragment.newInstance(), content, true, false)
                    }
                    is com.futo.platformplayer.api.media.platforms.js.models.JSWeb -> {
                        ma.navigate(WebDetailFragment.newInstance(), content, true, false)
                    }
                }
            },
            onSortChanged = {},
            onTagClicked = {},
            modifier = Modifier.fillMaxSize()
        )
    }

    private fun toFeedItem(content: com.futo.platformplayer.api.media.models.contents.IPlatformContent): FeedItem {
        val thumbnailUrl = when (content) {
            is IPlatformVideo -> content.thumbnails.getHQThumbnail()
            else -> null
        }
        return FeedItem(
            id = content.id?.value ?: "",
            title = content.name ?: "",
            subtitle = content.author?.name,
            thumbnailUrl = thumbnailUrl,
            timestamp = null
        )
    }
}
