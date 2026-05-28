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

import org.glavo.plumo.internal.ResponseBodies;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

/// A response body that supplies bytes to an HTTP response.
///
/// `ResponseBody` separates response metadata from body resource lifetime. Immutable and reusable bodies may be
/// sent more than once, while stream and channel bodies are one-shot resources.
@NotNullByDefault
public interface ResponseBody extends java.io.Closeable {
    /// Returns an empty reusable response body.
    static ResponseBody empty() {
        return ResponseBodies.empty();
    }

    /// Creates a reusable response body from a byte array copy.
    static ResponseBody of(byte[] data) {
        return ResponseBodies.of(data);
    }

    /// Creates a reusable response body from a copied byte array range.
    static ResponseBody of(byte[] data, int offset, int length) {
        return ResponseBodies.of(data, offset, length);
    }

    /// Creates a reusable response body from a copied byte buffer range.
    static ResponseBody of(ByteBuffer data) {
        return ResponseBodies.of(data);
    }

    /// Creates a reusable text response body.
    static ResponseBody of(String text) {
        return ResponseBodies.of(text);
    }

    /// Creates a one-shot response body backed by an input stream with an unknown length.
    static ResponseBody of(InputStream input) {
        return ResponseBodies.of(input, -1L);
    }

    /// Creates a one-shot response body backed by an input stream.
    static ResponseBody of(InputStream input, long contentLength) {
        return ResponseBodies.of(input, contentLength);
    }

    /// Creates a one-shot response body backed by a readable byte channel with an unknown length.
    static ResponseBody of(ReadableByteChannel input) {
        return ResponseBodies.of(input, -1L);
    }

    /// Creates a one-shot response body backed by a readable byte channel.
    static ResponseBody of(ReadableByteChannel input, long contentLength) {
        return ResponseBodies.of(input, contentLength);
    }

    /// Creates a reusable response body backed by a file path.
    static ResponseBody of(Path file) {
        return ResponseBodies.of(file);
    }

    /// Creates a reusable response body backed by a file.
    static ResponseBody of(File file) {
        return of(file.toPath());
    }

    /// Returns `true` if this body can be opened and sent more than once.
    boolean isReusable();

    /// Returns the content length in bytes, or `-1` when the length is unknown.
    long getContentLength(@Nullable String contentType) throws IOException;

    /// Opens a channel that reads this body in bytes.
    ReadableByteChannel openChannel(@Nullable String contentType) throws IOException;

    /// Releases resources held by this body.
    @Override
    void close() throws IOException;
}
