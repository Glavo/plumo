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
package org.glavo.plumo.fileserver.internal;

public final class FileServerUtils {

    private static final boolean[] DONT_NEED_ENCODING = new boolean[128];

    static {
        for (int i = 'a'; i <= 'z'; i++) {
            DONT_NEED_ENCODING[i] = true;
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            DONT_NEED_ENCODING[i] = true;
        }
        for (int i = '0'; i <= '9'; i++) {
            DONT_NEED_ENCODING[i] = true;
        }
        // encoding a space to a + is done in the encode() method
        DONT_NEED_ENCODING[' '] = true;
        DONT_NEED_ENCODING['-'] = true;
        DONT_NEED_ENCODING['_'] = true;
        DONT_NEED_ENCODING['.'] = true;
        DONT_NEED_ENCODING['*'] = true;


        DONT_NEED_ENCODING['/'] = true;
    }

    private static void encodeByte(StringBuilder out, byte b) {
        out.append('%');

        int n0 = (b >> 4) & 0xF;
        if (n0 < 10) {
            out.append((char) ('0' + n0));
        } else {
            out.append((char) ('A' - 10 + n0));
        }

        int n1 = b & 0xF;
        if (n1 < 10) {
            out.append((char) ('0' + n1));
        } else {
            out.append((char) ('A' - 10 + n1));
        }
    }

    public static void encodeURL(StringBuilder out, String s) {
        int i;
        for (i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 128 || !DONT_NEED_ENCODING[c] || c == ' ') {
                break;
            }
        }

        out.append(s, 0, i);

        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                if (DONT_NEED_ENCODING[c]) {
                    if (c == ' ') {
                        c = '+';
                    }
                    out.append(c);
                } else {
                    encodeByte(out, (byte) c);
                }
            } else if (c < 0x800) {
                encodeByte(out, (byte) (0xc0 | (c >> 6)));
                encodeByte(out, (byte) (0x80 | (c & 0x3f)));
            } else if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i < s.length() - 1) {
                    char d = s.charAt(i + 1);
                    if (Character.isLowSurrogate(d)) {
                        int uc = Character.toCodePoint(c, d);
                        encodeByte(out, (byte) (0xf0 | ((uc >> 18))));
                        encodeByte(out, (byte) (0x80 | ((uc >> 12) & 0x3f)));
                        encodeByte(out, (byte) (0x80 | ((uc >> 6) & 0x3f)));
                        encodeByte(out, (byte) (0x80 | (uc & 0x3f)));
                        i++;
                        continue;
                    }
                }

                // Replace unmappable characters
                encodeByte(out, (byte) '?');
            } else {
                encodeByte(out, (byte) (0xe0 | ((c >> 12))));
                encodeByte(out, (byte) (0x80 | ((c >> 6) & 0x3f)));
                encodeByte(out, (byte) (0x80 | (c & 0x3f)));
            }
        }
    }
}
