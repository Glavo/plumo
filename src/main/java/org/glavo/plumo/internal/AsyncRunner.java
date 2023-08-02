package org.glavo.plumo.internal;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;

public final class AsyncRunner implements AutoCloseable {

    private volatile ClientHandler firstHandle, lastHandle;

    private final Executor executor;
    private final boolean shutdown;

    private volatile boolean closed = false;

    private final ReentrantLock lock = new ReentrantLock();

    public AsyncRunner() {
        this.executor = VirtualThreadExecutor.AVAILABLE ? new VirtualThreadExecutor() : new DefaultExecutor();
        this.shutdown = false;
    }

    public AsyncRunner(Executor executor, boolean shutdown) {
        this.executor = executor;
        this.shutdown = shutdown;
    }

    void close(ClientHandler handler) {
        lock.lock();
        try {
            if (closed) {
                return;
            }

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
            handler.closeSocket();
        }
    }

    void exec(ClientHandler handler) {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("Runner is closed");
            }

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
            if (closed) {
                return;
            }

            closed = true;

            ClientHandler handler = firstHandle;

            while (handler != null) {
                handler.closeSocket();
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

    private static final class DefaultExecutor implements Executor {
        private long requestCount;

        @Override
        public void execute(@NotNull Runnable command) {
            Thread t = new Thread(command, "Plumo Request Processor (#" + this.requestCount++ + ")");
            t.setDaemon(true);
            t.start();
        }
    }

    private static final class VirtualThreadExecutor implements Executor {
        static final boolean AVAILABLE;
        static final boolean NEED_ENABLE_PREVIEW;

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
                t.setName("Plumo Request Processor (#" + this.requestCount++ + ")");
                t.start();
            } catch (Throwable e) {
                throw new InternalError(e);
            }
        }
    }
}