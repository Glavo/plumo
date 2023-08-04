package org.glavo.plumo.internal.util;

import java.util.AbstractMap;

public final class ParameterParser {
    private final String input;
    private int offset;
    private final int limit;

    private final char separator;

    public ParameterParser(String input, char separator) {
        this(input, 0, separator);
    }

    public ParameterParser(String input, int offset, char separator) {
        assert separator == ';' || separator == ',';

        this.input = input;
        this.offset = offset;
        this.limit = input.length();
        this.separator = separator;
    }

    private void skipInvalid() {
        int i = input.indexOf(separator, offset);
        offset = i < 0 ? limit : i + 1;
    }

    public AbstractMap.SimpleImmutableEntry<String, String> nextParameter() {
        return nextParameter(true);
    }

    public AbstractMap.SimpleImmutableEntry<String, String> nextParameter(boolean allowNullValue) {
        while (offset < limit) {
            char ch = input.charAt(offset);

            if (ch == ' ' || ch == '\t') {
                offset++;
                continue;
            }

            if (!IOUtils.isTokenPart(ch)) {
                skipInvalid();
                continue;
            }

            int tokenStart = offset;
            while (offset < limit) {
                ch = input.charAt(offset);
                if (IOUtils.isTokenPart(ch)) {
                    offset++;
                } else {
                    break;
                }
            }

            String key = input.substring(tokenStart, offset);
            if (ch != '=') {
                skipInvalid();
                if (allowNullValue) {
                    return new AbstractMap.SimpleImmutableEntry<>(key, null);
                } else {
                    continue;
                }
            }
            offset++;

            String value;
            if (offset < limit) {
                ch = input.charAt(offset);
                if (ch == '"') {
                    offset++;

                    StringBuilder builder = new StringBuilder();
                    boolean closed = false;
                    while (offset < limit) {
                        ch = input.charAt(offset++);

                        if (ch == '"') {
                            closed = true;
                            break;
                        } else if (ch == '\\' && offset < limit) {
                            builder.append(input.charAt(offset++));
                        } else {
                            builder.append(ch);
                        }
                    }

                    // unable to determine the boundary of the quoted string
                    if (!closed) {
                        offset = limit;
                        return null;
                    }

                    value = builder.toString();
                } else {
                    int i = input.indexOf(separator, offset);
                    if (i < 0) {
                        value = input.substring(offset).trim();
                        offset = limit;
                    } else {
                        value = input.substring(offset, i).trim();
                        offset = i + 1;
                    }
                }
            } else {
                value = "";
            }

            return new AbstractMap.SimpleImmutableEntry<>(key, value);
        }

        return null;
    }
}
