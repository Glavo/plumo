package org.glavo.plumo.internal;

import org.glavo.plumo.TempFileManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Default strategy for creating and cleaning up temporary files.
 * <p/>
 * <p>
 * This class stores its files in the standard location (that is, wherever
 * <code>java.io.tmpdir</code> points to). Files are added to an internal list,
 * and deleted when no longer needed (that is, when <code>clear()</code> is
 * invoked at the end of processing a request).
 * </p>
 */
public final class DefaultTempFileManager implements TempFileManager {

    private static final FileAttribute<?>[] EMPTY_FILE_ATTRIBUTES = new FileAttribute<?>[0];

    private static final Path TEMP_DIR;
    private static final boolean FALLBACK;

    static {
        Path dir;
        boolean fallback = false;

        try {
            dir = Files.createTempDirectory("plumo-");

            File d = dir.toFile();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!d.exists()) {
                    return;
                }

                File[] list = d.listFiles();
                if (list != null) {
                    for (File file : list) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }

                d.delete();
            }));
        } catch (IOException e) {
            HttpListener.LOG.log(Level.WARNING, "Failed to create temporary dir", e);
            dir = Paths.get(System.getProperty("java.io.tmpdir"));
            fallback = true;
        }

        TEMP_DIR = dir;
        FALLBACK = fallback;
    }

    private List<Path> tempFiles = new ArrayList<>();
    private Thread shutdownHook;

    public DefaultTempFileManager() {
        if (FALLBACK) {
            shutdownHook = new Thread(this::closeImpl);
        }
    }

    private void closeImpl() {
        if (tempFiles == null) {
            return;
        }

        for (Path file : this.tempFiles) {
            try {
                Files.deleteIfExists(file);
            } catch (Exception e) {
                HttpListener.LOG.log(Level.WARNING, "Could not delete temporary file", e);
            }
        }
        tempFiles = null;
    }

    @Override
    public void close() {
        closeImpl();
        if (FALLBACK) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }
    }

    @Override
    public Path createTempFile(String fileNameHint) throws Exception {
        Path tempFile = Files.createTempFile(TEMP_DIR, null, null, EMPTY_FILE_ATTRIBUTES).toAbsolutePath();
        tempFiles.add(tempFile);
        return tempFile;
    }
}
