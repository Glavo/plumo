/*
 * Copyright 2023 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.plumo.internal;

import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.internal.util.MultiStringMap;
import org.glavo.plumo.internal.util.Utils;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
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

        this.status = Status.OK;
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

        this.headers.putDirect("content-type", contentType);
    }

    private HttpResponseImpl copyIfFrozen() {
        if (!frozen) {
            return this;
        }

        assert !(body instanceof InputStream);

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
    public HttpResponse freeze() {
        if (!frozen) {
            if (body instanceof InputStream) {
                throw new UnsupportedOperationException("The response body is not reusable");
            }

            this.frozen = true;
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
        response.headers.putDirect(canonicalName, value);
        return response;
    }

    @Override
    public HttpResponse withHeader(String name, List<String> values) {
        String canonicalName = Utils.normalizeHttpHeaderFieldName(name);
        int size = values.size(); // implicit null check

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();

        if (size == 0) {
            response.headers.putDirect(canonicalName, null);
        } else if (size == 1) {
            response.headers.putDirect(canonicalName, values.get(0));
        } else {
            ArrayList<String> clone = new ArrayList<>(size);
            for (String value : values) {
                clone.add(Objects.requireNonNull(value));
            }
            if (clone.size() != size) {
                throw new ConcurrentModificationException();
            }
            response.headers.putDirect(canonicalName, clone);
        }

        return response;
    }

    @Override
    public HttpResponse addHeader(String name, String value) {
        String canonicalName = Utils.normalizeHttpHeaderFieldName(name);
        Objects.requireNonNull(value);

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();
        response.headers.addDirect(canonicalName, value);
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
        response.headers.putDirect(canonicalName, null);
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

    @Override
    public Status getStatus() {
        return status;
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
        // TODO IOUtils.safeClose((InputStream) body);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("HttpResponse {\n");
        builder.append("    ").append("HTTP/1.1 ").append(status).append("\n");

        if (!headers.containsKey("date")) {
            builder.append("    date: <calculated when request is sent>\n");
        }

        headers.forEachHeader((k, v) -> builder.append("    ").append(k).append(": ").append(v).append('\n'));

        builder.append("\n    ");
        if (body instanceof String) {
            builder.append("<string body, length=").append(((String) body).length()).append('>');
        } else if (body instanceof byte[]) {
            builder.append("<binary body, length=").append(((byte[]) body).length).append('>');
        } else if (body instanceof InputStream) {
            builder.append("<binary body, ");
            if (contentLength < 0) {
                builder.append("unknown length>");
            } else {
                builder.append("length=").append(contentLength).append('>');
            }

        } else if (body instanceof Path) {
            builder.append("<file body, path=").append(body).append('>');
        } else {
            assert body == null;
            builder.append("<empty body>");
        }

        builder.append("\n}");

        return builder.toString();
    }
}
