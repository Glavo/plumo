package org.glavo.webdav.nanohttpd;

import org.glavo.webdav.nanohttpd.internal.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class HttpServer {

    public static final int SOCKET_READ_TIMEOUT = 5000;

    public static HttpServer create(int port) {
        return create(new InetSocketAddress(port));
    }

    public static HttpServer create(InetAddress address, int port) {
        return create(new InetSocketAddress(address, port));
    }

    public static HttpServer create(String hostname, int port) {
        return create(new InetSocketAddress(hostname, port));
    }

    public static HttpServer create(Path path) {
        UnixDomainSocketUtils.checkAvailable();
        return new HttpServer(UnixDomainSocketUtils.createUnixDomainSocketAddress(path), path);
    }

    public static HttpServer create(InetSocketAddress address) {
        return new HttpServer(Objects.requireNonNull(address), null);
    }

    private final SocketAddress address;

    private HttpHandler httpHandler;
    private Supplier<TempFileManager> tempFileManagerFactory;
    private Executor executor;
    private boolean shutdownExecutor;
    private int timeout = SOCKET_READ_TIMEOUT;
    private SSLContext sslContext;
    private String[] sslProtocols;

    private final Path unixSocketPath;

    private Thread thread;

    public HttpServer(SocketAddress address, Path unixSocketPath) {
        this.address = address;
        this.unixSocketPath = unixSocketPath;
    }

    public HttpServer setSocketReadTimeout(int timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout: " + timeout);
        }

        this.timeout = timeout;
        return this;
    }

    public HttpServer setHttpHandler(HttpHandler handler) {
        this.httpHandler = Objects.requireNonNull(handler);
        return this;
    }

    /**
     * Pluggable strategy for creating and cleaning up temporary files.
     *
     * @param tempFileManagerFactory new strategy for handling temp files.
     */
    public HttpServer setTempFileManagerFactory(Supplier<TempFileManager> tempFileManagerFactory) {
        this.tempFileManagerFactory = tempFileManagerFactory;
        return this;
    }

    public HttpServer setUseHttps(SSLContext context) {
        setUseHttps(context, null);
        return this;
    }

    public HttpServer setUseHttps(SSLContext context, String[] sslProtocols) {
        if (!(address instanceof InetSocketAddress)) {
            throw new UnsupportedOperationException();
        }

        this.sslContext = Objects.requireNonNull(context);
        this.sslProtocols = sslProtocols;
        return this;
    }

    public HttpServer setExecutor(Executor executor) {
        this.executor = executor;
        this.shutdownExecutor = executor instanceof ExecutorService;
        return this;
    }

    public HttpServer setExecutorNotShutdown(ExecutorService executor) {
        this.executor = executor;
        this.shutdownExecutor = false;
        return this;
    }

    public HttpServer setUseVirtualThreadExecutor() {
        AsyncRunner.VirtualThreadExecutor.checkAvailable();
        this.executor = new AsyncRunner.VirtualThreadExecutor();
        this.shutdownExecutor = false;
        return this;
    }

    private volatile HttpServerImpl impl;
    private volatile Thread deleteUnixDomainSocketFileHook;

    private void deleteUnixDomainSocketFile() {
        try {
            Files.deleteIfExists(unixSocketPath);
        } catch (IOException e) {
            HttpServerImpl.LOG.log(Level.WARNING, "Could not delete unix socket file", e);
        }
    }

    private void startImpl(ThreadFactory threadFactory) throws IOException {
        HttpServerImpl impl;
        synchronized (this) {
            impl = this.impl;
            if (impl != null) {
                throw new IllegalStateException();
            }

            AsyncRunner asyncRunner;
            if (executor == null) {
                asyncRunner = new AsyncRunner(new AsyncRunner.DefaultExecutor(), false);
            } else {
                asyncRunner = new AsyncRunner(executor, shutdownExecutor);
            }

            HttpHandler httpHandler = this.httpHandler;
            if (httpHandler == null) {
                httpHandler = session ->
                        HttpResponse.newResponse(HttpResponse.Status.NOT_FOUND).setBody("Not Found");
            }

            Supplier<TempFileManager> tempFileManagerFactory = this.tempFileManagerFactory;
            if (tempFileManagerFactory == null) {
                tempFileManagerFactory = DefaultTempFileManager::new;
            }

            Closeable s = null;

            try {
                if (this.unixSocketPath == null) {
                    ServerSocket serverSocket;

                    if (sslContext == null) {
                        s = serverSocket = new ServerSocket();
                    } else {
                        s = serverSocket = sslContext.getServerSocketFactory().createServerSocket();

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
                } else {
                    ServerSocketChannel serverSocketChannel = UnixDomainSocketUtils.openUnixDomainServerSocketChannel();
                    s = serverSocketChannel;
                    serverSocketChannel.bind(this.address);

                    Thread hook = new Thread(this::deleteUnixDomainSocketFile);
                    Runtime.getRuntime().addShutdownHook(hook);
                    this.deleteUnixDomainSocketFileHook = hook;
                }
            } catch (Throwable e) {
                IOUtils.safeClose(s);
                throw e;
            }

            this.impl = impl = new HttpServerImpl(
                    s,
                    httpHandler, tempFileManagerFactory,
                    timeout, asyncRunner
            );
        }

        if (threadFactory == null) {
            impl.run();
        } else {
            this.thread = threadFactory.newThread(impl);
            this.thread.start();
        }
    }

    public HttpServer start() throws IOException {
        startImpl(null);
        return this;
    }

    public HttpServer startInNewThread() throws IOException {
        startInNewThread(true);
        return this;
    }

    public HttpServer startInNewThread(boolean daemon) throws IOException {
        startImpl(runnable -> {
            Thread t = new Thread(runnable);
            t.setName("NanoHttpd Main Listener");
            t.setDaemon(daemon);
            return t;
        });
        return this;
    }

    public HttpServer startInNewThread(ThreadFactory threadFactory) throws IOException {
        startImpl(Objects.requireNonNull(threadFactory));
        return this;
    }

    public synchronized void stop() {
        HttpServerImpl impl = this.impl;
        if (impl == null) {
            return;
        }

        impl.stop();
        if (thread != null && thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                HttpServerImpl.LOG.log(Level.SEVERE, "Interrupted", e);
            }
        }

        Thread hook = this.deleteUnixDomainSocketFileHook;
        if (hook != null) {
            deleteUnixDomainSocketFileHook = null;
            Runtime.getRuntime().removeShutdownHook(hook);
            deleteUnixDomainSocketFile();
        }
    }
}
