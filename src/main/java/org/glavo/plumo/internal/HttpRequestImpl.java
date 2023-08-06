package org.glavo.plumo.internal;

import org.glavo.plumo.ContentType;
import org.glavo.plumo.HttpDataFormat;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.internal.util.MultiStringMap;
import org.glavo.plumo.internal.util.ParameterParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

public final class HttpRequestImpl implements HttpRequest {

    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;

    final MultiStringMap headers = new MultiStringMap();

    // Initialize in HttpRequestReader
    Method method;
    String rawUri;
    String httpVersion;
    InputStream body;
    long bodySize;

    public HttpRequestImpl(SocketAddress remoteAddress, SocketAddress localAddress) {
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
    }

    public void close() throws IOException {
        if (body != null) {
            try {
                body.close();
            } finally {
                body = null;
            }
        }
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

    private Map<String, List<String>> cookies;

    @Override
    public Map<String, List<String>> getCookies() {
        if (cookies == null) {
            String cookie = headers.getFirst("cookie");
            if (cookie == null) {
                cookies = Collections.emptyMap();
            } else {
                MultiStringMap msm = new MultiStringMap();
                ParameterParser parser = new ParameterParser(cookie, ',');
                Map.Entry<String, String> pair;
                while ((pair = parser.nextParameter()) != null) {
                    String value = pair.getValue();
                    msm.add(pair.getKey(), value == null ? "" : value);
                }
                cookies = msm;
            }
        }

        return cookies;
    }

    private boolean hasGetBody = false;

    @Override
    @SuppressWarnings("unchecked")
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

    private ContentType contentType;
    private boolean contentTypeInitialized = false;

    @Override
    public ContentType getContentType() {
        if (!contentTypeInitialized) {
            contentTypeInitialized = true;

            String t = headers.getFirst("content-type");
            if (t != null) {
                contentType = new ContentType(t);
            }
        }

        return contentType;
    }

    private URI decodedUri;
    private boolean illegalUri = false;

    @Override
    public URI getURI() {
        if (decodedUri == null) {
            if (illegalUri) {
                return null;
            }

            try {
                decodedUri = new URI(rawUri);
            } catch (URISyntaxException e) {
                illegalUri = true;
            }
        }
        return decodedUri;
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
        headers.forEachPair((k, v) -> builder.append("    ").append(k).append(": ").append(v).append('\n'));
        builder.append("}");
        return builder.toString();
    }
}
