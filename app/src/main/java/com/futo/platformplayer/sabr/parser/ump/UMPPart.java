package com.futo.platformplayer.sabr.parser.ump;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.ExtractorInput;

@UnstableApi
public class UMPPart {
    public final int partId;
    public final int size;
    public final ExtractorInput data;

    public UMPPart(int partId, int size, ExtractorInput data) {
        this.partId = partId;
        this.size = size;
        this.data = data;
    }

    public UMPInputStream toStream() {
        return new UMPInputStream(this);
    }
}
