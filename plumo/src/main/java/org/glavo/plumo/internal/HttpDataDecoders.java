/*
 * Copyright 2023 Glavo
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
package org.glavo.plumo.internal;

import org.glavo.plumo.HttpDataDecoder;
import org.glavo.plumo.HttpHeaderField;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.internal.util.InputWrapper;
import org.glavo.plumo.internal.util.ParameterParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;

public final class HttpDataDecoders {

    public static final HttpDataDecoder<InputStream, Object, RuntimeException> INPUT_STREAM = new HttpDataDecoder<InputStream, Object, RuntimeException>() {
        @Override
        public InputStream decode(HttpRequest request, InputWrapper input, Object arg) {
            return input != null ? input : InputWrapper.nullInputWrapper();
        }

        @Override
        public String toString() {
            return "HttpDataDecoder.INPUT_STREAM";
        }
    };

    public static final HttpDataDecoder<ReadableByteChannel, Object, RuntimeException> BYTE_CHANNEL = new HttpDataDecoder<ReadableByteChannel, Object, RuntimeException>() {
        @Override
        public ReadableByteChannel decode(HttpRequest request, InputWrapper input, Object arg) {
            return input != null ? input : InputWrapper.nullInputWrapper();
        }

        @Override
        public String toString() {
            return "HttpDataDecoder.BYTE_CHANNEL";
        }
    };


    public static final HttpDataDecoder<String, Object, IOException> TEXT = new HttpDataDecoder<String, Object, IOException>() {
        @Override
        public String decode(HttpRequest response, InputWrapper input, Object arg) throws IOException {
            long size = response.getBodySize();
            if (size == 0 || input == null) {
                return "";
            }
            if (size > Constants.MAX_ARRAY_LENGTH) {
                throw new OutOfMemoryError("Request body is too large");
            }

            Charset encoding = ParameterParser.getEncoding(response.getHeader(HttpHeaderField.CONTENT_TYPE));

            if (size < 0) {
                InputStreamReader reader = new InputStreamReader(input, encoding);
                StringBuilder builder = new StringBuilder();

                char[] buffer = new char[512];

                int read;
                while ((read = reader.read(buffer)) > 0) {
                    builder.append(buffer, 0, read);
                }

                return builder.toString();
            } else {
                byte[] bytes = new byte[(int) size];

                int offset = 0;
                while (offset < size) {
                    int read = input.read(bytes, offset, (int) (size - offset));
                    if (read > 0) {
                        offset += read;
                    } else {
                        break;
                    }
                }

                return new String(bytes, 0, offset, encoding);
            }
        }

        @Override
        public String toString() {
            return "HttpDataDecoder.TEXT";
        }
    };

    public static final HttpDataDecoder<byte[], Object, IOException> BYTES = new HttpDataDecoder<byte[], Object, IOException>() {
        @Override
        public byte[] decode(HttpRequest response, InputWrapper input, Object arg) throws IOException {
            long size = response.getBodySize();
            if (size == 0 || input == null) {
                return Constants.EMPTY_BYTE_ARRAY;
            }

            if (size > Constants.MAX_ARRAY_LENGTH) {
                throw new OutOfMemoryError("Request body is too large");
            }

            if (size >= 0) {
                byte[] bytes = new byte[(int) size];

                int offset = 0;
                while (offset < size) {
                    int read = input.read(bytes, offset, (int) (size - offset));
                    if (read > 0) {
                        offset += read;
                    } else {
                        break;
                    }
                }

                if (offset == size) {
                    return bytes;
                } else {
                    return Arrays.copyOf(bytes, offset);
                }
            } else {
                byte[] bytes = new byte[512];

                int offset = 0;
                int read;
                while ((read = input.read(bytes, offset, bytes.length - offset)) > 0) {
                    offset += read;

                    if (offset >= bytes.length) {
                        int newLength = bytes.length << 1;
                        if (newLength < 0 || newLength > Constants.MAX_ARRAY_LENGTH) {
                            newLength = Constants.MAX_ARRAY_LENGTH;
                        }

                        if (newLength <= bytes.length) {
                            throw new OutOfMemoryError("Request body is too large");
                        }

                        bytes = Arrays.copyOf(bytes, newLength);
                    }
                }

                if (offset < bytes.length) {
                    return Arrays.copyOf(bytes, offset);
                } else {
                    return bytes;
                }
            }
        }

        @Override
        public String toString() {
            return "HttpDataDecoder.BYTES";
        }
    };

    public static final HttpDataDecoder<ByteBuffer, Object, IOException> BYTE_BUFFER = new HttpDataDecoder<ByteBuffer, Object, IOException>() {
        @Override
        public ByteBuffer decode(HttpRequest response, InputWrapper input, Object arg) throws IOException {
            long size = response.getBodySize();
            if (size == 0 || input == null) {
                return ByteBuffer.allocate(0);
            }

            if (size > Constants.MAX_ARRAY_LENGTH) {
                throw new OutOfMemoryError("Request body is too large");
            }

            ByteBuffer buffer = input.allocateTempByteBuffer(size > 0 ? (int) size : 8192);

            while (input.read(buffer) > 0) {
                if (!buffer.hasRemaining()) {
                    if (size > 0) {
                        break;
                    } else {
                        ByteBuffer newBuffer = input.allocateTempByteBuffer(buffer.position() * 2);
                        buffer.flip();
                        newBuffer.put(buffer);
                        buffer = newBuffer;
                    }
                }
            }

            buffer.flip();
            return buffer;

        }

        @Override
        public String toString() {
            return "HttpDataDecoder.BYTES";
        }
    };

    private HttpDataDecoders() {
    }
}
