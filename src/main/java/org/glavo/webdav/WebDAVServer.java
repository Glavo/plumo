package org.glavo.webdav;

import org.glavo.plumo.HttpContentType;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.HttpServer;

import java.nio.file.Path;

public final class WebDAVServer {
    private WebDAVServer() {
    }

    public static void main(String[] args) throws Throwable {
        HttpServer.create(10001)
                .setHttpHandler(request -> {
                    System.out.println(request);
                    System.out.println(request.getCookies());
                    return HttpResponse.newResponse(HttpResponse.Status.OK)
                            .setContentType(HttpContentType.HTML)
                            .setBody("<body>Hello World!</body>");
                }).start();
    }
}
