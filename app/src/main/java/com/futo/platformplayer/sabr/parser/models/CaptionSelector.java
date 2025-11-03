package com.futo.platformplayer.sabr.parser.models;

public class CaptionSelector extends FormatSelector {
    public CaptionSelector(String displayName, boolean discardMedia) {
        super(displayName, discardMedia);
    }

    @Override
    public String getMimePrefix() {
        return "text";
    }
}
