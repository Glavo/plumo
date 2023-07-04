package org.glavo.webdav.nanohttpd.protocols.http.mime;

import java.util.Map;

public class DefaultMimeTypesProvider implements MimeTypesProvider {
    @Override
    public void registerMIMETypes(Map<String, String> types) {
        types.put("css", "text/css");
        types.put("htm", "text/html");
        types.put("html", "text/html");
        types.put("xml", "text/xml");
        types.put("java", "text/x-java-source, text/java");
        types.put("md", "text/plain");
        types.put("txt", "text/plain");
        types.put("asc", "text/plain");
        types.put("gif", "image/gif");
        types.put("jpg", "image/jpeg");
        types.put("jpeg", "image/jpeg");
        types.put("png", "image/png");
        types.put("svg", "image/svg+xml");
        types.put("mp3", "audio/mpeg");
        types.put("m3u", "audio/mpeg-url");
        types.put("mp4", "video/mp4");
        types.put("ogv", "video/ogg");
        types.put("flv", "video/x-flv");
        types.put("mov", "video/quicktime");
        types.put("swf", "application/x-shockwave-flash");
        types.put("js", "application/javascript");
        types.put("pdf", "application/pdf");
        types.put("doc", "application/msword");
        types.put("ogg", "application/x-ogg");
        types.put("zip", "application/octet-stream");
        types.put("exe", "application/octet-stream");
        types.put("class", "application/octet-stream");
        types.put("m3u8", "application/vnd.apple.mpegurl");
        types.put("ts", "video/mp2t");
    }
}
