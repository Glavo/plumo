package org.glavo.webdav.nanohttpd.internal;

import java.io.IOException;
import java.io.InputStream;

public class PrependableInputStream extends InputStream {
    private final InputStream in;

    private byte[] head;
    private int headOff;
    private int headRemaining;

    public PrependableInputStream(InputStream in) {
        this.in = in;
    }

    public void prepend(byte[] arr, int off, int len) {
        int headLen;

        if (head == null) {
            headLen = Math.max(len, 8192);
            head = new byte[headLen];
        } else {
            headLen = head.length;
        }

        if (headLen - headOff - headRemaining > len) {
            System.arraycopy(arr, off, head, headOff + headRemaining, len);
        } else {
            byte[] target;
            if (headLen - headRemaining > len) {
                target = head;
            } else {
                head = target = new byte[Math.max(headRemaining + len, headLen + (headLen >>> 1))];
            }

            System.arraycopy(head, headOff, target, 0, headRemaining);
            System.arraycopy(arr, off, target, headRemaining, len);
            headOff = 0;
        }

        headRemaining += len;
    }

    @Override
    public int read() throws IOException {
        if (headRemaining > 0) {
            int b = head[headOff] & 0xff;

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

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (headRemaining > 0) {
            int r = Math.min(headRemaining, len);
            System.arraycopy(head, headOff, b, off, r);

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
