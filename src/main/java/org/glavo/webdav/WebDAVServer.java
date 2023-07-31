package org.glavo.webdav;

import org.glavo.plumo.HttpContentType;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.HttpServer;

import java.nio.file.Path;

public final class WebDAVServer {
    private WebDAVServer() {
    }

    public static void main(String[] args) throws Throwable {
        HttpServer.create(Path.of("D:\\unix-domain-socket"), true)
                .setHttpHandler(request -> {
                    System.out.println(request);
                    return HttpResponse.newResponse(HttpResponse.Status.OK)
                            .setContentType(HttpContentType.HTML)
                            .setBody("<body>Hello World!</body>");
                }).start();
    }
}
