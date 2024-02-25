/*
 * Copyright 2024 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.plumo.util;

import org.glavo.plumo.internal.util.UnixDomainSocketUtils;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.StandardProtocolFamily;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

public final class UnixDomainSocketFactory extends SocketFactory {
    private final Path path;

    public UnixDomainSocketFactory(Path path) {
        this.path = path;
    }

    @Override public Socket createSocket() throws IOException {
        SocketChannel socketChannel;
        try {
            socketChannel = (SocketChannel) SocketChannel.class.getMethod("open", ProtocolFamily.class)
                    .invoke(null, StandardProtocolFamily.valueOf("UNIX"));
            socketChannel.connect(UnixDomainSocketUtils.createUnixDomainSocketAddress(path));
        } catch (Throwable e) {
            throw new IOException(e);
        }

        return new UnixDomainSocket(socketChannel);
    }

    @Override public Socket createSocket(String host, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override public Socket createSocket(
            InetAddress host, int port, InetAddress localAddress, int localPort) throws IOException {
        throw new UnsupportedOperationException();
    }
}
