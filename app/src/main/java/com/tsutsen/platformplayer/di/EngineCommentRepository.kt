package com.tsutsen.platformplayer.di

import android.util.Log
import com.tsutsen.platformplayer.api.media.models.comments.IPlatformComment
import com.tsutsen.platformplayer.api.media.models.ratings.IRating
import com.tsutsen.platformplayer.api.media.models.ratings.RatingLikeDislikes
import com.tsutsen.platformplayer.api.media.models.ratings.RatingLikes
import com.tsutsen.platformplayer.core.data.repository.CommentRepository
import com.tsutsen.platformplayer.core.model.CommentItem
import com.tsutsen.platformplayer.states.StatePlatform
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Engine-based comment repository.
 * Fetches comments from the engine plugin (YouTube, etc.) and maps them to CommentItem.
 */
@Singleton
class EngineCommentRepository @Inject constructor() : CommentRepository {

    private val TAG = "EngineCommentRepository"
    private val commentPagers = mutableMapOf<String, Any>()
    private val fetchedUrls = mutableSetOf<String>()
    private val lastResultCount = mutableMapOf<String, Int>()

    override suspend fun getComments(contentUrl: String): List<CommentItem> {
        return try {
            Log.i(TAG, "========================================")
            Log.i(TAG, "Fetching comments for: $contentUrl")
            Log.i(TAG, "========================================")
            
            // Get or create pager
            if (commentPagers[contentUrl] == null) {
                Log.i(TAG, "Getting new pager...")
                val pager = StatePlatform.instance.getComments(contentUrl)
                commentPagers[contentUrl] = pager
            }
            
            val pager = commentPagers[contentUrl] as com.tsutsen.platformplayer.api.media.structures.IPager<*>
            // Only call nextPage() if we haven't fetched this URL before
            if (contentUrl !in fetchedUrls) {
                pager.nextPage()
                fetchedUrls.add(contentUrl)
            }
            val results = pager.getResults()
            
            Log.i(TAG, "Results size: ${results.size}")
            val comments = mutableListOf<CommentItem>()
            for (item in results) {
                if (item is IPlatformComment) {
                    mapToCommentItem(item)?.let { comments.add(it) }
                }
            }
            
            Log.i(TAG, "Fetched ${comments.size} comments")
            comments
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch comments", e)
            emptyList()
        }
    }
    
    override suspend fun loadMoreComments(contentUrl: String): List<CommentItem> {
        return try {
            Log.i(TAG, "Loading more comments for: $contentUrl")
            
            val pager = commentPagers[contentUrl] ?: return emptyList()
            val iPager = pager as com.tsutsen.platformplayer.api.media.structures.IPager<*>
            iPager.nextPage()
            val results = iPager.getResults()
            
            // Only return the NEW items (delta from previous call)
            val prevCount = lastResultCount[contentUrl] ?: 0
            val newResults = results.drop(prevCount)
            lastResultCount[contentUrl] = results.size
            
            Log.i(TAG, "More results size: ${results.size}, new items: ${newResults.size}")
            val comments = mutableListOf<CommentItem>()
            for (item in newResults) {
                if (item is IPlatformComment) {
                    mapToCommentItem(item)?.let { comments.add(it) }
                }
            }
            
            Log.i(TAG, "Loaded ${comments.size} more comments")
            comments
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load more comments", e)
            emptyList()
        }
    }

    /**
     * The platform has no per-comment id — [IPlatformComment.contextUrl] is
     * the *video* url, so every comment of a video would share one id and
     * any keyed Lazy* list crashes on the second comment. Synthesize unique
     * ids instead (nothing persists them, so a fresh value per mapping is
     * fine).
     */
    private val commentIdCounter = AtomicInteger(0)

    private fun mapToCommentItem(comment: IPlatformComment): CommentItem? {
        return try {
            val author = comment.author
            val (likeCount, _) = extractLikesDislikes(comment.rating)

            CommentItem(
                id = "${comment.contextUrl}#${commentIdCounter.incrementAndGet()}",
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
