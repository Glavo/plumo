package org.glavo.plumo.internal.util;

import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

public final class OutputWrapperTest {

    @FunctionalInterface
    private interface Action {
        void accept(OutputWrapper output) throws IOException;
    }

    private static void assertResult(byte[] expected, int bufferSize, Action action) throws IOException {
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (OutputWrapper output = new OutputWrapper(out, bufferSize)) {
                action.accept(output);
            }
            assertArrayEquals(expected, out.toByteArray());
        }

        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (OutputWrapper output = new OutputWrapper(Channels.newChannel(out), bufferSize)) {
                action.accept(output);
            }
            assertArrayEquals(expected, out.toByteArray());
        }

    }

    @Test
    public void testWriteByte() throws IOException {
        byte[] arr = new byte[]{0, 1, 2, 3, 4, 5};

        assertResult(arr, 4, output -> {
            for (byte b : arr) {
                output.write(b);
            }
        });
    }

    @Test
    public void testWriteByteArray() throws IOException {
        byte[] res = new byte[32 * 1024];
        for (int i = 0; i < res.length; i++) {
            res[i] = (byte) i;
        }

        assertResult(res, 4, output -> {
            Random random = new Random(0);

            int n = 0;
            do {
                byte[] arr = new byte[random.nextInt(Integer.min(res.length - n + 1, 12))];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = (byte) n++;
                }
                output.write(arr);
            } while (n < res.length);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testWriteByteBuffer(boolean direct) throws IOException {
        byte[] res = new byte[32 * 1024];
        for (int i = 0; i < res.length; i++) {
            res[i] = (byte) i;
        }

        assertResult(res, 4, output -> {
            Random random = new Random(0);

            int n = 0;
            do {
                int bufferSize = random.nextInt(Integer.min(res.length - n + 1, 12));

                ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(bufferSize) : ByteBuffer.allocate(bufferSize);
                while (buffer.hasRemaining()) {
                    buffer.put((byte) n++);
                }
                buffer.flip();
                output.write(buffer);
            } while (n < res.length);
        });
    }


    public static List<Arguments> testTransferFromArguments() {
        List<Arguments> arguments = new ArrayList<>();

        for (int seed = 0; seed < 4; seed++) {
            for (int length: new int[] {0, 10, 8192}) {
                Random random = new Random(seed);
                byte[] data = new byte[length];
                random.nextBytes(data);

                arguments.add(Arguments.of(seed, length, data, true));
                arguments.add(Arguments.of(seed, length, data, false));
            }
        }

        return arguments;
    }

    @ParameterizedTest
    @MethodSource("testTransferFromArguments")
    public void testTransferFrom(int seed, int length, byte[] data, boolean channel) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        try (OutputWrapper output = channel ? new OutputWrapper(Channels.newChannel(ba), 512) : new OutputWrapper(ba, 512)) {
            output.transferFrom(Channels.newChannel(new ByteArrayInputStream(data)));
        }

        assertArrayEquals(data, ba.toByteArray());
    }

    @ParameterizedTest
    @MethodSource("testTransferFromArguments")
    public void testTransferChunkedFrom(int seed, int length, byte[] data, boolean channel) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        try (OutputWrapper output = channel ? new OutputWrapper(Channels.newChannel(ba), 512) : new OutputWrapper(ba, 512)) {
            output.transferChunkedFrom(Channels.newChannel(new ByteArrayInputStream(data)));
        }

        SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8192);
        inputBuffer.bind(new ByteArrayInputStream(ba.toByteArray()));

        assertArrayEquals(data, new ChunkedInputStream(inputBuffer).readAllBytes());
    }

    @ParameterizedTest
    @MethodSource("testTransferFromArguments")
    public void testTransferGZipFrom(int seed, int length, byte[] data, boolean channel) throws IOException {
        {
            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            try (OutputWrapper output = channel ? new OutputWrapper(Channels.newChannel(ba), 512) : new OutputWrapper(ba, 512)) {
                output.transferGZipFrom(Channels.newChannel(new ByteArrayInputStream(data)));
            }

            SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8192);
            inputBuffer.bind(new ByteArrayInputStream(ba.toByteArray()));

            try (InputStream input = new GZIPInputStream(new ChunkedInputStream(inputBuffer))) {
                assertArrayEquals(data, input.readAllBytes());
            }
        }


        {
            ByteArrayOutputStream ba = new ByteArrayOutputStream();
            try (OutputWrapper output = channel ? new OutputWrapper(Channels.newChannel(ba), 512) : new OutputWrapper(ba, 512)) {
                output.transferGZipFrom(data, 0, length);
            }

            SessionInputBufferImpl inputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8192);
            inputBuffer.bind(new ByteArrayInputStream(ba.toByteArray()));

            try (InputStream input = new GZIPInputStream(new ChunkedInputStream(inputBuffer))) {
                assertArrayEquals(data, input.readAllBytes());
            }
        }
    }
}
