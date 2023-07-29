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

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Default threading strategy for NanoHTTPD.
 * <p/>
 * <p>
 * By default, the server spawns a new Thread for every incoming request. These
 * are set to <i>daemon</i> status, and named according to the request number.
 * The name is useful when profiling the application.
 * </p>
 */
public final class AsyncRunner implements AutoCloseable {

    private volatile ClientHandler firstHandle, lastHandle;

    private final Executor executor;
    private final boolean shutdown;

    private final ReentrantLock lock = new ReentrantLock();

    public AsyncRunner(Executor executor, boolean shutdown) {
        this.executor = executor;
        this.shutdown = shutdown;
    }

    void remove(ClientHandler handler) {
        lock.lock();
        try {
            ClientHandler next = handler.next;
            ClientHandler prev = handler.prev;

            if (prev == null) {
                firstHandle = next;
            } else {
                prev.next = next;
                handler.prev = null;
            }

            if (next == null) {
                lastHandle = prev;
            } else {
                next.prev = prev;
                handler.next = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void exec(ClientHandler handler) {
        lock.lock();
        try {
            ClientHandler last = this.lastHandle;

            if (last == null) {
                firstHandle = handler;
            } else {
                last.next = handler;
                handler.prev = last;
            }

            lastHandle = handler;
        } finally {
            lock.unlock();
        }

        executor.execute(handler);
    }

    @Override
    public void close() {
        lock.lock();
        try {
            ClientHandler handler = firstHandle;

            while (handler != null) {
                handler.close();
                handler = handler.next;
            }

            firstHandle = null;
            lastHandle = null;

            if (shutdown) {
                ((ExecutorService) executor).shutdown();
            }
        } finally {
            lock.unlock();
        }
    }

    public static final class DefaultExecutor implements Executor {
        private long requestCount;

        @Override
        public void execute(@NotNull Runnable command) {
            Thread t = new Thread(command, "NanoHttpd Request Processor (#" + this.requestCount++ + ")");
            t.setDaemon(true);
            t.start();
        }
    }

    public static final class VirtualThreadExecutor implements Executor {
        private static final boolean AVAILABLE;
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

        private long requestCount;

        public VirtualThreadExecutor() {
        }

        @Override
        public void execute(@NotNull Runnable command) {
            try {
                Thread t = (Thread) newThread.invokeExact(command);
                t.setName("NanoHttpd Request Processor (#" + this.requestCount++ + ")");
                t.start();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
