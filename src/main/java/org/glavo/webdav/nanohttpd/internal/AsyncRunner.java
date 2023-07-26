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

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Default threading strategy for NanoHTTPD.
 * <p/>
 * <p>
 * By default, the server spawns a new Thread for every incoming request. These
 * are set to <i>daemon</i> status, and named according to the request number.
 * The name is useful when profiling the application.
 * </p>
 */
public class AsyncRunner implements AutoCloseable {

    private volatile ClientHandler firstHandle, lastHandle;

    private final Executor executor;
    private final boolean shutdown;

    public AsyncRunner() {
        this(new DefaultExecutor());
    }

    public AsyncRunner(Executor executor) {
        this.executor = executor;
        this.shutdown = false;
    }

    public AsyncRunner(ExecutorService executor) {
        this.executor = executor;
        this.shutdown = true;
    }

    synchronized void remove(ClientHandler handler) {
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
    }

    public void exec(ClientHandler handler) {
        synchronized (this) {
            ClientHandler last = this.lastHandle;

            if (last == null) {
                firstHandle = handler;
            } else {
                last.next = handler;
                handler.prev = last;
            }

            lastHandle = handler;
        }

        executor.execute(handler);
    }

    @Override
    public synchronized void close() {
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
    }

    private static final class DefaultExecutor implements Executor {
        private long requestCount;

        @Override
        public void execute(@NotNull Runnable command) {
            Thread t = new Thread(command);
            t.setDaemon(true);
            t.setName("NanoHttpd Request Processor (#" + this.requestCount++ + ")");
            t.start();
        }
    }
}
