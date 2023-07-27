package org.glavo.webdav.nanohttpd.internal;

/*
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2016 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import org.glavo.webdav.nanohttpd.NanoHTTPD;
import org.glavo.webdav.nanohttpd.TempFileManager;

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
            dir = Files.createTempDirectory("NanoHTTPD-");

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
            NanoHTTPD.LOG.log(Level.WARNING, "Failed to create temporary dir", e);
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
                NanoHTTPD.LOG.log(Level.WARNING, "Could not delete temporary file", e);
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
