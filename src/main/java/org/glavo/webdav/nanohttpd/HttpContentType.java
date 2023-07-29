package org.glavo.webdav.nanohttpd;

/*
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2016 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import org.glavo.webdav.nanohttpd.internal.Pair;
import org.glavo.webdav.nanohttpd.internal.ParameterParser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class HttpContentType {

    public static final HttpContentType PLAIN_TEXT = new HttpContentType("text/plain", null, null);
    public static final HttpContentType PLAIN_HTML = new HttpContentType("text/html", null, null);

    private String contentTypeHeader;

    private final String mimeType;

    private final Charset charset;
    private final String boundary;

    public HttpContentType(String contentTypeHeader) {
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

                ParameterParser parser = new ParameterParser(contentTypeHeader, idx + 1);

                String charsetName = null;
                String boundary = null;

                Pair<String, String> next;
                while ((next = parser.nextParameter()) != null) {
                    if ("charset".equalsIgnoreCase(next.getKey())) {
                        charsetName = next.getValue();
                    } else if ("boundary".equalsIgnoreCase(next.getKey())) {
                        charsetName = next.getValue();
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

    public HttpContentType(String mimeType, Charset charset, String boundary) {
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
