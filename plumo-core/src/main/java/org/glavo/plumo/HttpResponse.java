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
package org.glavo.plumo;

import org.glavo.plumo.internal.HttpResponseImpl;
import org.jetbrains.annotations.ApiStatus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public /*sealed*/ interface HttpResponse {

    static HttpResponse newResponse() {
        return new HttpResponseImpl();
    }

    static HttpResponse newResponse(Status status) {
        return newResponse().withStatus(status);
    }

    static HttpResponse newTextResponse(String text, String contentType) {
        return newTextResponse(Status.OK, text, contentType);
    }

    static HttpResponse newTextResponse(Status status, String text, String contentType) {
        return new HttpResponseImpl(false, status, text, contentType);
    }

    HttpResponse freeze();

    HttpResponse withStatus(Status status);

    default HttpResponse withStatus(int statusCode) {
        return withStatus(Status.get(statusCode));
    }

    default HttpResponse withStatus(int statusCode, String description) {
        return withStatus(new Status(statusCode, description));
    }

    HttpResponse withHeader(HttpHeaderField field, String value);

    default HttpResponse withHeader(String field, String value) {
        return withHeader(HttpHeaderField.of(field), value);
    }

    HttpResponse withHeader(HttpHeaderField field, List<String> values);

    default HttpResponse withHeader(String field, List<String> values) {
        return withHeader(HttpHeaderField.of(field), values);
    }

    HttpResponse addHeader(HttpHeaderField field, String value);

    default HttpResponse addHeader(String field, String value) {
        return addHeader(HttpHeaderField.of(field), value);
    }

    HttpResponse removeHeader(HttpHeaderField field);

    default HttpResponse removeHeader(String field) {
        HttpHeaderField f;
        try {
            f = HttpHeaderField.of(field);
        } catch (IllegalArgumentException e) {
            return this;
        }
        return removeHeader(f);
    }

    HttpResponse withBody(byte[] data);

    HttpResponse withBody(String data);

    HttpResponse withBody(InputStream data, long contentLength);

    HttpResponse withBody(Path file);

    default HttpResponse withBody(File file) {
        return withBody(file.toPath());
    }

    // Getters

    Status getStatus();

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
            if (description == null || description.isEmpty()) {
                return statusCodeStr.getBytes(ISO_8859_1);
            }

            int binaryLength = statusCodeStr.length() + description.length() + 1;
            byte[] binary = new byte[binaryLength];

            statusCodeStr.getBytes(0, statusCodeStr.length(), binary, 0);
            binary[statusCodeStr.length()] = ' ';

            int offset = statusCodeStr.length() + 1;
            for (int i = 0; i < description.length(); i++) {
                char ch = description.charAt(i);

                if (ch > 0 && ch < 128) {
                    binary[offset + i] = (byte) ch;
                } else {
                    throw new IllegalArgumentException();
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

        @Override
        public String toString() {
            return new String(binary, ISO_8859_1);
        }
    }
}
