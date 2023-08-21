package org.glavo.plumo.webserver.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public final class MimeTable implements FileNameMap {

    private static final Map<String, String> TABLE = new HashMap<>();

    static {
        Properties properties = new Properties();
        try (InputStream input = MimeTable.class.getResourceAsStream("default-mimetypes.properties")) {
            properties.load(input);
        } catch (IOException e) {
            throw new Error(e);
        }

        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String mimeType = ((String) entry.getKey());
            String value = (String) entry.getValue();

            value = value.trim();

            if (mimeType.isEmpty() || value.isEmpty()) {
                continue;
            }

            if (value.indexOf(',') < 0) {
                TABLE.put(value, mimeType);
            } else {
                String[] exts = value.split(",");
                for (String s : exts) {
                    String ext = s.trim();
                    if (!ext.isEmpty()) {
                        TABLE.put(ext, mimeType);
                    }
                }
            }

        }
    }

    @Override
    public String getContentTypeFor(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < fileName.length() - 1) {
            return TABLE.get(fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT));
        } else {
            switch (fileName.toUpperCase(Locale.ROOT)) {
                case "README":
                case "LICENSE":
                    return "text/plain";
                default:
                    return null;
            }
        }
    }
}
