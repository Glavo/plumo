package org.glavo.plumo;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public final class HttpHeaderFieldTest {

    @Test
    public void testVerification() {
        assertThrows(IllegalArgumentException.class, () -> HttpHeaderField.of(""));
        assertThrows(IllegalArgumentException.class, () -> HttpHeaderField.of("content type"));
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
