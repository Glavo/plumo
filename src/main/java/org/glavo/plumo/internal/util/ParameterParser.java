package org.glavo.plumo.internal.util;

import org.glavo.plumo.internal.util.IOUtils;
import org.glavo.plumo.internal.util.Pair;

public final class ParameterParser {
    private final String input;
    private int offset;

    public ParameterParser(String input, int offset) {
        this.input = input;
        this.offset = offset;
    }

    private void skipInvalid() {
        int i = input.indexOf(';');
        offset = i < 0 ? input.length() : i + 1;
    }

    public Pair<String, String> nextParameter() {
        while (offset < input.length()) {
            char ch = input.charAt(offset);
            if (ch == ' ' || ch == '\t') {
                offset++;
                continue;
            }

            if (ch > 127 || !IOUtils.isTokenPart((byte) ch)) {
                skipInvalid();
                continue;
            }

            StringBuilder builder = new StringBuilder();
            builder.append(ch);

            offset++;
            while (offset < input.length()) {
                ch = input.charAt(offset);
                if (ch < 127 && IOUtils.isTokenPart((byte) ch)) {
                    builder.append(ch);
                    offset++;
                } else {
                    break;
                }
            }

            if (ch != '=') {
                skipInvalid();
                continue;
            }

            offset++;

            String key = builder.toString();
            String value;

            if (offset < input.length()) {
                ch = input.charAt(offset);
                if (ch == '"') {
                    offset++;
                    builder.setLength(0);

                    boolean closed = false;
                    while (offset < input.length()) {
                        ch = input.charAt(offset++);

                        if (ch == '"') {
                            closed = true;
                            break;
                        } else if (ch == '\\' && offset < input.length()) {
                            builder.append(input.charAt(offset++));
                        } else {
                            builder.append(ch);
                        }
                    }

                    // unable to determine the boundary of the quoted string
                    if (!closed) {
                        offset = input.length();
                        return null;
                    }

                    value = builder.toString();
                } else {
                    int i = input.indexOf(';', offset);
                    if (i < 0) {
                        i = input.length();
                    }

                    value = input.substring(offset, i).trim();
                    offset = i + 1;
                }
                return new Pair<>(key, value);
            } else {
                value = "";
            }
        }

        return null;
    }
}
