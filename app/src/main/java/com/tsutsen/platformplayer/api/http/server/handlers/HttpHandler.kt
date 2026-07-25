package com.tsutsen.platformplayer.api.http.server.handlers

import com.tsutsen.platformplayer.api.http.server.HttpContext
import com.tsutsen.platformplayer.api.http.server.HttpHeaders


abstract class HttpHandler(val method: String, val path: String) {
    var tag: String? = null;
    val headers = HttpHeaders()
    var allowHEAD = false;

    abstract fun handle(httpContext: HttpContext);

    fun withHeader(key: String, value: String) : HttpHandler {
        headers.put(key, value);
        return this;
    }

    fun withContentType(contentType: String) = withHeader("Content-Type", contentType);

    fun withTag(tag: String) : HttpHandler {
        this.tag = tag;
        return this;
    }
}