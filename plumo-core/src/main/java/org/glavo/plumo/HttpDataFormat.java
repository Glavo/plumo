package org.glavo.plumo;

import org.glavo.plumo.internal.HttpDataFormats;

import java.io.IOException;
import java.io.InputStream;

public interface HttpDataFormat<V, A, E extends Throwable> {
    HttpDataFormat<InputStream, ?, RuntimeException> INPUT_STREAM = HttpDataFormats.INPUT_STREAM;
    HttpDataFormat<String, ?, IOException> TEXT = HttpDataFormats.TEXT;
    HttpDataFormat<byte[], ?, IOException> BYTES = HttpDataFormats.BYTES;

    V decode(HttpRequest request, InputStream input, A arg) throws E;
}
