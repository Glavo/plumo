package org.glavo.webdav;

import org.glavo.webdav.nanohttpd.NanoHTTPD;
import org.glavo.webdav.nanohttpd.response.Response;
import org.glavo.webdav.nanohttpd.response.Status;

public final class WebDAVServer {
    private WebDAVServer() {
    }

    public static void main(String[] args) throws Throwable {
        NanoHTTPD nanoHTTPD = new NanoHTTPD(10001);

        nanoHTTPD.setHTTPHandler(httpSession -> {
            System.out.println(httpSession.getHeaders());

            Response response = Response.newFixedLengthResponse(Status.UNAUTHORIZED, NanoHTTPD.MIME_PLAINTEXT, "你好世界");
            response.addHeader("www-authenticate", "Basic realm=\"Hello World!\"");
            return response;
        });

        nanoHTTPD.start(false);
    }
}
