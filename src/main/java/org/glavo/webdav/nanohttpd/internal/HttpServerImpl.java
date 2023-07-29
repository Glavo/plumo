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
import java.io.IOException;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glavo.webdav.nanohttpd.HttpHandler;
import org.glavo.webdav.nanohttpd.TempFileManager;

/**
 * A simple, tiny, nicely embeddable HTTP server in Java
 * <p/>
 * <p/>
 * NanoHTTPD
 * <p>
 * Copyright (c) 2012-2013 by Paul S. Hawke, 2001,2005-2013 by Jarno Elonen,
 * 2010 by Konstantinos Togias
 * </p>
 * <p/>
 * <p/>
 * <b>Features + limitations: </b>
 * <ul>
 * <p/>
 * <li>Only one Java file</li>
 * <li>Java 5 compatible</li>
 * <li>Released as open source, Modified BSD licence</li>
 * <li>No fixed config files, logging, authorization etc. (Implement yourself if
 * you need them.)</li>
 * <li>Supports parameter parsing of GET and POST methods (+ rudimentary PUT
 * support in 1.25)</li>
 * <li>Supports both dynamic content and file serving</li>
 * <li>Supports file upload (since version 1.2, 2010)</li>
 * <li>Supports partial content (streaming)</li>
 * <li>Supports ETags</li>
 * <li>Never caches anything</li>
 * <li>Doesn't limit bandwidth, request time or simultaneous connections</li>
 * <li>Default code serves files and shows all HTTP parameters and headers</li>
 * <li>File server supports directory listing, index.html and index.htm</li>
 * <li>File server supports partial content (streaming)</li>
 * <li>File server supports ETags</li>
 * <li>File server does the 301 redirection trick for directories without '/'</li>
 * <li>File server supports simple skipping for files (continue download)</li>
 * <li>File server serves also very long files without memory overhead</li>
 * <li>Contains a built-in list of most common MIME types</li>
 * <li>All header names are converted to lower case so they don't vary between
 * browsers/clients</li>
 * <p/>
 * </ul>
 * <p/>
 * <p/>
 * <b>How to use: </b>
 * <ul>
 * <p/>
 * <li>Subclass and implement serve() and embed to your own program</li>
 * <p/>
 * </ul>
 * <p/>
 * See the separate "LICENSE.md" file for the distribution license (Modified BSD
 * licence)
 */
public final class HttpServerImpl implements Runnable {

    /**
     * Pseudo-Parameter to use to store the actual query string in the
     * parameters map for later re-processing.
     */
    public static final String QUERY_STRING_PARAMETER = "NanoHttpd.QUERY_STRING";

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
