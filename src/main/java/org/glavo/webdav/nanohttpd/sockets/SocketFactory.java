package org.glavo.webdav.nanohttpd.sockets;

import java.io.IOException;
import java.net.ServerSocket;

@FunctionalInterface
public interface SocketFactory {
    static SocketFactory getDefault() {
        return ServerSocket::new;
    }

    ServerSocket create() throws IOException;
}
