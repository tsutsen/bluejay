package com.futo.platformplayer.sabr;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class UrlEncodedQueryString implements UrlQueryString {
    private static final Pattern VALIDATION_PATTERN = Pattern.compile("[^\\/?&]+=[^\\/&]+");
    private static final Pattern URL_PREFIX = Pattern.compile("^[a-z.]+://.+$");
    @Nullable
    private String mQueryPrefix;
    @Nullable
    private UrlEncodedQueryStringBase mQueryString;
    private String mUrl;


    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        Matcher m = URL_PREFIX.matcher(url);
        return m.matches();
    }

    private UrlEncodedQueryString(String url) {
        if (url == null) {
            return;
        }

        mUrl = url;

        if (isValidUrl(url)) {
            URI parsedUrl = getURI(url);
            if (parsedUrl != null) {
                mQueryPrefix = String.format("%s://%s%s", parsedUrl.getScheme(), parsedUrl.getHost(), parsedUrl.getPath());
                mQueryString = UrlEncodedQueryStringBase.parse(parsedUrl);
            }
        } else { // Only query
            mQueryString = UrlEncodedQueryStringBase.parse(url);
        }
    }

    @Nullable
    private URI getURI(String url) {
        if (url == null) {
            return null;
        }

        try {
            // Fix illegal character exception. E.g.
            // https://www.youtube.com/results?search_query=Джентльмены удачи
            // https://www.youtube.com/results?search_query=|FR|+Mrs.+Doubtfire
            // https://youtu.be/wTw-jreMgCk\ (last char isn't valid)
            // https://m.youtube.com/watch?v=JsY3_Va6uqI&feature=emb_title###&Urj7svfj=&Rkj2f3jk=&Czj1i9k6= (# isn't valid)
            return new URI(url.length() > 100 ? // OOM fix: don't replace long string
                    url : url
                    .replace(" ", "+")
                    .replace("|", "%7C")
                    .replace("\\", "/")
                    .replace("#", "")
            );
        } catch (URISyntaxException e) {
            //throw new RuntimeException(e);
        }

        return null;
    }

    public static UrlEncodedQueryString parse(String url) {
        return new UrlEncodedQueryString(url);
    }

    @Override
    public void remove(String key) {
        if (mQueryString != null) {
            mQueryString.remove(key);
        }
    }

    @Override
    public String get(String key) {
        return mQueryString != null ? mQueryString.get(key) : null;
    }

    @Override
    public float getFloat(String key) {
        String val = get(key);
        return val != null ? Float.parseFloat(val) : 0;
    }

    @Override
    public void set(String key, String value) {
        if (mQueryString != null) {
            mQueryString.set(key, value);
        }
    }

    @Override
    public void set(String key, float value) {
        set(key, String.valueOf(value));
    }

    @Override
    public void set(String key, int value) {
        set(key, String.valueOf(value));
    }

    @NonNull
    @Override
    public String toString() {
        if (mQueryString == null) {
            return mUrl != null ? mUrl : "";
        }

        return mQueryPrefix != null ? String.format("%s?%s", mQueryPrefix, mQueryString) : mQueryString.toString();
    }

    public static boolean matchAll(String input, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(input);
            if (!matcher.find()) {
                return false;
            }
        }

        return true;
    }

    public static boolean matchAll(String input, String... regex) {
        for (String reg : regex) {
            Pattern pattern = Pattern.compile(reg);
            Matcher matcher = pattern.matcher(input);
            if (!matcher.find()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check query string
     */
    @Override
    public boolean isValid() {
        if (mUrl == null) {
            return false;
        }

        return matchAll(mUrl, VALIDATION_PATTERN);
    }

    @Override
    public boolean isEmpty() {
        return mUrl == null || mUrl.isEmpty();
    }

    @Override
    public boolean contains(String key) {
        return mQueryString != null && mQueryString.contains(key);
    }
}
