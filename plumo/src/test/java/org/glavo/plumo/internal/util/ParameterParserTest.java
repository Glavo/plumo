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

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ParameterParserTest {

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static ParameterParser createCookieParser(String cookie) {
        return new ParameterParser(cookie, ',');
    }

    @SafeVarargs
    private void assertCookies(String cookie, Map.Entry<String, String>... pairs) {
        ParameterParser parser = createCookieParser(cookie);

        for (Map.Entry<String, String> pair : pairs) {
            var actual = parser.nextParameter();
            assertEquals(pair, actual, () -> "actual = (%s, %s)".formatted(actual.getKey(), actual.getValue()));
        }
        assertNull(parser.nextParameter());
    }

    @Test
    public void testParseCookie() {
        assertCookies("");
        assertCookies(",");
        assertCookies("key", entry("key", null));
        assertCookies("key=", entry("key", ""));
        assertCookies("key=\"\"", entry("key", ""));
        assertCookies("key=value", entry("key", "value"));
        assertCookies("key=\"value\"", entry("key", "value"));
        assertCookies("key=\"val\\\"ue\"", entry("key", "val\"ue"));
        assertCookies("key=value,", entry("key", "value"));
        assertCookies("key1=,key2", entry("key1", ""), entry("key2", null));
        assertCookies("key1,key2=", entry("key1", null), entry("key2", ""));
        assertCookies("key1=value1,key2", entry("key1", "value1"), entry("key2", null));
        assertCookies("key1=value1, key2", entry("key1", "value1"), entry("key2", null));

        assertCookies(" key1=value1 , key2 , key3=\"value3\\\"\",,,",
                entry("key1", "value1"),
                entry("key2", null),
                entry("key3", "value3\"")
        );

        // invalid
    }

}
