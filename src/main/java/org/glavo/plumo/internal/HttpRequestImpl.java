package org.glavo.plumo.internal;

import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.internal.util.MultiStringMap;

import java.io.InputStream;
import java.net.*;
import java.util.*;

@SuppressWarnings("unchecked")
public final class HttpRequestImpl implements HttpRequest {

    final MultiStringMap headers = new MultiStringMap();

    Method method;
    String rawUri;
    String httpVersion;

    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;

    private URI decodedUri;
    private boolean illegalUri = false;

    public HttpRequestImpl(SocketAddress remoteAddress, SocketAddress localAddress) {
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
    }

    // headers

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

    @Override
    public Map<String, List<String>> getCookies() {
        return null; // TODO
    }

    // request

    @Override
    public InputStream getBody() {
        return null;
    }

    @Override
    public Method getMethod() {
        return method;
    }

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
