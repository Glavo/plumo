package org.glavo.plumo.internal.util;

import org.glavo.plumo.internal.HttpListener;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public final class IOUtils {
    private IOUtils() {
    }

    public static boolean isSeparator(int ch) {
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

    public static boolean isTokenPart(int ch) {
        return ch > 31 && ch < 127 && !IOUtils.isSeparator(ch);
    }

    public static void safeClose(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            HttpListener.LOG.log(Level.SEVERE, "Could not close", e);
        }
    }

    public static void deleteIfExists(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            HttpListener.LOG.log(Level.WARNING, "Could not delete file", e);
        }
    }
}
