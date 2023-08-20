package org.glavo.plumo.webserver;

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.Plumo;
import org.glavo.plumo.internal.Constants;
import org.glavo.plumo.webserver.internal.WebServerUtils;

import java.io.IOException;
import java.net.FileNameMap;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class WebServer implements HttpHandler {

    private static final HttpResponse METHOD_NOT_ALLOWED = HttpResponse.newResponse(HttpResponse.Status.METHOD_NOT_ALLOWED)
            .addHeader("allow", "HEAD, GET")
            .freeze();

    private static final String OPEN_HTML = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/></head><body>";
    private static final String CLOSE_HTML = "</body></html>";

    private static final HttpResponse NOT_FOUND = HttpResponse.newResponse(HttpResponse.Status.NOT_FOUND).freeze();

    private final Path root;
    private final FileNameMap mimeTable = URLConnection.getFileNameMap();

    public WebServer(Path root) {
        this.root = root.normalize().toAbsolutePath();
    }

    private static void sanitize(StringBuilder builder, String str) {
        for (int i = 0, len = str.length(); i < len; i++) {
            char ch = str.charAt(i);

            switch (ch) {
                case '&':
                    builder.append("&amp;");
                    break;
                case '<':
                    builder.append("&lt;");
                    break;
                case '>':
                    builder.append("&gt;");
                    break;
                case '"':
                    builder.append("&quot;");
                    break;
                case '\'':
                    builder.append("&#x27;");
                    break;
                case '/':
                    builder.append("&#x2F;");
                    break;
                default:
                    builder.append(ch);
            }
        }
    }

    private static String getRedirectURI(URI uri) {
        String query = uri.getRawQuery();
        String redirectPath = uri.getRawPath() + "/";
        return query == null ? redirectPath : redirectPath + "?" + query;
    }

    private static String appendURI(URI uri, String str) {
        String query = uri.getRawQuery();
        String redirectPath = uri.getRawPath() + str;
        return query == null ? redirectPath : redirectPath + "?" + query;
    }

    private static Path findIndexFile(Path path) {
        Path html = path.resolve("index.html");
        if (Files.isReadable(html)) {
            return html;
        }

        Path htm = path.resolve("index.htm");
        if (Files.isReadable(htm)) {
            return htm;
        }

        return null;
    }

    private HttpResponse notFound(URI uri) {
        StringBuilder builder = new StringBuilder();
        builder.append(OPEN_HTML);
        builder.append("<h1>File not found</h1>");
        builder.append(CLOSE_HTML);

        return HttpResponse.newResponse(HttpResponse.Status.NOT_FOUND)
                .withBody(builder.toString());
    }

    private HttpResponse listFiles(URI uri, Path path, BasicFileAttributes attributes) {
        String dirBase = uri.getPath();
        assert dirBase.endsWith("/");

        StringBuilder builder = new StringBuilder(1024);
        builder.append(OPEN_HTML + "<h1>Directory listing for ");
        sanitize(builder, dirBase);
        builder.append("</h1><ul>\n");

        try (DirectoryStream<Path> paths = Files.newDirectoryStream(path)) {
            for (Path file : paths) {
                if (Files.isHidden(file)) {
                    continue;
                }

                boolean isDir = Files.isDirectory(file);

                String fileName = file.getFileName().toString();

                builder.append("<li><a href=\"");
                WebServerUtils.encodeURL(builder, dirBase);
                WebServerUtils.encodeURL(builder, fileName);
                if (isDir) {
                    builder.append('/');
                }
                builder.append("\">");
                sanitize(builder, fileName);
                if (isDir) {
                    builder.append('/');
                }
                builder.append("</a></li>\n");

            }
        } catch (IOException | DirectoryIteratorException e) {
            return notFound(uri);
        }

        builder.append("</ul>" + CLOSE_HTML);

        return HttpResponse.newResponse()
                .withHeader("content-type", "text/html; charset=UTF-8")
                .withBody(builder.toString());
    }

    private HttpResponse handleFile(Path file, BasicFileAttributes attributes) {
        String mime = mimeTable.getContentTypeFor(file.getFileName().toString());
        if (mime == null) {
            mime = "application/octet-stream";
        }

        return HttpResponse.newResponse()
                .withHeader("content-type", mime)
                .withHeader("last-modified", Constants.HTTP_TIME_FORMATTER.format(attributes.lastModifiedTime().toInstant()))
                .withBody(file);
    }

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        if (request.getMethod() != HttpRequest.Method.GET && request.getMethod() != HttpRequest.Method.HEAD) {
            return METHOD_NOT_ALLOWED;
        }

        URI uri = request.getURI();

        Path path;
        try {
            String p = uri.getPath();
            for (int i = 0; i < p.length(); i++) {
                if (p.charAt(i) != '/') {
                    p = p.substring(i);
                    break;
                }
            }

            if (p.isEmpty() || p.equals("/")) {
                path = root;
            } else {
                path = root.resolve(p).normalize().toAbsolutePath();
                if (!path.startsWith(root)) {
                    return HttpResponse.newResponse(HttpResponse.Status.FORBIDDEN);
                }
            }
        } catch (Exception e) {
            return notFound(uri);
        }

        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            return notFound(uri);
        }

        if (!Files.isReadable(path)) {
            return notFound(uri);
        }

        if (attributes.isDirectory()) {
            String p = request.getURI().getPath();

            if (p.isEmpty() || p.charAt(p.length() - 1) != '/') {
                return HttpResponse.newResponse(HttpResponse.Status.REDIRECT)
                        .withHeader("location", appendURI(uri, "/"));
            }

            Path indexFile = findIndexFile(path);
            if (indexFile != null) {
                BasicFileAttributes indexFileAttributes;

                try {
                    indexFileAttributes = Files.readAttributes(indexFile, BasicFileAttributes.class);
                } catch (Throwable e) {
                    return NOT_FOUND;
                }

                return handleFile(indexFile, indexFileAttributes);
            } else {
                return listFiles(request.getURI(), path, attributes);
            }
        } else {
            return handleFile(path, attributes);
        }
    }

    public static void main(String[] args) throws Exception {
        Plumo plumo = Plumo.create();
        plumo.setHandler(new WebServer(Paths.get("")));
        ;
        plumo.bind(8888);
        plumo.start();
    }
}
