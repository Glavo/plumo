/*
 * Copyright 2026 Glavo
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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import static org.junit.jupiter.api.Assertions.*;

/// Tests immutable response and response body lifecycle semantics.
@NotNullByDefault
public final class HttpResponseTest {
    /// Verifies that fluent response methods return copies and keep the original response unchanged.
    @Test
    public void testResponseCopy() {
        HttpResponse original = HttpResponse.newResponse();
        HttpResponse withHeader = original.withHeader(HttpHeaderField.CONTENT_TYPE, "text/plain");
        HttpResponse withoutHeader = withHeader.removeHeader(HttpHeaderField.CONTENT_TYPE);

        assertNull(original.getHeader(HttpHeaderField.CONTENT_TYPE));
        assertEquals("text/plain", withHeader.getHeader(HttpHeaderField.CONTENT_TYPE));
        assertNull(withoutHeader.getHeader(HttpHeaderField.CONTENT_TYPE));
    }

    /// Verifies that byte array response bodies copy caller-owned data.
    @Test
    public void testByteArrayBodyCopiesData() throws IOException {
        byte[] data = new byte[]{1, 2, 3};
        ResponseBody body = ResponseBody.of(data);
        data[0] = 9;

        ByteBuffer buffer = ByteBuffer.allocate(3);
        try (ReadableByteChannel channel = body.openChannel(null)) {
            while (channel.read(buffer) > 0) {
                // read all bytes
            }
        }

        buffer.flip();
        byte[] actual = new byte[buffer.remaining()];
        buffer.get(actual);
        assertArrayEquals(new byte[]{1, 2, 3}, actual);
    }

    /// Verifies that stream response bodies can only be opened once.
    @Test
    public void testInputStreamBodyIsOneShot() throws IOException {
        ResponseBody body = ResponseBody.of(new ByteArrayInputStream(new byte[]{1}), 1L);

        try (ReadableByteChannel channel = body.openChannel(null)) {
            assertEquals(1, channel.read(ByteBuffer.allocate(1)));
        }

        assertThrows(IOException.class, () -> body.openChannel(null));
    }
}
