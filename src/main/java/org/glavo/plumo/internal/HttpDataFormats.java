package org.glavo.plumo.internal;

import org.glavo.plumo.ContentType;
import org.glavo.plumo.HttpDataFormat;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.internal.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class HttpDataFormats {

    public static final HttpDataFormat<InputStream, Object, RuntimeException> INPUT_STREAM = new HttpDataFormat<InputStream, Object, RuntimeException>() {
        @Override
        public InputStream decode(HttpRequest request, InputStream input, Object arg) {
            return input == null ? IOUtils.nullInputStream() : input;
        }

        @Override
        public String toString() {
            return "InputStream";
        }
    };

    public static final HttpDataFormat<String, Object, IOException> TEXT = new HttpDataFormat<String, Object, IOException>() {
        @Override
        public String decode(HttpRequest response, InputStream input, Object arg) throws IOException {
            long size = response.getBodySize();
            if (size == 0 || input == null) {
                return "";
            }
            if (size > Constants.MAX_ARRAY_LENGTH) {
                throw new OutOfMemoryError("Request body is too large");
            }

            ContentType contentType = response.getContentType();
            Charset encoding = contentType == null ? StandardCharsets.UTF_8 : contentType.getCharset();

            if (size < 0) {
                InputStreamReader reader = new InputStreamReader(input, encoding);
                StringBuilder builder = new StringBuilder();

                char[] buffer = new char[512];

                int read;
                while ((read = reader.read(buffer)) > 0) {
                    builder.append(buffer, 0, read);
                }

                return builder.toString();
            } else {
                byte[] bytes = new byte[(int) size];

                int offset = 0;
                while (offset < size) {
                    int read = input.read(bytes, offset, (int) (size - offset));
                    if (read > 0) {
                        offset += read;
                    } else {
                        break;
                    }
                }

                return new String(bytes, 0, offset, encoding);
            }
        }

        @Override
        public String toString() {
            return "text";
        }
    };

    public static final HttpDataFormat<byte[], Object, IOException> BYTES = new HttpDataFormat<byte[], Object, IOException>() {
        @Override
        public byte[] decode(HttpRequest response, InputStream input, Object arg) throws IOException {
            long size = response.getBodySize();
            if (size == 0 || input == null) {
                return Constants.EMPTY_BYTE_ARRAY;
            }

            if (size > Constants.MAX_ARRAY_LENGTH) {
                throw new OutOfMemoryError("Request body is too large");
            }

            if (size < 0) {
                byte[] bytes = new byte[512];

                int offset = 0;
                int read;
                while ((read = input.read(bytes, offset, bytes.length - offset)) > 0) {
                    offset += read;

                    if (offset >= bytes.length) {
                        int newLength = bytes.length << 1;
                        if (newLength < 0 || newLength > Constants.MAX_ARRAY_LENGTH) {
                            newLength = Constants.MAX_ARRAY_LENGTH;
                        }

                        if (newLength <= bytes.length) {
                            throw new OutOfMemoryError("Request body is too large");
                        }

                        bytes = Arrays.copyOf(bytes, newLength);
                    }
                }

                if (offset < bytes.length) {
                    return Arrays.copyOf(bytes, offset);
                } else {
                    return bytes;
                }
            } else {
                byte[] bytes = new byte[(int) size];

                int offset = 0;
                while (offset < size) {
                    int read = input.read(bytes, offset, (int) (size - offset));
                    if (read > 0) {
                        offset += read;
                    } else {
                        break;
                    }
                }

                if (offset < size) {
                    return Arrays.copyOf(bytes, offset);
                } else {
                    return bytes;
                }
            }
        }

        @Override
        public String toString() {
            return "bytes";
        }
    };

    private HttpDataFormats(){
    }
}
