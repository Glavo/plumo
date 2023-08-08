package org.glavo.plumo.internal;

import org.glavo.plumo.Plumo;
import org.glavo.plumo.internal.util.IOUtils;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

public final class HttpListener implements Runnable {

    public final ReentrantLock runningLock = new ReentrantLock();

    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed = false;

    final Closeable ss;
    final Path unixDomainSocketPath;

    final String protocol;
    final Plumo.Handler handler;
    final int timeout;

    private final Executor executor;
    private final boolean shutdownExecutor;

    private volatile HttpSession firstSession, lastSession;

    public HttpListener(Closeable ss, Path unixDomainSocketPath,
                        String protocol, Plumo.Handler handler,
                        Executor executor, boolean shutdownExecutor,
                        int timeout) {
        this.ss = ss;
        this.unixDomainSocketPath = unixDomainSocketPath;
        this.protocol = protocol;
        this.handler = handler;
        this.timeout = timeout;
        this.executor = executor;
        this.shutdownExecutor = shutdownExecutor;
    }

    void close(HttpSession session) {
        lock.lock();
        try {
            if (closed) {
                return;
            }

            HttpSession next = session.next;
            HttpSession prev = session.prev;

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

    void exec(HttpSession session) {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Runner is closed");
            }

            HttpSession last = this.lastSession;

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
    }

    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }

            closed = true;

            HttpSession session = firstSession;

            while (session != null) {
                session.close();
                session = session.next;
            }

            firstSession = null;
            lastSession = null;

            if (shutdownExecutor) {
                IOUtils.shutdown(executor);
            }

            IOUtils.safeClose(this.ss);
            IOUtils.deleteIfExists(unixDomainSocketPath);
        } finally {
            lock.unlock();
        }
    }

    public SocketAddress getLocalAddress() {
        if (ss instanceof ServerSocket) {
            ServerSocket serverSocket = (ServerSocket) ss;
            return !serverSocket.isClosed() ? serverSocket.getLocalSocketAddress() : null;
        } else {
            ServerSocketChannel serverSocketChannel  = (ServerSocketChannel) ss;
            try {
                return serverSocketChannel.isOpen() ? serverSocketChannel.getLocalAddress() : null;
            } catch (IOException e) {
                return null;
            }
        }
    }

    @Override
    public void run() {
        runningLock.lock();
        try {
            if (closed) {
                return;
            }

            if (ss instanceof ServerSocket) {
                ServerSocket serverSocket = (ServerSocket) ss;
                do {
                    try {
                        final Socket socket = serverSocket.accept();
                        if (timeout > 0) {
                            socket.setSoTimeout(timeout);
                        }
                        exec(new HttpSession(this, socket,
                                socket.getRemoteSocketAddress(), socket.getLocalSocketAddress(),
                                socket.getInputStream(), socket.getOutputStream()));
                    } catch (IOException e) {
                        Plumo.LOGGER.log(Plumo.Logger.Level.INFO, "Communication with the client broken", e);
                    }
                } while (!serverSocket.isClosed());
            } else {
                ServerSocketChannel serverSocketChannel = (ServerSocketChannel) ss;

                do {
                    try {
                        final SocketChannel socketChannel = serverSocketChannel.accept();
                        exec(new HttpSession(this, socketChannel,
                                socketChannel.getRemoteAddress(), socketChannel.getLocalAddress(),
                                Channels.newInputStream(socketChannel),
                                Channels.newOutputStream(socketChannel)));
                    } catch (IOException e) {
                        Plumo.LOGGER.log(Plumo.Logger.Level.INFO, "Communication with the client broken", e);
                    }
                } while (serverSocketChannel.isOpen());
            }
        } finally {
            close();
            runningLock.unlock();
        }
    }
}
