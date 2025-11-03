package com.futo.platformplayer.sabr.parser.parts;

import com.futo.platformplayer.sabr.parser.models.FormatSelector;
import com.futo.platformplayer.sabr.protos.videostreaming.FormatId;

public class FormatInitializedSabrPart implements SabrPart {
    public final FormatId formatId;
    public final FormatSelector formatSelector;

    public FormatInitializedSabrPart(FormatId formatId, FormatSelector formatSelector) {
        this.formatId = formatId;
        this.formatSelector = formatSelector;
    }
}
