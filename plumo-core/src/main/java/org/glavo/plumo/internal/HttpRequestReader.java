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

import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.internal.util.Utils;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public class HttpRequestReader implements Closeable {

    private static final Charset HEADER_ENCODING;

    static {
        if (Constants.HEADER_ENCODING != null) {
            HEADER_ENCODING = Charset.forName(Constants.HEADER_ENCODING);

            if (HEADER_ENCODING != StandardCharsets.US_ASCII
                && HEADER_ENCODING != StandardCharsets.UTF_8
                && HEADER_ENCODING != StandardCharsets.ISO_8859_1) {
                throw new IllegalArgumentException("Illegal http header encoding");
            }
        } else {
            HEADER_ENCODING = StandardCharsets.UTF_8;
        }
    }

    private final byte[] lineBuffer = new byte[Constants.LINE_BUFFER_LENGTH];
    private int bufferOff;
    private int bufferRemaining;

    private final InputStream in;

    public HttpRequestReader(InputStream in) {
        this.in = in;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public void readHeader(HttpRequestImpl request) throws IOException {
        byte[] buf = this.lineBuffer;

        int read = bufferRemaining;
        if (read == 0) {
            read = in.read(buf, 0, Constants.LINE_BUFFER_LENGTH);
            if (read == -1) {
                throw new EOFException();
            }

            assert bufferOff == 0;
            bufferRemaining = read;
        }

        boolean firstLine = true;
        while (read > 0) {
            while (true) {
                int lineEnd = findLineEnd(buf, bufferOff, bufferOff + bufferRemaining);

                if (lineEnd < 0) {
                    if (bufferOff + bufferRemaining == Constants.LINE_BUFFER_LENGTH) {
                        if (bufferOff > 0) {
                            System.arraycopy(buf, bufferOff, buf, 0, bufferRemaining);
                            bufferOff = 0;
                        } else {
                            // line buffer is full
                            throw new HttpResponseException(HttpResponse.Status.REQUEST_HEADER_FIELDS_TOO_LARGE);
                        }
                    }

                    // need more input
                    break;
                }

                int lineSeparatorEnd = findLineSeparatorEnd(buf, lineEnd);
                int tokenStart = findTokenStart(buf, bufferOff, lineEnd);

                int lineWithSeparatorLength = lineSeparatorEnd - bufferOff;
                if (lineWithSeparatorLength < bufferRemaining) {
                    bufferOff = lineSeparatorEnd;
                    bufferRemaining -= lineWithSeparatorLength;
                } else {
                    bufferOff = 0;
                    bufferRemaining = 0;
                }

                // end of http header
                if (tokenStart < 0) {
                    endOfHeader(request);
                    return;
                } else {
                    assert lineEnd > 0;

                    if (firstLine) {
                        firstLine = false;
                        processStartLine(request, buf, tokenStart, lineEnd);
                    } else {
                        processHeaderLine(request, buf, tokenStart, lineEnd);
                    }
                }
            }

            int off = bufferOff + bufferRemaining;
            read = in.read(buf, off, Constants.LINE_BUFFER_LENGTH - off);
        }

        throw new HttpResponseException(HttpResponse.Status.REQUEST_HEADER_FIELDS_TOO_LARGE);
    }

    private void endOfHeader(HttpRequestImpl request) throws HttpResponseException {
//        if (request.getContentType() != null && request.getContentType().isMultipart()) {
//            if (request.method == HttpRequest.Method.POST) {
//                throw new HttpResponseException(HttpResponse.Status.INTERNAL_ERROR, "TODO"); // TODO: Multipart Body
//            } else {
//                throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Invalid Content-Type.");
//            }
//        }

        String contentLength = request.headers.getFirst("content-length");
        long len;

        if (contentLength != null) {
            try {
                len = Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
                throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Invalid Content-Length.");
            }
        } else {
            len = 0L;
        }

        request.bodySize = len;
        if (len > 0) {
            request.body = new BoundedInputStream(len);
        }
    }

    public int read() throws IOException {
        if (bufferRemaining > 0) {
            int res = lineBuffer[bufferOff] & 0xff;

            bufferRemaining--;

            if (bufferRemaining == 0) {
                bufferOff = 0;
            } else {
                bufferOff++;
            }

            return res;
        }

        return in.read();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (bufferRemaining > 0) {
            int r = Math.min(bufferRemaining, len);
            System.arraycopy(lineBuffer, bufferOff, b, off, r);

            bufferRemaining -= r;

            if (bufferRemaining == 0) {
                bufferOff = 0;
            } else {
                bufferOff += r;
            }

            return r;
        }

        return in.read(b, off, len);
    }

    private byte[] skipBuffer;

    public void forceSkip(long n) throws IOException {
        if (n <= 0) {
            return;
        }

        if (bufferRemaining > 0) {
            int r = (int) Math.min(bufferRemaining, n);

            bufferRemaining -= r;

            if (bufferRemaining == 0) {
                bufferOff = 0;
            } else {
                bufferOff += r;
            }

            n -= r;
        }

        if (n > 0) {
            if (skipBuffer == null) {
                skipBuffer = new byte[2048];
            }

            while (n > 0) {
                int read = in.read(skipBuffer, 0, (int) Math.min(skipBuffer.length, n));
                if (read <= 0) {
                    in.close();
                    throw new EOFException();
                }

                n -= read;
            }
        }
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

    // Quick lookup table based on the method name length
    private static final int MAX_METHOD_NAME_LENGTH;
    private static final byte[][][] METHOD_NAME_LOOKUP;
    private static final HttpRequest.Method[][] METHOD_VALUE_LOOKUP;

    static {
        HttpRequest.Method[] methods = HttpRequest.Method.values();

        int maxLength = 0;
        for (HttpRequest.Method method : methods) {
            int len = method.name().length();
            if (len > maxLength) {
                maxLength = len;
            }
        }

        HttpRequest.Method[][] valueLookup = new HttpRequest.Method[maxLength + 1][];
        for (HttpRequest.Method method : methods) {
            int len = method.name().length();

            HttpRequest.Method[] arr = valueLookup[len];
            if (arr == null) {
                arr = new HttpRequest.Method[]{method};
            } else {
                arr = Arrays.copyOf(arr, arr.length + 1);
                arr[arr.length - 1] = method;
            }
            valueLookup[len] = arr;
        }

        byte[][][] nameLookup = new byte[valueLookup.length][][];
        for (int i = 0; i < valueLookup.length; i++) {
            HttpRequest.Method[] values = valueLookup[i];
            if (values != null) {
                byte[][] names = new byte[values.length][];
                for (int j = 0; j < values.length; j++) {
                    names[j] = values[j].name().getBytes(StandardCharsets.ISO_8859_1);
                }
                nameLookup[i] = names;
            }
        }

        MAX_METHOD_NAME_LENGTH = maxLength;
        METHOD_NAME_LOOKUP = nameLookup;
        METHOD_VALUE_LOOKUP = valueLookup;
    }

    private static HttpRequest.Method lookupMethod(byte[] arr, int off, int end) {
        int len = end - off;

        byte[][] names;
        if (len <= MAX_METHOD_NAME_LENGTH && (names = METHOD_NAME_LOOKUP[len]) != null) {

            outer:
            for (int i = 0; i < names.length; i++) {
                byte[] name = names[i];

                for (int j = 0; j < name.length; j++) {
                    if (arr[off + i] != name[i]) {
                        continue outer;
                    }
                }

                return METHOD_VALUE_LOOKUP[len][i];
            }
        }

        return null;
    }

    private static void processStartLine(HttpRequestImpl request, byte[] buf, int off, int lineEnd) throws HttpResponseException {
        int end = findTokenEnd(buf, off, lineEnd);
        if (end == lineEnd) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST);
        }

        HttpRequest.Method method = lookupMethod(buf, off, end);
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

        URI uri;
        try {
            uri = new URI(rawUri);
        } catch (URISyntaxException e) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Illegal URI.");
        }

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
        request.uri = uri;
        request.httpVersion = httpVersion;
    }

    private static void processHeaderLine(HttpRequestImpl request, byte[] buf, int off, int end) throws HttpResponseException {
        int nameEnd = off;
        while (nameEnd < end) {
            byte ch = buf[nameEnd];
            if (Utils.isTokenPart(ch) && ch != ':') {
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

        request.headers.addDirect(name.toLowerCase(Locale.ROOT), value);
    }

    private final class BoundedInputStream extends InputStream {
        private boolean closed = false;

        private final long limit;
        private long totalRead = 0;

        BoundedInputStream(long limit) {
            this.limit = limit;
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
        }

        @Override
        public int available() throws IOException {
            ensureOpen();
            return (int) Math.min(limit - totalRead, Integer.MAX_VALUE);
        }

        @Override
        public int read() throws IOException {
            ensureOpen();

            if (totalRead < limit) {
                int res = HttpRequestReader.this.read();
                if (res >= 0) {
                    totalRead++;
                }
                return res;
            } else {
                return -1;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ensureOpen();

            if (len == 0) {
                return 0;
            }

            long maxRead = limit - totalRead;
            if (maxRead > 0) {
                int nTryRead = (int) Math.min(len, maxRead);

                int res = HttpRequestReader.this.read(b, off, nTryRead);
                if (res > 0) {
                    assert res <= nTryRead;
                    totalRead += res;
                    return res;
                } else {
                    return -1;
                }
            } else {
                return -1;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }

            if (totalRead < limit) {
                forceSkip(limit - totalRead);
                totalRead = limit;
            }

            this.closed = true;
        }
    }

    private final class MultiPartInputStream extends InputStream {
        private boolean closed = false;

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
        }

        private final byte[] boundary;

        private long partCount = 0;
        private int possibleOffset;

        MultiPartInputStream(byte[] boundary) {
            this.boundary = boundary;
        }

        @Override
        public int read() throws IOException {
            ensureOpen();
            if (bufferRemaining > 0) {
                return HttpRequestReader.this.read();
            }


            // TODO
            return 0;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }

            closed = true;

            // TODO
        }
    }
}
