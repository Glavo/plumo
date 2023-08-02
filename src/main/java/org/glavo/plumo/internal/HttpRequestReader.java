package org.glavo.plumo.internal;

import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.internal.util.IOUtils;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public class HttpRequestReader implements Closeable {

    private static final Charset HEADER_ENCODING = StandardCharsets.UTF_8;

    private final byte[] headBuf = new byte[HttpSession.HEADER_BUFFER_SIZE];
    private int headOff;
    private int headRemaining;

    private final InputStream in;

    public HttpRequestReader(InputStream in) {
        this.in = in;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (headRemaining > 0) {
            int r = Math.min(headRemaining, len);
            System.arraycopy(headBuf, headOff, b, off, r);

            headRemaining -= r;

            if (headRemaining == 0) {
                headOff = 0;
            }

            return r;
        }

        return in.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public HttpRequestImpl read() throws IOException, HttpResponseException {
        byte[] buf = this.headBuf;

        HttpRequestImpl request = new HttpRequestImpl();

        int read = headRemaining;
        if (headOff > 0) {
            System.arraycopy(buf, headOff, buf, 0, read);
            headOff = 0;
        } else {
            read = in.read(buf, 0, HttpSession.HEADER_BUFFER_SIZE);

            if (read == -1) {
                throw new EOFException();
            }
        }

        int count = 0;

        int lineStart = 0;
        while (read > 0) {
            count += read;

            while (true) {
                int lineEnd = findLineEnd(buf, lineStart, count);
                int tokenStart = findTokenStart(buf, lineStart, lineEnd);

                if (lineEnd < 0) {
                    // need more input
                    break;
                }

                if (tokenStart < 0) {
                    // end of http header

                    int off = findLineSeparatorEnd(buf, lineEnd);
                    if (off < count) {
                        headOff = off;
                        headRemaining = count - off;
                    } else {
                        headOff = 0;
                        headRemaining = 0;
                    }

                    return request;
                }

                assert lineEnd > 0;

                if (lineStart == 0) {
                    processStartLine(request, buf, tokenStart, lineEnd);
                } else {
                    processHeaderLine(request, buf, tokenStart, lineEnd);
                }

                lineStart = findLineSeparatorEnd(buf, lineEnd);
            }

            read = in.read(buf, count, HttpSession.HEADER_BUFFER_SIZE - count);
        }

        throw new HttpResponseException(HttpResponse.Status.REQUEST_HEADER_FIELDS_TOO_LARGE);
    }

    private InputStream newBondul() {
        return new InputStream() {
            private boolean closed = false;

            private long totalRead = 0;
            private long limit;

            private void checkStatus() throws IOException {
                if (closed) {
                    throw new IOException("Stream closed");
                }
            }

            @Override
            public void close() throws IOException {

            }

            @Override
            public int read() throws IOException {

                return 0;
            }
        };
    }

    private static int findLineEnd(byte[] buf, int lineStart, int bufEnd) {
        int off = lineStart;
        while (off < bufEnd) {
            byte b1 = buf[off];

            if (b1 == '\r') {
                if (off < bufEnd - 1) {
                    return off;
                } else {
                    // Although we already know the line length,
                    // we still want to read the next character before processing
                    return -1;
                }
            }

            if (b1 == '\n') {
                return off;
            }

            off++;
        }

        return -1;
    }

    private static int findLineSeparatorEnd(byte[] buf, int off) {
        if (buf[off] == '\n') {
            return off + 1;
        } else { // buf[off] == '\r'
            // secure access without checking boundaries
            if (buf[off + 1] == '\n') {
                return off + 2;
            } else {
                return off + 1;
            }
        }
    }

    private static int findTokenEnd(byte[] buf, int off, int end) {
        while (off < end) {
            byte ch = buf[off];
            if (ch == ' ' || ch == '\t') {
                return off;
            }

            off++;
        }

        return end;
    }

    private static int findTokenStart(byte[] buf, int off, int end) {
        while (off < end) {
            byte ch = buf[off];
            if (ch != ' ' && ch != '\t') {
                return off;
            }

            off++;
        }

        return -1;
    }

    private static final HttpRequest.Method[] METHODS = HttpRequest.Method.values();
    private static final byte[][] METHOD_NAMES = new byte[METHODS.length][];

    static {
        for (int i = 0; i < METHODS.length; i++) {
            METHOD_NAMES[i] = METHODS[i].name().getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    private static void processStartLine(HttpRequestImpl request, byte[] buf, int off, int lineEnd) throws HttpResponseException {
        int end = findTokenEnd(buf, off, lineEnd);
        if (end == lineEnd) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST);
        }

        HttpRequest.Method method = null;
        for (int i = 0; i < METHOD_NAMES.length; i++) {
            byte[] methodName = METHOD_NAMES[i];

            if (Arrays.equals(methodName, 0, methodName.length, buf, off, end)) {
                method = METHODS[i];
                break;
            }
        }

        if (method == null) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST,
                    "BAD REQUEST: Syntax error. HTTP verb " + new String(buf, off, end - off, HEADER_ENCODING) + " unhandled.");
        }

        off = findTokenStart(buf, end, lineEnd);
        if (off < 0) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
        }
        end = findTokenEnd(buf, off, lineEnd);

        String rawUri = new String(buf, off, end - off, HEADER_ENCODING);

        off = findTokenStart(buf, end, lineEnd);
        if (end == lineEnd || off < 0) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Missing HTTP version.");
        }

        String httpVersion = new String(buf, off, lineEnd - off, HEADER_ENCODING).trim();
        if (!httpVersion.startsWith("HTTP/")) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Illegal HTTP version " + httpVersion + ".");
        }

        request.method = method;
        request.rawUri = rawUri;
        request.httpVersion = httpVersion;
    }

    private static void processHeaderLine(HttpRequestImpl request, byte[] buf, int off, int end) throws HttpResponseException {
        int nameEnd = off;
        while (nameEnd < end) {
            byte ch = buf[nameEnd];
            if (IOUtils.isTokenPart(ch) && ch != ':') {
                nameEnd++;
            } else {
                break;
            }
        }

        if (nameEnd == off) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
        }

        String name = new String(buf, off, nameEnd - off, HEADER_ENCODING);

        off = findTokenStart(buf, nameEnd, end);
        if (off < 0) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
        }

        byte b = buf[off];
        if (b != ':') {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
        }

        off = findTokenStart(buf, off + 1, end);

        String value = off < 0 ? "" : new String(buf, off, end - off, HEADER_ENCODING).trim();

        request.headers.add(name.toLowerCase(Locale.ROOT), value);
    }
}
