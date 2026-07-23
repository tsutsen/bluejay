package com.futo.platformplayer.core.data.repository.impl

import com.futo.platformplayer.core.data.repository.HomeRepository
import com.futo.platformplayer.core.model.Card
import com.futo.platformplayer.core.model.ContentType
import com.futo.platformplayer.core.model.FeedPage
import com.futo.platformplayer.core.model.VideoCard
import com.futo.platformplayer.states.StatePlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HomeRepository implementation that bridges to the legacy StatePlatform engine.
 * This is a temporary bridge — Phase 8 will replace this with direct engine calls.
 */
@Singleton
class HomeRepositoryImpl @Inject constructor() : HomeRepository {

    private val _feed = MutableStateFlow(FeedPage())
    override val feed: StateFlow<FeedPage> = _feed.asStateFlow()

    override suspend fun loadInitial() {
        _feed.update { it.copy(isLoading = true, error = null) }
        try {
            // Bridge to StatePlatform.getHomeRefresh
            val pager = runBlocking {
                try {
                    StatePlatform.instance.getHomeRefresh(this)
                } catch (e: Exception) {
                    null
                }
            }

            if (pager != null) {
                // Convert legacy pager results to Card list
                val items = emptyList<Card>() // Will be populated in Phase 3
                _feed.update { it.copy(items = items, isLoading = false, hasMorePages = true) }
            } else {
                _feed.update { it.copy(isLoading = false, error = "Failed to load feed") }
            }
        } catch (e: Exception) {
            _feed.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    override suspend fun loadNextPage() {
        _feed.update { it.copy(isLoading = true) }
        // Bridge to pager.nextPage()
        _feed.update { it.copy(isLoading = false) }
    }

    override suspend fun refresh() {
        loadInitial()
    }

    override suspend fun filterByTag(tag: String) {
        // Bridge to StatePlatform tag filtering
    }

    override suspend fun filterByAuthor(authorId: String) {
        // Bridge to StatePlatform author filtering
    }
}
