package com.tsutsen.platformplayer.di.fakes

import com.tsutsen.platformplayer.core.data.repository.ResolutionResult
import com.tsutsen.platformplayer.core.data.repository.VideoUrlResolver

/**
 * In-memory [VideoUrlResolver] proving the seam: consumers (e.g. PlayerRepository)
 * depend only on the interface, never on the engine-backed impl.
 */
class FakeVideoUrlResolver : VideoUrlResolver {
    private val results = mutableMapOf<String, ResolutionResult>()
    val requestedPositions = mutableMapOf<String, Long>()

    fun stub(url: String, result: ResolutionResult) {
        results[url] = result
    }

    override suspend fun resolve(contentUrl: String, resumePositionMs: Long): ResolutionResult {
        requestedPositions[contentUrl] = resumePositionMs
        return results[contentUrl] ?: ResolutionResult(null, null)
    }
}
