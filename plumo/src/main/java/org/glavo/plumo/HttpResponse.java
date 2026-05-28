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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/// Immutable HTTP response metadata and body.
///
/// A response object is immutable after construction. Body reusability is controlled by the attached
/// [`ResponseBody`][ResponseBody], so stream-backed responses remain one-shot even though the response object itself
/// is immutable.
///
/// @see HttpHandler#handle(HttpRequest)
@NotNullByDefault
public sealed interface HttpResponse permits HttpResponseImpl {
    /// Creates an empty `200 OK` response.
    static HttpResponse newResponse() {
        return newBuilder().build();
    }

    /// Creates an empty response with the supplied status.
    static HttpResponse newResponse(Status status) {
        return newBuilder(status).build();
    }

    /// Creates a reusable plain text `200 OK` response.
    static HttpResponse newTextResponse(String text) {
        return newTextResponse(Status.OK, text, "text/plain");
    }

    /// Creates a reusable text `200 OK` response with a content type.
    static HttpResponse newTextResponse(String text, String contentType) {
        return newTextResponse(Status.OK, text, contentType);
    }

    /// Creates a reusable text response with a status and content type.
    static HttpResponse newTextResponse(Status status, String text, String contentType) {
        return newBuilder(status)
                .withHeader(HttpHeaderField.CONTENT_TYPE, contentType)
                .withBody(text)
                .build();
    }

    /// Creates a new response builder.
    static Builder newBuilder() {
        return new HttpResponseImpl.BuilderImpl();
    }

    /// Creates a new response builder with the supplied status.
    static Builder newBuilder(Status status) {
        return new HttpResponseImpl.BuilderImpl().withStatus(status);
    }

    /// Returns a builder initialized from this response.
    default Builder toBuilder() {
        return new HttpResponseImpl.BuilderImpl(this);
    }

    /// Returns this response because responses are already immutable.
    default HttpResponse freeze() {
        return this;
    }

    /// Returns a copy of this response with a different status.
    default HttpResponse withStatus(Status status) {
        return toBuilder().withStatus(status).build();
    }

    /// Returns a copy of this response with a different status code.
    default HttpResponse withStatus(int statusCode) {
        return withStatus(Status.get(statusCode));
    }

    /// Returns a copy of this response with a custom status.
    default HttpResponse withStatus(int statusCode, String description) {
        return withStatus(new Status(statusCode, description));
    }

    /// Returns a copy of this response with a single header value.
    default HttpResponse withHeader(HttpHeaderField field, String value) {
        return toBuilder().withHeader(field, value).build();
    }

    /// Returns a copy of this response with a single header value.
    default HttpResponse withHeader(String field, String value) {
        return withHeader(HttpHeaderField.of(field), value);
    }

    /// Returns a copy of this response with the supplied header values.
    default HttpResponse withHeader(HttpHeaderField field, List<String> values) {
        return toBuilder().withHeader(field, values).build();
    }

    /// Returns a copy of this response with the supplied header values.
    default HttpResponse withHeader(String field, List<String> values) {
        return withHeader(HttpHeaderField.of(field), values);
    }

    /// Returns a copy of this response with an added header value.
    default HttpResponse addHeader(HttpHeaderField field, String value) {
        return toBuilder().addHeader(field, value).build();
    }

    /// Returns a copy of this response with an added header value.
    default HttpResponse addHeader(String field, String value) {
        return addHeader(HttpHeaderField.of(field), value);
    }

    /// Returns a copy of this response without a header.
    default HttpResponse removeHeader(HttpHeaderField field) {
        return toBuilder().removeHeader(field).build();
    }

    /// Returns a copy of this response without a header.
    default HttpResponse removeHeader(String field) {
        try {
            return removeHeader(HttpHeaderField.of(field));
        } catch (IllegalArgumentException e) {
            return this;
        }
    }

    /// Returns a copy of this response with a byte array body.
    default HttpResponse withBody(byte[] data) {
        return withBody(ResponseBody.of(data));
    }

    /// Returns a copy of this response with a byte array range body.
    default HttpResponse withBody(byte[] data, int offset, int length) {
        return withBody(ResponseBody.of(data, offset, length));
    }

    /// Returns a copy of this response with a byte buffer body.
    default HttpResponse withBody(ByteBuffer data) {
        return withBody(ResponseBody.of(data));
    }

    /// Returns a copy of this response with a text body.
    default HttpResponse withBody(String data) {
        return withBody(ResponseBody.of(data));
    }

    /// Returns a copy of this response with a channel body of unknown length.
    default HttpResponse withBody(ReadableByteChannel data) {
        return withBody(data, -1L);
    }

    /// Returns a copy of this response with a channel body.
    default HttpResponse withBody(ReadableByteChannel data, long contentLength) {
        return withBody(ResponseBody.of(data, contentLength));
    }

    /// Returns a copy of this response with a stream body of unknown length.
    default HttpResponse withBody(java.io.InputStream data) {
        return withBody(data, -1L);
    }

    /// Returns a copy of this response with a stream body.
    default HttpResponse withBody(java.io.InputStream data, long contentLength) {
        return withBody(ResponseBody.of(data, contentLength));
    }

    /// Returns a copy of this response with a file body.
    default HttpResponse withBody(Path file) {
        return withBody(ResponseBody.of(file));
    }

    /// Returns a copy of this response with a file body.
    default HttpResponse withBody(File file) {
        return withBody(file.toPath());
    }

    /// Returns a copy of this response with a response body.
    default HttpResponse withBody(ResponseBody body) {
        return toBuilder().withBody(body).build();
    }

    /// Returns the response status.
    Status getStatus();

    /// Returns the first value for a response header, or `null` if the header is not present.
    @Nullable
    String getHeader(HttpHeaderField field);

    /// Returns immutable response headers.
    Map<HttpHeaderField, @Unmodifiable List<String>> getHeaders();

    /// Returns the response body.
    ResponseBody getBody();

    /// Mutable builder for immutable HTTP responses.
    interface Builder {
        /// Sets the response status.
        Builder withStatus(Status status);

        /// Sets the response status code.
        default Builder withStatus(int statusCode) {
            return withStatus(Status.get(statusCode));
        }

        /// Sets a custom response status.
        default Builder withStatus(int statusCode, String description) {
            return withStatus(new Status(statusCode, description));
        }

        /// Replaces all values for a header with one value.
        Builder withHeader(HttpHeaderField field, String value);

        /// Replaces all values for a header with one value.
        default Builder withHeader(String field, String value) {
            return withHeader(HttpHeaderField.of(field), value);
        }

        /// Replaces all values for a header.
        Builder withHeader(HttpHeaderField field, List<String> values);

        /// Replaces all values for a header.
        default Builder withHeader(String field, List<String> values) {
            return withHeader(HttpHeaderField.of(field), values);
        }

        /// Adds one value to a header.
        Builder addHeader(HttpHeaderField field, String value);

        /// Adds one value to a header.
        default Builder addHeader(String field, String value) {
            return addHeader(HttpHeaderField.of(field), value);
        }

        /// Removes a header.
        Builder removeHeader(HttpHeaderField field);

        /// Removes a header.
        default Builder removeHeader(String field) {
            try {
                return removeHeader(HttpHeaderField.of(field));
            } catch (IllegalArgumentException e) {
                return this;
            }
        }

        /// Sets a byte array body.
        default Builder withBody(byte[] data) {
            return withBody(ResponseBody.of(data));
        }

        /// Sets a byte array range body.
        default Builder withBody(byte[] data, int offset, int length) {
            return withBody(ResponseBody.of(data, offset, length));
        }

        /// Sets a byte buffer body.
        default Builder withBody(ByteBuffer data) {
            return withBody(ResponseBody.of(data));
        }

        /// Sets a text body.
        default Builder withBody(String data) {
            return withBody(ResponseBody.of(data));
        }

        /// Sets a channel body of unknown length.
        default Builder withBody(ReadableByteChannel data) {
            return withBody(data, -1L);
        }

        /// Sets a channel body.
        default Builder withBody(ReadableByteChannel data, long contentLength) {
            return withBody(ResponseBody.of(data, contentLength));
        }

        /// Sets a stream body of unknown length.
        default Builder withBody(java.io.InputStream data) {
            return withBody(data, -1L);
        }

        /// Sets a stream body.
        default Builder withBody(java.io.InputStream data, long contentLength) {
            return withBody(ResponseBody.of(data, contentLength));
        }

        /// Sets a file body.
        default Builder withBody(Path file) {
            return withBody(ResponseBody.of(file));
        }

        /// Sets a file body.
        default Builder withBody(File file) {
            return withBody(file.toPath());
        }

        /// Sets a response body.
        Builder withBody(ResponseBody body);

        /// Builds an immutable response.
        HttpResponse build();
    }

    /// HTTP response status.
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

        /// Returns a registered status for common codes or creates a status with no description.
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
        private static byte[] binary(int statusCode, @Nullable String description) {
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
        private final @Nullable String description;
        private final byte @Unmodifiable [] binary;

        /// Creates a custom response status.
        public Status(int statusCode, @Nullable String description) {
            this.statusCode = statusCode;
            this.description = description;
            this.binary = binary(statusCode, description);
        }

        /// Returns the numeric status code.
        public int getStatusCode() {
            return statusCode;
        }

        /// Returns the reason phrase, or `null` when absent.
        public @Nullable String getDescription() {
            return description;
        }

        /// Writes the pre-encoded status line fragment.
        @ApiStatus.Internal
        public void writeTo(OutputStream out) throws IOException {
            out.write(binary);
        }

        /// Returns the status code and reason phrase.
        @Override
        public String toString() {
            return new String(binary, ISO_8859_1);
        }
    }
}
