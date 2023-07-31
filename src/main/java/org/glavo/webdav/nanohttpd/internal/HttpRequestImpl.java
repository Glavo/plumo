package org.glavo.webdav.nanohttpd.internal;

import org.glavo.webdav.nanohttpd.HttpHeaders;
import org.glavo.webdav.nanohttpd.HttpRequest;

import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.function.BiConsumer;

@SuppressWarnings("unchecked")
public final class HttpRequestImpl implements HttpRequest, HttpHeaders {

    final Map<String, Object> headers = new HashMap<>();

    Method method;
    String rawUri;
    String httpVersion;

    SocketAddress remoteAddress;
    SocketAddress localAddress;

    private URI decodedUri;
    private boolean illegalUri = false;

    // headers

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public boolean contains(String name) {
        return headers.containsKey(name.toLowerCase(Locale.ROOT));
    }

    String getImpl(String name) {
        Object v = headers.get(name);
        if (v == null) {
            return null;
        } else if (v instanceof String) {
            return ((String) v);
        } else {
            return ((List<String>) v).get(0);
        }
    }

    @Override
    public String get(String name) {
        return getImpl(name.toLowerCase(Locale.ROOT));
    }

    List<String> getAllImpl(String name) {
        Object v = headers.get(name);

        if (v == null) {
            return Collections.emptyList();
        } else if (v instanceof String) {
            return Collections.singletonList((String) v);
        } else {
            return (List<String>) v;
        }
    }

    @Override
    public List<String> getAll(String name) {
        return getAllImpl(name.toLowerCase(Locale.ROOT));
    }

    void putHeader(String name, String value) {
        headers.compute(name.toLowerCase(Locale.ROOT), (key, oldValue) -> {
            if (oldValue == null) {
                return value;
            }

            List<String> list;
            if (oldValue instanceof String) {
                list = new ArrayList<>(4);
                list.add((String) oldValue);
            } else {
                list = (List<String>) oldValue;
            }
            list.add(value);
            return list;
        });
    }

    @Override
    public void forEach(BiConsumer<String, String> consumer) {
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
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
    public HttpHeaders getHeaders() {
        return this;
    }

    @Override
    public String getHost() {
        return getImpl("host");
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("HttpRequest {\n");
        builder.append("    ").append(method).append(' ').append(rawUri).append(' ').append(httpVersion).append('\n');
        forEach((k, v) -> builder.append("    ").append(k).append(": ").append(v).append('\n'));
        builder.append("}");
        return builder.toString();
    }
}
