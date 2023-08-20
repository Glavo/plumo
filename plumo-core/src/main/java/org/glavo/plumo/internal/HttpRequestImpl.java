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

import org.glavo.plumo.HttpDataFormat;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.internal.util.MultiStringMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

public final class HttpRequestImpl implements HttpRequest {

    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;

    public final MultiStringMap headers = new MultiStringMap();

    // Initialize in HttpRequestReader
    Method method;
    URI uri;
    String rawUri;
    String httpVersion;
    InputStream body;
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
    public boolean containsHeader(String name) {
        return headers.containsKey(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String getHeader(String name) {
        return headers.getFirst(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public List<String> getHeaders(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    private boolean hasGetBody = false;

    @Override
    public <V, A, E extends Throwable> V getBody(HttpDataFormat<V, A, E> type, A arg) throws E {
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
    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    @Override
    public String getHost() {
        return headers.getFirst("host");
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
