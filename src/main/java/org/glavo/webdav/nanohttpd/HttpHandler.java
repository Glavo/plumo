package org.glavo.webdav.nanohttpd;

import java.io.IOException;

@FunctionalInterface
public interface HttpHandler {
    HttpResponse handle(HttpSession session) throws IOException;
}
