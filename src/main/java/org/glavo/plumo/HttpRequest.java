package org.glavo.plumo;

import org.glavo.plumo.internal.BodyFormats;

import java.io.IOException;
import java.io.InputStream;
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

    default <V, E extends Throwable> V getBody(BodyFormat<V, ?, E> type) throws E {
        return getBody(type, null);
    }

    <V, A, E extends Throwable> V getBody(BodyFormat<V, A, E> type, A arg) throws E;

    long getBodySize();

    ContentType getContentType();

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

    interface BodyFormat<V, A, E extends Throwable> {
        BodyFormat<InputStream, ?, RuntimeException> INPUT_STREAM = BodyFormats.INPUT_STREAM;
        BodyFormat<String, ?, IOException> TEXT = BodyFormats.TEXT;
        BodyFormat<byte[], ?, IOException> BYTES = BodyFormats.BYTES;

        V decode(HttpRequest request, InputStream input, A arg) throws E;
    }
}
