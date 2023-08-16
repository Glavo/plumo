package org.glavo.plumo.internal.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public final class Utils {

    private static final boolean[] IS_SEPARATOR = new boolean[128];

    static {
        IS_SEPARATOR['('] = true;
        IS_SEPARATOR[')'] = true;
        IS_SEPARATOR['<'] = true;
        IS_SEPARATOR['>'] = true;
        IS_SEPARATOR['@'] = true;
        IS_SEPARATOR[','] = true;
        IS_SEPARATOR[';'] = true;
        IS_SEPARATOR[':'] = true;
        IS_SEPARATOR['\\'] = true;
        IS_SEPARATOR['"'] = true;
        IS_SEPARATOR['/'] = true;
        IS_SEPARATOR['['] = true;
        IS_SEPARATOR[']'] = true;
        IS_SEPARATOR['?'] = true;
        IS_SEPARATOR['='] = true;
        IS_SEPARATOR['{'] = true;
        IS_SEPARATOR['}'] = true;
        IS_SEPARATOR[' '] = true;
        IS_SEPARATOR['\t'] = true;
    }

    public static boolean isSeparator(int ch) {
        return ch >= '!' && ch <= '~' && IS_SEPARATOR[ch];
    }

    public static boolean isTokenPart(int ch) {
        return ch >= '!' && ch <= '~' && !IS_SEPARATOR[ch];
    }

    @SuppressWarnings("deprecation")
    public static String normalizeHttpHeaderFieldName(String str) {
        int len = str.length(); // implicit null check
        if (len == 0) {
            throw new IllegalArgumentException("Header name must not be empty");
        }
        if (len > 80) {
            throw new IllegalArgumentException("Header name is too long");
        }

        int i = 0;
        while (i < len) {
            char ch = str.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                break;
            } else if (isTokenPart(ch)) {
                i++;
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
            if (ch >= 'A' && ch <= 'Z') {
                buf[i] = (byte) (ch | 0x20); // The oldest trick in the ASCII book
            } else if (isTokenPart(ch)) {
                buf[i] = (byte) ch;
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
