/*
 * Copyright 2024 Glavo
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

import org.glavo.plumo.internal.util.Utils;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class HttpHeaderField {

    public static final HttpHeaderField ACCEPT_ENCODING = ofTrusted("accept-encoding");
    public static final HttpHeaderField ALLOW = ofTrusted("allow");
    public static final HttpHeaderField DATE = ofTrusted("date");
    public static final HttpHeaderField CONNECTION = ofTrusted("connection");
    public static final HttpHeaderField CONTENT_ENCODING = ofTrusted("content-encoding");
    public static final HttpHeaderField CONTENT_LENGTH = ofTrusted("content-length");
    public static final HttpHeaderField CONTENT_RANGE = ofTrusted("content-range");
    public static final HttpHeaderField CONTENT_TYPE = ofTrusted("content-type");
    public static final HttpHeaderField IF_MODIFIED_SINCE = ofTrusted("if-modified-since");
    public static final HttpHeaderField LAST_MODIFIED = ofTrusted("last-modified");
    public static final HttpHeaderField LOCATION = ofTrusted("location");
    public static final HttpHeaderField HOST = ofTrusted("host");
    public static final HttpHeaderField RANGE = ofTrusted("range");
    public static final HttpHeaderField TRANSFER_ENCODING = ofTrusted("transfer-encoding");

    private static HttpHeaderField ofTrusted(String value) {
        return new HttpHeaderField(value.getBytes(StandardCharsets.ISO_8859_1), value);
    }

    public static HttpHeaderField of(String value) {
        int len = value.length(); // implicit null check
        if (len == 0) {
            throw new IllegalArgumentException("Header name must not be empty");
        }
        if (len > 80) {
            throw new IllegalArgumentException("Header name is too long");
        }

        byte[] bytes = new byte[len];
        boolean qualified = true;

        for (int i = 0; i < len; i++) {
            char ch = value.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                qualified = false;
                bytes[i] = (byte) (ch | 0x20); // The oldest trick in the ASCII book
            } else if (Utils.isTokenPart(ch)) {
                bytes[i] = (byte) ch;
            } else {
                throw new IllegalArgumentException("Invalid header name: " + value);
            }
        }

        HttpHeaderField field = new HttpHeaderField(bytes);
        if (qualified) {
            field.string = value;
        }
        return field;
    }

    private final byte[] bytes;
    private final int hashCode;

    private String string;

    private HttpHeaderField(byte[] bytes) {
        this(bytes, null);
    }

    private HttpHeaderField(byte[] bytes, String string) {
        this.bytes = bytes;
        this.string = string;
        this.hashCode = Arrays.hashCode(bytes);
    }

    @ApiStatus.Internal
    public void writeTo(OutputStream output) throws IOException {
        output.write(bytes);
    }

    public int length() {
        return bytes.length;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof HttpHeaderField && Arrays.equals(this.bytes, ((HttpHeaderField) obj).bytes);
    }

    @Override
    public String toString() {
        String s = this.string;
        if (s != null) {
            return s;
        }
        return this.string = new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
