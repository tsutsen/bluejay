package com.tsutsen.platformplayer.sabr

import com.google.protobuf.ByteString
import com.tsutsen.platformplayer.sabr.proto.AdCuepointConfig
import com.tsutsen.platformplayer.sabr.proto.BufferedRange
import com.tsutsen.platformplayer.sabr.proto.ClientAbrState
import com.tsutsen.platformplayer.sabr.proto.ClientInfo
import com.tsutsen.platformplayer.sabr.proto.FormatInitializationMetadata
import com.tsutsen.platformplayer.sabr.proto.MediaType
import com.tsutsen.platformplayer.sabr.proto.StreamerContext
import com.tsutsen.platformplayer.sabr.proto.VideoPlaybackAbrRequest
import kotlin.random.Random

/**
 * Encodes the VideoPlaybackAbrRequest protobuf. Verbatim from SabrSession's
 * buildRequest / firstSequenceOfRun; all mutable session state arrives as
 * per-call snapshot parameters (the session reads its volatiles and delegates).
 */
internal class SabrRequestFactory(
    private val clientInfo: ClientInfo,
    private val ustreamerConfig: ByteArray,
    private val contexts: SabrContextTracker,
    private val cuepoints: SabrCuepointTracker,
    private val formatInitialization: Map<SabrFormatKey, FormatInitializationMetadata>,
    private val bufferFor: (SabrFormat) -> SabrTrackBuffer
) {
    fun build(
        video: SabrFormat?,
        audio: SabrFormat?,
        positionMs: Long,
        videoAlternates: List<SabrFormat>,
        audioAlternates: List<SabrFormat>,
        viewportWidth: Int,
        viewportHeight: Int,
        playbackCookie: ByteString?,
        poToken: ByteString?,
        bandwidthEstimate: Long,
        lastActionMs: Long,
        createdAtMs: Long,
        lastRequestMs: Long,
        isLive: Boolean,
        demandFromUs: (SabrFormat) -> Long
    ): VideoPlaybackAbrRequest {
        val now = System.currentTimeMillis()
        val abrState = ClientAbrState.newBuilder()
            .setPlayerTimeMs(positionMs)
            .setBandwidthEstimate(bandwidthEstimate)
            .setNetworkLatencyMs(Random.nextLong(7, 97))
            .setTimeSinceLastActionMs(now - lastActionMs)
            .setTimeSinceLastManualFormatSelectionMs(now - createdAtMs)
            .setLastManualDirection(0)
            .setDrcEnabled(true)
            .setVisibility(0)
            .setPreferVp9(false)

        if (lastRequestMs > 0) abrState.setTimeSinceLastRequestMs(now - lastRequestMs)

        if (video != null) {
            val cap = videoAlternates.maxByOrNull { it.height } ?: video
            abrState.setClientViewportWidth((if (viewportWidth > 0) viewportWidth else cap.width).toLong())
            abrState.setClientViewportHeight((if (viewportHeight > 0) viewportHeight else cap.height).toLong())

            if (videoAlternates.size <= 1) {
                abrState.setLastManualSelectedResolution(video.height.toLong())
                abrState.setStickyResolution(video.height.toLong())
                abrState.setSelectedQualityHeight(video.height.toLong())
            }

            if (audio == null) abrState.setEnabledTrackTypesBitfield(MediaType.MEDIA_TYPE_VIDEO)
        } else if (audio != null) {
            abrState.setEnabledTrackTypesBitfield(MediaType.MEDIA_TYPE_AUDIO)
        }

        val streamerContext = StreamerContext.newBuilder()
            .setClientInfo(clientInfo)
            .addAllSabrContexts(contexts.activeHeld())
            .addAllUnsentSabrContexts(contexts.inactiveKeys())

        poToken?.let { streamerContext.setPoToken(it) }
        playbackCookie?.let { streamerContext.setPlaybackCookie(it) }

        val request = VideoPlaybackAbrRequest.newBuilder()
            .setClientAbrState(abrState)
            .setVideoPlaybackUstreamerConfig(ByteString.copyFrom(ustreamerConfig))
            .setStreamerContext(streamerContext)

        cuepoints.expire(positionMs)
        for (id in cuepoints.ids) {
            request.addAdCuepoints(AdCuepointConfig.newBuilder()
                .setCuepointId(id)
                .setMagicValue(AD_CUEPOINT_MAGIC))
        }

        if (videoAlternates.isEmpty()) video?.let { request.addPreferredVideoFormatIds(it.toFormatId()) }
        else videoAlternates.forEach { request.addPreferredVideoFormatIds(it.toFormatId()) }

        if (audioAlternates.isEmpty()) audio?.let { request.addPreferredAudioFormatIds(it.toFormatId()) }
        else audioAlternates.forEach { request.addPreferredAudioFormatIds(it.toFormatId()) }

        val held = (videoAlternates + audioAlternates).ifEmpty { listOfNotNull(video, audio) }
        for (format in held) {
            if (!formatInitialization.containsKey(format.key)) continue
            if (bufferFor(format).segmentCount == 0) continue
            request.addSelectedFormatIds(format.toFormatId())

            val buffer = bufferFor(format)
            val fromUs = demandFromUs(format)
            val lastSequence = buffer.lastCompletedSequence(fromUs)
            if (lastSequence < 0) continue

            val range = BufferedRange.newBuilder()
                .setFormatId(format.toFormatId())
                .setEndSegmentIndex(lastSequence.toLong())

            val startSeq = firstSequenceOfRun(buffer, fromUs)
            val startUs = buffer.get(startSeq)?.startUs ?: continue
            val exactEnd = if (isLive) buffer.exactEndFromSequence(startSeq) else Long.MIN_VALUE
            val end = if (exactEnd != Long.MIN_VALUE) exactEnd else buffer.bufferedEndUs(fromUs)
            val durationUs = if (end == Long.MIN_VALUE) 0 else (end - startUs).coerceAtLeast(0)
            range.setStartSegmentIndex(startSeq.toLong())
                .setStartTimeMs(startUs / 1000)
                .setDurationMs(durationUs / 1000)

            val first = buffer.get(startSeq)
            if (first != null && first.timescale > 0) {
                range.setTimeRange(
                    com.tsutsen.platformplayer.sabr.proto.TimeRange.newBuilder()
                        .setStartTicks(first.startTicks)
                        .setDurationTicks(SabrTimeMath.usToTicks(durationUs, first.timescale))
                        .setTimescale(first.timescale)
                )
            }
            request.addBufferedRanges(range.build())
        }

        return request.build()
    }

    private fun firstSequenceOfRun(buffer: SabrTrackBuffer, fromUs: Long): Int {
        val last = buffer.lastCompletedSequence(fromUs)
        if (last < 0) return buffer.lowestSequence.coerceAtLeast(1)
        var first = last
        while (first > 0 && buffer.get(first - 1)?.isComplete == true) first--
        return first.coerceAtLeast(0)
    }

    companion object {
        private const val AD_CUEPOINT_MAGIC = 11
    }
}
