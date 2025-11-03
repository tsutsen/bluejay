package com.futo.platformplayer.sabr.parser.parts;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorInput;
import com.futo.platformplayer.sabr.parser.models.FormatSelector;
import com.futo.platformplayer.sabr.protos.videostreaming.FormatId;

@UnstableApi
public class MediaSegmentDataSabrPart implements SabrPart {
    public final FormatSelector formatSelector;
    public final FormatId formatId;
    public final long sequenceNumber;
    public final boolean isInitSegment;
    public final int totalSegments;
    public final ExtractorInput data;
    public final int contentLength;
    public final int segmentStartBytes;

    public MediaSegmentDataSabrPart(
            FormatSelector formatSelector,
            FormatId formatId,
            long sequenceNumber,
            boolean isInitSegment,
            int totalSegments,
            ExtractorInput data,
            int contentLength,
            int segmentStartBytes) {
        this.formatSelector = formatSelector;
        this.formatId = formatId;
        this.sequenceNumber = sequenceNumber;
        this.isInitSegment = isInitSegment;
        this.totalSegments = totalSegments;
        this.data = data;
        this.contentLength = contentLength;
        this.segmentStartBytes = segmentStartBytes;
    }
}
