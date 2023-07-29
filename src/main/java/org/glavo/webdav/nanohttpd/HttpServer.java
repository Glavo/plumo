package org.glavo.webdav.nanohttpd;

import org.glavo.webdav.nanohttpd.internal.AsyncRunner;
import org.glavo.webdav.nanohttpd.internal.DefaultTempFileManager;
import org.glavo.webdav.nanohttpd.internal.HttpServerImpl;
import org.glavo.webdav.nanohttpd.internal.UnixDomainSocketUtils;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
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
        return create(UnixDomainSocketUtils.createUnixDomainSocketAddress(path));
    }

    public static HttpServer create(SocketAddress address) {
        return new HttpServer(Objects.requireNonNull(address));
    }

    private final SocketAddress address;

    private HttpHandler httpHandler;
    private Supplier<TempFileManager> tempFileManagerFactory;
    private Executor executor;
    private boolean shutdownExecutor;
    private int timeout = SOCKET_READ_TIMEOUT;
    private SSLServerSocketFactory sslServerSocketFactory;
    private String[] sslProtocols;

    private Thread thread;

    public HttpServer(SocketAddress address) {
        this.address = address;
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

    public HttpServer setUseHttps(SSLServerSocketFactory factory) {
        setUseHttps(factory, null);
        return this;
    }

    public HttpServer setUseHttps(SSLServerSocketFactory factory, String[] sslProtocols) {
        if (!(address instanceof InetSocketAddress)) {
            throw new UnsupportedOperationException();
        }

        this.sslServerSocketFactory = Objects.requireNonNull(factory);
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
                        HttpResponse.newResponse(HttpResponse.Status.NOT_FOUND, "Not Found");
            }

            Supplier<TempFileManager> tempFileManagerFactory = this.tempFileManagerFactory;
            if (tempFileManagerFactory == null) {
                tempFileManagerFactory = DefaultTempFileManager::new;
            }

            Closeable s = null;

            if (this.address instanceof InetSocketAddress) {
                ServerSocket serverSocket;

                if (sslServerSocketFactory == null) {
                    serverSocket = new ServerSocket();
                } else {
                    serverSocket = sslServerSocketFactory.createServerSocket();

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
                s = serverSocket;
            } else {
                ServerSocketChannel serverSocketChannel = UnixDomainSocketUtils.openUnixDomainServerSocketChannel();
                serverSocketChannel.bind(this.address);
                s = serverSocketChannel;
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
    }
}
