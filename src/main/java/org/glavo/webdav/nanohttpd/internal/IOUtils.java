package org.glavo.webdav.nanohttpd.internal;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public final class IOUtils {
    private IOUtils() {
    }

    public static void safeClose(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            HttpServerImpl.LOG.log(Level.SEVERE, "Could not close", e);
        }
    }

    public static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            HttpServerImpl.LOG.log(Level.WARNING, "Could not delete file", e);
        }
    }
}
