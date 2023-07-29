package org.glavo.webdav;

import org.glavo.webdav.nanohttpd.HttpResponse;
import org.glavo.webdav.nanohttpd.NanoHTTPD;

public final class WebDAVServer {
    private WebDAVServer() {
    }

    public static void main(String[] args) throws Throwable {
        NanoHTTPD nanoHTTPD = new NanoHTTPD(10001);

        nanoHTTPD.setHTTPHandler(httpSession -> {
            System.out.println(httpSession.getHeaders());

            return HttpResponse.newResponse(HttpResponse.Status.UNAUTHORIZED, "你好世界")
                    .setHeader("www-authenticate", "Basic realm=\"Hello World!\"");
        });

        nanoHTTPD.start(false);
    }
}
