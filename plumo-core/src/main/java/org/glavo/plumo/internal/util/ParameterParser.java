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
package org.glavo.plumo.internal.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Map;

public final class ParameterParser {

    public static Charset getEncoding(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }

        int idx = contentType.indexOf(';');
        if (idx < 0 || idx >= contentType.length() - 1) {
            return StandardCharsets.UTF_8;
        }

        ParameterParser parser = new ParameterParser(contentType, idx + 1, ';');

        Map.Entry<String, String> next;
        while ((next = parser.nextParameter(false)) != null) {
            if ("charset".equalsIgnoreCase(next.getKey())) {
                String charsetName = next.getValue();

                try {
                    return Charset.forName(charsetName);
                } catch (Throwable ignored) {
                    break; // unknown encoding
                }
            }
        }

        return StandardCharsets.UTF_8;
    }

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

            if (!Utils.isTokenPart(ch)) {
                skipInvalid();
                continue;
            }

            int tokenStart = offset;
            while (offset < limit) {
                ch = input.charAt(offset);
                if (Utils.isTokenPart(ch)) {
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
