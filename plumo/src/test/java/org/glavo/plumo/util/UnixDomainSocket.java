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

import okhttp3.OkHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnixDomainSocket extends Socket {

    public static OkHttpClient.Builder newClientBuilder(Path socketFile) {
        return new OkHttpClient.Builder().socketFactory(new UnixDomainSocketFactory(socketFile));
    }

    private final SocketChannel channel;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean indown = new AtomicBoolean(false);
    private final AtomicBoolean outdown = new AtomicBoolean(false);

    private final InputStream in;
    private final OutputStream out;

    public UnixDomainSocket(SocketChannel channel) {
        this.channel = channel;
        in = Channels.newInputStream(new UnselectableByteChannel(channel));
        out = Channels.newOutputStream(new UnselectableByteChannel(channel));
    }

    @Override
    public void bind(SocketAddress local) throws IOException {
        if (null != channel) {
            if (isClosed()) {
                throw new SocketException("Socket is closed");
            }
            if (isBound()) {
                throw new SocketException("already bound");
            }
//            try {
//                channel.bind(local);
//            } catch (IOException e) {
//                throw new SocketException(e);
//            }
        }
    }

    @Override
    public void close() throws IOException {
        if (null != channel && closed.compareAndSet(false, true)) {
            try {
                channel.close();
            } catch (IOException e) {
                ignore();
            }
        }
    }

    @Override
    public void connect(SocketAddress addr) throws IOException {
        connect(addr, 0);
    }

    @Override
    public void connect(SocketAddress addr, int timeout) throws IOException {
//        try {
//            channel.connect(addr);
//        } catch (UnsupportedAddressTypeException e) {
//            throw new IllegalArgumentException(e);
//        }
    }

    @Override
    public SocketChannel getChannel() {
        return channel;
    }

    @Override
    public InetAddress getInetAddress() {
        return null;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (channel.isConnected()) {
            return in;
        } else {
            throw new IOException("not connected");
        }
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        try {
            return channel.getLocalAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (channel.isConnected()) {
            return out;
        } else {
            throw new IOException("not connected");
        }
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        try {
            return channel.getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean isBound() {
        return getLocalAddress() != null;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    public boolean isInputShutdown() {
        return indown.get();
    }

    @Override
    public boolean isOutputShutdown() {
        return outdown.get();
    }

    @Override
    public void shutdownInput() throws IOException {
        if (indown.compareAndSet(false, true)) {
            channel.shutdownInput();
        }
    }

    @Override
    public void shutdownOutput() throws IOException {
        if (outdown.compareAndSet(false, true)) {
            channel.shutdownOutput();
        }
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        try {
            return channel.getOption(StandardSocketOptions.SO_KEEPALIVE);
        } catch (IOException e) {
            throw new SocketException(e);
        }
    }

    @Override
    public int getReceiveBufferSize() throws SocketException {
        try {
            return channel.getOption(StandardSocketOptions.SO_RCVBUF);
        } catch (IOException e) {
            throw new SocketException(e);
        }
    }

    @Override
    public int getSendBufferSize() throws SocketException {
        try {
            return channel.getOption(StandardSocketOptions.SO_SNDBUF);
        } catch (IOException e) {
            throw new SocketException(e);
        }
    }

    @Override
    public int getSoTimeout() throws SocketException {
//        try {
//            return channel.getOption(StandardSocketOptions.SO_RCVTIMEO);
//        } catch (IOException e) {
//            throw new SocketException(e);
//        }
        return 0;
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        try {
            channel.setOption(StandardSocketOptions.SO_KEEPALIVE, on);
        } catch (IOException e) {
            throw new SocketException(e);
        }
    }

    @Override
    public void setReceiveBufferSize(int size) throws SocketException {
        try {
            channel.setOption(StandardSocketOptions.SO_RCVBUF, size);
        } catch (IOException e) {
            throw new SocketException(e);
        }
    }

    @Override
    public void setSendBufferSize(int size) throws SocketException {
        try {
            channel.setOption(StandardSocketOptions.SO_SNDBUF, size);
        } catch (IOException e) {
            throw new SocketException(e);
        }
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
//        try {
//            channel.setOption(StandardSocketOptions.SO_RCVTIMEO, timeout);
//        } catch (IOException e) {
//            throw new SocketException(e);
//        }
    }

    private void ignore() {
    }

    /**
     * A byte channel that doesn't implement {@link SelectableChannel}. Though
     * that type isn't in the public API, if the channel passed in implements
     * that interface then unwanted synchronization is performed which can harm
     * concurrency and can cause deadlocks.
     * <p>
     * https://bugs.openjdk.java.net/browse/JDK-4774871
     */
    private static final class UnselectableByteChannel implements ReadableByteChannel, WritableByteChannel {
        private final SocketChannel channel;

        UnselectableByteChannel(SocketChannel channel) {
            this.channel = channel;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return channel.write(src);
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return channel.read(dst);
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }
}
