package org.glavo.plumo.internal;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLException;

import org.glavo.plumo.*;
import org.glavo.plumo.content.Cookie;
import org.glavo.plumo.content.CookieHandler;

public final class HttpSession {

    public static final String POST_DATA = "postData";

    private static final int REQUEST_BUFFER_LEN = 512;

    private static final int MEMORY_STORE_LIMIT = 1024;

    public static final int HEADER_BUFFER_SIZE = 8192;

    public static final int MAX_HEADER_SIZE = 1024;

    private final HttpHandler handler;

    private final TempFileManager tempFileManager;

    private final UnsyncBufferedOutputStream outputStream;

    private final HttpRequestReader inputStream;

    private int splitbyte;

    private int rlen;

    private String uri;

    private HttpRequest.Method method;

    private final Map<String, List<String>> parms = new HashMap<>();

    private final Map<String, String> headers = new HashMap<>();

    private CookieHandler cookies;

    private String queryParameterString;

    private String remoteIp;

    private String protocolVersion;

    public HttpSession(HttpHandler handler, TempFileManager tempFileManager, InputStream inputStream, OutputStream outputStream) {
        this(handler, tempFileManager, inputStream, outputStream, InetAddress.getLoopbackAddress());
    }

    public HttpSession(HttpHandler handler, TempFileManager tempFileManager, InputStream inputStream, OutputStream outputStream, InetAddress inetAddress) {
        this.handler = handler;
        this.tempFileManager = tempFileManager;
        this.inputStream = new HttpRequestReader(inputStream);
        this.outputStream = new UnsyncBufferedOutputStream(outputStream, 1024);
        this.remoteIp = inetAddress.isAnyLocalAddress() ? InetAddress.getLoopbackAddress().getHostAddress() : inetAddress.getHostAddress();
    }


    /**
     * Decodes the Multipart Body data and put it into Key/Value pairs.
     */
    private void decodeMultipartFormData(HttpContentType contentType, ByteBuffer fbuf, Map<String, List<String>> parms, Map<String, String> files) throws HttpResponseException {
        int pcount = 0;
        try {
            int[] boundaryIdxs = getBoundaryPositions(fbuf, contentType.getBoundary().getBytes(StandardCharsets.UTF_8));
            if (boundaryIdxs.length < 2) {
                throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but contains less than two boundary strings.");
            }

            byte[] partHeaderBuff = new byte[MAX_HEADER_SIZE];
            for (int boundaryIdx = 0; boundaryIdx < boundaryIdxs.length - 1; boundaryIdx++) {
                fbuf.position(boundaryIdxs[boundaryIdx]);
                int len = Math.min(fbuf.remaining(), MAX_HEADER_SIZE);
                fbuf.get(partHeaderBuff, 0, len);
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(partHeaderBuff, 0, len), contentType.getCharset()), len);



                int headerLines = 0;
                // First line is boundary string
                String mpline = in.readLine();
                headerLines++;
                if (mpline == null || !mpline.contains(contentType.getBoundary())) {
                    throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but chunk does not start with boundary.");
                }

                String partName = null, fileName = null, partContentType = null;
                // Parse the reset of the header lines
                mpline = in.readLine();
                headerLines++;
                while (mpline != null && mpline.trim().length() > 0) {
                    Matcher matcher = Constants.CONTENT_DISPOSITION_PATTERN.matcher(mpline);
                    if (matcher.matches()) {
                        String attributeString = matcher.group(2);
                        matcher = Constants.CONTENT_DISPOSITION_ATTRIBUTE_PATTERN.matcher(attributeString);
                        while (matcher.find()) {
                            String key = matcher.group(1);
                            if ("name".equalsIgnoreCase(key)) {
                                partName = matcher.group(2);
                            } else if ("filename".equalsIgnoreCase(key)) {
                                fileName = matcher.group(2);
                                // add these two line to support multiple
                                // files uploaded using the same field Id
                                if (!fileName.isEmpty()) {
                                    if (pcount > 0)
                                        partName = partName + String.valueOf(pcount++);
                                    else
                                        pcount++;
                                }
                            }
                        }
                    }
                    matcher = Constants.CONTENT_TYPE_PATTERN.matcher(mpline);
                    if (matcher.matches()) {
                        partContentType = matcher.group(2).trim();
                    }
                    mpline = in.readLine();
                    headerLines++;
                }
                int partHeaderLength = 0;
                while (headerLines-- > 0) {
                    partHeaderLength = scipOverNewLine(partHeaderBuff, partHeaderLength);
                }
                // Read the part data
                if (partHeaderLength >= len - 4) {
                    throw new HttpResponseException(HttpResponse.Status.INTERNAL_ERROR, "Multipart header size exceeds MAX_HEADER_SIZE.");
                }
                int partDataStart = boundaryIdxs[boundaryIdx] + partHeaderLength;
                int partDataEnd = boundaryIdxs[boundaryIdx + 1] - 4;

                fbuf.position(partDataStart);

                List<String> values = parms.computeIfAbsent(partName, k -> new ArrayList<>());

                if (partContentType == null) {
                    // Read the part into a string
                    byte[] dataBytes = new byte[partDataEnd - partDataStart];
                    fbuf.get(dataBytes);

                    values.add(new String(dataBytes, contentType.getCharset()));
                } else {
                    // Read it into a file
                    String path = saveTmpFile(fbuf, partDataStart, partDataEnd - partDataStart, fileName);
                    if (!files.containsKey(partName)) {
                        files.put(partName, path);
                    } else {
                        int count = 2;
                        while (files.containsKey(partName + count)) {
                            count++;
                        }
                        files.put(partName + count, path);
                    }
                    values.add(fileName);
                }
            }
        } catch (HttpResponseException re) {
            throw re;
        } catch (Exception e) {
            throw new HttpResponseException(HttpResponse.Status.INTERNAL_ERROR, e.toString());
        }
    }

    private int scipOverNewLine(byte[] partHeaderBuff, int index) {
        while (partHeaderBuff[index] != '\n') {
            index++;
        }
        return ++index;
    }

    /**
     * Decodes parameters in percent-encoded URI-format ( e.g.
     * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given Map.
     */
    private void decodeParms(String parms, Map<String, List<String>> p) {
        if (parms == null) {
            this.queryParameterString = "";
            return;
        }

        this.queryParameterString = parms;
        StringTokenizer st = new StringTokenizer(parms, "&");
        while (st.hasMoreTokens()) {
            String e = st.nextToken();
            int sep = e.indexOf('=');
            String key;
            String value;

            if (sep >= 0) {
                key = URLDecoder.decode(e.substring(0, sep), StandardCharsets.UTF_8).trim();
                value = URLDecoder.decode(e.substring(sep + 1), StandardCharsets.UTF_8);
            } else {
                key = URLDecoder.decode(e, StandardCharsets.UTF_8).trim();
                value = "";
            }

            p.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
    }

    public void execute() throws IOException {
        HttpResponseImpl r = null;
        try {
            // Read the first 8192 bytes.
            // The full header should fit in here.
            // Apache's default header limit is 8KB.
            // Do NOT assume that a single read will get the entire header
            // at once!

            this.splitbyte = 0;
            this.rlen = 0;

            HttpRequestImpl request;

            try {
                request = inputStream.read();
            } catch (SSLException e) {
                throw e;
            } catch (IOException e) {
                IOUtils.safeClose(this.inputStream);
                IOUtils.safeClose(this.outputStream);
                throw ServerShutdown.shutdown();
            }


//            if (null != this.remoteIp) {
//                this.headers.put("remote-addr", this.remoteIp);
//                this.headers.put("http-client-ip", this.remoteIp);
//            }
//
//            this.cookies = new CookieHandler(this.headers);

            String connection = request.headers.getFirst("connection");
            boolean keepAlive = "HTTP/1.1".equals(request.httpVersion) && (connection == null || !connection.equals("close"));

            // Ok, now do the serve()

            r = (HttpResponseImpl) handler.handle(request);

            if (r == null) {
                throw new HttpResponseException(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
            }

            String acceptEncoding = request.headers.getFirst("accept-encoding");
            // TODO: this.cookies.unloadQueue(r);
            if (acceptEncoding == null || !acceptEncoding.contains("gzip")) {
                r.setContentEncoding("");
            }
            r.setKeepAlive(keepAlive);

            try {
                send(r, this.outputStream);
            } catch (IOException ioe) {
                HttpServerImpl.LOG.log(Level.SEVERE, "Could not send response to the client", ioe);
                IOUtils.safeClose(this.outputStream);
                return;
            }

            if (!keepAlive || r.needCloseConnection()) {
                throw ServerShutdown.shutdown();
            }
        } catch (SocketException | SocketTimeoutException e) {
            // throw it out to close socket object (finalAccept)
            throw e;
        } catch (IOException | HttpResponseException e) {
            HttpResponse resp;
            if (e instanceof HttpResponseException) {
                resp = HttpResponse.newPlainTextResponse(((HttpResponseException) e).getStatus(), e.getMessage());
            } else if (e instanceof SSLException) {
                resp = HttpResponse.newPlainTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SSL PROTOCOL FAILURE: " + e.getMessage());
            } else {
                resp = HttpResponse.newPlainTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + e.getMessage());
            }

            send((HttpResponseImpl) resp, this.outputStream);
            IOUtils.safeClose(this.outputStream);
        } finally {
            if (r != null) {
                r.finish();
            }
            this.tempFileManager.close();
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
    public void send(HttpResponseImpl response, UnsyncBufferedOutputStream out) throws IOException {
        if (response.status == null) {
            throw new Error("sendResponse(): Status can't be null.");
        }

        boolean useGzip;
        if (response.contentEncoding == null) {
            if (response.contentType == null) {
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
            throw new Error("unsupported content encoding: " + response.contentEncoding);
        }

        HttpContentType contentType = response.contentType;

        out.writeASCII("HTTP/1.1 ");
        out.writeStatus(response.status);
        out.writeCRLF();

        if (contentType != null) {
            out.writeHttpHeader("content-type", contentType.toString());
        }

        Instant date = response.date == null ? Instant.now() : response.date;
        out.writeHttpHeader("date", Constants.HTTP_TIME_FORMATTER.format(date));

        for (Map.Entry<String, Object> entry : response.headers.entrySet()) {
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

        if (response.connection != null) {
            out.writeHttpHeader("connection", response.connection);
        }

        if (useGzip) {
            out.writeHttpHeader("content-encoding", "gzip");
        }

        long contentLength;
        boolean chunkedTransfer;
        InputStream input = null;
        byte[] data = null;

        Object body = response.body;
        if (body == null) {
            contentLength = 0L;
            chunkedTransfer = false;
        } else if (body instanceof InputStream) {
            input = (InputStream) body;
            contentLength = response.contentLength;
            chunkedTransfer = contentLength < 0;
        } else if (body instanceof byte[]) {
            data = (byte[]) body;
            contentLength = data.length;
            chunkedTransfer = useGzip;
        } else if (body instanceof String) {
            data = ((String) body).getBytes(contentType == null ? StandardCharsets.UTF_8 : contentType.getCharset());
            contentLength = data.length;
            chunkedTransfer = useGzip;
        } else {
            throw new InternalError("unexpected types: " + body.getClass());
        }

        if (contentLength >= 0) {
            out.writeHttpHeader("content-length", Long.toString(contentLength));
        }

        if (this.method != HttpRequest.Method.HEAD && chunkedTransfer) {
            out.writeHttpHeader("transfer-encoding", "chunked");
        }
        out.writeCRLF();
        if (method != HttpRequest.Method.HEAD && (input != null || data != null)) {
            if (input != null || chunkedTransfer) {
                long pending;

                if (input == null) {
                    input = new ByteArrayInputStream(data);
                    pending = Long.MAX_VALUE;
                } else {
                    pending = contentLength >= 0 ? contentLength : Long.MAX_VALUE;
                }

                OutputStream o = out;

                ChunkedOutputStream chunkedOutputStream = null;
                GZIPOutputStream gzipOutputStream = null;

                if (chunkedTransfer) {
                    o = chunkedOutputStream = new ChunkedOutputStream(o);
                }
                if (useGzip) {
                    o = gzipOutputStream = new GZIPOutputStream(o);
                }

                sendBody(input, o, pending);

                if (useGzip) {
                    gzipOutputStream.finish();
                }
                if (chunkedTransfer) {
                    chunkedOutputStream.finish();
                }
            } else {
                sendBody(data, outputStream);
            }
        }
        out.flush();
    }

    private void sendBody(InputStream input, OutputStream outputStream, long pending) throws IOException {
        long BUFFER_SIZE = 16 * 1024;
        byte[] buff = new byte[(int) BUFFER_SIZE];
        while (pending > 0) {
            long bytesToRead = Math.min(pending, BUFFER_SIZE);
            int read = input.read(buff, 0, (int) bytesToRead);
            if (read <= 0) {
                break;
            }
            outputStream.write(buff, 0, read);
            pending -= read;
        }
    }

    private void sendBody(byte[] data, OutputStream outputStream) throws IOException {
        outputStream.write(data);
    }

    // --- API ---

    public CookieHandler getCookies() {
        return this.cookies;
    }

    public Map<String, String> getHeaders() {
        return this.headers;
    }

    public HttpRequest.Method getMethod() {
        return this.method;
    }

    public Map<String, List<String>> getParameters() {
        return this.parms;
    }

    public String getQueryParameterString() {
        return this.queryParameterString;
    }

    private RandomAccessFile getTmpBucket() {
        try {
            Path tempFile = this.tempFileManager.createTempFile(null);
            return new RandomAccessFile(tempFile.toFile(), "rw");
        } catch (Exception e) {
            throw new Error(e); // we won't recover, so throw an error
        }
    }

    public String getUri() {
        return this.uri;
    }

    /**
     * Deduce body length in bytes. Either from "content-length" header or read
     * bytes.
     */
    public long getBodySize() {
        String length = headers.get("content-length");
        if (length != null) {
            return Long.parseLong(length);
        } else if (this.splitbyte < this.rlen) {
            return this.rlen - this.splitbyte;
        }
        return 0;
    }

    public void parseBody(Map<String, String> files) throws IOException, HttpResponseException {
        RandomAccessFile randomAccessFile = null;
        try {
            long size = getBodySize();
            ByteArrayOutputStream baos = null;
            DataOutput requestDataOutput;

            // Store the request in memory or a file, depending on size
            if (size < MEMORY_STORE_LIMIT) {
                baos = new ByteArrayOutputStream();
                requestDataOutput = new DataOutputStream(baos);
            } else {
                randomAccessFile = getTmpBucket();
                requestDataOutput = randomAccessFile;
            }

            // Read all the body and write it to request_data_output
            byte[] buf = new byte[REQUEST_BUFFER_LEN];
            while (this.rlen >= 0 && size > 0) {
                this.rlen = this.inputStream.read(buf, 0, (int) Math.min(size, REQUEST_BUFFER_LEN));
                size -= this.rlen;
                if (this.rlen > 0) {
                    requestDataOutput.write(buf, 0, this.rlen);
                }
            }

            ByteBuffer fbuf;
            if (baos != null) {
                fbuf = ByteBuffer.wrap(baos.toByteArray(), 0, baos.size());
            } else {
                fbuf = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, randomAccessFile.length());
                randomAccessFile.seek(0);
            }

            // If the method is POST, there may be parameters
            // in data section, too, read it:
            switch (this.method) {
                case POST:
                    HttpContentType contentType = new HttpContentType(this.headers.get("content-type"));
                    if (contentType.isMultipart()) {
                        String boundary = contentType.getBoundary();
                        if (boundary == null) {
                            throw new HttpResponseException(HttpResponse.Status.BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html");
                        }
                        decodeMultipartFormData(contentType, fbuf, this.parms, files);
                    } else {
                        byte[] postBytes = new byte[fbuf.remaining()];
                        fbuf.get(postBytes);
                        String postLine = new String(postBytes, contentType.getCharset()).trim();
                        // Handle application/x-www-form-urlencoded
                        if ("application/x-www-form-urlencoded".equalsIgnoreCase(contentType.getMimeType())) {
                            decodeParms(postLine, this.parms);
                        } else if (!postLine.isEmpty()) {
                            // Special case for raw POST data => create a
                            // special files entry "postData" with raw content
                            // data
                            files.put(POST_DATA, postLine);
                        }
                    }
                    break;
                case PUT:
                    files.put("content", saveTmpFile(fbuf, 0, fbuf.limit(), null));
                    break;
            }
        } finally {
            IOUtils.safeClose(randomAccessFile);
        }
    }

    /**
     * Retrieves the content of a sent file and saves it to a temporary file.
     * The full path to the saved file is returned.
     */
    private String saveTmpFile(ByteBuffer b, int offset, int len, String filenameHint) {
        String path = "";
        if (len > 0) {
            FileChannel dest = null;
            try {
                Path tempFile = this.tempFileManager.createTempFile(filenameHint);
                ByteBuffer src = b.duplicate();
                dest = FileChannel.open(tempFile, StandardOpenOption.WRITE);
                src.position(offset).limit(offset + len);
                dest.write(src.slice());
                path = tempFile.toString();
            } catch (Exception e) { // Catch exception if any
                throw new Error(e); // we won't recover, so throw an error
            } finally {
                IOUtils.safeClose(dest);
            }
        }
        return path;
    }

    public String getRemoteIpAddress() {
        return this.remoteIp;
    }
}
