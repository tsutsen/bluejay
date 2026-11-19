package com.tsutsen.platformplayer.demo

import android.net.Uri
import com.tsutsen.platformplayer.core.ui.R
import com.tsutsen.platformplayer.core.data.repository.impl.DemoPlayback
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.model.ContentItem
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.di.SettingsRepositoryImpl
import com.tsutsen.platformplayer.states.StateApp

/**
 * Demo mode (Settings > About) content generator: simulated local video
 * cards backed by the bundled demo images (core/ui drawable-nodpi
 * demo_1.jpg .. demo_6.jpg), never fetched from the network.
 *
 * All per-card fields derive deterministically from the seed
 * (see [DemoPlayback]) so pages are stable across reloads and paging, and a
 * card's title/thumbnail during playback matches what the feed showed.
 */
object DemoContent {
    /** Whether demo mode is ON (read per-call; takes effect on next load/refresh). */
    fun demoModeEnabled(prefs: AppPreferences? = null): Boolean =
        prefs?.other?.demoMode
            ?: SettingsRepositoryImpl.instance.preferences.value.other.demoMode

    /** Deterministic page of demo cards (24 per page). */
    fun cards(page: Int, count: Int = 24): List<VideoCard> =
        (0 until count).map { index ->
            card(page, index)
        }

    private fun card(page: Int, index: Int): VideoCard {
        val seed = DemoPlayback.seedFor(page, index)
        return VideoCard(
            id = "demo-$page-$index",
            title = DemoPlayback.title(seed),
            thumbnailUrl = thumbnailUri(seed),
            author = DemoPlayback.channel(seed),
            authorUrl = DemoPlayback.channelUrl(seed),
            authorThumbnailUrl = avatarUri(seed),
            durationMs = DemoPlayback.durationMs(seed),
            viewCount = DemoPlayback.viewCount(seed),
            publishedAt = DemoPlayback.publishedAt(seed),
            url = DemoPlayback.URL_PREFIX + page + "/" + index,
            sourceId = DemoPlayback.SOURCE_ID,
        )
    }

    /**
     * The [ContentItem] for a `demo://video/...` url (title/thumbnail from
     * the same mapping the cards use) — null when the url isn't a demo one.
     */
    fun contentItemFor(url: String): ContentItem? {
        val (page, index) = DemoPlayback.parseUrl(url) ?: return null
        val seed = DemoPlayback.seedFor(page, index)
        return ContentItem(
            id = "demo-$page-$index",
            url = url,
            title = DemoPlayback.title(seed),
            author =
                com.tsutsen.platformplayer.core.model.Author(
                    id = DemoPlayback.channelUrl(seed),
                    name = DemoPlayback.channel(seed),
                    url = DemoPlayback.channelUrl(seed),
                    thumbnailUrl = avatarUri(seed),
                ),
            thumbnailUrl = thumbnailUri(seed),
            contentType = com.tsutsen.platformplayer.core.model.ContentType.VIDEO,
            publishedAt = DemoPlayback.publishedAt(seed),
            durationMs = DemoPlayback.durationMs(seed),
            viewCount = DemoPlayback.viewCount(seed),
            description = "Demo mode content — no network fetch",
        )
    }

    /** The 6 bundled demo images as playable `android.resource://` uris. */
    fun imageUris(): List<String> {
        val context = StateApp.instance.contextOrNull ?: return emptyList()
        return demoResIds().map { resId ->
            Uri.parse("android.resource://${context.packageName}/$resId").toString()
        }
    }

    /** Search filter over the demo catalog: case-insensitive title contains. */
    fun search(query: String, page: Int, count: Int = 24): List<VideoCard> {
        val q = query.trim().lowercase()
        return cards(page, count).filter { q.isEmpty() || it.title.lowercase().contains(q) }
    }

    private fun thumbnailUri(seed: Long): String = resourceUri(DemoPlayback.thumbnailResName(seed))

    private fun avatarUri(seed: Long): String = resourceUri(DemoPlayback.avatarResName(seed))

    private fun resourceUri(resName: String): String {
        val context = StateApp.instance.contextOrNull
        val resId =
            if (context != null) {
                context.resources.getIdentifier(resName, "drawable", context.packageName)
            } else {
                0
            }
        if (context == null || resId == 0) return ""
        return Uri.parse("android.resource://${context.packageName}/$resId").toString()
    }

    private fun demoResIds(): List<Int> = listOf(R.drawable.demo_1, R.drawable.demo_2, R.drawable.demo_3, R.drawable.demo_4, R.drawable.demo_5, R.drawable.demo_6)
}
