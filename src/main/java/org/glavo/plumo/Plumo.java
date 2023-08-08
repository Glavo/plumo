package org.glavo.plumo;

import org.glavo.plumo.internal.Constants;
import org.glavo.plumo.internal.DefaultLogger;
import org.glavo.plumo.internal.HttpListener;
import org.glavo.plumo.internal.util.IOUtils;
import org.glavo.plumo.internal.util.UnixDomainSocketUtils;
import org.glavo.plumo.internal.util.VirtualThreadUtils;

import javax.net.ssl.*;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

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

        private boolean built = false;

        private void ensureAvailable() {
            if (built) {
                throw new IllegalStateException();
            }
        }

        private void shutdownExecutorIfNecessary() {
            if (shutdownExecutor && holdExecutor) {
                IOUtils.shutdown(executor);
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
            ensureAvailable();
            built = true;

            Executor executor = this.executor;
            boolean shutdownExecutor = this.shutdownExecutor;
            try {
                if (executor == null) {
                    final AtomicLong requestCount = new AtomicLong();

                    if (Constants.USE_VIRTUAL_THREAD == Boolean.TRUE || (Constants.USE_VIRTUAL_THREAD == null && VirtualThreadUtils.AVAILABLE)) {
                        VirtualThreadUtils.checkAvailable();

                        shutdownExecutor = false;
                        executor = command -> {
                            Thread t = VirtualThreadUtils.newVirtualThread(command);
                            t.setName("Plumo Request Processor #" + requestCount.getAndIncrement());
                            t.start();
                        };
                    } else {
                        shutdownExecutor = true;
                        executor = Executors.newCachedThreadPool(r -> {
                            Thread t = new Thread(r, "Plumo Request Processor #" + requestCount.getAndIncrement());
                            t.setDaemon(true);
                            return t;
                        });
                    }
                }

                Handler handler = this.handler;
                if (handler == null) {
                    handler = session -> HttpResponse.newPlainTextResponse("");
                }

                SocketAddress address = this.address;
                if (address == null) {
                    assert unixDomainSocketPath == null;
                    address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
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
                    IOUtils.shutdown(executor);
                }
                throw e;
            }
        }

        public Builder listen(int port) {
            ensureAvailable();
            try {
                this.address = new InetSocketAddress(port);
                this.unixDomainSocketPath = null;
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                throw e;
            }
            return this;
        }

        public Builder listen(String hostName, int port) {
            ensureAvailable();
            try {
                this.address = new InetSocketAddress(hostName, port);
                this.unixDomainSocketPath = null;
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                throw e;
            }
            return this;
        }

        public Builder listen(InetSocketAddress address) {
            ensureAvailable();
            this.address = safeCheckNull(address);
            this.unixDomainSocketPath = null;
            return this;
        }

        public Builder listen(Path path) {
            ensureAvailable();
            listen(path, false);
            return this;
        }

        public Builder listen(Path path, boolean deleteIfExists) {
            ensureAvailable();
            try {
                this.address = UnixDomainSocketUtils.createUnixDomainSocketAddress(path);
                this.unixDomainSocketPath = path;
                this.deleteUnixDomainSocketFileIfExists = deleteIfExists;
            } catch (Throwable e) {
                shutdownExecutorIfNecessary();
                throw e;
            }
            return this;
        }

        public Builder setSocketReadTimeout(int timeout) {
            ensureAvailable();
            if (timeout <= 0) {
                shutdownExecutorIfNecessary();
                throw new IllegalArgumentException("timeout: " + timeout);
            }

            this.timeout = timeout;
            return this;
        }

        public Builder setHandler(Handler handler) {
            ensureAvailable();
            this.handler = safeCheckNull(handler);
            return this;
        }

        public Builder setUseHttps(SSLContext context, String... sslProtocols) {
            ensureAvailable();
            this.sslContext = safeCheckNull(context);
            this.sslProtocols = sslProtocols;
            return this;
        }

        public Builder setUseHttps(KeyStore loadedKeyStore, KeyManager[] keyManagers) throws GeneralSecurityException {
            ensureAvailable();
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
            ensureAvailable();
            setUseHttps(loadedKeyStore, loadedKeyFactory.getKeyManagers());
            return this;
        }

        public Builder setUseHttps(String keyAndTrustStoreClasspathPath, char[] passphrase) throws IOException {
            ensureAvailable();
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
            ensureAvailable();
            shutdownExecutorIfNecessary();

            this.executor = executor;
            this.shutdownExecutor = false;
            this.holdExecutor = false;
            return this;
        }

        public Builder setExecutor(ExecutorService executor, boolean shutdownOnClose) {
            ensureAvailable();
            shutdownExecutorIfNecessary();

            this.executor = safeCheckNull(executor);
            this.shutdownExecutor = shutdownOnClose;
            this.holdExecutor = shutdownOnClose;
            return this;
        }
    }

    public static Plumo newHttpServer(int port, Handler handler) {
        Objects.requireNonNull(handler);

        return Plumo.newBuilder()
                .listen(port)
                .setHandler(handler)
                .build();
    }

    public static Plumo newHttpServer(InetSocketAddress address, Handler handler) {
        Objects.requireNonNull(address);
        Objects.requireNonNull(handler);

        return Plumo.newBuilder()
                .listen(address)
                .setHandler(handler)
                .build();
    }

    public static Plumo newHttpServer(Path path, Handler handler) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(handler);

        return Plumo.newBuilder()
                .listen(path)
                .setHandler(handler)
                .build();
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

    public static final Logger LOGGER;

    static {
        if (Constants.LOGGER_PROVIDER != null) {
            try {
                Class<?> providerClass = Class.forName(Constants.LOGGER_PROVIDER);
                @SuppressWarnings("deprecation")
                LoggerProvider provider = (LoggerProvider) providerClass.newInstance();
                LOGGER = provider.getLogger();
            } catch (Throwable e) {
                throw new Error(e);
            }
        } else {
            LOGGER = new DefaultLogger(System.out);
        }
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

    private Plumo(SocketAddress address, Path unixDomainSocketPath, boolean deleteUnixDomainSocketFileIfExists,
                  Handler handler,
                  Executor executor, boolean shutdownExecutor,
                  int timeout,
                  SSLContext sslContext, String[] sslProtocols) {
        this.address = address;
        this.unixDomainSocketPath = unixDomainSocketPath;
        this.deleteUnixDomainSocketFileIfExists = deleteUnixDomainSocketFileIfExists;
        this.handler = handler;
        this.executor = executor;
        this.shutdownExecutor = shutdownExecutor;
        this.timeout = timeout;
        this.sslContext = sslContext;
        this.sslProtocols = sslProtocols;
    }

    private void startImpl(ThreadFactory threadFactory) throws IOException {
        HttpListener listener;

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
                }
            } catch (Throwable e) {
                IOUtils.safeClose(s);
                if (shutdownExecutor) {
                    IOUtils.shutdown(executor);
                }
                throw e;
            }

            this.listener = listener = new HttpListener(
                    s,
                    unixDomainSocketPath,
                    sslContext == null ? "http" : "https",
                    handler,
                    executor, shutdownExecutor,
                    timeout
            );
        }

        if (threadFactory == null) {
            listener.run();
        } else {
            threadFactory.newThread(listener).start();
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
            if (runnable instanceof HttpListener) {
                t.setName("Plumo Listener [" + ((HttpListener) runnable).getLocalAddress() + "]");
            } else {
                t.setName("Plumo Main Listener");
            }
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
    }

    public boolean isRunning() {
        HttpListener listener = this.listener;
        return listener != null && listener.runningLock.isLocked();
    }

    public SocketAddress getLocalAddress() {
        HttpListener listener = this.listener;
        return listener != null ? listener.getLocalAddress() : null;
    }

    public int getPort() {
        SocketAddress addr = getLocalAddress();
        return addr instanceof InetSocketAddress ? ((InetSocketAddress) addr).getPort() : -1;
    }

    public String getProtocol() {
        return sslContext == null ? "http" : "https";
    }

    public static void main(String[] args) throws Throwable {
        Plumo.newHttpServer(10001, request -> {
            LOGGER.log(Logger.Level.INFO, "Hello World!");
            LOGGER.log(Logger.Level.INFO, "Hello World!", new Exception());
            LOGGER.log(Logger.Level.INFO, "Hello World!");


            System.out.println(Thread.currentThread());
            System.out.println(request);
            System.out.println(request.getBody(HttpDataFormat.TEXT));

            return HttpResponse.newHtmlResponse("<body>Hello World!</body>");
        }).start();
    }
}
