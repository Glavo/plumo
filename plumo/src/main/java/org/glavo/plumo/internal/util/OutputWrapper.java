package org.glavo.plumo.internal.util;

import org.glavo.plumo.HttpHeaderField;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.internal.Constants;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

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

        if (deflater != null) {
            deflater.end();
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
    public void write(byte[] src, int off, int len) throws IOException {
        int bufRem = buffer.remaining();

        if (len <= bufRem) {
            buffer.put(src, off, len);
            if (len == bufRem) {
                flushBuffer();
            }
            return;
        }

        int srcRem = len;

        if (buffer.position() > 0) {
            buffer.put(src, off, off + bufRem);

            flushBuffer();

            srcRem -= bufRem;
            off += bufRem;
        }

        final int bufferSize = buffer.capacity();
        // assert bufferSize == buffer.remaining();

        if (srcRem < bufferSize) {
            buffer.put(src, off, srcRem);
            return;
        }

        if (outputChannel != null) {
            ByteBuffer srcBuffer = ByteBuffer.wrap(src, off, srcRem);
            int count = 0;
            do {
                count += outputChannel.write(srcBuffer);
            } while (count < srcRem);
        } else {
            outputStream.write(src, off, srcRem);
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        final int srcLen = src.remaining();
        int bufRem = buffer.remaining();

        if (srcLen <= bufRem) {
            buffer.put(src);
            if (srcLen == bufRem) {
                flushBuffer();
            }
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

        final int bufferSize = buffer.capacity();
        // assert bufferSize == buffer.remaining();

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
        } else {
            if (src.hasArray()) {
                outputStream.write(src.array(), src.arrayOffset() + src.position(), srcRem);
            } else {
                // assert buffer.position() == 0;

                byte[] array = buffer.array();

                while (srcRem >= bufferSize) {
                    src.get(array);
                    outputStream.write(array);
                    srcRem -= bufferSize;
                }

                buffer.put(src);
            }
        }

        return srcLen;
    }

    public void writeASCII(String string) throws IOException {
        writeASCII(string, 0, string.length());
    }

    public void writeASCII(String string, int off, int len) throws IOException {
        for (int i = off, end = off + len; i < end; i++) {
            char ch = string.charAt(i);
            if (ch > 128) {
                throw new IOException("Non-ASCII character: " + ch);
            }

            write(ch);
        }
    }

    public void writeStatus(HttpResponse.Status status) throws IOException {
        status.writeTo(this);
    }

    public void writeCRLF() throws IOException {
        write(Constants.CRLF, 0, 2);
    }

    public void writeHttpHeader(HttpHeaderField field, String value) throws IOException {
        field.writeTo(this);
        write(Constants.HTTP_HEADER_SEPARATOR);
        writeASCII(value);
        writeCRLF();
    }

    public void transferFrom(ReadableByteChannel input) throws IOException {
        while (input.read(buffer) > 0) {
            if (!buffer.hasRemaining()) {
                flushBuffer();
            }
        }
    }

    private static final byte[] CHUNKED_FINISH = {'0', '\r', '\n', '\r', '\n'};

    public void transferChunkedFrom(ReadableByteChannel input) throws IOException {
        flushBuffer();

        int read;
        while ((read = input.read(buffer)) > 0) {
            if (outputChannel != null) {
                Utils.writeFully(outputChannel, ByteBuffer.wrap(Integer.toHexString(read).getBytes(StandardCharsets.ISO_8859_1)));
                Utils.writeFully(outputChannel, ByteBuffer.wrap(Constants.CRLF));
                flushBuffer();
                Utils.writeFully(outputChannel, ByteBuffer.wrap(Constants.CRLF));
            } else {
                outputStream.write(Integer.toHexString(read).getBytes(StandardCharsets.ISO_8859_1));
                outputStream.write(Constants.CRLF);
                flushBuffer();
                outputStream.write(Constants.CRLF);
            }
        }
        write(CHUNKED_FINISH);
    }

    private static final byte[] GZIP_HEADER = new byte[]{0x1f, (byte) 0x8b, 0x08, 0, 0, 0, 0, 0, 0, (byte) 0xff};

    private Deflater deflater;
    private CRC32 crc32;
    private ByteBuffer gzipReadBuffer;
    private byte[] gzipWriteBuffer;

    private void initDeflateContext() {
        if (deflater == null) {
            deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            crc32 = new CRC32();
            gzipReadBuffer = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
            gzipWriteBuffer = new byte[512];
        } else {
            deflater.reset();
            gzipReadBuffer.clear();
            crc32.reset();
        }
    }

    private void writeChunk(byte[] array, int offset, int length) throws IOException {
        write(Integer.toHexString(length).getBytes(StandardCharsets.ISO_8859_1));
        write(Constants.CRLF);
        write(array, offset, length);
        write(Constants.CRLF);
    }

    public void transferGZipFrom(ReadableByteChannel input) throws IOException {
        initDeflateContext();

        writeChunk(GZIP_HEADER, 0, GZIP_HEADER.length);
        while (input.read(gzipReadBuffer) > 0) {
            gzipReadBuffer.flip();

            deflater.setInput(gzipReadBuffer.array(), 0, gzipReadBuffer.limit());
            crc32.update(gzipReadBuffer.array(), 0, gzipReadBuffer.limit());
            while (!deflater.needsInput()) {
                int len = deflater.deflate(gzipWriteBuffer, 0, gzipWriteBuffer.length);
                if (len > 0) {
                    writeChunk(gzipWriteBuffer, 0, len);
                }
            }

            gzipReadBuffer.clear();
        }

        deflater.finish();
        while (!deflater.finished()) {
            int len = deflater.deflate(gzipWriteBuffer, 0, gzipWriteBuffer.length);
            if (len > 0) {
                writeChunk(gzipWriteBuffer, 0, len);
            }
        }

        write('8');
        write(Constants.CRLF);
        gzipReadBuffer.putInt(0, (int) crc32.getValue());
        gzipReadBuffer.putInt(4, deflater.getTotalIn());
        write(gzipReadBuffer.array(), 0, 8);
        write(Constants.CRLF);

        write(CHUNKED_FINISH);
    }

    public void transferGZipFrom(byte[] input, int offset, int length) throws IOException {
        initDeflateContext();

        writeChunk(GZIP_HEADER, 0, GZIP_HEADER.length);
        deflater.setInput(input, offset, length);
        crc32.update(input, offset, length);
        deflater.finish();

        while (!deflater.finished()) {
            int len = deflater.deflate(gzipWriteBuffer, 0, gzipWriteBuffer.length);
            if (len > 0) {
                writeChunk(gzipWriteBuffer, 0, len);
            }
        }

        write('8');
        write(Constants.CRLF);
        gzipReadBuffer.putInt(0, (int) crc32.getValue());
        gzipReadBuffer.putInt(4, deflater.getTotalIn());
        write(gzipReadBuffer.array(), 0, 8);
        write(Constants.CRLF);

        write(CHUNKED_FINISH);
    }

}
