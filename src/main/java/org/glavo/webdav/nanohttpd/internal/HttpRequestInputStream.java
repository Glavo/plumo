package org.glavo.webdav.nanohttpd.internal;

import java.io.IOException;
import java.io.InputStream;

public class HttpRequestInputStream extends InputStream {

    private final byte[] headBuf = new byte[HttpSessionImpl.HEADER_BUFFER_SIZE];
    private int headOff;
    private int headRemaining;

    private final InputStream in;

    public HttpRequestInputStream(InputStream in) {
        this.in = in;
    }

    public byte[] getHeaderBuffer() {
        return headBuf;
    }

    public int readHead() throws IOException {
        int read = headRemaining;
        if (headOff > 0) {
            System.arraycopy(headBuf, headOff, headBuf, 0, read);
            headOff = 0;
        } else {
            read = read(headBuf, 0, HttpSessionImpl.HEADER_BUFFER_SIZE);
        }
        return read;
    }

    public void setBufRemaining(int off, int len) {
        if (len > 0) {
            this.headOff = off;
            this.headRemaining = len;
        } else {
            this.headOff = 0;
            this.headRemaining = 0;
        }
    }

    @Override
    public int read() throws IOException {
        if (headRemaining > 0) {
            int b = headBuf[headOff] & 0xff;

            headRemaining--;

            if (headRemaining == 0) {
                headOff = 0;
            } else {
                headOff++;
            }

            return b;
        }
        return in.read();
    }

    public int readRaw(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (headRemaining > 0) {
            int r = Math.min(headRemaining, len);
            System.arraycopy(headBuf, headOff, b, off, r);

            headRemaining -= r;

            if (headRemaining == 0) {
                headOff = 0;
            }

            return r;
        }

        return in.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
