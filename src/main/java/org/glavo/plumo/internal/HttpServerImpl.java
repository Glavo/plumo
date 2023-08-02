package org.glavo.plumo.internal;

import java.io.Closeable;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.TempFileManager;
import org.glavo.plumo.internal.util.IOUtils;
import org.jetbrains.annotations.NotNull;

public final class HttpServerImpl implements Runnable, AutoCloseable {

    /**
     * logger to log to.
     */
    public static final Logger LOG = Logger.getLogger(HttpServerImpl.class.getName());

    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean closed = false;

    final Closeable ss;

    final HttpHandler httpHandler;
    final Supplier<TempFileManager> tempFileManagerFactory;
    final int timeout;

    private final Executor executor;
    private final boolean shutdownExecutor;

    private volatile HttpSession firstSession, lastSession;

    public HttpServerImpl(Closeable ss,
                          HttpHandler httpHandler, Supplier<TempFileManager> tempFileManagerFactory,
                          Executor executor, boolean shutdownExecutor,
                          int timeout) {
        this.ss = ss;
        this.httpHandler = httpHandler;
        this.tempFileManagerFactory = tempFileManagerFactory;
        this.timeout = timeout;

        if (executor == null) {
            this.executor = VirtualThreadExecutor.AVAILABLE ? new VirtualThreadExecutor() : new DefaultExecutor();
            this.shutdownExecutor = false;
        } else {
            this.executor = executor;
            this.shutdownExecutor = shutdownExecutor;
        }
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
            session.close();
        }
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

    @Override
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
                ((ExecutorService) executor).shutdown();
            }

            IOUtils.safeClose(this.ss);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {
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
                    HttpServerImpl.LOG.log(Level.FINE, "Communication with the client broken", e);
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
                    HttpServerImpl.LOG.log(Level.FINE, "Communication with the client broken", e);
                }
            } while (serverSocketChannel.isOpen());
        }
    }

    private static final class DefaultExecutor implements Executor {
        private long requestCount;

        @Override
        public void execute(@NotNull Runnable command) {
            Thread t = new Thread(command, "Plumo Request Processor (#" + this.requestCount++ + ")");
            t.setDaemon(true);
            t.start();
        }
    }

    private static final class VirtualThreadExecutor implements Executor {
        static final boolean AVAILABLE;
        static final boolean NEED_ENABLE_PREVIEW;

        private static final MethodHandle newThread;

        static {
            boolean available = false;
            boolean needEnablePreview = false;
            MethodHandle newThreadHandle = null;

            try {
                Class<?> vtBuilder = Class.forName("java.lang.Thread$Builder$OfVirtual");

                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Object factory = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(vtBuilder)).invoke();

                newThreadHandle = lookup.findVirtual(vtBuilder, "unstarted", MethodType.methodType(Thread.class, Runnable.class))
                        .bindTo(factory);

                available = true;
            } catch (UnsupportedOperationException ignored) {
                needEnablePreview = true;
            } catch (Throwable ignored) {
            }

            AVAILABLE = available;
            NEED_ENABLE_PREVIEW = needEnablePreview;
            newThread = newThreadHandle;
        }

        public static void checkAvailable() {
            if (!AVAILABLE) {
                throw new UnsupportedOperationException(NEED_ENABLE_PREVIEW
                        ? "Preview Features not enabled, need to run with --enable-preview"
                        : "Please upgrade to Java 19+");
            }
        }

        private long requestCount;

        public VirtualThreadExecutor() {
        }

        @Override
        public void execute(@NotNull Runnable command) {
            try {
                Thread t = (Thread) newThread.invokeExact(command);
                t.setName("Plumo Request Processor (#" + this.requestCount++ + ")");
                t.start();
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }
    }
}
