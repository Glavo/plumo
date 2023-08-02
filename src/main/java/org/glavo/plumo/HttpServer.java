package org.glavo.plumo;

import org.glavo.plumo.internal.*;

import javax.net.ssl.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
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
        return create(path, false);
    }

    public static HttpServer create(Path path, boolean deleteIfExists) {
        UnixDomainSocketUtils.checkAvailable();

        if (deleteIfExists) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

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

    private final Path unixDomainSocketPath;

    private volatile boolean started = false;
    private volatile boolean stopped = false;
    private volatile HttpServerImpl impl;
    private final AtomicReference<Thread> deleteUnixDomainSocketFileHook;
    private Thread thread;

    public HttpServer(SocketAddress address, Path unixDomainSocketPath) {
        this.address = address;
        this.unixDomainSocketPath = unixDomainSocketPath;
        this.deleteUnixDomainSocketFileHook = unixDomainSocketPath == null ? null : new AtomicReference<>();
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

    public HttpServer setUseHttps(KeyStore loadedKeyStore, KeyManager[] keyManagers) throws GeneralSecurityException {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(loadedKeyStore);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keyManagers, trustManagerFactory.getTrustManagers(), null);
        setUseHttps(ctx);

        return this;
    }

    public HttpServer setUseHttps(KeyStore loadedKeyStore, KeyManagerFactory loadedKeyFactory) throws GeneralSecurityException {
        setUseHttps(loadedKeyStore, loadedKeyFactory.getKeyManagers());
        return this;
    }

    public HttpServer setUseHttps(String keyAndTrustStoreClasspathPath, char[] passphrase) throws IOException {
        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream keystoreStream = HttpServer.class.getResourceAsStream(keyAndTrustStoreClasspathPath);

            if (keystoreStream == null) {
                throw new IOException("Unable to load keystore from classpath: " + keyAndTrustStoreClasspathPath);
            }

            keystore.load(keystoreStream, passphrase);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, passphrase);
            setUseHttps(keystore, keyManagerFactory);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        return this;
    }

    public HttpServer setExecutor(Executor executor) {
        this.executor = executor;
        this.shutdownExecutor = false;
        return this;
    }

    public HttpServer setExecutor(ExecutorService executor, boolean shutdownOnClose) {
        this.executor = executor;
        this.shutdownExecutor = shutdownOnClose;
        return this;
    }

    private void startImpl(ThreadFactory threadFactory) throws IOException {
        HttpServerImpl impl;

        synchronized (this) {
            if (started) {
                throw new IllegalStateException("Server has started");
            }
            started = true;
            AsyncRunner asyncRunner = executor == null ? new AsyncRunner() : new AsyncRunner(executor, shutdownExecutor);

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
                if (this.unixDomainSocketPath == null) {
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

                    UnixDomainSocketUtils.registerShutdownHook(this.deleteUnixDomainSocketFileHook, unixDomainSocketPath);
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
            t.setName("Plumo Main Listener");
            t.setDaemon(daemon);
            return t;
        });
        return this;
    }

    public HttpServer startInNewThread(ThreadFactory threadFactory) throws IOException {
        startImpl(Objects.requireNonNull(threadFactory));
        return this;
    }

    public void stop() {
        synchronized (this) {
            if (stopped) {
                return;
            }

            if (!started) {
                throw new IllegalStateException("Server not started");
            }

            stopped = true;
        }

        HttpServerImpl impl = this.impl;
        if (impl == null) {
            return;
        }

        this.impl = null;

        impl.stop();
        if (thread != null && thread.isAlive()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                HttpServerImpl.LOG.log(Level.SEVERE, "Interrupted", e);
            }
        }

        AtomicReference<Thread> hookHolder = this.deleteUnixDomainSocketFileHook;
        if (hookHolder != null) {
            boolean needDelete = false;
            Thread hook;
            synchronized (hookHolder) {
                hook = hookHolder.get();
                if (hook != null) {
                    needDelete = true;
                    hookHolder.set(null);
                }
            }

            if (needDelete) {
                Runtime.getRuntime().removeShutdownHook(hook);
                IOUtils.deleteIfExists(unixDomainSocketPath);
            }
        }
    }
}