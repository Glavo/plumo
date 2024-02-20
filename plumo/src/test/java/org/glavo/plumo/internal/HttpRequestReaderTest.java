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
package org.glavo.plumo.internal;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public final class HttpRequestReaderTest {
    @FunctionalInterface
    private interface Action {
        void accept(HttpRequestReader reader) throws Throwable;
    }

    private List<DynamicTest> createTest(String name, byte[] data, Action action) {
        return Arrays.asList(
                DynamicTest.dynamicTest(name + " (InputStream)", () -> action.accept(new HttpRequestReader(new ByteArrayInputStream(data)))),
                DynamicTest.dynamicTest(name + " (ByteChannel)", () -> action.accept(new HttpRequestReader(Channels.newChannel(new ByteArrayInputStream(data)))))
        );
    }

    @TestFactory
    public Stream<DynamicTest> testBoundedInput() {
        return IntStream.of(512, 8192, 8192 + 512)
                .mapToObj(length -> {
                    byte[] data = new byte[length];
                    new Random(0).nextBytes(data);
                    return data;
                })
                .flatMap(data -> Stream.of(0, 1, 16, data.length / 2, data.length)
                        .flatMap(prefixLength -> {
                            List<DynamicTest> tests = new ArrayList<>();
                            tests.addAll(createTest(String.format("BoundedInput::read(byte[]) (length=%d, prefixLength=%d)", data.length, prefixLength), data, reader -> {
                                HttpRequestReader.BoundedInput input = new HttpRequestReader.BoundedInput(reader, prefixLength);

                                byte[] temp = new byte[prefixLength];

                                int offset = 0;
                                int read;
                                while ((read = input.read(temp, offset, temp.length - offset)) > 0) {
                                    offset += read;

                                    if (offset >= temp.length) {
                                        int newLength = temp.length * 2;
                                        if (newLength < 0 || newLength > Constants.MAX_ARRAY_LENGTH) {
                                            newLength = Constants.MAX_ARRAY_LENGTH;
                                        }

                                        if (newLength <= temp.length) {
                                            throw new OutOfMemoryError("Request body is too large");
                                        }

                                        temp = Arrays.copyOf(temp, newLength);
                                    }
                                }

                                assertArrayEquals(Arrays.copyOf(data, prefixLength), Arrays.copyOf(temp, offset));
                            }));


                            for (boolean isDirect : new boolean[]{false, true}) {
                                tests.addAll(createTest(String.format("testBoundedInput::read(ByteBuffer) (length=%d, prefixLength=%d, isDirect=%s)", data.length, prefixLength, isDirect), data, reader -> {
                                    HttpRequestReader.BoundedInput input = new HttpRequestReader.BoundedInput(reader, prefixLength);

                                    ByteBuffer temp = isDirect ? ByteBuffer.allocateDirect(prefixLength * 2) : ByteBuffer.allocate(prefixLength * 2);

                                    while (input.read(temp) > 0) {
                                        if (!temp.hasRemaining()) {
                                            int newCap = temp.capacity() * 2;
                                            ByteBuffer newTemp = isDirect ? ByteBuffer.allocateDirect(newCap) : ByteBuffer.allocate(newCap);
                                            temp.flip();
                                            newTemp.put(temp);

                                            temp = newTemp;
                                        }
                                    }

                                    temp.flip();

                                    byte[] result = new byte[temp.remaining()];
                                    temp.get(result);

                                    assertArrayEquals(Arrays.copyOf(data, prefixLength), result);
                                }));
                            }

                            if (prefixLength > 0) {
                                tests.addAll(createTest(String.format("testBoundedInput::close() (length=%d, prefixLength=%d)", data.length, prefixLength), data, reader -> {
                                    byte[] temp = new byte[prefixLength];
                                    try (HttpRequestReader.BoundedInput input = new HttpRequestReader.BoundedInput(reader, data.length)) {
                                        int offset = 0;
                                        int read;
                                        while ((read = input.read(temp, offset, temp.length - offset)) > 0) {
                                            offset += read;
                                        }
                                    }
                                    assertEquals(-1, reader.read(temp, 0, prefixLength));
                                }));
                            }

                            return tests.stream();
                        }));
    }
}
