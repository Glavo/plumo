package org.glavo.plumo;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface HttpRequest {

    boolean containsHeader(String name);

    String getHeader(String name);

    List<String> getHeaders(String name);

    Map<String, List<String>> getHeaders();

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
