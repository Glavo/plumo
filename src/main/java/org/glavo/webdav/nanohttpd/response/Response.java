package org.glavo.webdav.nanohttpd.response;

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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

import org.glavo.webdav.nanohttpd.internal.ChunkedOutputStream;
import org.glavo.webdav.nanohttpd.internal.HttpUtils;
import org.glavo.webdav.nanohttpd.internal.SimpleStringMap;
import org.glavo.webdav.nanohttpd.internal.UnsyncBufferedOutputStream;
import org.glavo.webdav.nanohttpd.request.Method;
import org.glavo.webdav.nanohttpd.NanoHTTPD;
import org.glavo.webdav.nanohttpd.content.ContentType;

/**
 * HTTP response. Return one of these from serve().
 */
public class Response implements Closeable {

    /**
     * HTTP status code after processing, e.g. "200 OK", Status.OK
     */
    private Status status;

    /**
     * MIME type of content, e.g. "text/html"
     */
    private String mimeType;

    /**
     * Data of the response, may be null.
     */
    private InputStream data;

    private final long contentLength;

    /**
     * Headers for the HTTP response. Use addHeader() to add lines. the
     * lowercase map is automatically kept up to date.
     */
    private final SimpleStringMap<String> header = new SimpleStringMap<>();

    private final SimpleStringMap<List<String>> multiHeader = new SimpleStringMap<>();

    /**
     * The request method that spawned this response.
     */
    private Method requestMethod;

    /**
     * Use chunkedTransfer
     */
    private boolean chunkedTransfer;

    private boolean keepAlive;

    private Boolean gzipUsage;

    /**
     * Creates a fixed length response if totalBytes>=0, otherwise chunked.
     */
    protected Response(Status status, String mimeType, InputStream data, long totalBytes) {
        this.status = status;
        this.mimeType = mimeType;
        if (data == null) {
            this.data = new ByteArrayInputStream(new byte[0]);
            this.contentLength = 0L;
        } else {
            this.data = data;
            this.contentLength = totalBytes;
        }
        this.chunkedTransfer = this.contentLength < 0;
        this.keepAlive = true;
    }

    @Override
    public void close() throws IOException {
        if (this.data != null) {
            this.data.close();
        }
    }

    /**
     * Adds given line to the header.
     */
    public void addHeader(String name, String value) {
        this.header.put(name.toLowerCase(Locale.ROOT), value);
    }

    public List<String> getMultiHeaders(String name) {
        return multiHeader.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>(4));
    }

    /**
     * Indicate to close the connection after the Response has been sent.
     *
     * @param close {@code true} to hint connection closing, {@code false} to let
     *              connection be closed by client.
     */
    public void closeConnection(boolean close) {
        if (close)
            this.header.put("connection", "close");
        else
            this.header.remove("connection");
    }

    /**
     * @return {@code true} if connection is to be closed after this Response
     * has been sent.
     */
    public boolean isCloseConnection() {
        return "close".equals(header.get("connection"));
    }

    public InputStream getData() {
        return this.data;
    }

    public String getHeader(String name) {
        return this.header.get(name.toLowerCase(Locale.ROOT));
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public Method getRequestMethod() {
        return this.requestMethod;
    }

    public Status getStatus() {
        return this.status;
    }

    public void setKeepAlive(boolean useKeepAlive) {
        this.keepAlive = useKeepAlive;
    }

    /**
     * Sends given response to the socket.
     */
    public void send(OutputStream outputStream) {
        if (this.status == null) {
            throw new Error("sendResponse(): Status can't be null.");
        }

        try {
            UnsyncBufferedOutputStream out = new UnsyncBufferedOutputStream(outputStream, 1024);

            out.writeASCII("HTTP/1.1 ");
            out.writeStatus(status);
            out.writeCRLF();

            if (this.mimeType != null) {
                printHeader(out, "content-type", this.mimeType);
            }
            if (header.get("date") == null) {
                printHeader(out, "date", HttpUtils.getHttpTime(Instant.now()));
            }

            this.header.forEachChecked((k, v) -> printHeader(out, k, v));
            this.multiHeader.forEachChecked((k, l) -> {
                for (String v : l) {
                    printHeader(out, k, v);
                }
            });

            if (header.get("connection") == null) {
                printHeader(out, "connection", (this.keepAlive ? "keep-alive" : "close"));
            }
            if (header.get("content-length") != null) {
                setUseGzip(false);
            }
            if (useGzipWhenAccepted()) {
                printHeader(out, "content-encoding", "gzip");
                setChunkedTransfer(true);
            }
            long pending = this.data != null ? this.contentLength : 0;
            if (this.requestMethod != Method.HEAD && this.chunkedTransfer) {
                printHeader(out, "transfer-encoding", "chunked");
            } else if (!useGzipWhenAccepted()) {
                pending = sendContentLengthHeaderIfNotAlreadyPresent(out, pending);
            }
            out.writeCRLF();
            sendBodyWithCorrectTransferAndEncoding(out, pending);
            out.flush();
            NanoHTTPD.safeClose(this.data);
        } catch (IOException ioe) {
            NanoHTTPD.LOG.log(Level.SEVERE, "Could not send response to the client", ioe);
        }
    }

    private static final byte[] SEPARATOR = {':', ' '};

    protected void printHeader(UnsyncBufferedOutputStream out, String key, String value) throws IOException {
        out.writeASCII(key);
        out.write(SEPARATOR);
        out.writeASCII(value);
        out.writeCRLF();
    }

    protected long sendContentLengthHeaderIfNotAlreadyPresent(UnsyncBufferedOutputStream out, long defaultSize) throws IOException {
        String contentLengthString = header.get("content-length");
        long size = defaultSize;
        if (contentLengthString != null) {
            try {
                size = Long.parseLong(contentLengthString);
            } catch (NumberFormatException ex) {
                NanoHTTPD.LOG.severe("content-length was no number " + contentLengthString);
            }
        } else {
            out.writeASCII("content-length: ");
            out.writeASCII(Long.toString(size));
            out.writeCRLF();
        }
        return size;
    }

    private void sendBodyWithCorrectTransferAndEncoding(OutputStream outputStream, long pending) throws IOException {
        if (this.requestMethod != Method.HEAD && this.chunkedTransfer) {
            ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(outputStream);
            sendBodyWithCorrectEncoding(chunkedOutputStream, -1);
            try {
                chunkedOutputStream.finish();
            } catch (Exception e) {
                if (this.data != null) {
                    this.data.close();
                }
            }
        } else {
            sendBodyWithCorrectEncoding(outputStream, pending);
        }
    }

    private void sendBodyWithCorrectEncoding(OutputStream outputStream, long pending) throws IOException {
        if (useGzipWhenAccepted()) {
            GZIPOutputStream gzipOutputStream = null;
            try {
                gzipOutputStream = new GZIPOutputStream(outputStream);
            } catch (Exception e) {
                if (this.data != null) {
                    this.data.close();
                }
            }
            if (gzipOutputStream != null) {
                sendBody(gzipOutputStream, -1);
                gzipOutputStream.finish();
            }
        } else {
            sendBody(outputStream, pending);
        }
    }

    /**
     * Sends the body to the specified OutputStream. The pending parameter
     * limits the maximum amounts of bytes sent unless it is -1, in which case
     * everything is sent.
     *
     * @param outputStream the OutputStream to send data to
     * @param pending      -1 to send everything, otherwise sets a max limit to the
     *                     number of bytes sent
     * @throws IOException if something goes wrong while sending the data.
     */
    private void sendBody(OutputStream outputStream, long pending) throws IOException {
        long BUFFER_SIZE = 16 * 1024;
        byte[] buff = new byte[(int) BUFFER_SIZE];
        boolean sendEverything = pending == -1;
        while (pending > 0 || sendEverything) {
            long bytesToRead = sendEverything ? BUFFER_SIZE : Math.min(pending, BUFFER_SIZE);
            int read = this.data.read(buff, 0, (int) bytesToRead);
            if (read <= 0) {
                break;
            }
            try {
                outputStream.write(buff, 0, read);
            } catch (Exception e) {
                if (this.data != null) {
                    this.data.close();
                }
            }
            if (!sendEverything) {
                pending -= read;
            }
        }
    }

    public void setChunkedTransfer(boolean chunkedTransfer) {
        this.chunkedTransfer = chunkedTransfer;
    }

    public void setData(InputStream data) {
        this.data = data;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setRequestMethod(Method requestMethod) {
        this.requestMethod = requestMethod;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    /**
     * Create a response with unknown length (using HTTP 1.1 chunking).
     */
    public static Response newChunkedResponse(Status status, String mimeType, InputStream data) {
        return new Response(status, mimeType, data, -1);
    }

    public static Response newFixedLengthResponse(Status status, String mimeType, byte[] data) {
        return newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(data), data.length);
    }

    /**
     * Create a response with known length.
     */
    public static Response newFixedLengthResponse(Status status, String mimeType, InputStream data, long totalBytes) {
        return new Response(status, mimeType, data, totalBytes);
    }

    /**
     * Create a text response with known length.
     */
    public static Response newFixedLengthResponse(Status status, String mimeType, String txt) {
        ContentType contentType = new ContentType(mimeType);
        Charset encoding = contentType.getEncoding();
        if (encoding == StandardCharsets.US_ASCII) {
            encoding = StandardCharsets.UTF_8;
        }

        byte[] bytes = txt != null ? txt.getBytes(encoding) : new byte[0];
        return newFixedLengthResponse(status, contentType.getContentTypeHeader(), new ByteArrayInputStream(bytes), bytes.length);
    }

    /**
     * Create a text response with known length.
     */
    public static Response newFixedLengthResponse(String msg) {
        return newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_HTML, msg);
    }

    public void setUseGzip(boolean useGzip) {
        gzipUsage = useGzip;
    }

    // If a Gzip usage has been enforced, use it.
    // Else decide whether to use Gzip.
    public boolean useGzipWhenAccepted() {
        if (gzipUsage == null) {
            if (getMimeType() == null) {
                return false;
            }

            String lowerMimeType = getMimeType().toLowerCase(Locale.ROOT);
            return lowerMimeType.contains("text/") || lowerMimeType.contains("/json");
        }

        return gzipUsage;
    }
}
