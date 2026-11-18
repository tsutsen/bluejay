package com.tsutsen.platformplayer.feature.player.impl

import android.util.Log
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.data.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pagination of the player's side content (comments, recommendations) into
 * the shared [PlayerState]. Extracted from [PlayerViewModel]; the ViewModel
 * keeps thin delegating methods.
 */
internal class PlayerSideContentLoader(
    private val playerRepository: PlayerRepository,
    private val commentRepository: CommentRepository,
    private val scope: CoroutineScope,
) {
    fun loadMoreComments(contentUrl: String) {
        scope.launch {
            // Guard: a faster switch to a new video supersedes this request.
            if (playerRepository.playerState.value.currentVideo
                    ?.url != contentUrl
            ) {
                return@launch
            }
            try {
                val moreComments =
                    withContext(Dispatchers.IO) {
                        commentRepository.loadMoreComments(contentUrl)
                    }

                if (moreComments.isNotEmpty()) {
                    val current = playerRepository.playerState.value
                    playerRepository.setVideoExtras(
                        current.comments + moreComments,
                        current.recommendations,
                    )
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to load more comments", e)
            }
        }
    }

    /** Recommendations tab: fetch the next page and append it to the shared list. */
    fun loadMoreRecommendations(contentUrl: String) {
        scope.launch {
            // Guard: a faster switch to a new video supersedes this request.
            if (playerRepository.playerState.value.currentVideo
                    ?.url != contentUrl
            ) {
                return@launch
            }
            try {
                withContext(Dispatchers.IO) {
                    playerRepository.loadMoreRecommendations(contentUrl)
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to load more recommendations", e)
            }
        }
    }
}
