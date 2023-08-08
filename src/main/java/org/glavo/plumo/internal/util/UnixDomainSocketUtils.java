package org.glavo.plumo.internal.util;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class UnixDomainSocketUtils {
    private static final boolean AVAILABLE;

    private static final MethodHandle createUnixDomainSocketAddress;
    private static final MethodHandle openUnixDomainServerSocketChannel;


    static {
        boolean available = false;

        StandardProtocolFamily unixProtocolFamily;

        MethodHandle createUnixDomainSocketAddressHandle = null;
        MethodHandle openUnixDomainServerSocketChannelHandle = null;

        try {
            unixProtocolFamily = StandardProtocolFamily.valueOf("UNIX");

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            Class<?> udsaClass = Class.forName("java.net.UnixDomainSocketAddress");
            createUnixDomainSocketAddressHandle = lookup.findStatic(udsaClass, "of",
                            MethodType.methodType(udsaClass, Path.class))
                    .asType(MethodType.methodType(SocketAddress.class, Path.class));

            openUnixDomainServerSocketChannelHandle = lookup.findStatic(ServerSocketChannel.class, "open",
                            MethodType.methodType(ServerSocketChannel.class, ProtocolFamily.class))
                    .bindTo(unixProtocolFamily);

            available = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }

        AVAILABLE = available;
        createUnixDomainSocketAddress = createUnixDomainSocketAddressHandle;
        openUnixDomainServerSocketChannel = openUnixDomainServerSocketChannelHandle;
    }

    public static void checkAvailable() {
        if (!AVAILABLE) {
            throw new UnsupportedOperationException("Please upgrade to Java 16+");
        }
    }

    public static SocketAddress createUnixDomainSocketAddress(Path path) {
        try {
            return (SocketAddress) createUnixDomainSocketAddress.invokeExact(path);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw e;
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    public static ServerSocketChannel openUnixDomainServerSocketChannel() throws IOException {
        try {
            return (ServerSocketChannel) openUnixDomainServerSocketChannel.invokeExact();
        } catch (IOException | NullPointerException e) {
            throw e;
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    public static AtomicReference<Thread> registerShutdownHook(Path file) {
        AtomicReference<Thread> ref = new AtomicReference<>();
        Thread hook = new Thread(() -> {
            synchronized (ref) {
                if (ref.get() == null) {
                    return;
                }
                ref.set(null);
            }

            IOUtils.deleteIfExists(file);
        });
        ref.set(hook);
        Runtime.getRuntime().addShutdownHook(hook);
        return ref;
    }

    private UnixDomainSocketUtils() {
    }
}
