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

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

public final class Status implements Serializable {
    private static final long serialVersionUID = 0L;

    public static final Status SWITCH_PROTOCOL = new Status(101, "Switching Protocols");

    public static final Status OK = new Status(200, "OK");
    public static final Status CREATED = new Status(201, "Created");
    public static final Status ACCEPTED = new Status(202, "Accepted");
    public static final Status NO_CONTENT = new Status(204, "No Content");
    public static final Status PARTIAL_CONTENT = new Status(206, "Partial Content");
    public static final Status MULTI_STATUS = new Status(207, "Multi-Status");

    public static final Status REDIRECT = new Status(301, "Moved Permanently");
    /**
     * Many user agents mishandle 302 in ways that violate the RFC1945 spec
     * (i.e., redirect a POST to a GET). 303 and 307 were added in RFC2616 to
     * address this. You should prefer 303 and 307 unless the calling user agent
     * does not support 303 and 307 functionality
     */
    @Deprecated
    public static final Status FOUND = new Status(302, "Found");
    public static final Status REDIRECT_SEE_OTHER = new Status(303, "See Other");
    public static final Status NOT_MODIFIED = new Status(304, "Not Modified");
    public static final Status TEMPORARY_REDIRECT = new Status(307, "Temporary Redirect");

    public static final Status BAD_REQUEST = new Status(400, "Bad Request");
    public static final Status UNAUTHORIZED = new Status(401, "Unauthorized");
    public static final Status FORBIDDEN = new Status(403, "Forbidden");
    public static final Status NOT_FOUND = new Status(404, "Not Found");
    public static final Status METHOD_NOT_ALLOWED = new Status(405, "Method Not Allowed");
    public static final Status NOT_ACCEPTABLE = new Status(406, "Not Acceptable");
    public static final Status REQUEST_TIMEOUT = new Status(408, "Request Timeout");
    public static final Status CONFLICT = new Status(409, "Conflict");
    public static final Status GONE = new Status(410, "Gone");
    public static final Status LENGTH_REQUIRED = new Status(411, "Length Required");
    public static final Status PRECONDITION_FAILED = new Status(412, "Precondition Failed");
    public static final Status PAYLOAD_TOO_LARGE = new Status(413, "Payload Too Large");
    public static final Status UNSUPPORTED_MEDIA_TYPE = new Status(415, "Unsupported Media Type");
    public static final Status RANGE_NOT_SATISFIABLE = new Status(416, "Requested Range Not Satisfiable");
    public static final Status EXPECTATION_FAILED = new Status(417, "Expectation Failed");
    public static final Status TOO_MANY_REQUESTS = new Status(429, "Too Many Requests");

    public static final Status INTERNAL_ERROR = new Status(500, "Internal Server Error");
    public static final Status NOT_IMPLEMENTED = new Status(501, "Not Implemented");
    public static final Status SERVICE_UNAVAILABLE = new Status(503, "Service Unavailable");
    public static final Status UNSUPPORTED_HTTP_VERSION = new Status(505, "HTTP Version Not Supported");

    private final int statusCode;
    private final String description;
    private final byte[] binary;

    public Status(int statusCode, String description) {
        this.statusCode = statusCode;
        this.description = description;
        this.binary = binary(statusCode, description);
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
