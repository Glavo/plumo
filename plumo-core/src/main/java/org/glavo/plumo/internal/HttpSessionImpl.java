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
import org.glavo.plumo.internal.util.ChunkedOutputStream;
import org.glavo.plumo.internal.util.ParameterParser;
import org.glavo.plumo.internal.util.UnsyncBufferedOutputStream;

import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;

public final class HttpSessionImpl implements HttpSession, Runnable, Closeable {

    public final PlumoImpl server;
    public final Closeable socket;
    public final SocketAddress remoteAddress;
    public final SocketAddress localAddress;

    public final HttpRequestReader requestReader;
    public final UnsyncBufferedOutputStream outputStream;

    // Use in HttpServerImpl
    volatile HttpSessionImpl prev, next;

    public HttpSessionImpl(PlumoImpl server, Closeable acceptSocket,
                           SocketAddress remoteAddress, SocketAddress localAddress,
                           InputStream inputStream, OutputStream outputStream) {
        this.server = server;
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.requestReader = new HttpRequestReader(inputStream);
        this.outputStream = new UnsyncBufferedOutputStream(outputStream, 1024);
        this.socket = acceptSocket;
    }

    @Override
    public void run() {
        HttpHandler handler = server.handler;
        try {
            while (isOpen()) {
                try {
                    HttpRequestImpl request = new HttpRequestImpl(remoteAddress, localAddress);
                    try {
                        requestReader.readHeader(request);
                    } catch (EOFException e) {
                        return;
                    }
                    try (HttpResponseImpl r = (HttpResponseImpl) handler.handle(request)) {
                        if (r == null) {
                            return;
                        }

                        String connection = request.headers.getFirst("connection");
                        boolean keepAlive = "HTTP/1.1".equals(request.getHttpVersion()) && (connection == null || !connection.equals("close"));

                        if (!r.isAvailable()) {
                            handler.handleUnrecoverableException(this, new AssertionError("The response has been sent before"));
                            return;
                        }

                        try {
                            send(request, r, outputStream, keepAlive);
                        } catch (IOException ioe) {
                            handler.handleUnrecoverableException(this, ioe);
                            return;
                        }

                        if (!keepAlive || "close".equals(r.headers.getFirst("connection"))) {
                            return;
                        }
                    } finally {
                        request.finish();
                    }
                } catch (SocketTimeoutException e) {
                    return;
                } catch (SocketException e) {
                    // throw it out to close socket
                    throw e;
                } catch (IOException | UncheckedIOException e) {
                    handler.handleRecoverableException(this, e);
                }
            }
        } catch (Exception e) {
            handler.handleUnrecoverableException(this, e);
        } finally {
            server.close(this);
        }
    }

    @Override
    public void close() {
        HttpHandler handler = server.handler;

        handler.safeClose(this.outputStream);
        handler.safeClose(this.requestReader);
        handler.safeClose(this.socket);
    }

    private boolean isOpen() {
        return socket instanceof Socket
                ? !((Socket) socket).isClosed()
                : ((SocketChannel) socket).isOpen();
    }

    /**
     * Sends given response to the socket.
     */
    public void send(HttpRequestImpl request, HttpResponseImpl response, UnsyncBufferedOutputStream out, boolean keepAlive) throws IOException {
        if (response.status == null) {
            throw new Error("sendResponse(): Status can't be null.");
        }

        out.writeASCII("HTTP/1.1 ");
        out.writeStatus(response.status);
        out.writeCRLF();

        if (request == null || !request.headers.containsKey("date")) {
            out.writeHttpHeader("date", Constants.HTTP_TIME_FORMATTER.format(Instant.now()));
        }

        response.headers.writeHeadersTo(out);

        if (!keepAlive && !response.headers.containsKey("connection")) {
            out.writeHttpHeader("connection", "close");
        }

        String contentType = response.headers.getFirst("content-type");

        long inputLength;
        Object preprocessedData;

        Closeable needToClose = null;
        try {
            Object body = response.body;
            if (body == null) {
                preprocessedData = null;
                inputLength = 0L;
            } else if (body instanceof InputStream) {
                preprocessedData = body;
                inputLength = response.contentLength;
            } else if (body instanceof byte[]) {
                preprocessedData = body;
                inputLength = ((byte[]) body).length;
            } else if (body instanceof String) {
                byte[] ba = ((String) body).getBytes(ParameterParser.getEncoding(contentType));
                preprocessedData = ba;
                inputLength = ba.length;
            } else if (body instanceof Path) {
                SeekableByteChannel channel = Files.newByteChannel((Path) body);
                preprocessedData = needToClose = Channels.newInputStream(channel);
                inputLength = channel.size();
            } else {
                throw new InternalError("unexpected type: " + body.getClass());
            }

            boolean autoGZip;
            if (response.headers.containsKey("content-encoding")) {
                autoGZip = false;
            } else {
                String acceptEncoding = request != null ? request.headers.getFirst("accept-encoding") : null;
                if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
                    autoGZip = false;
                } else if (contentType == null || (inputLength >= 0 && inputLength <= 512)) { // TODO: magic number
                    autoGZip = false;
                } else {
                    autoGZip = contentType.startsWith("text/") || contentType.startsWith("application/json");
                }
            }

            if (autoGZip) {
                out.writeHttpHeader("content-encoding", "gzip");
            }

            long outputLength;
            boolean useGZipOutputStream;
            if (autoGZip) {
                if (inputLength >= 0 && inputLength < 8192) { // TODO: magic number
                    ByteArrayOutputStream bao = new ByteArrayOutputStream((int) inputLength);
                    try (GZIPOutputStream go = new GZIPOutputStream(bao)) {
                        if (preprocessedData instanceof InputStream) {
                            InputStream input = (InputStream) preprocessedData;

                            int count = 0;
                            byte[] buffer = new byte[128];
                            while (count < inputLength) {
                                int read = input.read(buffer, 0, Math.min((int) inputLength - count, buffer.length));
                                if (read <= 0) {
                                    throw new EOFException();
                                }
                                go.write(buffer, 0, read);
                                count += read;
                            }
                        } else {
                            byte[] ba = (byte[]) preprocessedData;
                            go.write(ba, 0, (int) inputLength);
                        }
                    }

                    byte[] compressedData = bao.toByteArray();

                    preprocessedData = compressedData;
                    inputLength = compressedData.length;
                    outputLength = inputLength;
                    useGZipOutputStream = false;
                } else {
                    outputLength = -1;
                    useGZipOutputStream = true;
                }
            } else {
                outputLength = inputLength;
                useGZipOutputStream = false;
            }

            if (outputLength >= 0 && !response.headers.containsKey("content-length")) {
                out.writeHttpHeader("content-length", Long.toString(outputLength));
            }

            boolean chunkedTransfer = outputLength < 0;
            HttpRequest.Method method = request != null ? request.method : null;

            if (method != HttpRequest.Method.HEAD && chunkedTransfer) {
                out.writeHttpHeader("transfer-encoding", "chunked");
            }
            out.writeCRLF();
            if (method != HttpRequest.Method.HEAD && outputLength != 0) {
                sendBody(out, preprocessedData, inputLength, chunkedTransfer, useGZipOutputStream);
            }
            out.flush();
        } finally {
            server.handler.safeClose(needToClose);
        }
    }

    private void sendBody(UnsyncBufferedOutputStream out,
            /* InputStream | byte[] */ Object preprocessedData,
                          long inputLength, boolean chunkedTransfer,
                          boolean useGZipOutputStream) throws IOException {
        if (preprocessedData == null) {
            throw new InternalError();
        }

        if (preprocessedData instanceof InputStream || useGZipOutputStream) {
            InputStream input;

            if (preprocessedData instanceof InputStream) {
                input = (InputStream) preprocessedData;
            } else {
                input = new ByteArrayInputStream((byte[]) preprocessedData);
            }

            OutputStream o = out;

            ChunkedOutputStream chunkedOutputStream = null;
            GZIPOutputStream gzipOutputStream = null;

            if (chunkedTransfer) {
                o = chunkedOutputStream = new ChunkedOutputStream(o);
            }
            if (useGZipOutputStream) {
                o = gzipOutputStream = new GZIPOutputStream(o);
            }

            final int BUFFER_SIZE = 8 * 1024;
            byte[] buff = new byte[BUFFER_SIZE];

            if (inputLength > 0) {
                while (inputLength > 0) {
                    long bytesToRead = Math.min(inputLength, BUFFER_SIZE);
                    int read = input.read(buff, 0, (int) bytesToRead);
                    if (read <= 0) {
                        break;
                    }
                    o.write(buff, 0, read);
                    inputLength -= read;
                }

                if (inputLength > 0) {
                    throw new EOFException();
                }

            } else {
                int read;
                while ((read = input.read(buff)) > 0) {
                    o.write(buff, 0, read);
                }
            }

            if (useGZipOutputStream) {
                gzipOutputStream.finish();
            }
            if (chunkedTransfer) {
                chunkedOutputStream.finish();
            }
        } else {
            out.write((byte[]) preprocessedData, 0, (int) inputLength);
        }
    }
}
