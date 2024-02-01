package org.glavo.plumo.internal.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public final class OutputWrapper extends OutputStream implements WritableByteChannel {

    private final OutputStream outputStream;
    private final WritableByteChannel outputChannel;

    // assert outputChannel != null || (outputStream != null && buffer.hasArray())
    private final ByteBuffer buffer;
    private boolean closed = false;

    public OutputWrapper(OutputStream outputStream, int bufferSize) {
        this.outputStream = outputStream;
        this.outputChannel = null;
        this.buffer = ByteBuffer.allocate(bufferSize);
    }

    public OutputWrapper(WritableByteChannel outputChannel, int bufferSize) {
        this.outputStream = null;
        this.outputChannel = outputChannel;
        this.buffer = ByteBuffer.allocateDirect(bufferSize);
    }

    @Override
    public boolean isOpen() {
        return !closed;
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
                if (outputChannel != null) {
                    outputChannel.close();
                } else { // outputStream != null
                    outputStream.close();
                }
            } catch (Throwable closeException) {
                if (flushException != null && flushException != closeException) {
                    closeException.addSuppressed(flushException);
                }
                //noinspection ThrowFromFinallyBlock
                throw closeException;
            }
        }
    }

    private void flushBuffer() throws IOException {
        int position = buffer.position();
        if (position > 0) {
            if (outputChannel != null) {
                buffer.flip();

                int count = 0;
                do {
                    count += outputChannel.write(buffer);
                } while (count < position);
            } else {
                outputStream.write(buffer.array(), 0, position);
            }
            buffer.clear();
        }
    }

    public void flush() throws IOException {
        flushBuffer();
        if (outputStream != null) {
            outputStream.flush();
        }
    }

    @Override
    public void write(int b) throws IOException {
        buffer.put((byte) b);
        if (!buffer.hasRemaining()) {
            flushBuffer();
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        final int srcLen = src.remaining();
        final int bufRem = buffer.remaining();

        if (srcLen <= bufRem) {
            buffer.put(src);
            return srcLen;
        }

        int srcRem = srcLen;

        if (buffer.position() > 0) {
            int oldLimit = src.limit();
            src.limit(src.position() + bufRem);
            buffer.put(src);
            src.limit(oldLimit);

            flushBuffer();
            srcRem -= bufRem;
        }

        final int bufferSize = buffer.remaining();
        // assert bufferSize == buffer.capacity();

        if (srcRem < bufferSize) {
            buffer.put(src);
            return srcLen;
        }

        if (outputChannel != null) {
            int count = 0;

            do {
                count += outputChannel.write(src);
            } while (count < srcRem);

            return srcLen;
        }

        // assert outputStream != null;
        if (src.hasArray()) {
            int arrayOffset = src.arrayOffset();
            outputStream.write(src.array(), arrayOffset + src.position(), srcRem);
        } else {
            // assert buffer.position() == 0;

            byte[] array = buffer.array();

            while (srcRem > bufferSize) {
                src.get(array);
                outputStream.write(array);
                srcRem -= bufferSize;
            }

            buffer.put(src);
        }

        return srcLen;
    }
}
