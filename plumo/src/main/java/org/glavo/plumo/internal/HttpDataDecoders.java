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
import java.nio.charset.Charset;
import java.util.Arrays;

public final class HttpDataDecoders {

    public static final HttpDataDecoder<InputStream, Object, RuntimeException> INPUT_STREAM = new HttpDataDecoder<InputStream, Object, RuntimeException>() {
        @Override
        public InputStream decode(HttpRequest request, InputStream input, Object arg) {
            return input == null ? InputWrapper.nullInputWrapper() : input;
        }

        @Override
        public String toString() {
            return "InputStream";
        }
    };

    public static final HttpDataDecoder<String, Object, IOException> TEXT = new HttpDataDecoder<String, Object, IOException>() {
        @Override
        public String decode(HttpRequest response, InputStream input, Object arg) throws IOException {
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
            return "text";
        }
    };

    public static final HttpDataDecoder<byte[], Object, IOException> BYTES = new HttpDataDecoder<byte[], Object, IOException>() {
        @Override
        public byte[] decode(HttpRequest response, InputStream input, Object arg) throws IOException {
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
            return "bytes";
        }
    };

    private HttpDataDecoders(){
    }
}
