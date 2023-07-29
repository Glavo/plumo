package org.glavo.webdav.nanohttpd.internal;

import org.glavo.webdav.nanohttpd.HttpResponse;
import org.glavo.webdav.nanohttpd.HttpContentType;
import org.glavo.webdav.nanohttpd.content.Cookie;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class HttpResponseImpl implements HttpResponse {

    final SimpleStringMap<String> headers = new SimpleStringMap<>();

    Status status;
    Instant date;

    String connection;

    // InputStream | String | byte[]
    Object body;
    long contentLength;
    HttpContentType contentType;
    String contentEncoding;

    List<Cookie> cookies;

    public HttpResponseImpl() {
    }

    public HttpResponseImpl(Status status) {
        this.status = status;
    }

    public HttpResponseImpl(Status status, Object body, HttpContentType contentType) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
    }

    @Override
    public HttpResponse setStatus(Status status) {
        this.status = status;
        return this;
    }

    @Override
    public HttpResponse setDate(Instant date) {
        this.date = date;
        return this;
    }

    @Override
    public HttpResponse setHeader(String name, String value) {
        Objects.requireNonNull(value);

        name = name.toLowerCase(Locale.ROOT);
        switch (name) {
            case "connection":
                connection = value;
                break;
            case "content-length":
                if (!(body instanceof InputStream)) {
                    throw new UnsupportedOperationException();
                }

                long len = Long.parseLong(value);
                if (len < 0) {
                    throw new IllegalArgumentException();
                }
                this.contentLength = len;
                break;
            case "content-type":
                this.contentType = new HttpContentType(value);
                break;
            case "content-encoding":
                this.contentEncoding = value;
                break;
            case "date":
                this.date = Instant.from(Constants.HTTP_TIME_FORMATTER.parse(value));
                break;
            default:
                headers.put(name, value);
        }

        return this;
    }

    @Override
    public HttpResponse setContentType(HttpContentType contentType) {
        this.contentType = contentType;
        return this;
    }

    @Override
    public HttpResponse setContentType(String contentType) {
        this.contentType = new HttpContentType(contentType);
        return this;
    }

    @Override
    public HttpResponse setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
        return this;
    }

    @Override
    public HttpResponse setKeepAlive(boolean keepAlive) {
        connection = keepAlive ? "keep-alive" : "close";
        return this;
    }

    @Override
    public HttpResponse addCookies(Iterable<? extends Cookie> cookies) {
        if (this.cookies == null) {
            this.cookies = new ArrayList<>();
        }

        for (Cookie cookie : cookies) {
            this.cookies.add(cookie);
        }

        return this;
    }

    @Override
    public HttpResponse setBody(byte[] data) {
        this.body = data;
        this.contentLength = -1;
        return this;
    }

    @Override
    public HttpResponse setBody(String data) {
        this.body = data;
        this.contentLength = -1;
        return this;
    }

    @Override
    public HttpResponse setBody(InputStream data, long contentLength) {
        if (contentLength < 0) {
            throw new IllegalArgumentException();
        }

        this.body = data;
        this.contentLength = contentLength;
        return this;
    }

    @Override
    public HttpResponse setDataUnknownSize(InputStream data) {
        this.body = data;
        this.contentLength = -1;
        return this;
    }

    // ----

    boolean needCloseConnection() {
        return "close".equals(connection);
    }

    void finish() {
        if (body instanceof InputStream) {
            IOUtils.safeClose((InputStream) body);
        }
    }
}
