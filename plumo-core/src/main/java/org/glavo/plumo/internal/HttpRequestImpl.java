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

import org.glavo.plumo.HttpDataDecoder;
import org.glavo.plumo.HttpHeaderField;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.internal.util.InputWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

public final class HttpRequestImpl implements HttpRequest {

    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;

    public final Headers headers = new Headers();

    // Initialize in HttpRequestReader
    Method method;
    URI uri;
    String rawUri;
    String httpVersion;
    InputWrapper body;
    long bodySize;

    public HttpRequestImpl(SocketAddress remoteAddress, SocketAddress localAddress) {
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
    }

    public void finish() throws IOException {
        if (body != null) {
            try {
                body.close();
            } finally {
                body = null;
            }
        }
    }

    @Override
    public String getHttpVersion() {
        return httpVersion;
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public boolean containsHeader(HttpHeaderField field) {
        return headers.containsKey(field);
    }

    @Override
    public String getHeader(HttpHeaderField field) {
        return headers.getFirst(field);
    }

    @Override
    public List<String> getHeaders(HttpHeaderField field) {
        return headers.get(field);
    }

    private boolean hasGetBody = false;

    @Override
    public <V, A, E extends Throwable> V getBody(HttpDataDecoder<V, A, E> type, A arg) throws E {
        Objects.requireNonNull(type);
        if (hasGetBody) {
            throw new IllegalStateException();
        }

        hasGetBody = true;
        return type.decode(this, body, arg);
    }

    @Override
    public long getBodySize() {
        return bodySize;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public String getRawURI() {
        return rawUri;
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public InetAddress getRemoteInetAddress() {
        return remoteAddress instanceof InetSocketAddress
                ? ((InetSocketAddress) remoteAddress).getAddress()
                : InetAddress.getLoopbackAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public InetAddress getLocalInetAddress() {
        return localAddress instanceof InetSocketAddress
                ? ((InetSocketAddress) localAddress).getAddress()
                : InetAddress.getLoopbackAddress();
    }

    @Override
    public Map<HttpHeaderField, List<String>> getHeaders() {
        return headers;
    }

    private Map<String, List<String>> cookies;

    @Override
    public Map<String, List<String>> getCookies() {
        if (cookies == null) {
            cookies = new HashMap<>();
            // TODO
        }

        return cookies;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("HttpRequest[remote-address=").append(remoteAddress)
                .append(", ")
                .append("local-address=").append(localAddress)
                .append("] {\n");
        builder.append("    ").append(method).append(' ').append(rawUri).append(' ').append(httpVersion).append('\n');
        headers.forEachHeader((k, v) -> builder.append("    ").append(k).append(": ").append(v).append('\n'));
        builder.append("}");
        return builder.toString();
    }
}
