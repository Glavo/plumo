package org.glavo.plumo.internal;

import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.ContentType;
import org.glavo.plumo.Cookie;
import org.glavo.plumo.internal.util.IOUtils;
import org.glavo.plumo.internal.util.MultiStringMap;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

public final class HttpResponseImpl implements HttpResponse, Cloneable {

    MultiStringMap headers = new MultiStringMap();

    Status status;
    Instant date;

    String connection;

    // InputStream | String | byte[]
    Object body;
    long contentLength;
    ContentType contentType;
    String contentEncoding;

    List<Cookie> cookies;

    public HttpResponseImpl() {
    }

    public HttpResponseImpl(Status status) {
        this.status = status;
    }

    public HttpResponseImpl(Status status, Object body, ContentType contentType) {
        this.status = status;
        this.body = body;
        this.contentType = contentType;
    }

    public boolean isReusable() {
        return !(body instanceof InputStream);
    }

    // ----

    boolean needCloseConnection() {
        return "close".equals(connection);
    }

    void finish() {
        if (body instanceof InputStream) {
            IOUtils.safeClose((InputStream) body);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected HttpResponseImpl clone() throws CloneNotSupportedException {
        if (!(isReusable())) {
            throw new CloneNotSupportedException();
        }

        HttpResponseImpl res = (HttpResponseImpl) super.clone();
        res.headers = new MultiStringMap((HashMap<String, Object>) headers.map.clone());
        if (cookies != null) {
            res.cookies = new ArrayList<>(cookies);
        }
        return res;
    }

    public static final class BuilderImpl implements HttpResponse.Builder {
        private HttpResponseImpl response;
        private boolean aliased;

        public BuilderImpl() {
            this.response = new HttpResponseImpl();
            this.aliased = false;
        }

        public BuilderImpl(HttpResponse response) {
            this.response = (HttpResponseImpl) response;
            this.aliased = true;
        }

        private void ensureUnaliased() {
            if (aliased) {
                try {
                    response = response.clone();
                } catch (CloneNotSupportedException e) {
                    throw new UnsupportedOperationException();
                }
                aliased = false;
            }
        }

        @Override
        public HttpResponse build() {
            aliased = true;
            return response;
        }

        @Override
        public Builder setStatus(Status status) {
            ensureUnaliased();
            response.status = status;
            return this;
        }

        @Override
        public Builder setDate(Instant date) {
            ensureUnaliased();
            response.date = date;
            return this;
        }

        @Override
        public Builder addHeader(String name, String value) {
            Objects.requireNonNull(value);
            ensureUnaliased();

            name = name.toLowerCase(Locale.ROOT);
            switch (name) {
                case "connection":
                    response.connection = value;
                    break;
                case "content-length":
                    if (!(response.body instanceof InputStream)) {
                        throw new UnsupportedOperationException();
                    }

                    long len = Long.parseLong(value);
                    if (len < 0) {
                        throw new IllegalArgumentException();
                    }
                    response.contentLength = len;
                    break;
                case "content-type":
                    response.contentType = new ContentType(value);
                    break;
                case "content-encoding":
                    response.contentEncoding = value;
                    break;
                case "date":
                    response.date = Instant.from(Constants.HTTP_TIME_FORMATTER.parse(value));
                    break;
                default:
                    response.headers.add(name, value);
            }

            return this;
        }

        @Override
        public Builder setContentType(ContentType contentType) {
            ensureUnaliased();
            response.contentType = contentType;
            return this;
        }

        @Override
        public Builder setContentType(String contentType) {
            ensureUnaliased();
            response.contentType = new ContentType(contentType);
            return this;
        }

        @Override
        public Builder setContentEncoding(String contentEncoding) {
            ensureUnaliased();
            response.contentEncoding = contentEncoding;
            return this;
        }

        @Override
        public Builder setKeepAlive(boolean keepAlive) {
            ensureUnaliased();
            response.connection = keepAlive ? "keep-alive" : "close";
            return this;
        }

        @Override
        public Builder addCookies(Iterable<? extends Cookie> cookies) {
            ensureUnaliased();
            if (response.cookies == null) {
                response.cookies = new ArrayList<>();
            }

            for (Cookie cookie : cookies) {
                response.cookies.add(cookie);
            }

            return this;
        }

        @Override
        public Builder setBody(byte[] data) {
            ensureUnaliased();
            response.body = data;
            response.contentLength = -1;
            return this;
        }

        @Override
        public Builder setBody(String data) {
            ensureUnaliased();
            response.body = data;
            response.contentLength = -1;
            return this;
        }

        @Override
        public Builder setBody(InputStream data, long contentLength) {
            ensureUnaliased();
            if (contentLength < 0) {
                throw new IllegalArgumentException();
            }

            response.body = data;
            response.contentLength = contentLength;
            return this;
        }

        @Override
        public Builder setBodyUnknownSize(InputStream data) {
            response.body = data;
            response.contentLength = -1;
            return this;
        }
    }
}
