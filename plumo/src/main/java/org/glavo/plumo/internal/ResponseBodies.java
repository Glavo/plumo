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
package org.glavo.plumo.internal;

import org.glavo.plumo.ResponseBody;
import org.glavo.plumo.internal.util.InputWrapper;
import org.glavo.plumo.internal.util.ParameterParser;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/// Factory and implementation holder for built-in response body types.
@NotNullByDefault
public final class ResponseBodies {
    private static final ResponseBody EMPTY = new EmptyBody();

    /// Returns the singleton empty body.
    public static ResponseBody empty() {
        return EMPTY;
    }

    /// Creates a reusable byte array body from a copy of the supplied data.
    public static ResponseBody of(byte[] data) {
        return new ByteArrayBody(Arrays.copyOf(data, data.length));
    }

    /// Creates a reusable byte array body from a copy of the supplied range.
    public static ResponseBody of(byte[] data, int offset, int length) {
        return new ByteArrayBody(Arrays.copyOfRange(data, offset, offset + length));
    }

    /// Creates a reusable byte buffer body from a copy of the remaining bytes.
    public static ResponseBody of(ByteBuffer data) {
        ByteBuffer duplicate = data.duplicate();
        byte[] copy = new byte[duplicate.remaining()];
        duplicate.get(copy);
        return new ByteArrayBody(copy);
    }

    /// Creates a reusable text body.
    public static ResponseBody of(String text) {
        return new TextBody(text);
    }

    /// Creates a one-shot stream body.
    public static ResponseBody of(InputStream input, long contentLength) {
        return new InputStreamBody(input, checkedContentLength(contentLength));
    }

    /// Creates a one-shot channel body.
    public static ResponseBody of(ReadableByteChannel input, long contentLength) {
        return new ChannelBody(input, checkedContentLength(contentLength));
    }

    /// Creates a reusable file body.
    public static ResponseBody of(Path file) {
        return new FileBody(file);
    }

    private static long checkedContentLength(long contentLength) {
        if (contentLength < -1L) {
            throw new IllegalArgumentException("Content length must be -1 or greater");
        }
        return contentLength;
    }

    private ResponseBodies() {
    }

    private static final class EmptyBody implements ResponseBody {
        @Override
        public boolean isReusable() {
            return true;
        }

        @Override
        public long getContentLength(@Nullable String contentType) {
            return 0L;
        }

        @Override
        public ReadableByteChannel openChannel(@Nullable String contentType) {
            return InputWrapper.nullInputWrapper();
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return "<empty body>";
        }
    }

    private static final class ByteArrayBody implements ResponseBody {
        private final byte[] data;

        private ByteArrayBody(byte[] data) {
            this.data = data;
        }

        @Override
        public boolean isReusable() {
            return true;
        }

        @Override
        public long getContentLength(@Nullable String contentType) {
            return data.length;
        }

        @Override
        public ReadableByteChannel openChannel(@Nullable String contentType) {
            return Channels.newChannel(new ByteArrayInputStream(data));
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return "<binary body, length=" + data.length + ">";
        }
    }

    private static final class TextBody implements ResponseBody {
        private final String text;

        private TextBody(String text) {
            this.text = Objects.requireNonNull(text);
        }

        @Override
        public boolean isReusable() {
            return true;
        }

        @Override
        public long getContentLength(@Nullable String contentType) {
            return bytes(contentType).length;
        }

        @Override
        public ReadableByteChannel openChannel(@Nullable String contentType) {
            return Channels.newChannel(new ByteArrayInputStream(bytes(contentType)));
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return "<text body, length=" + text.length() + ">";
        }

        private byte[] bytes(@Nullable String contentType) {
            return text.getBytes(ParameterParser.getEncoding(contentType));
        }
    }

    private abstract static class OneShotBody implements ResponseBody {
        private final long contentLength;
        private boolean opened;

        private OneShotBody(long contentLength) {
            this.contentLength = contentLength;
        }

        @Override
        public final boolean isReusable() {
            return false;
        }

        @Override
        public final long getContentLength(@Nullable String contentType) {
            return contentLength;
        }

        @Override
        public final synchronized ReadableByteChannel openChannel(@Nullable String contentType) throws IOException {
            if (opened) {
                throw new IOException("Response body has already been consumed");
            }
            opened = true;
            return openChannelImpl();
        }

        protected abstract ReadableByteChannel openChannelImpl();
    }

    private static final class InputStreamBody extends OneShotBody {
        private final InputStream input;

        private InputStreamBody(InputStream input, long contentLength) {
            super(contentLength);
            this.input = Objects.requireNonNull(input);
        }

        @Override
        protected ReadableByteChannel openChannelImpl() {
            return Channels.newChannel(input);
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        @Override
        public String toString() {
            long contentLength = getContentLength(null);
            return contentLength < 0 ? "<stream body, unknown length>" : "<stream body, length=" + contentLength + ">";
        }
    }

    private static final class ChannelBody extends OneShotBody {
        private final ReadableByteChannel input;

        private ChannelBody(ReadableByteChannel input, long contentLength) {
            super(contentLength);
            this.input = Objects.requireNonNull(input);
        }

        @Override
        protected ReadableByteChannel openChannelImpl() {
            return input;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }

        @Override
        public String toString() {
            long contentLength = getContentLength(null);
            return contentLength < 0 ? "<channel body, unknown length>" : "<channel body, length=" + contentLength + ">";
        }
    }

    private static final class FileBody implements ResponseBody {
        private final Path file;

        private FileBody(Path file) {
            this.file = Objects.requireNonNull(file);
        }

        @Override
        public boolean isReusable() {
            return true;
        }

        @Override
        public long getContentLength(@Nullable String contentType) throws IOException {
            return Files.size(file);
        }

        @Override
        public ReadableByteChannel openChannel(@Nullable String contentType) throws IOException {
            return Files.newByteChannel(file);
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return "<file body, path=" + file + ">";
        }
    }
}
