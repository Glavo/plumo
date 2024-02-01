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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class HttpHeaderField {

    public static HttpHeaderField of(String value) {
        int len = value.length(); // implicit null check
        if (len == 0) {
            throw new IllegalArgumentException("Header name must not be empty");
        }
        if (len > 80) {
            throw new IllegalArgumentException("Header name is too long");
        }

        byte[] bytes = new byte[len];

        for (int i = 0; i < len; i++) {
            char ch = value.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                bytes[i] = (byte) (ch | 0x20); // The oldest trick in the ASCII book
            } else if (Utils.isTokenPart(ch)) {
                bytes[i] = (byte) ch;
            } else {
                throw new IllegalArgumentException("Invalid header name: " + value);
            }
        }

        return new HttpHeaderField(bytes);
    }

    private final byte[] bytes;
    private int hash;

    private String string;

    private HttpHeaderField(byte[] bytes) {
        this.bytes = bytes;
    }

    @ApiStatus.Internal
    public void get(ByteBuffer target, int offset, int length) {
        target.put(bytes, offset, length);
    }

    public int length() {
        return bytes.length;
    }

    @Override
    public int hashCode() {
        int h = this.hash;
        if (h != 0) {
            return h;
        }

        h = Arrays.hashCode(bytes);
        if (h == 0) {
            h = 644954127; // magic number
        }
        return this.hash = h;
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
        return this.string = new String(bytes, StandardCharsets.US_ASCII);
    }
}
