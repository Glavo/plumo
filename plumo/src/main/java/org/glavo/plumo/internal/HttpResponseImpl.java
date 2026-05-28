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

import org.glavo.plumo.HttpHeaderField;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.ResponseBody;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/// Immutable implementation of `HttpResponse`.
@NotNullByDefault
public final class HttpResponseImpl implements HttpResponse {
    final Status status;
    final Headers headers;
    final ResponseBody body;

    /// Creates an immutable response.
    public HttpResponseImpl(Status status, Headers headers, ResponseBody body) {
        this.status = Objects.requireNonNull(status);
        this.headers = Objects.requireNonNull(headers);
        this.body = Objects.requireNonNull(body);
    }

    /// Returns the internal immutable header table.
    public Headers internalHeaders() {
        return headers;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public @Nullable String getHeader(HttpHeaderField field) {
        return headers.getFirst(field);
    }

    @Override
    public Map<HttpHeaderField, @Unmodifiable List<String>> getHeaders() {
        LinkedHashMap<HttpHeaderField, List<String>> result = new LinkedHashMap<>();
        headers.forEachHeader((field, value) -> result.computeIfAbsent(field, ignored -> new ArrayList<>(1)).add(value));

        result.replaceAll((field, values) -> Collections.unmodifiableList(values));
        return Collections.unmodifiableMap(result);
    }

    @Override
    public ResponseBody getBody() {
        return body;
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

        builder.append("\n    ").append(body);
        builder.append("\n}");

        return builder.toString();
    }

    /// Mutable builder used to create immutable response objects.
    @NotNullByDefault
    public static final class BuilderImpl implements HttpResponse.Builder {
        private Status status = Status.OK;
        private Headers headers = new Headers();
        private ResponseBody body = ResponseBody.empty();

        /// Creates an empty response builder.
        public BuilderImpl() {
        }

        /// Creates a response builder initialized from an existing response.
        public BuilderImpl(HttpResponse response) {
            this.status = response.getStatus();
            this.body = response.getBody();

            if (response instanceof HttpResponseImpl) {
                this.headers = ((HttpResponseImpl) response).headers.clone();
            } else {
                response.getHeaders().forEach((field, values) -> {
                    if (values.size() == 1) {
                        headers.putDirect(field, values.get(0));
                    } else if (!values.isEmpty()) {
                        headers.putDirect(field, new ArrayList<>(values));
                    }
                });
            }
        }

        @Override
        public BuilderImpl withStatus(Status status) {
            this.status = Objects.requireNonNull(status);
            return this;
        }

        @Override
        public BuilderImpl withHeader(HttpHeaderField field, String value) {
            headers.putDirect(Objects.requireNonNull(field), Objects.requireNonNull(value));
            return this;
        }

        @Override
        public BuilderImpl withHeader(HttpHeaderField field, List<String> values) {
            Objects.requireNonNull(field);
            int size = values.size();

            if (size == 0) {
                headers.removeDirect(field);
            } else if (size == 1) {
                headers.putDirect(field, Objects.requireNonNull(values.get(0)));
            } else {
                ArrayList<String> clone = new ArrayList<>(size);
                for (String value : values) {
                    clone.add(Objects.requireNonNull(value));
                }
                if (clone.size() != size) {
                    throw new ConcurrentModificationException();
                }
                headers.putDirect(field, clone);
            }

            return this;
        }

        @Override
        public BuilderImpl addHeader(HttpHeaderField field, String value) {
            headers.addDirect(Objects.requireNonNull(field), Objects.requireNonNull(value));
            return this;
        }

        @Override
        public BuilderImpl removeHeader(HttpHeaderField field) {
            headers.removeDirect(Objects.requireNonNull(field));
            return this;
        }

        @Override
        public BuilderImpl withBody(ResponseBody body) {
            this.body = Objects.requireNonNull(body);
            return this;
        }

        @Override
        public HttpResponse build() {
            return new HttpResponseImpl(status, headers.clone(), body);
        }
    }
}
