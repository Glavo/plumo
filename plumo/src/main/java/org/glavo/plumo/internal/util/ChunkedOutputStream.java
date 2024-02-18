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
package org.glavo.plumo.internal.util;

import org.glavo.plumo.internal.Constants;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ChunkedOutputStream extends FilterOutputStream {

    private static final byte[] FINISH = {'0', '\r', '\n', '\r', '\n'};

    public ChunkedOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        byte[] data = {(byte) b};
        write(data, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0)
            return;

        out.write(Integer.toHexString(len).getBytes(StandardCharsets.ISO_8859_1));
        out.write(Constants.CRLF);
        out.write(b, off, len);
        out.write(Constants.CRLF);
    }

    public void finish() throws IOException {
        out.write(FINISH);
    }
}
