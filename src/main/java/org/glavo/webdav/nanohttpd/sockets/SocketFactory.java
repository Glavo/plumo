package org.glavo.webdav.nanohttpd.sockets;

import java.io.IOException;
import java.net.ServerSocket;

@FunctionalInterface
public interface SocketFactory {
    ServerSocket create() throws IOException;
}
