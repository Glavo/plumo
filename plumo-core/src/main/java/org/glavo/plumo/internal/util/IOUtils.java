/*
 * Copyright 2023 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.plumo.internal.util;

import org.glavo.plumo.internal.Constants;
import org.glavo.plumo.internal.DefaultLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class IOUtils {
    private IOUtils() {
    }

    public static void safeClose(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            DefaultLogger.log(DefaultLogger.Level.ERROR, "Could not close", e);
        }
    }

    public static void deleteIfExists(Path file) {
        try {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (Throwable e) {
            DefaultLogger.log(DefaultLogger.Level.ERROR, "Could not delete file", e);
        }
    }

    public static void shutdown(Executor executor) {
        if (!(executor instanceof ExecutorService)) {
            return;
        }

        ExecutorService es = (ExecutorService) executor;
        boolean terminated = es.isTerminated();
        if (!terminated) {
            es.shutdown();
            boolean interrupted = false;
            while (!terminated) {
                try {
                    terminated = es.awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    if (!interrupted) {
                        es.shutdownNow();
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
