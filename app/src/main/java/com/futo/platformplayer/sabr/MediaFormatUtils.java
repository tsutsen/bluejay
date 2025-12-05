package com.futo.platformplayer.sabr;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaFormatUtils {
    public static final String MIME_WEBM_AUDIO = "audio/webm";
    public static final String MIME_WEBM_VIDEO = "video/webm";
    public static final String MIME_MP4_AUDIO = "audio/mp4";
    public static final String MIME_MP4_VIDEO = "video/mp4";
    private static final Pattern CODECS_PATTERN = Pattern.compile(".*codecs=\\\"(.*)\\\"");

    public static boolean isNumeric(String s) {
        return s != null && s.matches("^[-+]?\\d*\\.?\\d+$");
    }

    public static boolean isDash(String id) {
        if (!isNumeric(id)) {
            return false;
        }

        int maxRegularITag = 50;
        int itag = Integer.parseInt(id);

        return itag > maxRegularITag;
    }

    public static boolean isDash(MediaFormat format) {
        if (format.getITag() == null) {
            return false;
        }

        if (format.getGlobalSegmentList() != null) {
            return true;
        }

        String id = format.getITag();

        return isDash(id);
    }

    public static boolean checkMediaUrl(MediaFormat format) {
        return format != null && format.getUrl() != null;
    }

    public static String extractMimeType(MediaFormat format) {
        if (format.getGlobalSegmentList() != null) {
            return format.getMimeType();
        }

        String codecs = extractCodecs(format);

        if (codecs.startsWith("vorbis") ||
                codecs.startsWith("opus")) {
            return MIME_WEBM_AUDIO;
        }

        if (codecs.startsWith("vp9") ||
                codecs.startsWith("vp09")) {
            return MIME_WEBM_VIDEO;
        }

        if (codecs.startsWith("mp4a") ||
                codecs.startsWith("ec-3") ||
                codecs.startsWith("ac-3")) {
            return MIME_MP4_AUDIO;
        }

        if (codecs.startsWith("avc") ||
                codecs.startsWith("av01")) {
            return MIME_MP4_VIDEO;
        }

        return null;
    }

    public static String extractCodecs(MediaFormat format) {
        // input example: video/mp4;+codecs="avc1.640033"
        Matcher matcher = CODECS_PATTERN.matcher(format.getMimeType());
        matcher.find();
        return matcher.group(1);
    }

    public static boolean isLiveMedia(MediaFormat format) {
        boolean isLive =
                format.getUrl().contains("live=1") ||
                        format.getUrl().contains("yt_live_broadcast");

        return isLive;
    }

    private static String normalize(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }

        return word.toLowerCase().replace("ё", "е");
    }

    public static boolean startsWith(String word, String prefix) {
        if (word == null && prefix == null) {
            return true;
        }

        if (word == null || prefix == null) {
            return false;
        }

        word = normalize(word);
        prefix = normalize(prefix);

        return word.startsWith(prefix);
    }

    public static boolean isAudio(String mimeType) {
        return startsWith(mimeType, "audio");
    }

    public static boolean isVideo(String mimeType) {
        return startsWith(mimeType, "video");
    }
}
