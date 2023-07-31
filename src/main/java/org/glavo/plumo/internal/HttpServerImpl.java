package org.glavo.plumo.internal;

import java.io.Closeable;
import java.io.IOException;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.TempFileManager;

public final class HttpServerImpl implements Runnable {

    /**
     * logger to log to.
     */
    public static final Logger LOG = Logger.getLogger(HttpServerImpl.class.getName());

    final Closeable ss;

    final HttpHandler httpHandler;
    final Supplier<TempFileManager> tempFileManagerFactory;
    final int timeout;

    /**
     * Pluggable strategy for asynchronously executing requests.
     */
    private final AsyncRunner asyncRunner;

    public HttpServerImpl(Closeable ss,
                          HttpHandler httpHandler, Supplier<TempFileManager> tempFileManagerFactory,
                          int timeout,
                          AsyncRunner asyncRunner) {
        this.ss = ss;
        this.httpHandler = httpHandler;
        this.tempFileManagerFactory = tempFileManagerFactory;
        this.timeout = timeout;
        this.asyncRunner = asyncRunner;
    }

    @Override
    public void run() {
        if (ss instanceof ServerSocket) {
            ServerSocket serverSocket = (ServerSocket) ss;
            do {
                try {
                    final Socket finalAccept = serverSocket.accept();
                    if (timeout > 0) {
                        finalAccept.setSoTimeout(timeout);
                    }
                    asyncRunner.exec(new ClientHandler(this, asyncRunner,
                            finalAccept.getLocalAddress(),
                            finalAccept.getInputStream(), finalAccept.getOutputStream(),
                            finalAccept));
                } catch (IOException e) {
                    HttpServerImpl.LOG.log(Level.FINE, "Communication with the client broken", e);
                }
            } while (!serverSocket.isClosed());
        } else {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) ss;

            do {
                try {
                    final SocketChannel finalAccept = serverSocketChannel.accept();
                    asyncRunner.exec(new ClientHandler(this, asyncRunner,
                            InetAddress.getLoopbackAddress(),
                            Channels.newInputStream(finalAccept),
                            Channels.newOutputStream(finalAccept),
                            finalAccept));
                } catch (IOException e) {
                    HttpServerImpl.LOG.log(Level.FINE, "Communication with the client broken", e);
                }
            } while (serverSocketChannel.isOpen());
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        try {
            IOUtils.safeClose(this.ss);
            this.asyncRunner.close();
        } catch (Exception e) {
            HttpServerImpl.LOG.log(Level.SEVERE, "Could not stop all connections", e);
        }
    }
}
