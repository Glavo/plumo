package org.glavo.plumo.internal;

import org.glavo.plumo.internal.util.MultiStringMap;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public final class HeadersTest {
    private static final Set<String> HEADERS = Set.of(
            "accept", "allow", "authorization",
            "connection", "content-length", "content-type", "cookie",
            "date",
            "etag", "expires",
            "host",
            "last-modified",
            "set-cookie"

    );

    private static final List<String> RANDOM_HEADERS = new ArrayList<>();

    static {
        Random random = new Random(0);
        for (int i = 0; i < 20000; i++) {
            int len = random.nextInt(60);
            StringBuilder builder = new StringBuilder(len + 4);
            builder.append(String.format("%04x", i));

            for (int j = 0; j < len; j++) {
                builder.append((char) ('a' + random.nextInt(26)));
            }
            RANDOM_HEADERS.add(builder.toString());
        }
    }

    private static void testPutHeader(int offset, int length) {
        MultiStringMap multiStringMap = new MultiStringMap();
        for (int i = offset; i < offset + length; i++) {
            String header = RANDOM_HEADERS.get(i);
            try {
                assertEquals(i - offset, multiStringMap.size());
                assertFalse(multiStringMap.containsKey(header));
                assertNull(multiStringMap.getFirst(header));
                assertNull(multiStringMap.get(header));

                multiStringMap.putDirect(header, "foo");

                assertEquals(i + 1 - offset, multiStringMap.size());
                assertTrue(multiStringMap.containsKey(header));
                assertEquals("foo", multiStringMap.getFirst(header));
                assertEquals(List.of("foo"), multiStringMap.get(header));

                multiStringMap.putDirect(header, "foo");
                assertEquals(i + 1 - offset, multiStringMap.size());
                assertEquals("foo", multiStringMap.getFirst(header));
                assertEquals(List.of("foo"), multiStringMap.get(header));
            } catch (AssertionError error) {
                throw new AssertionError("header: " + header, error);
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
