/*
 * Copyright 2023 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.plumo.internal;

import org.glavo.plumo.*;
import org.glavo.plumo.internal.util.OutputWrapper;

import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class HttpSessionImpl implements HttpSession, Runnable, Closeable {

    public final PlumoImpl server;
    public final Closeable socket;
    public final SocketAddress remoteAddress;
    public final SocketAddress localAddress;

    public final HttpRequestReader requestReader;
    public final OutputWrapper output;

    // Use in HttpServerImpl
    volatile HttpSessionImpl prev, next;

    private Object userData;

    public HttpSessionImpl(PlumoImpl server, Closeable acceptSocket,
                           SocketAddress remoteAddress, SocketAddress localAddress,
                           HttpRequestReader requestReader, OutputWrapper output) {
        this.server = server;
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.requestReader = requestReader;
        this.output = output;
        this.socket = acceptSocket;
    }

    @Override
    public void run() {
        HttpHandler handler = server.handler;
        try {
            while (isOpen()) {
                HttpRequestImpl request = null;
                try {
                    request = new HttpRequestImpl(remoteAddress, localAddress);
                    try {
                        requestReader.readHeader(request);
                    } catch (EOFException e) {
                        return;
                    }

                    HttpResponse r = null;
                    try {
                        try {
                            r = handler.handle(request);
                            if (r == null) {
                                return;
                            }
                        } catch (Throwable e) {
                            r = handler.handleRecoverableException(this, request, e);
                        }

                        if (r == null) {
                            return;
                        }

                        String connection = request.headers.getFirst(HttpHeaderField.CONNECTION);
                        boolean keepAlive = "1.1".equals(request.getHttpVersion()) && (connection == null || !connection.equals("close"));

                        send(request, r, output, keepAlive);

                        if (!keepAlive || "close".equals(r.getHeader(HttpHeaderField.CONNECTION))) {
                            return;
                        }
                    } finally {
                        if (r != null) {
                            handler.safeClose(r.getBody());
                        }
                        request.finish();
                    }
                } catch (SocketTimeoutException e) {
                    return;
                } catch (Exception e) {
                    handler.handleUnrecoverableException(this, request, e);
                    return;
                }
            }
        } finally {
            server.close(this);
        }
    }

    @Override
    public void close() {
        HttpHandler handler = server.handler;

        handler.safeClose(this.output);
        handler.safeClose(this.requestReader);
        handler.safeClose(this.socket);
    }

    private boolean isOpen() {
        return socket instanceof Socket
                ? !((Socket) socket).isClosed()
                : ((SocketChannel) socket).isOpen();
    }

    private static final byte[] HTTP_VERSION = "HTTP/1.1 ".getBytes(StandardCharsets.US_ASCII);

    /**
     * Sends given response to the socket.
     */
    public void send(HttpRequestImpl request, HttpResponse response, OutputWrapper out, boolean keepAlive) throws IOException {
        HttpResponse.Status status = response.getStatus();
        if (status == null) {
            throw new Error("sendResponse(): Status can't be null.");
        }

        Headers headers = ((HttpResponseImpl) response).internalHeaders();
        ResponseBody body = response.getBody();

        out.write(HTTP_VERSION);
        out.writeStatus(status);
        out.writeCRLF();

        if (!headers.containsKey(HttpHeaderField.DATE)) {
            out.writeHttpHeader(HttpHeaderField.DATE, Constants.HTTP_TIME_FORMATTER.format(Instant.now()));
        }

        headers.writeHeadersTo(out);

        if (!keepAlive && !headers.containsKey(HttpHeaderField.CONNECTION)) {
            out.writeHttpHeader(HttpHeaderField.CONNECTION, "close");
        }

        String contentType = headers.getFirst(HttpHeaderField.CONTENT_TYPE);
        long inputLength = body.getContentLength(contentType);

        ReadableByteChannel input = null;
        try {
            boolean autoGZip;
            if (headers.containsKey(HttpHeaderField.CONTENT_ENCODING)) {
                autoGZip = false;
            } else {
                String acceptEncoding = request != null ? request.headers.getFirst(HttpHeaderField.ACCEPT_ENCODING) : null;
                if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
                    autoGZip = false;
                } else if (contentType == null || inputLength < 16) {
                    autoGZip = false;
                } else {
                    autoGZip = contentType.startsWith("text/") || contentType.startsWith("application/json");
                }
            }

            if (autoGZip) {
                out.writeHttpHeader(HttpHeaderField.CONTENT_ENCODING, "gzip");
            }

            long outputLength = autoGZip ? -1 : inputLength;

            if (outputLength >= 0 && !headers.containsKey(HttpHeaderField.CONTENT_LENGTH)) {
                out.writeHttpHeader(HttpHeaderField.CONTENT_LENGTH, Long.toString(outputLength));
            }

            boolean chunkedTransfer = outputLength < 0;
            HttpRequest.Method method = request != null ? request.method : null;

            if (method != HttpRequest.Method.HEAD && chunkedTransfer) {
                out.writeHttpHeader(HttpHeaderField.TRANSFER_ENCODING, "chunked");
            }
            out.writeCRLF();
            if (method != HttpRequest.Method.HEAD && outputLength != 0) {
                input = body.openChannel(contentType);

                if (autoGZip) {
                    output.transferGZipFrom(input);
                } else if (chunkedTransfer) {
                    output.transferChunkedFrom(input);
                } else {
                    output.transferFrom(input);
                }
            }
            out.flush();
        } finally {
            server.handler.safeClose(input);
        }
    }

    @Override
    public Object getUserData() {
        return userData;
    }

    public void setUserData(Object userData) {
        this.userData = userData;
    }
}
