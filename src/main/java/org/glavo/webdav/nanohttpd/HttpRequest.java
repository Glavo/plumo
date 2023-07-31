package org.glavo.webdav.nanohttpd;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.URI;

public interface HttpRequest {

    HttpHeaders getHeaders();

    InputStream getBody();

    HttpRequestMethod getMethod();

    URI getUri();

    String getRawUri();

    SocketAddress getRemoteAddress();

    InetAddress getRemoteInetAddress();

    SocketAddress getLocalAddress();

    InetAddress getLocalInetAddress();

    String getHost();
}
