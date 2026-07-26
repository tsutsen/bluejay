package com.tsutsen.platformplayer.di

import android.util.Log
import com.tsutsen.platformplayer.api.media.models.comments.IPlatformComment
import com.tsutsen.platformplayer.api.media.models.ratings.IRating
import com.tsutsen.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.tsutsen.platformplayer.api.media.models.ratings.RatingLikes
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.states.StatePlatform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine-based comment repository.
 * Fetches comments from the engine plugin (YouTube, etc.) and maps them to CommentItem.
 */
@Singleton
class EngineCommentRepository @Inject constructor() : CommentRepository {

    private val TAG = "EngineCommentRepository"

    override suspend fun getComments(contentUrl: String): List<CommentItem> {
        return try {
            Log.i(TAG, "Fetching comments for: $contentUrl")
            val commentsPager = StatePlatform.instance.getComments(contentUrl)
            val comments = mutableListOf<CommentItem>()
            
            // Load first page of comments
            commentsPager.nextPage()
            val results = commentsPager.getResults()
            
            for (item in results) {
                if (item is IPlatformComment) {
                    val comment = mapToCommentItem(item)
                    if (comment != null) {
                        comments.add(comment)
                    }
                }
            }
            
            Log.i(TAG, "Fetched ${comments.size} comments")
            comments
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch comments", e)
            emptyList()
        }
    }

    private fun mapToCommentItem(comment: IPlatformComment): CommentItem? {
        return try {
            val author = comment.author
            val (likeCount, _) = extractLikesDislikes(comment.rating)
            
            CommentItem(
                id = comment.contextUrl,
                author = author?.name ?: "Unknown",
                authorThumbnailUrl = author?.thumbnail,
                text = comment.message,
                likeCount = likeCount ?: 0,
                replyCount = comment.replyCount ?: 0,
                publishedAtMs = comment.date?.toInstant()?.toEpochMilli()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to map comment", e)
            null
        }
    }

    private fun extractLikesDislikes(rating: IRating?): Pair<Long?, Long?> {
        if (rating == null) return Pair(null, null)
        return when (rating) {
            is RatingLikes -> Pair(rating.likes, null)
            is RatingLikeDislikes -> Pair(rating.likes, rating.dislikes)
            else -> Pair(null, null)
        }
    }
}
