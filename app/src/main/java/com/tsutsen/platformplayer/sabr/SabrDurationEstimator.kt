package com.tsutsen.platformplayer.sabr

import com.tsutsen.platformplayer.sabr.proto.LiveMetadata

/**
 * Estimates/back-patches segment durations for tracks that report no exact
 * duration. Verbatim from SabrSession's estimateSegmentUs / liveCadenceUs /
 * backPatchPrevious / reestimateInexactDurations; liveMetadata is read through
 * a provider so the estimator owns no mutable session state.
 */
internal class SabrDurationEstimator(private val liveMetadata: () -> LiveMetadata?) {
    fun estimateSegmentUs(sequence: Int, startUs: Long, buffer: SabrTrackBuffer): Long {
        val cadence = liveCadenceUs(buffer)
        if (cadence > 0) return cadence

        val lm = liveMetadata()
        if (lm != null && lm.headSequenceNumber > sequence) {
            val segments = lm.headSequenceNumber - sequence
            val spanUs = lm.headSequenceTimeMs * 1000 - startUs
            if (spanUs > 0) return (spanUs / segments).coerceAtLeast(1)
        }

        val prev = buffer.get(sequence - 1)
        if (prev != null && prev.durationUs > 0) return prev.durationUs

        return DEFAULT_LIVE_SEGMENT_US
    }

    fun liveCadenceUs(buffer: SabrTrackBuffer): Long {
        val observed = buffer.recentStartDeltasUs(8)
        if (observed.isNotEmpty()) return observed.sorted()[observed.size / 2].coerceAtLeast(1)

        val lm = liveMetadata() ?: return -1
        val anchor = buffer.firstAtOrAfter(-1) ?: return -1
        if (lm.headSequenceNumber <= anchor.sequenceNumber) return -1

        val spanUs = lm.headSequenceTimeMs * 1000 - anchor.startUs
        if (spanUs <= 0) return -1
        return (spanUs / (lm.headSequenceNumber - anchor.sequenceNumber)).coerceAtLeast(1)
    }

    fun backPatchPrevious(buffer: SabrTrackBuffer, sequence: Int, startUs: Long) {
        val prev = buffer.get(sequence - 1) ?: return
        if (prev.durationExact) return

        val deltaUs = startUs - prev.startUs
        if (deltaUs <= 0) return

        val cadence = liveCadenceUs(buffer).takeIf { it > 0 } ?: prev.durationUs
        if (cadence > 0 && deltaUs > cadence * 3 / 2) return

        prev.setDuration(deltaUs, exact = true)
    }

    fun reestimateInexactDurations(buffers: Collection<SabrTrackBuffer>) {
        for (buffer in buffers) {
            for (segment in buffer.snapshot()) {
                if (segment.durationExact || segment.isInit) continue
                buffer.get(segment.sequenceNumber + 1)?.let { backPatchPrevious(buffer, it.sequenceNumber, it.startUs) }
                if (segment.durationExact) continue
                segment.setDuration(estimateSegmentUs(segment.sequenceNumber, segment.startUs, buffer), exact = false)
            }
        }
    }

    companion object {
        private const val DEFAULT_LIVE_SEGMENT_US = 5_000_000L
    }
}
