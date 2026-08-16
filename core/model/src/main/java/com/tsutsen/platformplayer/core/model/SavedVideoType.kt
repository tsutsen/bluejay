package com.tsutsen.platformplayer.core.model

/**
 * Save destinations for the "Save" sheet actions. A video can hold several
 * types at once (e.g. Watch Later + Favourite).
 */
enum class SavedVideoType {
    WATCH_LATER,
    LIKED,
    FAVOURITE,
}
