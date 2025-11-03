package com.futo.platformplayer.sabr.parser.parts;

import com.futo.platformplayer.sabr.parser.models.FormatSelector;
import com.futo.platformplayer.sabr.protos.videostreaming.FormatId;

public class MediaSeekSabrPart implements SabrPart {
    public Reason reason;
    public FormatId formatId;
    public FormatSelector formatSelector;

    public MediaSeekSabrPart(Reason reason, FormatId formatId, FormatSelector formatSelector) {
        this.reason = reason;
        this.formatId = formatId;
        this.formatSelector = formatSelector;
    }

    // Lets the consumer know the media sequence for a format may change
    public enum Reason {
        UNKNOWN,
        SERVER_SEEK,         // SABR_SEEK from server
        CONSUMED_SEEK        // Seeking as next fragment is already buffered
    }
}
