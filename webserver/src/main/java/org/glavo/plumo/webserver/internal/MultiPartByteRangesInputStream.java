/*
 * Copyright 2023 Glavo
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
package org.glavo.plumo.webserver.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.UUID;

public class MultiPartByteRangesInputStream extends InputStream {
    private static String randomBoundary() {
        return "BOUNDARY_" + Long.toHexString(System.nanoTime()) + "-" + UUID.randomUUID();
    }

    private final FileChannel channel;
    private final long fileSize;
    private final ContentRange[] ranges;
    private final String[] headers;

    private final String boundary = randomBoundary();


    private String header;
    private boolean readingHeader = false;
    private int headerOffset;

    private int currentRangeIndex = -1;
    private long currentRangeRemaining;

    private boolean end = false;

    public MultiPartByteRangesInputStream(FileChannel channel, long fileSize, ContentRange[] ranges,
                                          String contentType) throws IOException {
        assert ranges.length > 1;

        this.channel = channel;
        this.fileSize = fileSize;

        this.ranges = ranges;
        this.headers = new String[ranges.length + 1];


        StringBuilder headerBuilder = new StringBuilder(256);
        int headerBuilderOffset;

        headerBuilder.append("\r\n--").append(boundary).append("\r\n");
        if (contentType != null) {
            headerBuilder.append("content-type: ").append(contentType).append("\r\n");
        }
        headerBuilder.append("content-range: bytes ");
        headerBuilderOffset = headerBuilder.length();

        for (int i = 0; i < ranges.length; i++) {
            ContentRange range = ranges[i];

            headerBuilder.setLength(headerBuilderOffset);
            headerBuilder.append(range.start).append('-').append(range.end).append('/').append(fileSize).append("\r\n\r\n");


            headers[i] = (i == 0) ? headerBuilder.substring(2) : headerBuilder.toString();
        }

        headerBuilder.setLength(0);
        headerBuilder.append("\r\n--").append(boundary).append("--\r\n");
        headers[ranges.length] = headerBuilder.toString();

        nextByteRange();
    }

    private void nextByteRange() throws IOException {
        int idx = ++currentRangeIndex;
        ContentRange range = idx < ranges.length ? ranges[idx] : null;
        this.header = headers[idx];

        this.readingHeader = true;
        this.headerOffset = 0;

        if (range != null) {
            this.currentRangeRemaining = range.end - range.start + 1;
            this.channel.position(range.start);
        } else {
            this.currentRangeRemaining = 0;
        }
    }

    public String getBoundary() {
        return boundary;
    }

    public long getContentLength() {
        long len = 0L;

        for (String header : headers) {
            len += header.length();
        }

        for (ContentRange range : ranges) {
            len += (range.end - range.start + 1);
        }

        return len;
    }

    @Override
    public int read() throws IOException {
        throw new AssertionError(); // unused
    }

    private ByteBuffer cache;

    @Override
    @SuppressWarnings("deprecation")
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }

        if (end) {
            return -1;
        }

        if (readingHeader) {
            int n = Math.min(len, header.length() - headerOffset);
            header.getBytes(headerOffset, headerOffset + n, b, off);
            headerOffset += n;

            if (headerOffset == header.length()) {
                readingHeader = false;

                if (currentRangeRemaining == 0L) {
                    if (currentRangeIndex == ranges.length) {
                        end = true;
                    } else {
                        nextByteRange();
                    }
                }
            }

            return n;
        }

        int max = (int) Math.min(len, currentRangeRemaining);

        if (cache != null && cache.array() == b) {
            ((Buffer) cache).clear();
            cache.position(off).limit(off + max);
        } else {
            cache = ByteBuffer.wrap(b, off, max);
        }

        int n = channel.read(cache);

        currentRangeRemaining -= n;
        if (currentRangeRemaining == 0) {
            nextByteRange();
        }
        return n;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
