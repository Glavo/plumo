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

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.HttpHeaderField;
import org.glavo.plumo.HttpResponse;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.*;

public final class HttpResponseImpl implements HttpResponse {

    public Headers headers = new Headers();

    public Status status;

    // InputStream | ReadableByteChannel | String | ByteBuffer | Path
    public Object body;
    public long contentLength;
    private boolean closed = false;

    private boolean frozen;
    private boolean headerIsAlias;

    public HttpResponseImpl() {
        this.frozen = false;
        this.headerIsAlias = false;

        this.status = Status.OK;
        this.headers = new Headers();
    }

    public HttpResponseImpl(Headers old) {
        this.frozen = false;
        this.headerIsAlias = true;

        this.headers = old;
    }

    public HttpResponseImpl(boolean frozen, Status status, Object body, String contentType) {
        this.frozen = frozen;
        this.headerIsAlias = false;

        this.status = status;
        this.body = body;

        this.headers.putDirect(HttpHeaderField.CONTENT_TYPE, contentType);
    }

    boolean isReusable() {
        return !(body instanceof InputStream);
    }

    private HttpResponseImpl copyIfFrozen() {
        if (!frozen) {
            return this;
        }

        assert isReusable();

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
            if (!isReusable()) {
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
    public HttpResponse withHeader(HttpHeaderField field, String value) {
        Objects.requireNonNull(value);

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();
        response.headers.putDirect(field, value);
        return response;
    }

    @Override
    public HttpResponse withHeader(HttpHeaderField field, List<String> values) {
        int size = values.size(); // implicit null check

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();

        if (size == 0) {
            response.headers.putDirect(field, null);
        } else if (size == 1) {
            response.headers.putDirect(field, values.get(0));
        } else {
            ArrayList<String> clone = new ArrayList<>(size);
            for (String value : values) {
                clone.add(Objects.requireNonNull(value));
            }
            if (clone.size() != size) {
                throw new ConcurrentModificationException();
            }
            response.headers.putDirect(field, clone);
        }

        return response;
    }

    @Override
    public HttpResponse addHeader(HttpHeaderField field, String value) {
        Objects.requireNonNull(value);

        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();
        response.headers.addDirect(field, value);
        return response;
    }

    @Override
    public HttpResponse removeHeader(HttpHeaderField field) {
        HttpResponseImpl response = copyIfFrozen().ensureHeaderUnaliased();
        response.headers.putDirect(field, null);
        return response;
    }

    // ----

    @Override
    public HttpResponse withBody(ByteBuffer data) {
        HttpResponseImpl response = copyIfFrozen();
        response.body = data;
        response.contentLength = -1L;
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
    public HttpResponse withBody(ReadableByteChannel data, long contentLength) {
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
        return isReusable() || !closed;
    }

    public void close(HttpHandler handler) {
        if (isReusable() || closed) {
            return;
        }
        closed = true;
        handler.safeClose((Closeable) body);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("HttpResponse {\n");
        builder.append("    ").append("HTTP/1.1 ").append(status).append("\n");

        if (!headers.containsKey(HttpHeaderField.DATE)) {
            builder.append("    date: <calculated when request is sent>\n");
        }

        headers.forEachHeader((k, v) -> builder.append("    ").append(k).append(": ").append(v).append('\n'));

        builder.append("\n    ");
        if (body instanceof String) {
            builder.append("<string body, length=").append(((String) body).length()).append('>');
        } else if (body instanceof ByteBuffer) {
            builder.append("<binary body, length=").append(((ByteBuffer) body).remaining()).append('>');
        } else if (body instanceof InputStream || body instanceof ReadableByteChannel) {
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
