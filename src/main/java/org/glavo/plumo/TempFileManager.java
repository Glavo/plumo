package org.glavo.plumo;

import java.nio.file.Path;

/**
 * Temp file manager.
 * <p/>
 * <p>
 * Temp file managers are created 1-to-1 with incoming requests, to create and
 * cleanup temporary files created as a result of handling the request.
 * </p>
 */
public interface TempFileManager extends AutoCloseable {
    Path createTempFile(String fileNameHint) throws Exception;

    void close();
}
