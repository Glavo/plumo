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
package org.glavo.plumo;

import org.glavo.plumo.internal.HttpDataDecoders;
import org.glavo.plumo.internal.util.InputWrapper;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public interface HttpDataDecoder<V, A, E extends Throwable> {
    HttpDataDecoder<InputStream, ?, RuntimeException> INPUT_STREAM = HttpDataDecoders.INPUT_STREAM;
    HttpDataDecoder<ReadableByteChannel, Object, RuntimeException> BYTE_CHANNEL = HttpDataDecoders.BYTE_CHANNEL;
    HttpDataDecoder<String, ?, IOException> TEXT = HttpDataDecoders.TEXT;
    HttpDataDecoder<byte[], ?, IOException> BYTES = HttpDataDecoders.BYTES;
    HttpDataDecoder<ByteBuffer, Object, IOException> BYTE_BUFFER = HttpDataDecoders.BYTE_BUFFER;
}
