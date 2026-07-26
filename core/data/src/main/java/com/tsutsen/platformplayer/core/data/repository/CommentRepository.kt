package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.CommentItem

/**
 * Repository interface for fetching video comments.
 * Implementations in the app module handle engine-specific comment fetching.
 */
interface CommentRepository {
    suspend fun getComments(contentUrl: String): List<CommentItem>
}
