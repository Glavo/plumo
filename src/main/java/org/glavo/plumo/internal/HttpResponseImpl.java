package org.glavo.plumo.internal;

import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.internal.util.IOUtils;
import org.glavo.plumo.internal.util.MultiStringMap;
import org.glavo.plumo.internal.util.Utils;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

public final class HttpResponseImpl implements HttpResponse, AutoCloseable {

    public MultiStringMap headers = new MultiStringMap();

    public Status status;

    // InputStream | String | byte[] | Path
    public Object body;
    public long contentLength;
    private boolean closed = false;

    private boolean frozen;
    private boolean headerIsAlias;

    public HttpResponseImpl() {
        this.frozen = false;
        this.headerIsAlias = false;

        this.headers = new MultiStringMap();
    }

    public HttpResponseImpl(MultiStringMap old) {
        this.frozen = false;
        this.headerIsAlias = true;

        this.headers = old;
    }

    public HttpResponseImpl(boolean frozen, Status status, Object body, String contentType) {
        this.frozen = frozen;
        this.headerIsAlias = false;

        this.status = status;
        this.body = body;

        this.headers.map.put("content-type", contentType);
    }

    private HttpResponseImpl copyIfFrozen() {
        if (!frozen) {
            return this;
        }

        if (body instanceof InputStream) {
            throw new UnsupportedOperationException("Body is not reusable");
        }

        HttpResponseImpl response = new HttpResponseImpl(this.headers);
        response.body = this.body;
        response.contentLength = this.contentLength;
        return response;
    }

    private HttpResponseImpl ensureHeaderUnaliased() {
        if (headerIsAlias) {
            this.headers = headers.clone();
            headerIsAlias = false;
        }

        return this;
    }

    @Override
    public HttpResponse withStatus(Status status) {
        HttpResponseImpl response = copyIfFrozen();
        response.status = status;
        return response;
    }

    @Override
    public HttpResponse withHeader(String name, String value) {
        String canonicalName = Utils.normalizeHttpHeaderFieldName(name);
        Objects.requireNonNull(value);

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();
        response.headers.map.put(canonicalName, value);
        return response;
    }

    @Override
    public HttpResponse withHeader(String name, List<String> values) {
        String canonicalName = Utils.normalizeHttpHeaderFieldName(name);
        int size = values.size(); // implicit null check

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();

        if (size == 0) {
            response.headers.map.put(canonicalName, null);
        } else if (size == 1) {
            response.headers.map.put(canonicalName, values.get(0));
        } else {
            ArrayList<String> clone = new ArrayList<>(size);
            for (String value : values) {
                clone.add(Objects.requireNonNull(value));
            }
            if (clone.size() != size) {
                throw new ConcurrentModificationException();
            }
            response.headers.map.put(canonicalName, clone);
        }

        return response;
    }

    @Override
    public HttpResponse addHeader(String name, String value) {
        String canonicalName = Utils.normalizeHttpHeaderFieldName(name);
        Objects.requireNonNull(value);

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();
        response.headers.add(canonicalName, value);
        return response;
    }

    @Override
    public HttpResponse removeHeader(String name) {
        String canonicalName;

        try {
            canonicalName = Utils.normalizeHttpHeaderFieldName(name);
        } catch (IllegalArgumentException ignored) {
            return this;
        }

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();
        response.headers.map.put(canonicalName, null);
        return response;
    }

    @Override
    public HttpResponse withBody(byte[] data) {
        HttpResponseImpl response = copyIfFrozen();
        response.body = data;
        response.contentLength = data.length;
        return response;
    }

    @Override
    public HttpResponse withBody(String data) {
        HttpResponseImpl response = copyIfFrozen();
        response.body = data;
        response.contentLength = -1L;
        return response;
    }

    @Override
    public HttpResponse withBody(InputStream data, long contentLength) {
        Objects.requireNonNull(data);

        HttpResponseImpl response = copyIfFrozen();
        response.body = data;
        response.contentLength = contentLength;
        return response;
    }

    @Override
    public HttpResponse withBody(Path file) {
        Objects.requireNonNull(file);

        HttpResponseImpl response = copyIfFrozen();
        response.body = file;
        response.contentLength = -1L;
        return response;
    }

    // ----

    public boolean isAvailable() {
        return !(body instanceof InputStream) || !closed;
    }

    @Override
    public void close() {
        if (!(body instanceof InputStream) || closed) {
            return;
        }

        closed = true;
        IOUtils.safeClose((InputStream) body);
    }
}
