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
package org.glavo.plumo.internal;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Constants {

    public static final byte[] CRLF = {'\r', '\n'};

    public static final byte[] HTTP_HEADER_SEPARATOR = {':', ' '};

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    public static final DateTimeFormatter HTTP_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    // Properties

    private static final String PROPERTY_PREFIX = "org.glavo.plumo.";

    private static Boolean getBoolean(String name, Boolean defaultValue) {
        String property = System.getProperty(name);
        if (property == null) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(property);
        }
    }

    // public static final boolean DEBUG = Boolean.getBoolean(PROPERTY_PREFIX + "debug");
    public static final int LINE_BUFFER_LENGTH = Integer.getInteger(PROPERTY_PREFIX + "lineBufferLength", 8192);
    public static final Boolean USE_VIRTUAL_THREAD = getBoolean(PROPERTY_PREFIX + "useVirtualThread", null);
    public static final String HEADER_ENCODING = System.getProperty(PROPERTY_PREFIX + "httpHeaderEncoding");
    public static final String LOGGER_LEVEL = System.getProperty(PROPERTY_PREFIX + "defaultLogger.level");

    static {
        if (LINE_BUFFER_LENGTH < 0) {
            throw new Error("line buffer length cannot be negative");
        } else if (LINE_BUFFER_LENGTH < 80) {
            throw new Error("line buffer length is too small");
        }
    }

    private Constants() {
    }
}
