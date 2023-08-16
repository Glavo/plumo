package org.glavo.plumo.internal;

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.HttpSession;

public final class DefaultHttpHandler implements HttpHandler {

    private static final HttpResponse RESPONSE = HttpResponse.newTextResponse(
            "<html><header><title>Plumo Default Page</title></header><body>This is the default page for <a href=\"https://github.com/Glavo/plumo\">Plumo</a>.</body></html>",
            "text/html"
    );

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        return RESPONSE;
    }
}
