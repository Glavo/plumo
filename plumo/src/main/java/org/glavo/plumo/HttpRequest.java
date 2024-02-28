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
package org.glavo.plumo;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface HttpRequest {

    String getHttpVersion();

    Method getMethod();

    URI getURI();

    String getRawURI();

    SocketAddress getRemoteAddress();

    InetAddress getRemoteInetAddress();

    SocketAddress getLocalAddress();

    InetAddress getLocalInetAddress();

    Map<HttpHeaderField, List<String>> getHeaders();

    boolean containsHeader(HttpHeaderField field);

    default boolean containsHeader(String field) {
        try {
            return containsHeader(HttpHeaderField.of(field));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    String getHeader(HttpHeaderField field);

    default String getHeader(String field) {
        try {
            return getHeader(HttpHeaderField.of(field));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    List<String> getHeaders(HttpHeaderField field);

    default List<String> getHeaders(String field) {
        try {
            return getHeaders(HttpHeaderField.of(field));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    Map<String, List<String>> getCookies();

    default <V, E extends Throwable> V getBody(HttpDataDecoder<V, ?, E> decoder) throws E {
        return getBody(decoder, null);
    }

    <V, A, E extends Throwable> V getBody(HttpDataDecoder<V, A, E> decoder, A arg) throws E;

    long getBodySize();

    /**
     * HTTP Request methods.
     */
    enum Method {
        GET,
        PUT,
        POST,
        DELETE,
        HEAD,
        OPTIONS,
        TRACE,
        CONNECT,
        PATCH,
        PROPFIND,
        PROPPATCH,
        MKCOL,
        MOVE,
        COPY,
        LOCK,
        UNLOCK,
        NOTIFY,
        SUBSCRIBE
    }

}
