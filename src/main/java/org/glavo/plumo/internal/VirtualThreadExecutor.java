package org.glavo.plumo.internal;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class VirtualThreadExecutor implements Executor {
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

    private final AtomicLong requestCount = new AtomicLong();

    public VirtualThreadExecutor() {
        if (!AVAILABLE) {
            throw new UnsupportedOperationException(NEED_ENABLE_PREVIEW
                    ? "Preview Features not enabled, need to run with --enable-preview"
                    : "Please upgrade to Java 19+");
        }
    }

    @Override
    public void execute(@NotNull Runnable command) {
        try {
            Thread t = (Thread) newThread.invokeExact(command);
            t.setName("Plumo Request Processor (#" + this.requestCount.getAndIncrement() + ")");
            t.start();
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }
}
