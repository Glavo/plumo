package org.glavo.plumo.internal;

import org.glavo.plumo.HttpHeaderField;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.internal.util.Utils;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.glavo.plumo.internal.Constants.LINE_BUFFER_LENGTH;

public final class HttpRequestReader implements Closeable {
    private static final Charset HEADER_ENCODING = StandardCharsets.UTF_8;

    private final InputStream inputStream;
    private final ReadableByteChannel inputChannel;

    private final ByteBuffer lineBuffer;

    private boolean closed = false;

    public HttpRequestReader(InputStream inputStream) {
        this.inputStream = inputStream;
        this.inputChannel = null;
        this.lineBuffer = ByteBuffer.allocate(LINE_BUFFER_LENGTH).limit(0);
    }

    public HttpRequestReader(ReadableByteChannel inputChannel) {
        this.inputStream = null;
        this.inputChannel = inputChannel;
        this.lineBuffer = ByteBuffer.allocateDirect(LINE_BUFFER_LENGTH).limit(0);
    }

    public int read() throws IOException {
        if (lineBuffer.hasRemaining()) {
            return lineBuffer.get() & 0xff;
        } else if (inputChannel != null) {
            lineBuffer.limit(1).position(0);
            int n = inputChannel.read(lineBuffer);
            if (n < 0) {
                lineBuffer.limit(0);
                return -1;
            } else {
                return lineBuffer.flip().get() & 0xff;
            }
        } else {
            return inputStream.read();
        }
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (lineBuffer.hasRemaining()) {
            int n = Math.min(lineBuffer.remaining(), len);
            lineBuffer.get(b, off, n);
            return n;
        } else if (inputChannel != null) {
            return inputChannel.read(ByteBuffer.wrap(b, off, len));
        } else {
            return inputStream.read(b, off, len);
        }
    }

    public void forceSkip(long n) throws IOException {
        if (n <= 0) {
            return;
        }

        if (lineBuffer.hasRemaining()) {
            int remaining = lineBuffer.remaining();
            int r = (int) Math.min(remaining, n);

            if (r == remaining) {
                lineBuffer.limit(0);
            } else {
                lineBuffer.position(lineBuffer.position() + r);
            }

            n -= r;
        }

        if (inputChannel != null) {
            while (n > 0) {
                lineBuffer.position(0).limit((int) Math.min(LINE_BUFFER_LENGTH, n));
                int r = inputChannel.read(lineBuffer);
                if (r <= 0) {
                    throw new EOFException();
                }

                n -= r;
            }

            lineBuffer.limit(0);
        } else {
            while (n > 0) {
                int r = inputStream.read(lineBuffer.array(), 0, (int) Math.min(LINE_BUFFER_LENGTH, n));
                if (r <= 0) {
                    throw new EOFException();
                }

                n -= r;
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        lineBuffer.position(0).limit(0);
        if (inputChannel != null) {
            inputChannel.close();
        } else {
            inputStream.close();
        }
    }

    private int readMore() throws IOException {
        if (lineBuffer.limit() == LINE_BUFFER_LENGTH) {
            if (lineBuffer.position() == 0) {
                return 0;
            }

            lineBuffer.compact();
        }

        int oldPosition = lineBuffer.position();
        int oldLimit = lineBuffer.limit();

        int n;
        if (inputChannel != null) {
            lineBuffer.limit(LINE_BUFFER_LENGTH).position(oldLimit);
            n = inputChannel.read(lineBuffer);
            lineBuffer.position(oldPosition);
        } else {
            n = inputStream.read(lineBuffer.array(), oldLimit, LINE_BUFFER_LENGTH - oldLimit);
        }
        lineBuffer.limit(oldLimit + Math.max(0, n));
        return n;
    }

    private static int findLineEnd(ByteBuffer lineBuffer, int position, int limit) {
        while (position < limit) {
            byte b1 = lineBuffer.get(position);

            if (b1 == '\r') {
                if (position < limit - 1) {
                    return position;
                } else {
                    // Although we already know the line length,
                    // we still want to read the next character before processing
                    return -1;
                }
            }

            if (b1 == '\n') {
                return position;
            }

            position++;
        }

        return -1;
    }

    private static int findLineSeparatorEnd(ByteBuffer lineBuffer, int off) {
        if (lineBuffer.get(off) == '\n') {
            return off + 1;
        } else { // buf[off] == '\r'
            // secure access without checking boundaries
            if (lineBuffer.get(off + 1) == '\n') {
                return off + 2;
            } else {
                return off + 1;
            }
        }
    }

    private static int findTokenEnd(ByteBuffer lineBuffer, int off, int end) {
        while (off < end) {
            byte ch = lineBuffer.get(off);
            if (ch == ' ' || ch == '\t') {
                return off;
            }

            off++;
        }

        return end;
    }

    private static int findTokenStart(ByteBuffer lineBuffer, int off, int end) {
        while (off < end) {
            byte ch = lineBuffer.get(off);
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

    private static HttpRequest.Method lookupMethod(ByteBuffer lineBuffer, int off, int end) {
        int len = end - off;

        byte[][] names;
        if (len <= MAX_METHOD_NAME_LENGTH && (names = METHOD_NAME_LOOKUP[len]) != null) {

            outer:
            for (int i = 0; i < names.length; i++) {
                byte[] name = names[i];

                for (int j = 0; j < name.length; j++) {
                    if (lineBuffer.get(off + i) != name[i]) {
                        continue outer;
                    }
                }

                return METHOD_VALUE_LOOKUP[len][i];
            }
        }

        return null;
    }

    private void endOfHeader(HttpRequestImpl request) throws HttpResponseException {
//        if (request.getContentType() != null && request.getContentType().isMultipart()) {
//            if (request.method == HttpRequest.Method.POST) {
//                throw new HttpResponseException(HttpResponse.Status.INTERNAL_ERROR, "TODO"); // TODO: Multipart Body
//            } else {
//                throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Invalid Content-Type.");
//            }
//        }

        String contentLength = request.headers.getFirst(HttpHeaderField.CONTENT_LENGTH);
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

    private static void processStartLine(HttpRequestImpl request, ByteBuffer lineBuffer, int off, int lineEnd) throws HttpResponseException {
        int end = findTokenEnd(lineBuffer, off, lineEnd);
        if (end == lineEnd) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST);
        }

        HttpRequest.Method method = lookupMethod(lineBuffer, off, end);
        if (method == null) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST,
                    "BAD REQUEST: Syntax error. HTTP verb " + Utils.newString(lineBuffer, off, end, HEADER_ENCODING) + " unhandled.");
        }

        off = findTokenStart(lineBuffer, end, lineEnd);
        if (off < 0) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
        }
        end = findTokenEnd(lineBuffer, off, lineEnd);

        String rawUri = Utils.newString(lineBuffer, off, end, HEADER_ENCODING);

        URI uri;
        try {
            uri = new URI(rawUri);
        } catch (URISyntaxException e) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Illegal URI.");
        }

        off = findTokenStart(lineBuffer, end, lineEnd);
        if (end == lineEnd || off < 0) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Missing HTTP version.");
        }

        String httpVersion = Utils.newString(lineBuffer, off, lineEnd, HEADER_ENCODING).trim();
        if (!httpVersion.startsWith("HTTP/")) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Illegal HTTP version " + httpVersion + ".");
        }

        request.method = method;
        request.rawUri = rawUri;
        request.uri = uri;
        request.httpVersion = httpVersion;
    }

    private static void processHeaderLine(HttpRequestImpl request, ByteBuffer lineBuffer, int off, int end) throws HttpResponseException {
        int nameEnd = off;
        while (nameEnd < end) {
            byte ch = lineBuffer.get(nameEnd);
            if (Utils.isTokenPart(ch) && ch != ':') {
                nameEnd++;
            } else {
                break;
            }
        }

        if (nameEnd == off) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
        }

        String name = Utils.newString(lineBuffer, off, nameEnd, HEADER_ENCODING);

        off = findTokenStart(lineBuffer, nameEnd, end);
        if (off < 0) {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
        }

        byte b = lineBuffer.get(off);
        if (b != ':') {
            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
        }

        off = findTokenStart(lineBuffer, off + 1, end);

        String value = off < 0 ? "" : Utils.newString(lineBuffer, off, end, HEADER_ENCODING).trim();

        request.headers.addDirect(HttpHeaderField.of(name), value);
    }

    public void readHeader(HttpRequestImpl request) throws IOException {
        boolean firstLine = true;

        while (true) {
            int position = lineBuffer.position();
            int lineEnd = findLineEnd(lineBuffer, position, lineBuffer.limit());
            if (lineEnd < 0) {
                if (lineBuffer.remaining() < LINE_BUFFER_LENGTH) {
                    int n = readMore();
                    if (n <= 0) {
                        throw new EOFException();
                    }
                    continue;
                } else {
                    // line buffer is full
                    throw new HttpResponseException(HttpResponse.Status.REQUEST_HEADER_FIELDS_TOO_LARGE);
                }
            }

            int lineSeparatorEnd = findLineSeparatorEnd(lineBuffer, lineEnd);
            int tokenStart = findTokenStart(lineBuffer, position, lineEnd);

            int lineWithSeparatorLength = lineSeparatorEnd - position;

            if (lineWithSeparatorLength < lineBuffer.remaining()) {
                lineBuffer.position(lineSeparatorEnd);
            } else {
                lineBuffer.position(0).limit(0);
            }

            // end of http header
            if (tokenStart < 0) {
                endOfHeader(request);
                return;
            } else {
                assert lineEnd > 0;

                if (firstLine) {
                    firstLine = false;
                    processStartLine(request, lineBuffer, tokenStart, lineEnd);
                } else {
                    processHeaderLine(request, lineBuffer, tokenStart, lineEnd);
                }
            }
        }
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
}
