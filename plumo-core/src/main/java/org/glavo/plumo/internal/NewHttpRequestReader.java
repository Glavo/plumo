package org.glavo.plumo.internal;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class NewHttpRequestReader implements Closeable {
    private static final Charset HEADER_ENCODING = StandardCharsets.UTF_8;

    private final InputStream inputStream;
    private final ReadableByteChannel inputChannel;

    private final ByteBuffer lineBuffer;

    private boolean closed = false;

    public NewHttpRequestReader(InputStream inputStream) {
        this.inputStream = inputStream;
        this.inputChannel = null;
        this.lineBuffer = ByteBuffer.allocate(Constants.LINE_BUFFER_LENGTH).limit(0);
    }

    public NewHttpRequestReader(ReadableByteChannel inputChannel) {
        this.inputStream = null;
        this.inputChannel = inputChannel;
        this.lineBuffer = ByteBuffer.allocateDirect(Constants.LINE_BUFFER_LENGTH).limit(0);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        lineBuffer.position(0).limit(0);
        if (inputChannel != null) {
            inputChannel.close();
        } else {
            inputStream.close();
        }
    }
}
