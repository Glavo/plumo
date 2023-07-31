package org.glavo.webdav.nanohttpd;

public final class HttpResponseException extends Exception {

    private final HttpResponse.Status status;

    public HttpResponseException(HttpResponse.Status status) {
        this.status = status;
    }

    public HttpResponseException(HttpResponse.Status status, String message) {
        super(message);
        this.status = status;
    }

    public HttpResponseException(HttpResponse.Status status, String message, Exception e) {
        super(message, e);
        this.status = status;
    }

    public HttpResponse.Status getStatus() {
        return this.status;
    }
}
