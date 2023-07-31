package org.glavo.webdav.nanohttpd.internal;

/*
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2016 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

import org.glavo.webdav.nanohttpd.TempFileManager;

/**
 * The runnable that will be used for every new client connection.
 */
public final class ClientHandler implements Runnable {

    private final HttpServerImpl httpd;
    private final AsyncRunner asyncRunner;
    private final InetAddress inetAddress;

    private final InputStream inputStream;
    private final OutputStream outputStream;

    private final Closeable acceptSocket;

    volatile ClientHandler prev, next;

    public ClientHandler(HttpServerImpl httpd, AsyncRunner asyncRunner, InetAddress inetAddress,
                         InputStream inputStream, OutputStream outputStream,
                         Closeable acceptSocket) {
        this.httpd = httpd;
        this.asyncRunner = asyncRunner;
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
            TempFileManager tempFileManager = httpd.tempFileManagerFactory.get();
            HttpSession session = new HttpSession(httpd.httpHandler, tempFileManager, this.inputStream, outputStream, inetAddress);

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
            asyncRunner.close(this);
        }
    }
}
