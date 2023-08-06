package org.glavo.webdav;

import org.glavo.plumo.*;

public final class WebDAVServer {
    private WebDAVServer() {
    }

    public static void main(String[] args) throws Throwable {
        Plumo.create(10001)
                .setHttpHandler(request -> {
                    System.out.println(Thread.currentThread());
                    System.out.println(request);
                    System.out.println(request.getBody(HttpDataFormat.TEXT));

                    return HttpResponse.newResponse(HttpResponse.Status.OK)
                            .setContentType(ContentType.HTML)
                            .setBody("<body>Hello World!</body>");
                }).start();
    }
}
