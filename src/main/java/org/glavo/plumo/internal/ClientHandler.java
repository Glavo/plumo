package org.glavo.plumo.internal;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

import org.glavo.plumo.TempFileManager;

/**
 * The runnable that will be used for every new client connection.
 */
public final class ClientHandler implements Runnable {

    private final HttpServerImpl server;
    private final InetAddress inetAddress;

    private final InputStream inputStream;
    private final OutputStream outputStream;

    private final Closeable acceptSocket;

    volatile ClientHandler prev, next;

    public ClientHandler(HttpServerImpl server, InetAddress inetAddress,
                         InputStream inputStream, OutputStream outputStream,
                         Closeable acceptSocket) {
        this.server = server;
        this.inetAddress = inetAddress;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.acceptSocket = acceptSocket;
    }

    void closeSocket() {
        IOUtils.safeClose(this.outputStream);
        IOUtils.safeClose(this.inputStream);
        IOUtils.safeClose(this.acceptSocket);
    }

    @Override
    public void run() {
        try {
            TempFileManager tempFileManager = server.tempFileManagerFactory.get();
            HttpSession session = new HttpSession(server.httpHandler, tempFileManager, this.inputStream, outputStream, inetAddress);

            if (this.acceptSocket instanceof Socket) {
                Socket socket = (Socket) this.acceptSocket;
                while (!socket.isClosed()) {
                    session.execute();
                }
            } else {
                SocketChannel socketChannel = (SocketChannel) this.acceptSocket;
                while (socketChannel.isOpen()) {
                    session.execute();
                }
            }
        } catch (ServerShutdown | SocketTimeoutException ignored) {
            // When the socket is closed by the client,
            // we throw our own SocketException
            // to break the "keep alive" loop above. If
            // the exception was anything other
            // than the expected SocketException OR a
            // SocketTimeoutException, print the
            // stacktrace
        } catch (Exception e) {
            HttpServerImpl.LOG.log(Level.SEVERE, "Communication with the client broken, or an bug in the handler code", e);
        } finally {
            server.close(this);
        }
    }
}
