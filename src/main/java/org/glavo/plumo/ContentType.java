package org.glavo.plumo;

import org.glavo.plumo.internal.util.ParameterParser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class ContentType {

    public static final ContentType PLAIN_TEXT = new ContentType("text/plain", null, null);
    public static final ContentType HTML = new ContentType("text/html", null, null);
    public static final ContentType JSON = new ContentType("application/json", null, null);

    public static final ContentType OCTET_STREAM = new ContentType("application/octet-stream", null, null);

    private String contentTypeHeader;

    private final String mimeType;

    private final Charset charset;
    private final String boundary;

    public ContentType(String contentTypeHeader) {
        this.contentTypeHeader = contentTypeHeader;

        if (contentTypeHeader == null) {
            this.mimeType = "";
            this.charset = null;
            this.boundary = null;
        } else {
            int idx = contentTypeHeader.indexOf(';');
            if (idx < 0) {
                this.mimeType = contentTypeHeader.trim();
                this.charset = null;
                this.boundary = null;
            } else {
                this.mimeType = contentTypeHeader.substring(0, idx).trim();

                ParameterParser parser = new ParameterParser(contentTypeHeader, idx + 1, ';');

                String charsetName = null;
                String boundary = null;

                Map.Entry<String, String> next;
                while ((next = parser.nextParameter(false)) != null) {
                    if ("charset".equalsIgnoreCase(next.getKey())) {
                        charsetName = next.getValue();
                    } else if ("boundary".equalsIgnoreCase(next.getKey())) {
                        boundary = next.getValue();
                    }
                }

                Charset cs = null;
                if (charsetName != null) {
                    try {
                        cs = Charset.forName(charsetName);
                    } catch (Throwable ignored) {
                        // unknown encoding
                    }
                }

                this.charset = cs;
                this.boundary = boundary;
            }
        }
    }

    public ContentType(String mimeType, Charset charset, String boundary) {
        this.mimeType = mimeType;
        this.charset = charset;
        this.boundary = boundary;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Charset getCharset() {
        return charset == null ? StandardCharsets.UTF_8 : charset;
    }

    public String getBoundary() {
        return boundary;
    }

    public boolean isMultipart() {
        return "multipart/form-data".equalsIgnoreCase(mimeType);
    }

    @Override
    public String toString() {
        if (contentTypeHeader == null) {
            if (charset == null && boundary == null) {
                contentTypeHeader = mimeType;
            } else {
                StringBuilder builder = new StringBuilder();
                builder.append(mimeType);

                if (charset != null) {
                    builder.append("; charset=").append(charset == StandardCharsets.UTF_8 ? "utf-8" : charset.name());
                }

                if (boundary != null) {
                    builder.append("; boundary=").append(boundary);
                }

                contentTypeHeader = builder.toString();
            }
        }
        return contentTypeHeader;
    }
}
