package com.futo.platformplayer.sabr.parser.models;

public class VideoSelector extends FormatSelector {
    public VideoSelector(String displayName, boolean discardMedia) {
        super(displayName, discardMedia);
    }

    @Override
    public String getMimePrefix() {
        return "video";
    }
}
