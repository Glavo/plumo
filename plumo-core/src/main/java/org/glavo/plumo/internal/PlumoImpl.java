/*
 * Copyright 2023 Glavo
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
package org.glavo.plumo.internal;

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.Plumo;
import org.glavo.plumo.internal.util.OutputWrapper;
import org.glavo.plumo.internal.util.UnixDomainSocketUtils;
import org.glavo.plumo.internal.util.Utils;
import org.glavo.plumo.internal.util.VirtualThreadUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class PlumoImpl implements Plumo {

    private final ReentrantLock sessionLock = new ReentrantLock();
    private CountDownLatch latch;

    private static final int STATE_INIT = 0;
    private static final int STATE_STARTUP = 1;
    private static final int STATE_RUNNING = 2;
    private static final int STATE_SHUTDOWN = 3;
    private static final int STATE_COMPLETE = 4;

    private final AtomicInteger state = new AtomicInteger();

    private SocketAddress address;
    private Path unixDomainSocketPath;
    private boolean deleteUnixDomainSocketFileIfExists;
    private Executor executor;
    private boolean shutdownExecutor;
    private SSLContext sslContext;
    private String[] sslProtocols;
    private String protocol;
    HttpHandler handler;
    private int timeout = 5000;

    private Closeable serverSocketOrChannel;
    private SocketAddress localAddress;

    private volatile HttpSessionImpl firstSession, lastSession;

    private void ensureNotStarted() {
        if (state.get() != STATE_INIT) {
            throw new IllegalStateException();
        }
    }

    @Override
    public void bind(InetSocketAddress address) {
        ensureNotStarted();

        Objects.requireNonNull(address);

        if (this.address != null) {
            throw new IllegalStateException("The server is already bound to address " + this.address);
        }

        this.address = address;
    }

    @Override
    public void bind(Path path, boolean deleteIfExists) {
        ensureNotStarted();
        UnixDomainSocketUtils.checkAvailable();
        Objects.requireNonNull(path);

        if (this.address != null) {
            throw new IllegalStateException("The server is already bound to address " + this.address);
        }

        this.address = UnixDomainSocketUtils.createUnixDomainSocketAddress(path);
        this.unixDomainSocketPath = path;
        this.deleteUnixDomainSocketFileIfExists = deleteIfExists;
    }

    @Override
    public void setSSLContext(SSLContext sslContext) {
        ensureNotStarted();
        Objects.requireNonNull(sslContext);

        if (this.sslContext != null) {
            throw new IllegalStateException("SSLContext has been set");
        }

        this.sslContext = sslContext;
    }

    @Override
    public void setEnabledSSLProtocols(String[] protocols) {
        ensureNotStarted();
        Objects.requireNonNull(protocols);
        if (protocols.length == 0) {
            throw new IllegalArgumentException("SSL protocols must not be empty");
        }

        if (this.sslProtocols != null) {
            throw new IllegalStateException("SSL protocols has been set");
        }

        if (this.sslContext == null) {
            throw new IllegalStateException("SSLContext must be set first");
        }

        this.sslProtocols = protocols;
    }

    @Override
    public void setSocketTimeout(int timeout) {
        ensureNotStarted();
        if (timeout < 0) {
            throw new IllegalArgumentException("socket timeout must not be negative");
        }

        this.timeout = timeout;
    }

    @Override
    public void setHandler(HttpHandler handler) {
        ensureNotStarted();
        Objects.requireNonNull(handler);

        if (this.handler != null) {
            throw new IllegalStateException("Http handler has been set");
        }

        this.handler = handler;
    }

    // ---

    @Override
    public boolean isRunning() {
        int state = this.state.get();
        return state == STATE_RUNNING || state == STATE_SHUTDOWN;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    private void startImpl(ThreadFactory threadFactory) throws IOException {
        if (!state.compareAndSet(STATE_INIT, STATE_STARTUP)) {
            throw new IllegalStateException("Server has been started");
        }

        if (this.executor == null) {
            final AtomicLong requestCount = new AtomicLong();

            if (Constants.USE_VIRTUAL_THREAD == Boolean.TRUE || (Constants.USE_VIRTUAL_THREAD == null && VirtualThreadUtils.newThread != null)) {
                VirtualThreadUtils.checkAvailable();

                this.shutdownExecutor = false;
                this.executor = command -> {
                    Thread t = VirtualThreadUtils.newVirtualThread(command);
                    t.setName("plumo-worker-" + requestCount.getAndIncrement());
                    t.start();
                };
            } else {
                this.shutdownExecutor = true;
                this.executor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "plumo-worker-" + requestCount.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                });
            }
        }

        if (this.handler == null) {
            this.handler = new DefaultHttpHandler();
        }

        if (this.address == null) {
            this.address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        }

        try {
            if (this.unixDomainSocketPath == null) {
                ServerSocket serverSocket;

                if (sslContext == null) {
                    serverSocketOrChannel = serverSocket = new ServerSocket();
                } else {
                    serverSocketOrChannel = serverSocket = sslContext.getServerSocketFactory().createServerSocket();

                    SSLServerSocket ss = (SSLServerSocket) serverSocket;
                    if (sslProtocols != null) {
                        ss.setEnabledProtocols(sslProtocols);
                    }
                    ss.setUseClientMode(false);
                    ss.setWantClientAuth(false);
                    ss.setNeedClientAuth(false);
                }
                serverSocket.setReuseAddress(true);
                serverSocket.bind(this.address);

                this.localAddress = serverSocket.getLocalSocketAddress();
            } else {
                if (deleteUnixDomainSocketFileIfExists) {
                    Files.deleteIfExists(unixDomainSocketPath);
                }

                ServerSocketChannel serverSocketChannel = UnixDomainSocketUtils.openUnixDomainServerSocketChannel();
                serverSocketOrChannel = serverSocketChannel;
                serverSocketChannel.bind(this.address);

                this.localAddress = serverSocketChannel.getLocalAddress();
            }
        } catch (Throwable e) {
            handler.safeClose(serverSocketOrChannel);
            serverSocketOrChannel = null;
            if (shutdownExecutor) {
                Utils.shutdown(executor);
            }
            throw e;
        }

        this.protocol = sslContext == null ? "http" : "https";
        this.latch = new CountDownLatch(1);

        if (threadFactory == null) {
            this.run();
        } else {
            try {
                threadFactory.newThread(this::run).start();
            } catch (Throwable e) {
                state.set(STATE_COMPLETE);
                latch.countDown();
                throw e;
            }
        }
    }

    @Override
    public void start() throws IOException {
        startImpl(null);
    }

    @Override
    public void startInNewThread(boolean daemon) throws IOException {
        startImpl(runnable -> {
            Thread t = new Thread(runnable);
            t.setName("Plumo Listener [" + localAddress + "]");
            t.setDaemon(daemon);
            return t;
        });
    }

    @Override
    public void startInNewThread(ThreadFactory threadFactory) throws IOException {
        Objects.requireNonNull(threadFactory);
        startImpl(threadFactory);
    }

    @Override
    public void stop() {
        int currentState;
        do {
            currentState = state.get();
            if (currentState >= STATE_SHUTDOWN) {
                return;
            }
        } while (!state.compareAndSet(currentState, STATE_SHUTDOWN));
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        if (latch != null) {
            latch.await();
        }
    }

    // ---

    private void run() {
        if (!state.compareAndSet(STATE_STARTUP, STATE_RUNNING)) {
            int current = state.get();

            if (current == STATE_SHUTDOWN) {
                state.compareAndSet(STATE_SHUTDOWN, STATE_COMPLETE);
                return;
            } else {
                throw new AssertionError(); // ???
            }
        }

        try {
            if (serverSocketOrChannel instanceof ServerSocket) {
                ServerSocket serverSocket = (ServerSocket) serverSocketOrChannel;
                do {
                    try {
                        final Socket socket = serverSocket.accept();
                        if (timeout > 0) {
                            socket.setSoTimeout(timeout);
                        }
                        exec(new HttpSessionImpl(this, socket,
                                socket.getRemoteSocketAddress(), socket.getLocalSocketAddress(),
                                new HttpRequestReader(socket.getInputStream()),
                                new OutputWrapper(socket.getOutputStream(), 1024)));
                    } catch (IOException e) {
                        DefaultLogger.log(DefaultLogger.Level.INFO, "Communication with the client broken", e);
                    }
                } while (!serverSocket.isClosed());
            } else {
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) serverSocketOrChannel;

                do {
                    try {
                        final SocketChannel socketChannel = serverSocketChannel.accept();
                        exec(new HttpSessionImpl(this, socketChannel,
                                socketChannel.getRemoteAddress(), socketChannel.getLocalAddress(),
                                new HttpRequestReader(socketChannel),
                                new OutputWrapper(socketChannel, 1024)));
                    } catch (IOException e) {
                        DefaultLogger.log(DefaultLogger.Level.INFO, "Communication with the client broken", e);
                    }
                } while (serverSocketChannel.isOpen());
            }
        } finally {
            finish();
        }
    }

    private void finish() {
        sessionLock.lock();
        try {
            int currentState;
            do {
                currentState = state.get();
                if (currentState == STATE_COMPLETE) {
                    return;
                }
            } while (!state.compareAndSet(currentState, STATE_COMPLETE));

            HttpSessionImpl session = firstSession;

            while (session != null) {
                session.close();
                session = session.next;
            }

            firstSession = null;
            lastSession = null;

            if (shutdownExecutor) {
                Utils.shutdown(executor);
            }

            handler.safeClose(this.serverSocketOrChannel);

            if (unixDomainSocketPath != null) {
                try {
                    Files.deleteIfExists(unixDomainSocketPath);
                } catch (IOException ignored) {
                }
            }
        } finally {
            latch.countDown();
            sessionLock.unlock();
        }
    }

    void exec(HttpSessionImpl session) throws IOException {
        sessionLock.lock();
        try {
            int currentState = state.get();
            switch (currentState) {
                case STATE_RUNNING:
                    break;
                case STATE_SHUTDOWN:
                    // break the main loop
                    throw new IOException("Server stopped");
                default:
                    throw new AssertionError("impossible state: " + currentState);
            }

            HttpSessionImpl last = this.lastSession;

            if (last == null) {
                firstSession = session;
            } else {
                last.next = session;
                session.prev = last;
            }

            lastSession = session;
        } finally {
            sessionLock.unlock();
        }

        executor.execute(session);
    }

    void close(HttpSessionImpl session) {
        sessionLock.lock();
        try {
            if (state.get() == STATE_COMPLETE) {
                return;
            }

            HttpSessionImpl next = session.next;
            HttpSessionImpl prev = session.prev;

            if (prev == null) {
                firstSession = next;
            } else {
                prev.next = next;
                session.prev = null;
            }

            if (next == null) {
                lastSession = prev;
            } else {
                next.prev = prev;
                session.next = null;
            }
        } finally {
            sessionLock.unlock();
        }

        session.close();
    }

}
