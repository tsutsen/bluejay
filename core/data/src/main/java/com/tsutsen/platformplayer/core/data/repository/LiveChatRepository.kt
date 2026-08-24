package com.tsutsen.platformplayer.core.data.repository

import com.tsutsen.platformplayer.core.model.LiveChatUiState
import kotlinx.coroutines.flow.StateFlow

/**
 * Live chat for a currently playing video.
 * [state] is null when no chat session is active.
 */
interface LiveChatRepository {
    val state: StateFlow<LiveChatUiState?>

    /**
     * Start (or switch to) the chat for [url]. Suspends until the chat
     * source is resolved; unavailable chat surfaces as [LiveChatUiState.error].
     */
    suspend fun start(url: String)

    fun stop()
}
