package org.glavo.webdav;

import org.glavo.webdav.nanohttpd.HttpContentType;
import org.glavo.webdav.nanohttpd.HttpResponse;
import org.glavo.webdav.nanohttpd.HttpServer;

import java.nio.file.Path;

public final class WebDAVServer {
    private WebDAVServer() {
    }

    public static void main(String[] args) throws Throwable {
        HttpServer.create(Path.of("D:\\unix-domain-socket"))
                .setUseVirtualThreadExecutor()
                .setHttpHandler(session -> {
                    System.out.println("======" + session.getMethod() + "======");
                    System.out.println(session.getUri());
                    System.out.println(session.getHeaders());
                    return HttpResponse.newResponse(HttpResponse.Status.OK)
                            .setContentType(HttpContentType.PLAIN_HTML)
                            .setBody("<body>Hello World!</body>");
                }).start();
    }
}
