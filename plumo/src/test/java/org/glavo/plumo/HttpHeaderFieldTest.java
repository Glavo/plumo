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
package org.glavo.plumo;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public final class HttpHeaderFieldTest {

    @Test
    public void testVerification() {
        assertThrows(NullPointerException.class, () -> HttpHeaderField.of(null));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaderField.of(""));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaderField.of("content type"));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaderField.of("Content Type"));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaderField.of("测试"));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaderField.of("TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT"));
    }

    @Test
    public void testToString() {
        assertEquals("content-type", HttpHeaderField.of("content-type").toString());
        assertEquals("content-type", HttpHeaderField.of("Content-Type").toString());
    }

    @Test
    public void testHash() {
        HashMap<HttpHeaderField, String> map = new HashMap<>();
        map.put(HttpHeaderField.of("content-type"), "value1");
        map.put(HttpHeaderField.of("content-length"), "value2");


        assertEquals("value1", map.get(HttpHeaderField.of("Content-Type")));
        assertEquals("value2", map.get(HttpHeaderField.of("Content-Length")));
    }
}
