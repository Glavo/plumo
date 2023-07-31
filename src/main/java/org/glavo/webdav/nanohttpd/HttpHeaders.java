package org.glavo.webdav.nanohttpd;

import java.util.List;
import java.util.function.BiConsumer;

public interface HttpHeaders {

    int size();

    boolean contains(String name);

    String get(String name);

    List<String> getAll(String name);

    void forEach(BiConsumer<String, String> consumer);
}
