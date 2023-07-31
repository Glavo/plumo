package org.glavo.plumo.internal;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public final class IOUtils {
    private IOUtils() {
    }

    public static boolean isSeparator(byte ch) {
        switch (ch) {
            case '(':
            case ')':
            case '<':
            case '>':
            case '@':
            case ',':
            case ';':
            case ':':
            case '\\':
            case '"':
            case '/':
            case '[':
            case ']':
            case '?':
            case '=':
            case '{':
            case '}':
            case ' ':
            case '\t':
                return true;
            default:
                return false;
        }
    }

    public static boolean isTokenPart(byte ch) {
        return ch > 31 && ch < 127 && !IOUtils.isSeparator(ch);
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
