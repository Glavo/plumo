package org.glavo.nanohttpd.protocols.http.mime;

import java.util.Map;

public interface MIMETypesProvider {
    void registerMIMETypes(Map<String, String> types);
}
