package org.glavo.plumo;

import org.glavo.plumo.internal.*;
import org.glavo.plumo.internal.util.IOUtils;
import org.glavo.plumo.internal.util.UnixDomainSocketUtils;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Iterator;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class Plumo {

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private SocketAddress address;
        private Path unixDomainSocketPath;
        private boolean deleteUnixDomainSocketFileIfExists;

        private Handler handler;
        private int timeout = Constants.SOCKET_TIMEOUT;
        private SSLContext sslContext;
        private String[] sslProtocols;

        private Executor executor;
        private boolean shutdownExecutor;
        private boolean holdExecutor;

        private void shutdownExecutorIfNecessary() {
            if (shutdownExecutor && holdExecutor) {
                ((ExecutorService) executor).shutdown();
            }
        }

        private <T> T safeCheckNull(T t) throws NullPointerException {
            if (t == null) {
                shutdownExecutorIfNecessary();
                throw new NullPointerException();
            }

            return t;
        }

        Builder() {
        }

        public Plumo build() {
            if (shutdownExecutor && !holdExecutor) {
                throw new IllegalStateException("Need to reset the executor");
            }

            Executor executor = this.executor;
            boolean shutdownExecutor = this.shutdownExecutor;
            try {
                if (executor == null) {
                    if (Constants.USE_VIRTUAL_THREAD == Boolean.TRUE || (Constants.USE_VIRTUAL_THREAD == null && VirtualThreadExecutor.AVAILABLE)) {
                        shutdownExecutor = false;
                        executor = new VirtualThreadExecutor();
                    } else {
                        shutdownExecutor = true;

                        final AtomicLong requestCount = new AtomicLong();
                        executor = Executors.newCachedThreadPool(r -> {
                            Thread t = new Thread(r, "Plumo Request Processor (#" + requestCount.getAndIncrement() + ")");
                            t.setDaemon(true);
                            return t;
                        });
                    }
                }

                Handler handler = this.handler;
                if (handler == null) {
                    handler = session -> HttpResponse.newBuilder()
                            .setStatus(HttpResponse.Status.NOT_FOUND)
                            .setBody("Not Found")
                            .build();
                }

                if (this.executor != null && shutdownExecutor) {
                    holdExecutor = false;
                }

                return new Plumo(address, unixDomainSocketPath, deleteUnixDomainSocketFileIfExists,
                        handler,
                        executor, shutdownExecutor,
                        timeout, sslContext, sslProtocols);
            } catch (Throwable e) {
                if (shutdownExecutor && executor != null) {
                    ((ExecutorService) executor).shutdown();
                }
                throw e;
            }
        }

        public Builder setAddress(int port) {
            try {
                this.address = new InetSocketAddress(port);
                this.unixDomainSocketPath = null;
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                throw e;
            }
            return this;
        }

        public Builder setAddress(InetAddress inetAddress, int port) {
            try {
                this.address = new InetSocketAddress(inetAddress, port);
                this.unixDomainSocketPath = null;
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                throw e;
            }
            return this;
        }

        public Builder setAddress(String hostName, int port) {
            try {
                this.address = new InetSocketAddress(hostName, port);
                this.unixDomainSocketPath = null;
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                throw e;
            }
            return this;
        }

        public Builder setAddress(InetSocketAddress address) {
            this.address = safeCheckNull(address);
            this.unixDomainSocketPath = null;
            return this;
        }

        public Builder setAddress(Path path) {
            setAddress(path, false);
            return this;
        }

        public Builder setAddress(Path path, boolean deleteIfExists) {
            try {
                this.address = UnixDomainSocketAddress.of(path);
                this.unixDomainSocketPath = path;
                this.deleteUnixDomainSocketFileIfExists = deleteIfExists;
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                throw e;
            }
            return this;
        }

        public Builder setSocketReadTimeout(int timeout) {
            if (timeout <= 0) {
                shutdownExecutorIfNecessary();
                throw new IllegalArgumentException("timeout: " + timeout);
            }

            this.timeout = timeout;
            return this;
        }

        public Builder setHandler(Handler handler) {
            this.handler = safeCheckNull(handler);
            return this;
        }

        public Builder setUseHttps(SSLContext context) {
            setUseHttps(context, null);
            return this;
        }

        public Builder setUseHttps(SSLContext context, String[] sslProtocols) {
            this.sslContext = safeCheckNull(context);
            this.sslProtocols = sslProtocols;
            return this;
        }

        public Builder setUseHttps(KeyStore loadedKeyStore, KeyManager[] keyManagers) throws GeneralSecurityException {
            try {
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(loadedKeyStore);
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(keyManagers, trustManagerFactory.getTrustManagers(), null);
                setUseHttps(ctx);
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                throw e;
            }

            return this;
        }

        public Builder setUseHttps(KeyStore loadedKeyStore, KeyManagerFactory loadedKeyFactory) throws GeneralSecurityException {
            setUseHttps(loadedKeyStore, loadedKeyFactory.getKeyManagers());
            return this;
        }

        public Builder setUseHttps(String keyAndTrustStoreClasspathPath, char[] passphrase) throws IOException {
            try {
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                InputStream keystoreStream = Plumo.class.getResourceAsStream(keyAndTrustStoreClasspathPath);

                if (keystoreStream == null) {
                    throw new IOException("Unable to load keystore from classpath: " + keyAndTrustStoreClasspathPath);
                }

                keystore.load(keystoreStream, passphrase);
                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keystore, passphrase);
                setUseHttps(keystore, keyManagerFactory);
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                if (e instanceof IOException) {
                    throw (IOException) e;
                } else {
                    throw new IOException(e);
                }
            }

            return this;
        }

        public Builder setExecutor(Executor executor) {
            shutdownExecutorIfNecessary();

            this.executor = executor;
            this.shutdownExecutor = false;
            this.holdExecutor = false;
            return this;
        }

        public Builder setExecutor(ExecutorService executor, boolean shutdownOnClose) {
            shutdownExecutorIfNecessary();

            this.executor = safeCheckNull(executor);
            this.shutdownExecutor = shutdownOnClose;
            this.holdExecutor = shutdownOnClose;
            return this;
        }
    }

    public static final Logger LOGGER;

    static {
        Iterator<LoggerProvider> providerIterator = ServiceLoader.load(LoggerProvider.class).iterator();

        if (providerIterator.hasNext()) {
            // TODO: Provider Name?
            LOGGER = providerIterator.next().getLogger();
        } else {
            LOGGER = new DefaultLogger(System.out);
        }
    }

    public static Plumo newHttpServer(int port, Handler handler) {
        Objects.requireNonNull(handler);

        return Plumo.newBuilder()
                .setAddress(port)
                .setHandler(handler)
                .build();
    }

    public static Plumo newHttpServer(InetSocketAddress address, Handler handler) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(handler);

        return Plumo.newBuilder()
                .setAddress(address)
                .setHandler(handler)
                .build();
    }

    public static Plumo newHttpServer(Path path, Handler handler) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(handler);

        return Plumo.newBuilder()
                .setAddress(path)
                .setHandler(handler)
                .build();
    }

    private final SocketAddress address;
    private final Path unixDomainSocketPath;
    private final boolean deleteUnixDomainSocketFileIfExists;
    private final Handler handler;
    private final Executor executor;
    private final boolean shutdownExecutor;
    private final int timeout;
    private final SSLContext sslContext;
    private final String[] sslProtocols;

    private volatile boolean started = false;
    private volatile boolean stopped = false;
    private volatile HttpListener listener;
    private final AtomicReference<Thread> deleteUnixDomainSocketFileHook;

    private Plumo(SocketAddress address, Path unixDomainSocketPath, boolean deleteUnixDomainSocketFileIfExists,
                  Handler handler,
                  Executor executor, boolean shutdownExecutor,
                  int timeout,
                  SSLContext sslContext, String[] sslProtocols) {
        this.address = address;
        this.unixDomainSocketPath = unixDomainSocketPath;
        this.deleteUnixDomainSocketFileIfExists = deleteUnixDomainSocketFileIfExists;
        this.deleteUnixDomainSocketFileHook = unixDomainSocketPath == null ? null : new AtomicReference<>();
        this.handler = handler;
        this.executor = executor;
        this.shutdownExecutor = shutdownExecutor;
        this.timeout = timeout;
        this.sslContext = sslContext;
        this.sslProtocols = sslProtocols;
    }

    private void startImpl(ThreadFactory threadFactory) throws IOException {
        HttpListener impl;

        synchronized (this) {
            if (started) {
                throw new IllegalStateException("Server has started");
            }
            started = true;

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
                    if (deleteUnixDomainSocketFileIfExists) {
                        IOUtils.deleteIfExists(unixDomainSocketPath);
                    }

                    ServerSocketChannel serverSocketChannel = UnixDomainSocketUtils.openUnixDomainServerSocketChannel();
                    s = serverSocketChannel;
                    serverSocketChannel.bind(this.address);

                    UnixDomainSocketUtils.registerShutdownHook(this.deleteUnixDomainSocketFileHook, unixDomainSocketPath);
                }
            } catch (Throwable e) {
                IOUtils.safeClose(s);
                if (shutdownExecutor) {
                    ((ExecutorService) executor).shutdown();
                }
                throw e;
            }

            this.listener = impl = new HttpListener(
                    s,
                    handler,
                    executor, shutdownExecutor,
                    timeout
            );
        }

        if (threadFactory == null) {
            impl.run();
        } else {
            threadFactory.newThread(impl).start();
        }
    }

    public Plumo start() throws IOException {
        startImpl(null);
        return this;
    }

    public Plumo startInNewThread() throws IOException {
        startInNewThread(true);
        return this;
    }

    public Plumo startInNewThread(boolean daemon) throws IOException {
        startImpl(runnable -> {
            Thread t = new Thread(runnable);
            t.setName("Plumo Main Listener");
            t.setDaemon(daemon);
            return t;
        });
        return this;
    }

    public Plumo startInNewThread(ThreadFactory threadFactory) throws IOException {
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

        HttpListener listener = this.listener;
        if (listener == null) {
            return;
        }

        this.listener = null;

        listener.close();

        // wait running lock
        listener.runningLock.lock();
        listener.runningLock.unlock();

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

    public interface Logger {
        enum Level {
            ALL(Integer.MIN_VALUE),
            TRACE(400),
            DEBUG(500),
            INFO(800),
            WARNING(900),
            ERROR(1000),
            OFF(Integer.MAX_VALUE);

            private final int severity;

            Level(int severity) {
                this.severity = severity;
            }

            public int getSeverity() {
                return severity;
            }
        }

        default void log(Level level, String message) {
            log(level, message, null);
        }

        void log(Level level, String message, Throwable exception);
    }

    public interface LoggerProvider {
        Logger getLogger();
    }

    @FunctionalInterface
    public interface Handler {
        HttpResponse handle(HttpRequest request) throws IOException, HttpResponseException;
    }

    public static void main(String[] args) throws Throwable {
        Plumo.newHttpServer(10001, request -> {
            System.out.println(Thread.currentThread());
            System.out.println(request);
            System.out.println(request.getBody(HttpDataFormat.TEXT));

            return HttpResponse.newHtmlResponse("<body>Hello World!</body>");
        }).start();
    }
}
