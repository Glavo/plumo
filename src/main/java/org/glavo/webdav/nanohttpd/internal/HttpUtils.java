package org.glavo.webdav.nanohttpd.internal;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class HttpUtils {
    private HttpUtils() {
    }

    private static final DateTimeFormatter HTTP_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    public static String getHttpTime(Instant instant) {
        return HTTP_TIME_FORMATTER.format(instant);
    }
}
