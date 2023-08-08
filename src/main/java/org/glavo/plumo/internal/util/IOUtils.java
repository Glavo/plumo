package org.glavo.plumo.internal.util;

import org.glavo.plumo.Plumo;
import org.glavo.plumo.internal.Constants;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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
            Plumo.LOGGER.log(Plumo.Logger.Level.ERROR, "Could not close", e);
        }
    }

    public static void deleteIfExists(Path file) {
        try {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (Throwable e) {
            Plumo.LOGGER.log(Plumo.Logger.Level.ERROR, "Could not delete file", e);
        }
    }

    public static void shutdown(Executor executor) {
        if (!(executor instanceof ExecutorService)) {
            return;
        }

        ExecutorService es = (ExecutorService) executor;
        boolean terminated = es.isTerminated();
        if (!terminated) {
            es.shutdown();
            boolean interrupted = false;
            while (!terminated) {
                try {
                    terminated = es.awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    if (!interrupted) {
                        es.shutdownNow();
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static InputStream nullInputStream() {
        return new InputStream() {
            private volatile boolean closed;

            private void ensureOpen() throws IOException {
                if (closed) {
                    throw new IOException("Stream closed");
                }
            }

            @Override
            public int available() throws IOException {
                ensureOpen();
                return 0;
            }

            @Override
            public int read() throws IOException {
                ensureOpen();
                return -1;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (off < 0 || len < 0 || off + len > b.length) {
                    throw new IndexOutOfBoundsException();
                }

                if (len == 0) {
                    return 0;
                }

                ensureOpen();

                return -1;
            }

            public byte[] readAllBytes() throws IOException {
                ensureOpen();
                return Constants.EMPTY_BYTE_ARRAY;
            }

            public int readNBytes(byte[] b, int off, int len)
                    throws IOException {
                Objects.checkFromIndexSize(off, len, b.length);
                ensureOpen();
                return 0;
            }

            public byte[] readNBytes(int len) throws IOException {
                if (len < 0) {
                    throw new IllegalArgumentException("len < 0");
                }
                ensureOpen();
                return new byte[0];
            }

            @Override
            public long skip(long n) throws IOException {
                ensureOpen();
                return 0L;
            }

            public void skipNBytes(long n) throws IOException {
                ensureOpen();
                if (n > 0) {
                    throw new EOFException();
                }
            }

            public long transferTo(OutputStream out) throws IOException {
                Objects.requireNonNull(out);
                ensureOpen();
                return 0L;
            }

            @Override
            public void close() throws IOException {
                closed = true;
            }
        };
    }
}
