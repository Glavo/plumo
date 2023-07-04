package org.glavo.webdav.nanohttpd.protocols.http.mime;

import java.util.Map;

public interface MimeTypesProvider {
    void registerMIMETypes(Map<String, String> types);
}
