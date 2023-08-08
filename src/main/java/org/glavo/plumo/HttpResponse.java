package org.glavo.plumo;

import org.glavo.plumo.internal.HttpResponseImpl;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.time.Instant;


public /*sealed*/ interface HttpResponse {

    static Builder newBuilder() {
        return new HttpResponseImpl.BuilderImpl();
    }

    static Builder newBuilder(HttpResponse base) {
        if (!base.isReusable()) {
            throw new UnsupportedOperationException();
        }
        return new HttpResponseImpl.BuilderImpl(base);
    }

    interface Builder {

        HttpResponse build();

        Builder setStatus(Status status);

        Builder setDate(Instant time);

        Builder setContentType(ContentType contentType);

        Builder setContentType(String contentType);

        Builder setContentEncoding(String contentEncoding);

        Builder setKeepAlive(boolean keepAlive);

        Builder addHeader(String name, String value);

        Builder addCookies(Iterable<? extends Cookie> cookies);

        Builder setBody(byte[] data);

        Builder setBody(String data);

        Builder setBody(InputStream data, long contentLength);

        Builder setBodyUnknownSize(InputStream data);
    }

    static HttpResponse newPlainTextResponse(String data) {
        return new HttpResponseImpl(Status.OK, data, ContentType.PLAIN_TEXT);
    }

    static HttpResponse newPlainTextResponse(Status status, String data) {
        return new HttpResponseImpl(status, data, ContentType.PLAIN_TEXT);
    }

    static HttpResponse newHtmlResponse(String html) {
        return new HttpResponseImpl(Status.OK, html, ContentType.HTML);
    }

    static HttpResponse newHtmlResponse(Status status, String html) {
        return new HttpResponseImpl(status, html, ContentType.HTML);
    }

    boolean isReusable();

    final class Status implements Serializable {
        private static final long serialVersionUID = 0L;

        private static final Status[] LOOKUP = new Status[500];

        public static final Status SWITCH_PROTOCOL = register(101, "Switching Protocols");

        public static final Status OK = register(200, "OK");
        public static final Status CREATED = register(201, "Created");
        public static final Status ACCEPTED = register(202, "Accepted");
        public static final Status NO_CONTENT = register(204, "No Content");
        public static final Status PARTIAL_CONTENT = register(206, "Partial Content");
        public static final Status MULTI_STATUS = register(207, "Multi-Status");

        public static final Status REDIRECT = register(301, "Moved Permanently");
        /**
         * Many user agents mishandle 302 in ways that violate the RFC1945 spec
         * (i.e., redirect a POST to a GET). 303 and 307 were added in RFC2616 to
         * address this. You should prefer 303 and 307 unless the calling user agent
         * does not support 303 and 307 functionality
         */
        @Deprecated
        public static final Status FOUND = register(302, "Found");
        public static final Status REDIRECT_SEE_OTHER = register(303, "See Other");
        public static final Status NOT_MODIFIED = register(304, "Not Modified");
        public static final Status TEMPORARY_REDIRECT = register(307, "Temporary Redirect");

        public static final Status BAD_REQUEST = register(400, "Bad Request");
        public static final Status UNAUTHORIZED = register(401, "Unauthorized");
        public static final Status FORBIDDEN = register(403, "Forbidden");
        public static final Status NOT_FOUND = register(404, "Not Found");
        public static final Status METHOD_NOT_ALLOWED = register(405, "Method Not Allowed");
        public static final Status NOT_ACCEPTABLE = register(406, "Not Acceptable");
        public static final Status REQUEST_TIMEOUT = register(408, "Request Timeout");
        public static final Status CONFLICT = register(409, "Conflict");
        public static final Status GONE = register(410, "Gone");
        public static final Status LENGTH_REQUIRED = register(411, "Length Required");
        public static final Status PRECONDITION_FAILED = register(412, "Precondition Failed");
        public static final Status PAYLOAD_TOO_LARGE = register(413, "Payload Too Large");
        public static final Status UNSUPPORTED_MEDIA_TYPE = register(415, "Unsupported Media Type");
        public static final Status RANGE_NOT_SATISFIABLE = register(416, "Requested Range Not Satisfiable");
        public static final Status EXPECTATION_FAILED = register(417, "Expectation Failed");
        public static final Status TOO_MANY_REQUESTS = register(429, "Too Many Requests");
        public static final Status REQUEST_HEADER_FIELDS_TOO_LARGE = register(431, "Request Header Fields Too Large");

        public static final Status INTERNAL_ERROR = register(500, "Internal Server Error");
        public static final Status NOT_IMPLEMENTED = register(501, "Not Implemented");
        public static final Status SERVICE_UNAVAILABLE = register(503, "Service Unavailable");
        public static final Status UNSUPPORTED_HTTP_VERSION = register(505, "HTTP Version Not Supported");

        private static Status register(int statusCode, String description) {
            Status status = new Status(statusCode, description);
            LOOKUP[statusCode - 100] = status;
            return status;
        }

        public static Status get(int statusCode) {
            if (statusCode >= 100 && statusCode < 600) {
                Status status = LOOKUP[statusCode - 100];
                if (status != null) {
                    return status;
                }
            }

            return new Status(statusCode, null);
        }

        @SuppressWarnings("deprecation")
        private static byte[] binary(int statusCode, String description) {
            String statusCodeStr = String.valueOf(statusCode);
            boolean descriptionIsEmpty = description == null || description.isEmpty();

            int binaryLength = statusCodeStr.length() + (descriptionIsEmpty ? 0 : description.length() + 1);
            byte[] binary = new byte[binaryLength];

            statusCodeStr.getBytes(0, statusCodeStr.length(), binary, 0);
            if (!descriptionIsEmpty) {
                binary[statusCodeStr.length()] = ' ';

                int offset = statusCodeStr.length() + 1;
                for (int i = 0; i < description.length(); i++) {
                    char ch = description.charAt(i);

                    if (ch < 128) {
                        binary[offset + i] = (byte) ch;
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
            }

            return binary;
        }

        private final int statusCode;
        private final String description;
        private final byte[] binary;

        public Status(int statusCode, String description) {
            this.statusCode = statusCode;
            this.description = description;
            this.binary = binary(statusCode, description);
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getDescription() {
            return description;
        }

        @ApiStatus.Internal
        public void writeTo(OutputStream out) throws IOException {
            out.write(binary);
        }
    }
}
