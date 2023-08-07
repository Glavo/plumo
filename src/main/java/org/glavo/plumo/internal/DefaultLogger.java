package org.glavo.plumo.internal;

import org.glavo.plumo.Plumo;

import java.io.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

public final class DefaultLogger implements Plumo.Logger {

    private final PrintStream out;

    private final StringWriter stackTraceBuffer = new StringWriter();
    private final PrintWriter stackTraceBufferWrapper = new PrintWriter(stackTraceBuffer);
    private final ReentrantLock stackTraceBufferLock = new ReentrantLock();

    public DefaultLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void log(Level level, String message, Throwable exception) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.formatTo(ZonedDateTime.now(), builder);
        builder.append("] ");

        builder.append(level).append(": ").append(message == null ? "" : message);

        if (exception != null) {
            builder.append('\n');

            stackTraceBufferLock.lock();
            try {
                exception.printStackTrace(stackTraceBufferWrapper);
                stackTraceBufferWrapper.flush();
                builder.append(stackTraceBuffer.getBuffer());
                stackTraceBuffer.getBuffer().setLength(0);
            } finally {
                stackTraceBufferLock.unlock();
            }
        }
        builder.append('\n');

        out.println(builder);
    }
}
