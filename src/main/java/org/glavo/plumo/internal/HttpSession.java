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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class HttpSession implements Closeable, Runnable {

    public final HttpListener listener;
    public final Closeable socket;
    public final SocketAddress remoteAddress;
    public final SocketAddress localAddress;

    public final HttpRequestReader requestReader;
    public final UnsyncBufferedOutputStream outputStream;

    // Use in HttpServerImpl
    volatile HttpSession prev, next;

    public HttpSession(HttpListener listener, Closeable acceptSocket,
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
            while (isOpen()) {
                try {
                    HttpRequestImpl request = new HttpRequestImpl(remoteAddress, localAddress);
                    try {
                        requestReader.readHeader(request);
                    } catch (EOFException e) {
                        throw ServerShutdown.shutdown();
                    }
                    try {
                        listener.handler.handle(this, request);
                    } finally {
                        request.close();
                    }
                } catch (SocketException | SocketTimeoutException e) {
                    // throw it out to close socket
                    throw e;
                } catch (IOException | UncheckedIOException | HttpResponseException e) {
                    HttpResponse resp;
                    if (e instanceof HttpResponseException) {
                        resp = HttpResponse.newTextResponse(((HttpResponseException) e).getStatus(), e.getMessage(), "text/plain");
                    } else if (e instanceof SSLException) {
                        resp = HttpResponse.newTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SSL PROTOCOL FAILURE: " + e.getMessage(), "text/plain");
                    } else {
                        Plumo.LOGGER.log(Plumo.Logger.Level.WARNING, "Server internal error", e);
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
            Plumo.LOGGER.log(Plumo.Logger.Level.ERROR, "Communication with the client broken, or an bug in the handler code", e);
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

    public void handleImpl(HttpRequest request, Plumo.Handler handler) throws IOException, HttpResponseException {
        try (HttpResponseImpl r = (HttpResponseImpl) handler.handle(request)) {
            if (r == null) {
                throw ServerShutdown.shutdown();
            }

            HttpRequestImpl requestImpl = (HttpRequestImpl) request;

            String connection = requestImpl.headers.getHeader("connection");
            boolean keepAlive = "HTTP/1.1".equals(requestImpl.getHttpVersion()) && (connection == null || !connection.equals("close"));

            if (!r.isAvailable()) {
                Plumo.LOGGER.log(Plumo.Logger.Level.ERROR, "The response has been sent before");
                throw new HttpResponseException(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR");
            }

            try {
                send((HttpRequestImpl) request, r, outputStream, keepAlive);
            } catch (IOException ioe) {
                Plumo.LOGGER.log(Plumo.Logger.Level.WARNING, "Could not send response to the client", ioe);
                throw ServerShutdown.shutdown();
            }

            if (!keepAlive || "close".equals(r.headers.getHeader("connection"))) {
                throw ServerShutdown.shutdown();
            }
        }
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
                Plumo.LOGGER.log(Plumo.Logger.Level.WARNING, "Unsupported content encoding: " + contentEncoding);
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
