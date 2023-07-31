package org.glavo.plumo.internal;

import org.glavo.plumo.HttpRequest;

import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.function.BiConsumer;

@SuppressWarnings("unchecked")
public final class HttpRequestImpl implements HttpRequest {

    final MultiStringMap headers = new MultiStringMap();

    Method method;
    String rawUri;
    String httpVersion;

    SocketAddress remoteAddress;
    SocketAddress localAddress;

    private URI decodedUri;
    private boolean illegalUri = false;

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

    public void forEachHeader(BiConsumer<String, String> consumer) {
        for (Map.Entry<String, Object> entry : headers.map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                consumer.accept(key, (String) value);
            } else {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) value;
                for (String v : list) {
                    consumer.accept(key, v);
                }
            }
        }
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
    public URI getUri() {
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
    public String getRawUri() {
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
        builder.append("HttpRequest {\n");
        builder.append("    ").append(method).append(' ').append(rawUri).append(' ').append(httpVersion).append('\n');
        forEachHeader((k, v) -> builder.append("    ").append(k).append(": ").append(v).append('\n'));
        builder.append("}");
        return builder.toString();
    }
}
