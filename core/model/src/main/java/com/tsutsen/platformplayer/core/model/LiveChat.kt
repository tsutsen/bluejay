package com.tsutsen.platformplayer.core.model

/**
 * Snapshot of a live chat session, as consumed by the player UI.
 * Decoupled from the app-module IPlatformLiveEvent types (which are
 * Javet-coupled) so the feature layer stays app-free.
 */
data class LiveChatUiState(
    val viewCount: Long? = null,
    val entries: List<LiveChatEntry> = emptyList(),
    /** Emote name -> image URL (from the streamer's emote set). */
    val emoteUrls: Map<String, String> = emptyMap(),
    val error: String? = null,
)

sealed interface LiveChatEntry {
    /**
     * Monotonic id assigned when the entry enters the UI list. The chat
     * list keys on this: timeMs alone is not unique (plugins may not send
     * timestamps — the default -1 duplicated and crashed the LazyColumn —
     * and overlapping poll windows can redeliver events).
     */
    val uid: Long
    val timeMs: Long

    data class ChatMessage(
        override val uid: Long,
        override val timeMs: Long,
        val name: String,
        val colorName: String?,
        val badge: String?,
        val text: String,
        val thumbnail: String? = null,
    ) : LiveChatEntry

    data class Raid(
        override val uid: Long,
        override val timeMs: Long,
        val targetName: String,
        val targetUrl: String,
        val isOutgoing: Boolean,
    ) : LiveChatEntry

    data class Donation(
        override val uid: Long,
        override val timeMs: Long,
        val name: String,
        val message: String,
    ) : LiveChatEntry
}
