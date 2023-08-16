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
import java.util.List;
import java.util.Map;

public interface HttpRequest {

    String getHttpVersion();

    Method getMethod();

    String getRawURI();

    SocketAddress getRemoteAddress();

    InetAddress getRemoteInetAddress();

    SocketAddress getLocalAddress();

    InetAddress getLocalInetAddress();

    Map<String, List<String>> getHeaders();

    boolean containsHeader(String name);

    String getHeader(String name);

    List<String> getHeaders(String name);

    String getHost();

    default <V, E extends Throwable> V getBody(HttpDataFormat<V, ?, E> type) throws E {
        return getBody(type, null);
    }

    <V, A, E extends Throwable> V getBody(HttpDataFormat<V, A, E> type, A arg) throws E;

    long getBodySize();

    /**
     * HTTP Request methods, with the ability to decode a <code>String</code> back
     * to its enum value.
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
