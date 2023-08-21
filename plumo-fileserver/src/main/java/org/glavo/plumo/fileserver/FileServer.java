package org.glavo.plumo.fileserver;

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.Plumo;
import org.glavo.plumo.fileserver.internal.MimeTable;
import org.glavo.plumo.fileserver.internal.FileServerUtils;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class FileServer implements HttpHandler {

    public enum OutputLevel {
        NONE, INFO, VERBOSE
    }

    private static final DateTimeFormatter HTTP_TIME_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy HH:mm:ss Z", Locale.US);

    private static final HttpResponse METHOD_NOT_ALLOWED = HttpResponse.newResponse(HttpResponse.Status.METHOD_NOT_ALLOWED)
            .addHeader("allow", "HEAD, GET")
            .freeze();

    private static final String OPEN_HTML = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/></head><body>";
    private static final String CLOSE_HTML = "</body></html>";

    private static final HttpResponse NOT_FOUND = HttpResponse.newResponse(HttpResponse.Status.NOT_FOUND).freeze();
    private static final HttpResponse FORBIDDEN = HttpResponse.newResponse(HttpResponse.Status.FORBIDDEN).freeze();

    private final Path root;
    private final FileNameMap mimeTable;
    private final OutputLevel outputLevel;
    private final PrintStream logOutput;
    private final boolean doCache;

    private FileServer(Path root, FileNameMap mimeTable, OutputLevel outputLevel, PrintStream logOutput, boolean doCache) {
        this.root = root;
        this.mimeTable = mimeTable;
        this.outputLevel = outputLevel;
        this.logOutput = logOutput;
        this.doCache = doCache;
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
                FileServerUtils.encodeURL(builder, dirBase);
                FileServerUtils.encodeURL(builder, fileName);
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

    private HttpResponse handleFile(HttpRequest request, Path file, BasicFileAttributes attributes) {
        Instant lastModifiedTime = attributes.lastModifiedTime().toInstant();
        String httpTime = HTTP_TIME_FORMATTER.format(lastModifiedTime);

        String mime = mimeTable.getContentTypeFor(file.getFileName().toString());

        if (mime != null && doCache) {
            if (httpTime.equals(request.getHeader("if-modified-since"))) {
                return HttpResponse.newResponse(HttpResponse.Status.NOT_MODIFIED);
            }
        }

        FileChannel channel = null;
        try {
            // Check Permissions
            channel = FileChannel.open(file);

            HttpResponse response = HttpResponse.newResponse()
                    .withHeader("last-modified", HTTP_TIME_FORMATTER.format(lastModifiedTime));

            if (mime != null) {
                response = response.withHeader("content-type", mime);
            }

            return response.withBody(Channels.newInputStream(channel), channel.size());
        } catch (Throwable e) {
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException ignored) {
            }

            return FORBIDDEN;
        }
    }

    private void logRequest(HttpRequest request, HttpResponse response) {
        if (outputLevel == OutputLevel.NONE) {
            return;
        }

        StringBuilder log = new StringBuilder(256);

        SocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress instanceof InetSocketAddress) {
            log.append(((InetSocketAddress) remoteAddress).getAddress().getHostAddress());
        } else {
            log.append("127.0.0.1");
        }

        log.append(" -- [");
        LOG_TIME_FORMATTER.formatTo(OffsetDateTime.now(), log);
        log.append("] \"");
        log.append(request.getMethod()).append(' ').append(request.getRawURI()).append(' ').append(request.getHttpVersion());
        log.append("\" ");
        log.append(response.getStatus().getStatusCode()).append(" -");

        if (outputLevel == OutputLevel.VERBOSE) {
            log.append('\n');
            log.append(request);
            log.append('\n');
            log.append(response);
            log.append('\n');
        }

        logOutput.println(log);
    }

    private HttpResponse handleImpl(HttpRequest request) {
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
                path = root.resolve(p).toAbsolutePath().normalize();
                if (!path.startsWith(root)) {
                    return FORBIDDEN;
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
                try {
                    BasicFileAttributes indexFileAttributes = Files.readAttributes(indexFile, BasicFileAttributes.class);
                    return handleFile(request, indexFile, indexFileAttributes);
                } catch (Throwable ignored) {
                }
            }

            return listFiles(request.getURI(), path, attributes);
        } else {
            return handleFile(request, path, attributes);
        }
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        HttpResponse response = handleImpl(request);
        logRequest(request, response);
        return response;
    }

    private static final String HELP_MESSAGE = "Usage: plumo-fileserver [-b bind address] [-p port] [-d directory]\n" +
                                               "\n" +
                                               "Options:\n" +
                                               "\n" +
                                               "  -b, --bind-address <bind address> \n" +
                                               "                        Address to bind to. Default: 0.0.0.0 (all interfaces).\n" +
                                               "                        For loopback use \"-b 127.0.0.1\" or \"-b ::1\".\n" +
                                               "                        For unix domain socket use \"-b unix:<socket file>\".\n" +
                                               "  -p, --port <port>     Port to listen on. Default: 8000.\n" +
                                               "  -d, --directory <directory>       \n" +
                                               "                        Directory to serve. Default: current directory.\n" +
                                               "  -?, -h, --help        Prints this help message and exits.\n" +
                                               "  --version             Prints version information and exits.";

    private static String getVersion() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                FileServer.class.getResourceAsStream("version.txt"), StandardCharsets.UTF_8))) {
            return reader.readLine();
        } catch (IOException | NullPointerException e) {
            return "unknown";
        }
    }

    private static String nextArg(String[] args, int index) {
        if (index < args.length - 1) {
            return args[index + 1];
        } else {
            System.err.println("Error: no value given for " + args[index]);
            System.exit(1);
            throw new AssertionError();
        }
    }

    private static void reportDuplicateOption(String opt) {
        System.err.println("Error: duplicate option: " + opt);
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        InetAddress addr = null;
        Path unixAddr = null;
        OutputLevel outputLevel = null;
        Path root = null;
        int port = -1;
        boolean doCache = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h":
                case "-?":
                case "-help":
                case "--help":
                    System.out.println(HELP_MESSAGE);
                    return;
                case "-version":
                case "--version":
                    System.out.println("plumo-fileserver " + getVersion());
                    return;
                case "-b":
                case "-bind-address":
                case "--bind-address":
                    if (addr != null || unixAddr != null) {
                        reportDuplicateOption(arg);
                    }

                    String addrString = nextArg(args, i++);

                    if (addrString.startsWith("unix:")) {
                        unixAddr = Paths.get(addrString.substring("unix:".length())).toAbsolutePath().normalize();
                    } else {
                        addr = InetAddress.getByName(addrString);
                    }
                    break;
                case "-d":
                case "-directory":
                case "--directory":
                    if (root != null) {
                        reportDuplicateOption(arg);
                    }
                    root = Paths.get(nextArg(args, i++)).toAbsolutePath().normalize();
                    break;
                case "-output-level":
                case "--output-level":
                    if (outputLevel != null) {
                        reportDuplicateOption(arg);
                    }
                    outputLevel = OutputLevel.valueOf(nextArg(args, i++).toUpperCase(Locale.ROOT));
                    break;
                case "-p":
                case "-port":
                case "--port":
                    if (port > 0) {
                        reportDuplicateOption(arg);
                    }
                    port = Integer.parseInt(nextArg(args, i++));
                    if (port < 0 || port >= 65536) {
                        System.err.println("Error: invalid port: " + port);
                        System.exit(1);
                    }
                    break;
                // Undocumented
                case "--do-cache":
                    doCache = true;
                    break;
                default:
                    System.err.println("Error: unknown option: " + arg);
                    System.exit(1);
            }
        }


        if (port == -1) {
            port = 8000;
        }

        InetSocketAddress inetSocketAddress;
        if (unixAddr != null) {
            inetSocketAddress = null;
        } else if (addr == null) {
            inetSocketAddress = new InetSocketAddress(port);
        } else {
            inetSocketAddress = new InetSocketAddress(addr, port);
        }

        if (root == null) {
            root = Paths.get("").toAbsolutePath().normalize();
        }

        if (outputLevel == null) {
            outputLevel = OutputLevel.INFO;
        }

        FileServer server = new FileServer(root, new MimeTable(), outputLevel, System.out, doCache);

        Plumo plumo = Plumo.create();
        if (inetSocketAddress != null) {
            plumo.bind(inetSocketAddress);
        } else {
            plumo.bind(unixAddr);
        }
        plumo.setHandler(server);
        plumo.startInNewThread(false);

        if (unixAddr == null) {
            InetSocketAddress localAddress = (InetSocketAddress) plumo.getLocalAddress();
            String hostAddress = localAddress.getAddress().getHostAddress();

            String url;
            if (localAddress.getAddress().isAnyLocalAddress()) {
                url = "http://localhost:" + localAddress.getPort();
            } else {
                url = "http://" + hostAddress + ":" + localAddress.getPort();
            }

            System.out.printf("Serving %s and subdirectories on %s port %s (%s)%n", root, hostAddress, localAddress.getPort(), url);
        } else {
            System.out.println("Serving " + root + " and subdirectories on unix:" + unixAddr);
        }
    }
}
