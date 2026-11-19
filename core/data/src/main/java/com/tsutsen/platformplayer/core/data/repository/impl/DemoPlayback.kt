package com.tsutsen.platformplayer.core.data.repository.impl

/**
 * Deterministic derivation for demo-mode content, keyed by a single seed.
 *
 * Lives in core/data (not the app module) so both the feed surfaces
 * (via the app's DemoContent) and the demo playback branch in
 * [PlayerRepositoryImpl] share ONE source of truth for the title / channel /
 * duration formulas — a demo card tapped in the feed always resolves to the
 * same title during playback.
 *
 * Demo urls are `demo://video/<page>/<index>`; the seed is
 * `page * 1000 + index`, so pages are stable across reloads and paging.
 */
object DemoPlayback {
    const val URL_PREFIX = "demo://video/"
    const val SOURCE_ID = "demo"

    /** Per-item image duration for the demo slideshow playback (Media3 image playback). */
    const val IMAGE_DURATION_MS = 5_000L
    const val IMAGE_COUNT = 6
    const val TOTAL_DURATION_MS = IMAGE_DURATION_MS * IMAGE_COUNT

    // Base artwork titles (demo_1.jpg .. demo_6.jpg in core/ui drawable-nodpi).
    val TITLES =
        listOf(
            "Van Gogh — Self-portrait study",
            "Dunes at Carson Desert, Nevada",
            "A Sheet of Studies and Sketches",
            "The Biglin Brothers Turning the Stake",
            "Twilight in the Wilderness",
            "Winter Trees Reflected in a Pond",
        )
    private val SUFFIXES =
        listOf(
            "",
            " — full documentary",
            " — restored print",
            " — commentary",
            " — remastered",
        )

    /** Seed for a demo url: `page * 1000 + index` (stable per page/index). */
    fun seedFor(page: Int, index: Int): Long = page * 1000L + index

    /** @return (page, index) of a `demo://video/...` url, or null when it isn't one. */
    fun parseUrl(url: String): Pair<Int, Int>? {
        if (!url.startsWith(URL_PREFIX)) return null
        val parts = url.removePrefix(URL_PREFIX).split('/')
        val page = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val index = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return page to index
    }

    /** Deterministic title: base artwork title cycling with a suffix. */
    fun title(seed: Long): String {
        val base = TITLES[(seed % TITLES.size).toInt()]
        val suffix = SUFFIXES[((seed / TITLES.size) % SUFFIXES.size).toInt()]
        return base + suffix
    }

    /** Deterministic channel name (Demo Channel 1..4). */
    fun channel(seed: Long): String = "Demo Channel ${(seed % 4) + 1}"

    /** Deterministic channel url — also the demo SubscriptionCreator id. */
    fun channelUrl(seed: Long): String = "demo://channel/${(seed % 4) + 1}"

    /** Deterministic duration, 4-60 minutes. */
    fun durationMs(seed: Long): Long = (4 + (seed * 37 % 57)) * 60L * 1000L

    /** Deterministic view count. */
    fun viewCount(seed: Long): Long = 1_000 + (seed * 7_919L) % 4_000_000L

    /** Deterministic published offset, 0-29 days back from now. */
    fun publishedAt(seed: Long): Long =
        System.currentTimeMillis() - (seed % 30) * 24L * 60 * 60 * 1000

    /** Drawable resource name of the card thumbnail (demo_1 .. demo_6). */
    fun thumbnailResName(seed: Long): String = "demo_${(seed % IMAGE_COUNT) + 1}"

    /** Drawable resource name of the channel avatar (offset so it differs from the thumbnail). */
    fun avatarResName(seed: Long): String = "demo_${((seed / 3) % IMAGE_COUNT) + 1}"
}
