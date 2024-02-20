/*
 * Copyright 2024 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.plumo.internal.util;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public abstract class InputWrapper extends InputStream implements ReadableByteChannel {
    public static InputWrapper nullInputWrapper() {
        return new InputWrapper() {
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
                if (len == 0) {
                    return 0;
                }
                ensureOpen();
                return -1;
            }

            @Override
            public int read(ByteBuffer dst) throws IOException {
                ensureOpen();
                return -1;
            }

            // @Override
            public byte[] readAllBytes() throws IOException {
                ensureOpen();
                return new byte[0];
            }

            // @Override
            public int readNBytes(byte[] b, int off, int len)
                    throws IOException {
                ensureOpen();
                return 0;
            }

            // @Override
            @SuppressWarnings("Since15")
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

            // @Override
            @SuppressWarnings("Since15")
            public void skipNBytes(long n) throws IOException {
                ensureOpen();
                if (n > 0) {
                    throw new EOFException();
                }
            }

            // @Override
            public long transferTo(OutputStream out) throws IOException {
                ensureOpen();
                return 0L;
            }

            @Override
            public void close() throws IOException {
                closed = true;
            }
        };
    }

    protected volatile boolean closed;

    protected void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    public ByteBuffer allocateTempByteBuffer(int capacity) {
        return ByteBuffer.allocate(capacity);
    }
}
