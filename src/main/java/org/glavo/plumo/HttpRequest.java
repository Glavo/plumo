package org.glavo.plumo;

import java.io.InputStream;
import java.net.ContentHandler;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

public interface HttpRequest {

    Method getMethod();

    URI getURI();

    String getRawURI();

    SocketAddress getRemoteAddress();

    InetAddress getRemoteInetAddress();

    SocketAddress getLocalAddress();

    InetAddress getLocalInetAddress();

    Map<String, List<String>> getHeaders();

    boolean containsHeader(String name);

    String getHeader(String name);

    List<String> getHeaders(String name);

    Map<String, List<String>> getCookies();

    String getHost();

    InputStream getBody();

    long getBodySize();

    HttpContentType getContentType();

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
