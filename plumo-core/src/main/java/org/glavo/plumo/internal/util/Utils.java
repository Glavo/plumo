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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public final class Utils {

    private static final boolean[] IS_SEPARATOR = new boolean[128];

    static {
        IS_SEPARATOR['('] = true;
        IS_SEPARATOR[')'] = true;
        IS_SEPARATOR['<'] = true;
        IS_SEPARATOR['>'] = true;
        IS_SEPARATOR['@'] = true;
        IS_SEPARATOR[','] = true;
        IS_SEPARATOR[';'] = true;
        IS_SEPARATOR[':'] = true;
        IS_SEPARATOR['\\'] = true;
        IS_SEPARATOR['"'] = true;
        IS_SEPARATOR['/'] = true;
        IS_SEPARATOR['['] = true;
        IS_SEPARATOR[']'] = true;
        IS_SEPARATOR['?'] = true;
        IS_SEPARATOR['='] = true;
        IS_SEPARATOR['{'] = true;
        IS_SEPARATOR['}'] = true;
        IS_SEPARATOR[' '] = true;
        IS_SEPARATOR['\t'] = true;
    }

    public static boolean isSeparator(int ch) {
        return ch >= '!' && ch <= '~' && IS_SEPARATOR[ch];
    }

    public static boolean isTokenPart(int ch) {
        return ch >= '!' && ch <= '~' && !IS_SEPARATOR[ch];
    }

    public static String newString(ByteBuffer buffer, int offset, int end, Charset charset) {
        if (buffer.hasArray()) {
            return new String(buffer.array(), buffer.arrayOffset() + offset, end - offset, charset);
        } else {
            byte[] bytes = new byte[end - offset];
            ByteBuffer duplicate = buffer.duplicate();
            duplicate.limit(end);
            duplicate.position(offset);
            duplicate.get(bytes);
            return new String(bytes, charset);
        }
    }

    public static void shutdown(Executor executor) {
        if (!(executor instanceof ExecutorService)) {
            return;
        }

        ExecutorService es = (ExecutorService) executor;
        boolean terminated = es.isTerminated();
        if (!terminated) {
            es.shutdown();
            while (!terminated) {
                try {
                    terminated = es.awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    es.shutdownNow();
                }
            }
        }
    }

    private Utils() {
    }
}
