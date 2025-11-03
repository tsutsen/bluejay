package com.futo.platformplayer.sabr.parser.parts;

import com.futo.platformplayer.sabr.parser.models.FormatSelector;
import com.futo.platformplayer.sabr.protos.videostreaming.FormatId;

public class MediaSegmentEndSabrPart implements SabrPart {
    public final FormatSelector formatSelector;
    public final FormatId formatId;
    public final long sequenceNumber;
    public final boolean isInitSegment;
    public final long totalSegments;

    public MediaSegmentEndSabrPart(
            FormatSelector formatSelector,
            FormatId formatId,
            long sequenceNumber,
            boolean isInitSegment,
            long totalSegments) {
        this.formatSelector = formatSelector;
        this.formatId = formatId;
        this.sequenceNumber = sequenceNumber;
        this.isInitSegment = isInitSegment;
        this.totalSegments = totalSegments;
    }
}
