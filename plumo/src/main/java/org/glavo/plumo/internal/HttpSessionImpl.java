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
import org.glavo.plumo.internal.util.ParameterParser;
import org.glavo.plumo.internal.util.Utils;

import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

                    HttpResponseImpl r = null;
                    try {
                        try {
                            r = (HttpResponseImpl) handler.handle(request);
                            if (r == null) {
                                return;
                            }
                        } catch (Throwable e) {
                            r = (HttpResponseImpl) handler.handleRecoverableException(this, request, e);
                        }

                        if (!r.isAvailable()) {
                            r.close(handler);
                            throw new IOException("The response has been sent before");
                        }

                        String connection = request.headers.getFirst(HttpHeaderField.CONNECTION);
                        boolean keepAlive = "HTTP/1.1".equals(request.getHttpVersion()) && (connection == null || !connection.equals("close"));

                        send(request, r, output, keepAlive);

                        if (!keepAlive || "close".equals(r.headers.getFirst(HttpHeaderField.CONNECTION))) {
                            return;
                        }
                    } finally {
                        if (r != null) {
                            r.close(handler);
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
    public void send(HttpRequestImpl request, HttpResponseImpl response, OutputWrapper out, boolean keepAlive) throws IOException {
        if (response.status == null) {
            throw new Error("sendResponse(): Status can't be null.");
        }

        out.write(HTTP_VERSION);
        out.writeStatus(response.status);
        out.writeCRLF();

        if (request == null || !request.headers.containsKey(HttpHeaderField.DATE)) {
            out.writeHttpHeader(HttpHeaderField.DATE, Constants.HTTP_TIME_FORMATTER.format(Instant.now()));
        }

        response.headers.writeHeadersTo(out);

        if (!keepAlive && !response.headers.containsKey(HttpHeaderField.CONNECTION)) {
            out.writeHttpHeader(HttpHeaderField.CONNECTION, "close");
        }

        String contentType = response.headers.getFirst(HttpHeaderField.CONTENT_TYPE);

        long inputLength;
        Object preprocessedData; // ReadableByteChannel | byte[] | ByteBuffer

        Closeable needToClose = null;
        try {
            Object body = response.body;
            if (body == null) {
                preprocessedData = null;
                inputLength = 0L;
            } else if (body instanceof ReadableByteChannel) {
                preprocessedData = body;
                inputLength = response.contentLength;
            } else if (body instanceof InputStream) {
                preprocessedData = Channels.newChannel((InputStream) body);
                inputLength = response.contentLength;
            } else if (body instanceof ByteBuffer) {
                ByteBuffer byteBuffer = (ByteBuffer) body;
                preprocessedData = byteBuffer.duplicate();
                inputLength = byteBuffer.remaining();
            } else if (body instanceof String) {
                byte[] ba = ((String) body).getBytes(ParameterParser.getEncoding(contentType));
                preprocessedData = ByteBuffer.wrap(ba);
                inputLength = ba.length;
            } else if (body instanceof Path) {
                SeekableByteChannel channel = Files.newByteChannel((Path) body);
                preprocessedData = needToClose = channel;

                try {
                    inputLength = channel.size();
                } catch (Throwable e) {
                    server.handler.safeClose(channel);
                    throw e;
                }
            } else {
                throw new InternalError("unexpected type: " + body.getClass());
            }

            boolean autoGZip;
            if (response.headers.containsKey(HttpHeaderField.CONTENT_ENCODING)) {
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

            if (outputLength >= 0 && !response.headers.containsKey(HttpHeaderField.CONTENT_LENGTH)) {
                out.writeHttpHeader(HttpHeaderField.CONTENT_LENGTH, Long.toString(outputLength));
            }

            boolean chunkedTransfer = outputLength < 0;
            HttpRequest.Method method = request != null ? request.method : null;

            if (method != HttpRequest.Method.HEAD && chunkedTransfer) {
                out.writeHttpHeader(HttpHeaderField.TRANSFER_ENCODING, "chunked");
            }
            out.writeCRLF();
            if (method != HttpRequest.Method.HEAD && outputLength != 0) {
                if (preprocessedData instanceof ReadableByteChannel) {
                    ReadableByteChannel input = (ReadableByteChannel) preprocessedData;

                    if (autoGZip) {
                        output.transferGZipFrom(input);
                    } else if (chunkedTransfer) {
                        output.transferChunkedFrom(input);
                    } else {
                        output.transferFrom(input);
                    }
                } else if (autoGZip) {
                    ByteBuffer data = (ByteBuffer) preprocessedData;
                    output.transferGZipFrom(data);
                } else {
                    Utils.writeFully(out, (ByteBuffer) preprocessedData);
                }
            }
            out.flush();
        } finally {
            server.handler.safeClose(needToClose);
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
