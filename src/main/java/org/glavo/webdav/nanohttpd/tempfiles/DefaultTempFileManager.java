package org.glavo.webdav.nanohttpd.tempfiles;

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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import org.glavo.webdav.nanohttpd.NanoHTTPD;

import static java.nio.file.attribute.PosixFilePermission.*;

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

    private static final LinkOption[] EMPTY_LINK_OPTIONS = new LinkOption[0];

    private final Path dir;
    private final Set<PosixFilePermission> filePermissions;

    private final List<Path> tempFiles = new ArrayList<>();

    public DefaultTempFileManager() {
        Path dir;
        Set<PosixFilePermission> permissions = null;

        try {
            dir = Files.createTempDirectory("NanoHTTPD-");

            // CVE-2022-21230
            PosixFileAttributeView view = Files.getFileAttributeView(dir, PosixFileAttributeView.class, EMPTY_LINK_OPTIONS);
            if (view != null) {
                permissions = EnumSet.of(OWNER_READ, OWNER_WRITE);
                try {
                    view.setPermissions(permissions);
                } catch (IOException e) {
                    NanoHTTPD.LOG.log(Level.WARNING, "Could not set temporary file permissions", e);
                }
            }
        } catch (IOException e) {
            NanoHTTPD.LOG.log(Level.WARNING, "Failed to create temporary dir", e);

            dir = Paths.get(System.getProperty("java.io.tmpdir"));
            dir.toFile().mkdirs();

            if (Files.getFileAttributeView(dir, PosixFileAttributeView.class, EMPTY_LINK_OPTIONS) != null) {
                permissions = EnumSet.of(OWNER_READ, OWNER_WRITE);
            }
        }

        this.dir = dir;
        this.filePermissions = permissions;
    }

    @Override
    public void close() {
        for (Path file : this.tempFiles) {
            try {
                Files.delete(file);
            } catch (Exception e) {
                NanoHTTPD.LOG.log(Level.WARNING, "Could not delete temporary file", e);
            }
        }
        this.tempFiles.clear();
    }

    private static boolean isSimpleName(String fileName) {
        if (fileName == null)
            return false;

        int len = fileName.length();
        if (len > 20) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            char ch = fileName.charAt(i);
            if (ch > 'z') {
                return false;
            }

            if ((ch >= 'a' /*&& ch <= 'z'*/)
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.'
                    || ch == '_'
                    || ch == '-') {
                continue;
            } else {
                return false;
            }
        }

        return true;
    }

    @Override
    public Path createTempFile(String fileNameHint) throws Exception {
        String prefix = null;
        String suffix = null;

        if (isSimpleName(fileNameHint)) {
            int dotIndex = fileNameHint.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < fileNameHint.length() - 1) {
                prefix = fileNameHint.substring(0, dotIndex);
                suffix = fileNameHint.substring(dotIndex);
            } else {
                prefix = fileNameHint;
            }
        }

        Path tmpFile = Files.createTempFile(dir, prefix, suffix).toAbsolutePath();

        // CVE-2022-21230
        if (filePermissions != null) {
            PosixFileAttributeView view = Files.getFileAttributeView(tmpFile, PosixFileAttributeView.class, EMPTY_LINK_OPTIONS);
            if (view != null) {
                try {
                    view.setPermissions(filePermissions);
                } catch (IOException e) {
                    NanoHTTPD.LOG.log(Level.WARNING, "Could not set file permissions", e);
                }
            }
        }

        return tmpFile;
    }
}
