package org.glavo.webdav.nanohttpd;

import java.io.IOException;

@FunctionalInterface
public interface HttpHandler {
    HttpResponse handle(HttpRequest request) throws IOException;
}
