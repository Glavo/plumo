package org.glavo.plumo.internal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UtilsTest {
    @Test
    public void testNormalizeHttpHeaderFieldName() {
        assertEquals("content-length", Utils.normalizeHttpHeaderFieldName("content-length"));
        assertEquals("content-length", Utils.normalizeHttpHeaderFieldName("Content-Length"));
        assertEquals("content-length", Utils.normalizeHttpHeaderFieldName("content-Length"));

        assertThrows(NullPointerException.class, () -> Utils.normalizeHttpHeaderFieldName(null));
        assertThrows(IllegalArgumentException.class, () -> Utils.normalizeHttpHeaderFieldName(""));
        assertThrows(IllegalArgumentException.class, () -> Utils.normalizeHttpHeaderFieldName("content length"));
        assertThrows(IllegalArgumentException.class, () -> Utils.normalizeHttpHeaderFieldName("Content length"));
        assertThrows(IllegalArgumentException.class, () -> Utils.normalizeHttpHeaderFieldName("A".repeat(81)));
    }
}
