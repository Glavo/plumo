package org.glavo.webdav.nanohttpd.internal;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public class UnsyncBufferedOutputStream extends OutputStream {

    private final OutputStream out;
    private final byte[] buf;
    private int count;
    private boolean closed = false;

    public UnsyncBufferedOutputStream(OutputStream out, int bufferSize) {
        this.out = out;
        this.buf = new byte[bufferSize];
    }

    private void flushBuffer() throws IOException {
        out.write(buf, 0, count);
        count = 0;
    }

    @Override
    public void write(int b) throws IOException {
        if (count >= buf.length) {
            flushBuffer();
        }

        buf[count++] = (byte) b;
    }

    @Override
    public void write(byte @NotNull [] b, int off, int len) throws IOException {
        int bufRem = buf.length - count;

        if (bufRem >= len) {
            System.arraycopy(b, off, buf, count, len);
            count += len;
            return;
        }

        if (count > 0) {
            System.arraycopy(b, off, buf, count, bufRem);
            count += bufRem;
            off += bufRem;
            len -= bufRem;
            flushBuffer();
        }

        if (len < buf.length) {
            System.arraycopy(b, off, buf, 0, len);
            count = len;
        } else {
            out.write(b, off, len);
        }
    }

    private static boolean isASCII(String str, int off, int len) {
        while (off < len) {
            if (str.charAt(off++) >= 128) {
                return false;
            }
        }

        return true;
    }

    public void writeASCII(String str) throws IOException {
        writeASCII(str, 0, str.length());
    }

    @SuppressWarnings("deprecation")
    public void writeASCII(String str, int off, int len) throws IOException {
        assert isASCII(str, off, len);

        int bufRem = buf.length - count;

        if (bufRem >= len) {
            str.getBytes(off, off + len, buf, count);
            count += len;
            return;
        }

        while (len > 0) {
            if (bufRem == 0) {
                flushBuffer();
                bufRem = buf.length;
            }

            int num = Integer.min(bufRem, len);

            str.getBytes(off, off + num, buf, count);

            off += num;
            len -= num;
            count += num;
            bufRem -= num;
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        Throwable flushException = null;
        try {
            flush();
        } catch (Throwable e) {
            flushException = e;
            throw e;
        } finally {
            try {
                out.close();
            } catch (Throwable closeException) {
                if (flushException != null && flushException != closeException) {
                    closeException.addSuppressed(flushException);
                }
                //noinspection ThrowFromFinallyBlock
                throw closeException;
            }
        }
    }
}
