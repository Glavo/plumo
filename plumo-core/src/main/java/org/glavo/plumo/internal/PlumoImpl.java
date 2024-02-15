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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class PlumoImpl implements Plumo {

    private final ReentrantLock lock = new ReentrantLock();
    private final CountDownLatch latch = new CountDownLatch(1);

    private final SocketAddress address;
    private final Path unixDomainSocketPath;
    private final boolean deleteUnixDomainSocketFileIfExists;
    private final Executor executor;
    private final boolean shutdownExecutor;
    private final SSLContext sslContext;
    private final String[] sslProtocols;
    private final int timeout;
    final HttpHandler handler;
    private final String protocol;

    private volatile SocketAddress localAddress;

    private volatile HttpSessionImpl firstSession;
    private volatile HttpSessionImpl lastSession;

    private static final int STATUS_INIT = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_TERMINATING = 2;
    private static final int STATUS_FINISH = 3;

    private volatile int status = STATUS_INIT;

    public PlumoImpl(SocketAddress address, Path unixDomainSocketPath, boolean deleteUnixDomainSocketFileIfExists, Executor executor, boolean shutdownExecutor, SSLContext sslContext, String[] sslProtocols, int timeout, HttpHandler handler) {
        this.address = address;
        this.unixDomainSocketPath = unixDomainSocketPath;
        this.deleteUnixDomainSocketFileIfExists = deleteUnixDomainSocketFileIfExists;
        this.executor = executor;
        this.shutdownExecutor = shutdownExecutor;
        this.sslContext = sslContext;
        this.sslProtocols = sslProtocols;
        this.timeout = timeout;
        this.handler = handler;

        this.protocol = sslContext == null ? "http" : "https";
    }

    private volatile Closeable serverSocketOrChannel;

    @Override
    public boolean isRunning() {
        int status = this.status;
        return status > STATUS_INIT && status < STATUS_FINISH;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            int status = this.status;

            if (status == STATUS_INIT) {
                this.status = STATUS_FINISH;
            } else if (this.status == STATUS_RUNNING) {
                this.status = STATUS_TERMINATING;
                handler.safeClose(serverSocketOrChannel);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void awaitTermination() throws InterruptedException {
        if (status < STATUS_FINISH) {
            latch.await();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (status < STATUS_FINISH) {
            return latch.await(timeout, unit);
        } else {
            return true;
        }
    }

    @Override
    public void start(boolean daemon) throws IOException {
        startImpl(r -> {
            Thread thread = new Thread(r);
            thread.setName("Plumo Listener [" + localAddress + "]");
            if (daemon) {
                thread.setDaemon(true);
            }
            return thread;
        });
    }

    @Override
    public void start(ThreadFactory threadFactory) throws IOException {
        Objects.requireNonNull(threadFactory);
        startImpl(threadFactory);
    }

    @Override
    public void startAndWait() throws IOException {
        startImpl(null);
    }

    private void startImpl(ThreadFactory threadFactory) throws IOException {
        lock.lock();
        try {
            if (status != STATUS_INIT) {
                throw new IllegalStateException();
            }

            if (this.unixDomainSocketPath == null && (sslContext != null || timeout > 0)) {
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
                ServerSocketChannel serverSocketChannel;

                if (unixDomainSocketPath == null) {
                    serverSocketChannel = ServerSocketChannel.open();
                } else {
                    if (deleteUnixDomainSocketFileIfExists) {
                        Files.deleteIfExists(unixDomainSocketPath);
                    }
                    serverSocketChannel = UnixDomainSocketUtils.openUnixDomainServerSocketChannel();
                }

                serverSocketOrChannel = serverSocketChannel;
                serverSocketChannel.bind(this.address);

                this.localAddress = serverSocketChannel.getLocalAddress();
            }

            if (threadFactory == null) {
                this.run();
            } else {
                threadFactory.newThread(this::run).start();
            }
        } catch (Throwable e) {
            status = STATUS_FINISH;
            handler.safeClose(serverSocketOrChannel);
            serverSocketOrChannel = null;
            if (shutdownExecutor) {
                Utils.shutdown(executor);
            }
            lock.unlock();
            throw e;
        }
    }

    private void run() {
        lock.lock();
        try {
            if (this.status != STATUS_INIT) {
                throw new IllegalStateException("status: " + this.status);
            }

            this.status = STATUS_RUNNING;
        } finally {
            lock.unlock();
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
                        if (!exec(new HttpSessionImpl(this, socket,
                                socket.getRemoteSocketAddress(), socket.getLocalSocketAddress(),
                                new HttpRequestReader(socket.getInputStream()),
                                new OutputWrapper(socket.getOutputStream(), 1024)))) {
                            break;
                        }
                    } catch (IOException e) {
                        DefaultLogger.log(DefaultLogger.Level.INFO, "Communication with the client broken", e);
                    }
                } while (!serverSocket.isClosed());
            } else {
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) serverSocketOrChannel;

                do {
                    try {
                        final SocketChannel socketChannel = serverSocketChannel.accept();
                        if (!exec(new HttpSessionImpl(this, socketChannel,
                                socketChannel.getRemoteAddress(), socketChannel.getLocalAddress(),
                                new HttpRequestReader(socketChannel),
                                new OutputWrapper(socketChannel, 1024)))) {
                            break;
                        }
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
        lock.lock();
        try {
            if (status == STATUS_FINISH) {
                return;
            }
            status = STATUS_FINISH;

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
            lock.unlock();
            latch.countDown();
        }
    }

    private boolean exec(HttpSessionImpl session) throws IOException {
        lock.lock();
        try {
            if (status != STATUS_RUNNING) {
                return false;
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
            lock.unlock();
        }

        executor.execute(session);
        return true;
    }

    void close(HttpSessionImpl session) {
        lock.lock();
        try {
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
            lock.unlock();
        }

        session.close();
    }

}
