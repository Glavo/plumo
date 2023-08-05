package org.glavo.plumo.internal;

import org.glavo.plumo.ContentType;
import org.glavo.plumo.HttpRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class BodyTypes {

    public static final HttpRequest.BodyType<InputStream, Object, RuntimeException> INPUT_STREAM = new HttpRequest.BodyType<InputStream, Object, RuntimeException>() {
        @Override
        public InputStream decode(HttpRequest request, InputStream input, Object arg) throws RuntimeException {
            return input;
        }

        @Override
        public String toString() {
            return "InputStream";
        }
    };

    public static final HttpRequest.BodyType<String, Object, IOException> TEXT = new HttpRequest.BodyType<String, Object, IOException>() {
        @Override
        public String decode(HttpRequest response, InputStream input, Object arg) throws IOException {
            long size = response.getBodySize();
            if (size == 0) {
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
            return "Text";
        }
    };


    private BodyTypes(){
    }
}
