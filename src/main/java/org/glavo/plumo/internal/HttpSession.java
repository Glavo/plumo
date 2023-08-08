package org.glavo.plumo.internal;

import org.glavo.plumo.*;
import org.glavo.plumo.internal.util.ChunkedOutputStream;
import org.glavo.plumo.internal.util.IOUtils;
import org.glavo.plumo.internal.util.UnsyncBufferedOutputStream;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class HttpSession implements Closeable, Runnable {

    public static final String POST_DATA = "postData";

    private static final int REQUEST_BUFFER_LEN = 512;

    private static final int MEMORY_STORE_LIMIT = 1024;

    public static final int MAX_HEADER_SIZE = 1024;

    private final HttpListener server;
    private final Closeable socket;
    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;

    private final HttpRequestReader requestReader;
    private final UnsyncBufferedOutputStream outputStream;

    private HttpRequestImpl lastRequest;

    // Use in HttpServerImpl
    volatile HttpSession prev, next;

    public HttpSession(HttpListener server, Closeable acceptSocket,
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
        try {
            while (isOpen()) {
                execute();
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
            server.close(this);
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

    private void execute() throws IOException {
        HttpResponseImpl r = null;
        try {
            if (lastRequest != null) {
                lastRequest.close();
                lastRequest = null;
            }

            HttpRequestImpl request = new HttpRequestImpl(remoteAddress, localAddress);
            try {
                requestReader.readHeader(request);
            } catch (EOFException e) {
                throw ServerShutdown.shutdown();
            }
            lastRequest = request;

            String connection = request.headers.getFirst("connection");
            boolean keepAlive = "HTTP/1.1".equals(request.httpVersion) && (connection == null || !connection.equals("close"));

            // Ok, now do the serve()

            r = (HttpResponseImpl) server.handler.handle(request);

            if (r == null) {
                throw ServerShutdown.shutdown();
            }

            if (!r.isAvailable()) {
                Plumo.LOGGER.log(Plumo.Logger.Level.ERROR, "The response has been sent before");
                throw new HttpResponseException(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR");
            }

            try {
                send(request, r, this.outputStream, keepAlive);
            } catch (IOException ioe) {
                Plumo.LOGGER.log(Plumo.Logger.Level.WARNING, "Could not send response to the client", ioe);
                throw ServerShutdown.shutdown();
            }

            if (!keepAlive || r.needCloseConnection()) {
                throw ServerShutdown.shutdown();
            }
        } catch (SocketException | SocketTimeoutException e) {
            // throw it out to close socket object (finalAccept)
            throw e;
        } catch (IOException | UncheckedIOException | HttpResponseException e) {
            HttpResponse resp;
            if (e instanceof HttpResponseException) {
                resp = HttpResponse.newPlainTextResponse(((HttpResponseException) e).getStatus(), e.getMessage());
            } else if (e instanceof SSLException) {
                resp = HttpResponse.newPlainTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SSL PROTOCOL FAILURE: " + e.getMessage());
            } else {
                Plumo.LOGGER.log(Plumo.Logger.Level.WARNING, "Server internal error", e);
                resp = HttpResponse.newPlainTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR");
            }

            send(null, (HttpResponseImpl) resp, this.outputStream, false);
        } finally {
            if (r != null) {
                r.close();
            }
        }
    }

    /**
     * Find the byte positions where multipart boundaries start. This reads a
     * large block at a time and uses a temporary buffer to optimize (memory
     * mapped) file access.
     */
    private int[] getBoundaryPositions(ByteBuffer b, byte[] boundary) {
        int[] res = new int[0];
        if (b.remaining() < boundary.length) {
            return res;
        }

        int search_window_pos = 0;
        byte[] search_window = new byte[4 * 1024 + boundary.length];

        int first_fill = Math.min(b.remaining(), search_window.length);
        b.get(search_window, 0, first_fill);
        int new_bytes = first_fill - boundary.length;

        do {
            // Search the search_window
            for (int j = 0; j < new_bytes; j++) {
                for (int i = 0; i < boundary.length; i++) {
                    if (search_window[j + i] != boundary[i])
                        break;
                    if (i == boundary.length - 1) {
                        // Match found, add it to results
                        int[] new_res = new int[res.length + 1];
                        System.arraycopy(res, 0, new_res, 0, res.length);
                        new_res[res.length] = search_window_pos + j;
                        res = new_res;
                    }
                }
            }
            search_window_pos += new_bytes;

            // Copy the end of the buffer to the start
            System.arraycopy(search_window, search_window.length - boundary.length, search_window, 0, boundary.length);

            // Refill search_window
            new_bytes = search_window.length - boundary.length;
            new_bytes = Math.min(b.remaining(), new_bytes);
            b.get(search_window, boundary.length, new_bytes);
        } while (new_bytes > 0);
        return res;
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

        ContentType contentType = response.contentType;
        if (contentType != null) {
            out.writeHttpHeader("content-type", contentType.toString());
        }

        Instant date = response.date == null ? Instant.now() : response.date;
        out.writeHttpHeader("date", Constants.HTTP_TIME_FORMATTER.format(date));

        for (Map.Entry<String, Object> entry : response.headers.map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                out.writeHttpHeader(key, (String) value);
            } else {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) value;
                for (String v : list) {
                    out.writeHttpHeader(key, v);
                }
            }

        }

        if (response.cookies != null) {
            for (Cookie cookie : response.cookies) {
                out.writeHttpHeader("set-cookie", cookie.toString());
            }
        }

        if (!keepAlive) {
            out.writeHttpHeader("connection", "close");
        } else if (response.connection != null) {
            out.writeHttpHeader("connection", response.connection);
        }

        long inputLength;
        Object preprocessedData;

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
            byte[] ba = ((String) body).getBytes(contentType == null ? StandardCharsets.UTF_8 : contentType.getCharset());
            preprocessedData = ba;
            inputLength = ba.length;
        } else {
            throw new InternalError("unexpected types: " + body.getClass());
        }

        boolean useGzip;
        String acceptEncoding = request != null ? request.headers.getFirst("accept-encoding") : null;
        if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
            useGzip = false;
        } else if (response.contentEncoding == null) {
            if (response.contentType == null || (inputLength >= 0 && inputLength <= 512)) { // TODO: magic number
                useGzip = false;
            } else {
                String type = response.contentType.getMimeType();
                useGzip = type.startsWith("text/") || type.endsWith("json");
            }
        } else if (response.contentEncoding.isEmpty() || "identity".equals(response.contentEncoding)) {
            useGzip = false;
        } else if ("gzip".equals(response.contentEncoding)) {
            useGzip = true;
        } else {
            Plumo.LOGGER.log(Plumo.Logger.Level.WARNING, "Unsupported content encoding: " + response.contentEncoding);
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
