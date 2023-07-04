package org.glavo.webdav.nanohttpd.mime;

import java.util.Map;

public interface MimeTypesProvider {
    void registerMIMETypes(Map<String, String> types);
}
