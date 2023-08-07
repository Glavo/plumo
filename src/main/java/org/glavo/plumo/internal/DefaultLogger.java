package org.glavo.plumo.internal;

import org.glavo.plumo.Plumo;

import java.io.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class DefaultLogger implements Plumo.Logger {

    private final PrintStream out;

    public DefaultLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void log(Level level, String message, Throwable exception) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.formatTo(ZonedDateTime.now(), builder);
        builder.append('/').append(level).append("] ").append(message == null ? "" : message);

        if (exception != null) {
            builder.append('\n');

            StringWriter sw = new StringWriter();
            try (PrintWriter pw = new PrintWriter(sw)) {
                exception.printStackTrace(pw);
            }
            builder.append(sw.getBuffer());
        }

        out.println(builder);
    }
}
