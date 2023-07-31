package org.glavo.webdav.nanohttpd;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.URI;

public interface HttpRequest {

    HttpHeaders getHeaders();

    InputStream getBody();

    Method getMethod();

    URI getUri();

    String getRawUri();

    SocketAddress getRemoteAddress();

    InetAddress getRemoteInetAddress();

    SocketAddress getLocalAddress();

    InetAddress getLocalInetAddress();

    String getHost();

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
        SUBSCRIBE;

        public static Method lookup(String method) {
            if (method == null)
                return null;

            try {
                return valueOf(method);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
