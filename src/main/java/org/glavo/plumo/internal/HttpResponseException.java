package org.glavo.plumo.internal;

import org.glavo.plumo.HttpResponse;

import java.io.IOException;

public final class HttpResponseException extends IOException {

    private final HttpResponse response;

    public HttpResponseException(HttpResponse.Status status) {
        this.response = HttpResponse.newResponse(status);
    }

    public HttpResponseException(HttpResponse.Status status, String message) {
        super(message);
        this.response = HttpResponse.newTextResponse(status, message, "text/plain");
    }

    public HttpResponseException(HttpResponse.Status status, String message, Exception e) {
        super(message, e);
        this.response = HttpResponse.newTextResponse(status, message, "text/plain");
    }

    public HttpResponse getResponse() {
        return this.response;
    }
}
