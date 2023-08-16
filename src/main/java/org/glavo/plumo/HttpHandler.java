package org.glavo.plumo;

@FunctionalInterface
public interface HttpHandler {
    HttpResponse handle(HttpRequest request) throws Exception;
}
