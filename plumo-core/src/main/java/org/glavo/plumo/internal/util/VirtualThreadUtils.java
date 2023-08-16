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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class VirtualThreadUtils {
    public static final boolean AVAILABLE;

    private static final boolean NEED_ENABLE_PREVIEW;

    private static final MethodHandle newThread;

    static {
        boolean available = false;
        boolean needEnablePreview = false;
        MethodHandle newThreadHandle = null;

        try {
            Class<?> vtBuilder = Class.forName("java.lang.Thread$Builder$OfVirtual");

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object factory = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(vtBuilder)).invoke();

            newThreadHandle = lookup.findVirtual(vtBuilder, "unstarted", MethodType.methodType(Thread.class, Runnable.class))
                    .bindTo(factory);

            available = true;
        } catch (UnsupportedOperationException ignored) {
            needEnablePreview = true;
        } catch (Throwable ignored) {
        }

        AVAILABLE = available;
        NEED_ENABLE_PREVIEW = needEnablePreview;
        newThread = newThreadHandle;
    }

    public static void checkAvailable() {
        if (!AVAILABLE) {
            throw new UnsupportedOperationException(NEED_ENABLE_PREVIEW
                    ? "Preview Features not enabled, need to run with --enable-preview"
                    : "Please upgrade to Java 19+");
        }
    }

    public static Thread newVirtualThread(Runnable command) {
        try {
            return (Thread) newThread.invokeExact(command);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }


    private VirtualThreadUtils() {
    }
}
