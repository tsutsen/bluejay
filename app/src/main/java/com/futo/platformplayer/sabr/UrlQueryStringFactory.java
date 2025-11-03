package com.futo.platformplayer.sabr;

import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class UrlQueryStringFactory {
    public static UrlQueryString parse(Uri url) {
        if (url == null) {
            return null;
        }

        return parse(url.toString());
    }

    public static String toString(InputStream in) {
        try {
            int bufsize = 8196;
            char[] cbuf = new char[bufsize];
            StringBuilder buf = new StringBuilder(bufsize);
            InputStreamReader reader = new InputStreamReader(in, "UTF-8");

            int readBytes;
            while ((readBytes = reader.read(cbuf, 0, bufsize)) != -1) {
                buf.append(cbuf, 0, readBytes);
            }

            return buf.toString();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("UrlQueryStringFactory", e.getMessage());
        }

        return null;
    }

    public static UrlQueryString parse(InputStream urlContent) {
        return parse(toString(urlContent));
    }

    //public static UrlQueryString parse(String url) {
    //    UrlQueryString pathQueryString = PathQueryString.parse(url);
    //
    //    if (pathQueryString.isValid()) {
    //        return pathQueryString;
    //    }
    //
    //    UrlQueryString urlQueryString = UrlEncodedQueryString.parse(url);
    //
    //    if (urlQueryString.isValid()) {
    //        return urlQueryString;
    //    }
    //
    //    return NullQueryString.parse(url);
    //}

    public static UrlQueryString parse(String url) {
        return CombinedQueryString.parse(url);
    }
}
