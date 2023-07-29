package org.glavo.webdav.nanohttpd.internal;

import java.io.Closeable;
import java.io.IOException;
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
}
