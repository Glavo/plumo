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

import org.glavo.plumo.HttpHeaderField;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public final class HeadersTest {
    private static final List<HttpHeaderField> RANDOM_HEADERS = new ArrayList<>();

    static {
        Random random = new Random(0);
        for (int i = 0; i < 20000; i++) {
            int len = random.nextInt(60);
            StringBuilder builder = new StringBuilder(len + 4);
            builder.append(String.format("%04x", i));

            for (int j = 0; j < len; j++) {
                builder.append((char) ('a' + random.nextInt(26)));
            }
            RANDOM_HEADERS.add(HttpHeaderField.of(builder.toString()));
        }
    }

    private static void testPutHeader(int offset, int length) {
        Headers multiStringMap = new Headers();
        for (int i = offset; i < offset + length; i++) {
            HttpHeaderField field = RANDOM_HEADERS.get(i);
            try {
                assertEquals(i - offset, multiStringMap.size());
                assertFalse(multiStringMap.containsKey(field));
                assertNull(multiStringMap.getFirst(field));
                assertNull(multiStringMap.get(field));

                multiStringMap.putDirect(field, "foo");

                assertEquals(i + 1 - offset, multiStringMap.size());
                assertTrue(multiStringMap.containsKey(field));
                assertEquals("foo", multiStringMap.getFirst(field));
                assertEquals(List.of("foo"), multiStringMap.get(field));

                multiStringMap.putDirect(field, "foo");
                assertEquals(i + 1 - offset, multiStringMap.size());
                assertEquals("foo", multiStringMap.getFirst(field));
                assertEquals(List.of("foo"), multiStringMap.get(field));
            } catch (AssertionError error) {
                throw new AssertionError("header: " + field, error);
            }
        }
    }

    @Test
    public void testPutHeader() {
        testPutHeader(0, RANDOM_HEADERS.size());

        // Test for hash collisions
        int fragmentSize = 10;
        for (int fragment = 0; fragment < RANDOM_HEADERS.size() / fragmentSize; fragment++) {
            int offset = fragment * fragmentSize;
            testPutHeader(offset, fragmentSize);
        }
    }
}
