package org.glavo.plumo.internal;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Constants {

    public static final byte[] CRLF = {'\r', '\n'};

    public static final byte[] HTTP_HEADER_SEPARATOR = {':', ' '};

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    public static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    public static final String CONTENT_DISPOSITION_REGEX = "([ |\t]*Content-Disposition[ |\t]*:)(.*)";

    public static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile(CONTENT_DISPOSITION_REGEX, Pattern.CASE_INSENSITIVE);

    public static final String CONTENT_TYPE_REGEX = "([ |\t]*content-type[ |\t]*:)(.*)";

    public static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(CONTENT_TYPE_REGEX, Pattern.CASE_INSENSITIVE);

    public static final String CONTENT_DISPOSITION_ATTRIBUTE_REGEX = "[ |\t]*([a-zA-Z]*)[ |\t]*=[ |\t]*['|\"]([^\"^']*)['|\"]";

    public static final Pattern CONTENT_DISPOSITION_ATTRIBUTE_PATTERN = Pattern.compile(CONTENT_DISPOSITION_ATTRIBUTE_REGEX);

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

    public static final boolean DEBUG = Boolean.getBoolean(PROPERTY_PREFIX + "debug");

    public static final int LINE_BUFFER_LENGTH = Integer.getInteger(PROPERTY_PREFIX + "lineBufferLength", 8192);

    public static final int SOCKET_TIMEOUT = Integer.getInteger(PROPERTY_PREFIX + "socketTimeout", 5000);

    public static final Boolean USE_VIRTUAL_THREAD = getBoolean(PROPERTY_PREFIX + "useVirtualThread", null);

    static {
        if (LINE_BUFFER_LENGTH < 0) {
            throw new Error("line buffer length cannot be negative");
        } else if (LINE_BUFFER_LENGTH <= 2) {
            throw new Error("line buffer length is too small");
        }
    }

    private Constants() {
    }
}
