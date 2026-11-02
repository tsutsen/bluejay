package com.tsutsen.platformplayer.di

import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.model.SavedVideoType
import com.tsutsen.platformplayer.core.model.VideoCard
import com.tsutsen.platformplayer.di.fakes.FakeLibraryRepository
import com.tsutsen.platformplayer.di.fakes.FakeVideoUrlResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the core/data seams are real: two adapters (engine impl in prod, fakes
 * here) satisfy the same repository interfaces, and consumer behavior is
 * testable through the interface alone.
 */
class RepositorySeamFakesTest {

    @Test
    fun videoUrlResolver_fakeReturnsCannedResult_andRecordsResumePosition() = runTest {
        val resolver = FakeVideoUrlResolver()
        resolver.stub("https://v.example/1", ResolutionResult(mediaSource = null, videoDetails = null))

        val result = resolver.resolve("https://v.example/1", resumePositionMs = 42_000)
        assertNull(result.mediaSource) // stubbed empty source is returned as-is
        assertEquals(42_000L, resolver.requestedPositions["https://v.example/1"])

        val miss = resolver.resolve("https://v.example/unknown")
        assertNull(miss.mediaSource)
        assertNull(miss.videoDetails)
    }

    @Test
    fun libraryRepository_fakeSaveFlowsToObservers() = runTest {
        val repo = FakeLibraryRepository()
        val card = VideoCard(id = "v1", title = "T", thumbnailUrl = null, author = null, url = "https://v.example/1")

        repo.saveVideo(SavedVideoType.WATCH_LATER, card)
        assertEquals(setOf(SavedVideoType.WATCH_LATER), repo.observeSavedTypes(card.url).first())

        repo.saveVideo(SavedVideoType.LIKED, card)
        assertEquals(setOf(SavedVideoType.WATCH_LATER, SavedVideoType.LIKED), repo.observeSavedTypes(card.url).first())

        repo.removeSavedVideo(SavedVideoType.WATCH_LATER, card.url)
        assertEquals(setOf(SavedVideoType.LIKED), repo.observeSavedTypes(card.url).first())
        assertTrue(repo.observeSavedTypes("https://v.example/other").first().isEmpty())
    }

    @Test
    fun libraryRepository_fakePlaylistRoundTrip() = runTest {
        val repo = FakeLibraryRepository()
        val id = repo.createPlaylist("My list", "desc")
        val info = repo.observeLocalPlaylist(id).first()
        assertEquals("My list", info?.first?.name)
        assertEquals(0, repo.observePlaylistStats(id).first().videoCount)
        assertNull(repo.observeLocalPlaylist(999).first())
    }
}
