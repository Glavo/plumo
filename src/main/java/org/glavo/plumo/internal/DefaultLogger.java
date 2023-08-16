package org.glavo.plumo.internal;

import java.io.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class DefaultLogger {
    public enum Level {
        ALL(Integer.MIN_VALUE),
        TRACE(400),
        DEBUG(500),
        INFO(800),
        WARNING(900),
        ERROR(1000),
        OFF(Integer.MAX_VALUE);

        private final int severity;

        Level(int severity) {
            this.severity = severity;
        }

        public int getSeverity() {
            return severity;
        }
    }

    public static void log(Level level, String message) {
        log(level, message, null);
    }

    public static void log(Level level, String message, Throwable exception) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.formatTo(ZonedDateTime.now(), builder);
        builder.append(']');

        Thread thread = Thread.currentThread();
        String threadName = Thread.currentThread().getName();
        if (threadName == null || threadName.isEmpty()) {
            threadName = thread.toString();
        }

        builder.append(" [").append(threadName).append('/').append(level).append("] ");

        builder.append(message == null ? "" : message);

        if (exception != null) {
            builder.append('\n');

            StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                exception.printStackTrace(pw);
            }
            builder.append(sw.getBuffer());
        }

        System.out.println(builder);
    }
}
