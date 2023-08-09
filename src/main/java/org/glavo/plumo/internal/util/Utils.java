package org.glavo.plumo.internal.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public final class Utils {

    @SuppressWarnings("deprecation")
    public static String normalizeHttpHeaderFieldName(String str) {
        int len = str.length(); // implicit null check
        if (len == 0) {
            throw new IllegalArgumentException("Header name must not be empty");
        }

        int i = 0;
        while (i < len) {
            char ch = str.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_') {
                i++;
            } else if (ch >= 'A' && ch <= 'Z') {
                break;
            } else {
                throw new IllegalArgumentException("Invalid header name: " + str);
            }
        }

        if (i == len) {
            return str;
        }

        byte[] buf = new byte[len];
        if (i > 0) {
            str.getBytes(0, i, buf, 0);
        }

        while (i < len) {
            char ch = str.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_') {
                buf[i] = (byte) ch;
            } else if (ch >= 'A' && ch <= 'Z') {
                buf[i] = (byte) (ch | 0x20); // The oldest trick in the ASCII book
            } else {
                throw new IllegalArgumentException("Invalid header name: " + str);
            }
            i++;
        }

        return new String(buf, ISO_8859_1);
    }

    private Utils() {
    }
}
