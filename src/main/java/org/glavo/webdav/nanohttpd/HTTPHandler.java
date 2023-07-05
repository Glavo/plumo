package org.glavo.webdav.nanohttpd;

import org.glavo.webdav.nanohttpd.response.Response;
import org.glavo.webdav.nanohttpd.response.StandardStatus;

import java.util.ArrayList;
import java.util.Objects;

@FunctionalInterface
public interface HTTPHandler {

    Response handle(HTTPSession session);

    HTTPHandler DEFAULT = session -> Response.newFixedLengthResponse(StandardStatus.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");

    static Builder builder() {
        return new Builder(DEFAULT);
    }

    static Builder builder(HTTPHandler defaultHandler) {
        return new Builder(defaultHandler);
    }

    final class Builder {
        private final ArrayList<HTTPHandler> interceptors = new ArrayList<>();
        private HTTPHandler defaultHandle;

        Builder(HTTPHandler defaultHandle) {
            this.defaultHandle = defaultHandle;
        }

        public Builder addInterceptor(HTTPHandler interceptor) {
            this.interceptors.add(Objects.requireNonNull(interceptor));
            return this;
        }

        public Builder setDefaultHandler(HTTPHandler defaultHandle) {
            this.defaultHandle = Objects.requireNonNull(defaultHandle);
            return this;
        }

        public HTTPHandler build() {
            if (interceptors.isEmpty()) {
                return defaultHandle;
            } else {
                return session -> {
                    for (HTTPHandler interceptor : interceptors) {
                        Response response = interceptor.handle(session);
                        if (response != null) {
                            return response;
                        }
                    }

                    return defaultHandle.handle(session);
                };
            }
        }
    }
}
