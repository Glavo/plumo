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
import org.glavo.plumo.internal.util.IOUtils;
import org.glavo.plumo.internal.util.ParameterParser;
import org.glavo.plumo.internal.util.UnsyncBufferedOutputStream;

import javax.net.ssl.SSLException;
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

    public final PlumoImpl listener;
    public final Closeable socket;
    public final SocketAddress remoteAddress;
    public final SocketAddress localAddress;

    public final HttpRequestReader requestReader;
    public final UnsyncBufferedOutputStream outputStream;

    // Use in HttpServerImpl
    volatile HttpSessionImpl prev, next;

    public HttpSessionImpl(PlumoImpl listener, Closeable acceptSocket,
                           SocketAddress remoteAddress, SocketAddress localAddress,
                           InputStream inputStream, OutputStream outputStream) {
        this.listener = listener;
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.requestReader = new HttpRequestReader(inputStream);
        this.outputStream = new UnsyncBufferedOutputStream(outputStream, 1024);
        this.socket = acceptSocket;
    }

    @Override
    public void run() {
        try {
            HttpHandler handler = listener.handler;

            while (isOpen()) {
                try {
                    HttpRequestImpl request = new HttpRequestImpl(remoteAddress, localAddress);
                    try {
                        requestReader.readHeader(request);
                    } catch (EOFException e) {
                        throw ServerShutdown.shutdown();
                    }
                    try (HttpResponseImpl r = (HttpResponseImpl) handler.handle(request)) {
                        if (r == null) {
                            throw ServerShutdown.shutdown();
                        }

                        String connection = request.headers.getHeader("connection");
                        boolean keepAlive = "HTTP/1.1".equals(request.getHttpVersion()) && (connection == null || !connection.equals("close"));

                        if (!r.isAvailable()) {
                            DefaultLogger.log(DefaultLogger.Level.ERROR, "The response has been sent before");
                            throw new HttpResponseException(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR");
                        }

                        try {
                            send(request, r, outputStream, keepAlive);
                        } catch (IOException ioe) {
                            DefaultLogger.log(DefaultLogger.Level.WARNING, "Could not send response to the client", ioe);
                            throw ServerShutdown.shutdown();
                        }

                        if (!keepAlive || "close".equals(r.headers.getHeader("connection"))) {
                            throw ServerShutdown.shutdown();
                        }
                    } finally {
                        request.close();
                    }
                } catch (SocketException | SocketTimeoutException e) {
                    // throw it out to close socket
                    throw e;
                } catch (IOException | UncheckedIOException e) {
                    HttpResponse resp;
                    if (e instanceof HttpResponseException) {
                        resp = ((HttpResponseException) e).getResponse();
                    } else if (e instanceof SSLException) {
                        resp = HttpResponse.newTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SSL PROTOCOL FAILURE: " + e.getMessage(), "text/plain");
                    } else {
                        DefaultLogger.log(DefaultLogger.Level.WARNING, "Server internal error", e);
                        resp = HttpResponse.newTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR", "text/plain");
                    }

                    send(null, (HttpResponseImpl) resp, this.outputStream, false);
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
            DefaultLogger.log(DefaultLogger.Level.ERROR, "Communication with the client broken, or an bug in the handler code", e);
        } finally {
            listener.close(this);
        }
    }

    @Override
    public void close() {
        IOUtils.safeClose(this.outputStream);
        IOUtils.safeClose(this.requestReader);
        IOUtils.safeClose(this.socket);
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

        if (request == null || !request.headers.containsHeader("date")) {
            out.writeHttpHeader("date", Constants.HTTP_TIME_FORMATTER.format(Instant.now()));
        }

        response.headers.writeTo(out);

        if (!keepAlive && !response.headers.containsHeader("connection")) {
            out.writeHttpHeader("connection", "close");
        }

        String contentType = null;
        String contentEncoding = null;

        if (request != null) {
            contentType = request.headers.getHeader("content-type");
            contentEncoding = request.headers.getHeader("content-encoding");
        }

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
                throw new InternalError("unexpected types: " + body.getClass());
            }

            boolean useGzip;
            String acceptEncoding = request != null ? request.headers.getHeader("accept-encoding") : null;
            if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
                useGzip = false;
            } else if (contentEncoding == null) {
                if (contentType == null || (inputLength >= 0 && inputLength <= 512)) { // TODO: magic number
                    useGzip = false;
                } else {
                    useGzip = contentType.startsWith("text/") || contentType.startsWith("application/json");
                }
            } else if (contentEncoding.isEmpty() || "identity".equals(contentEncoding)) {
                useGzip = false;
            } else if ("gzip".equals(contentEncoding)) {
                useGzip = true;
            } else {
                DefaultLogger.log(DefaultLogger.Level.WARNING, "Unsupported content encoding: " + contentEncoding);
                useGzip = false;
            }

            if (useGzip) {
                out.writeHttpHeader("content-encoding", "gzip");
            }

            long outputLength;
            boolean useGzipOutputStream;
            if (useGzip) {
                if (inputLength >= 0 && inputLength < 8192) { // TODO: magic number
                    ByteArrayOutputStream bao = new ByteArrayOutputStream((int) inputLength);
                    try (GZIPOutputStream go = new GZIPOutputStream(bao)) {
                        if (preprocessedData == null || inputLength == 0) {
                            // do nothing
                        } else if (preprocessedData instanceof InputStream) {
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
                    useGzipOutputStream = false;
                } else {
                    outputLength = -1;
                    useGzipOutputStream = true;
                }
            } else {
                outputLength = inputLength;
                useGzipOutputStream = false;
            }

            if (outputLength >= 0) {
                out.writeHttpHeader("content-length", Long.toString(outputLength));
            }

            boolean chunkedTransfer = outputLength < 0;
            HttpRequest.Method method = request != null ? request.method : null;

            if (method != HttpRequest.Method.HEAD && chunkedTransfer) {
                out.writeHttpHeader("transfer-encoding", "chunked");
            }
            out.writeCRLF();
            if (method != HttpRequest.Method.HEAD && outputLength != 0) {
                sendBody(out, preprocessedData, inputLength, chunkedTransfer, useGzipOutputStream);
            }
            out.flush();
        } finally {
            IOUtils.safeClose(needToClose);
        }
    }

    private void sendBody(UnsyncBufferedOutputStream out,
            /* InputStream | byte[] */ Object preprocessedData,
                          long inputLength, boolean chunkedTransfer,
                          boolean useGzipOutputStream) throws IOException {
        if (preprocessedData == null) {
            throw new InternalError();
        }

        if (preprocessedData instanceof InputStream || useGzipOutputStream) {
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
            if (useGzipOutputStream) {
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

            if (useGzipOutputStream) {
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
