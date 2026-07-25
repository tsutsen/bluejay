package com.tsutsen.platformplayer.api.http.server.handlers

import com.tsutsen.platformplayer.api.http.server.HttpContext

class HttpFunctionHandler(method: String, path: String, val handler: (HttpContext)->Unit) : HttpHandler(method, path) {
    override fun handle(httpContext: HttpContext) {
        httpContext.setResponseHeaders(this.headers);
        handler(httpContext);
    }
}