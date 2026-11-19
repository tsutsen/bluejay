package com.tsutsen.platformplayer.sabr

import com.tsutsen.platformplayer.sabr.proto.MediaHeader

/**
 * Assembles UMP MEDIA/MEDIA_END parts into [SabrSegment]s and files them into
 * the per-format track buffers. Verbatim from SabrSession's onMediaHeader and
 * the MEDIA/MEDIA_END branches of consume(); all mutable session state is
 * reached through the constructor callbacks, and the media byte/us counters
 * are owned here (the session only reads them).
 */
internal class SabrSegmentAssembler(
    private val bufferFor: (SabrFormatKey) -> SabrTrackBuffer,
    private val isKnownDemandedKey: (SabrFormatKey) -> Boolean,
    private val adoptServerFormat: (SabrFormatKey) -> Unit,
    private val clockKey: () -> SabrFormatKey?,
    private val onSegmentsSettled: () -> Unit,
    private val estimator: SabrDurationEstimator
) {
    @Volatile internal var demandedHeaders = 0
        private set
    @Volatile internal var foreignHeaders = 0
        private set
    @Volatile internal var mediaBytes = 0L
        private set
    @Volatile internal var mediaUsDelivered = 0L
        private set

    private val pending = HashMap<Int, SabrSegment>()

    fun resetHeaderCounters() {
        demandedHeaders = 0
        foreignHeaders = 0
    }

    fun onMediaPart(part: UmpPart) {
        val (headerId, offset) = UmpReader.decodeVarInt(part.data, 0)
        mediaBytes += (part.data.size - offset).toLong()
        pending[headerId.toInt()]?.let { segment ->
            segment.append(part.data, offset, part.data.size - offset)
            bufferFor(segment.formatKey).notifyChanged()
        }
    }

    fun onMediaEndPart(part: UmpPart) {
        val (headerId, _) = UmpReader.decodeVarInt(part.data, 0)
        pending.remove(headerId.toInt())?.let { segment ->
            if (segment.contentLength > 0 && segment.size != segment.contentLength) {
                SabrSession.sabrLog("Dropping truncated seq=${segment.sequenceNumber} itag=${segment.formatKey.itag} got=${segment.size} want=${segment.contentLength}")
                bufferFor(segment.formatKey).discard(segment)
            } else {
                segment.markComplete()
                bufferFor(segment.formatKey).notifyChanged()
                onSegmentsSettled()
            }
        }
    }

    fun discardPending() {
        for (segment in pending.values) {
            val buffer = bufferFor(segment.formatKey)
            buffer.discard(segment)
            buffer.notifyChanged()
        }
    }

    fun onMediaHeader(header: MediaHeader, requestedKeys: Set<SabrFormatKey>) {
        val key = SabrFormatKey.of(header.itag, header.lmt, header.xtags)
        val buffer = bufferFor(key)

        if (key in requestedKeys) {
            demandedHeaders++
            adoptServerFormat(key)
        }
        else if (!isKnownDemandedKey(key)) foreignHeaders++

        val existing = if (header.isInitSegment) buffer.initSegment else buffer.get(header.sequenceNumber)
        if (existing != null && existing.isComplete) {
            pending[header.headerId] = SabrSegment(key, header.sequenceNumber, header.isInitSegment, 0, 0, 0)
            return
        }

        val timescale = header.timeRange.timescale
        val startUs: Long
        var durationUs: Long
        if (timescale > 0) {
            startUs = SabrTimeMath.ticksToUs(header.timeRange.startTicks, timescale)
            durationUs = SabrTimeMath.ticksToUs(header.timeRange.durationTicks, timescale)
        } else {
            startUs = header.startMs * 1000
            durationUs = header.durationMs * 1000
        }
        val exact = durationUs > 0
        if (!header.isInitSegment) {
            estimator.backPatchPrevious(buffer, header.sequenceNumber, startUs)
            if (!exact) durationUs = estimator.estimateSegmentUs(header.sequenceNumber, startUs, buffer)

            val clock = clockKey()
            if (clock != null && clock == key) mediaUsDelivered += durationUs
        }

        val segment = SabrSegment(
            key,
            header.sequenceNumber,
            header.isInitSegment,
            startUs,
            durationUs,
            header.contentLength.toInt(),
            if (timescale > 0) header.timeRange.startTicks else 0,
            timescale
        )
        if (exact) segment.setDuration(durationUs, exact = true)
        pending[header.headerId] = segment
        buffer.announce(segment)

        SabrSession.sabrLog("MediaHeader itag=${header.itag} seq=${header.sequenceNumber} init=${header.isInitSegment} " +
            "startMs=${startUs / 1000} durMs=${durationUs / 1000} tr=${header.timeRange.startTicks}/${header.timeRange.durationTicks}@${header.timeRange.timescale} " +
            "hdrStartMs=${header.startMs} hdrDurMs=${header.durationMs} len=${header.contentLength}")
    }
}
