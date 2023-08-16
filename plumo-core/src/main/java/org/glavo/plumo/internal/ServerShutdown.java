package org.glavo.plumo.internal;

import java.net.SocketException;

public final class ServerShutdown extends SocketException {
    private static final ServerShutdown SHUTDOWN = new ServerShutdown();

    public static ServerShutdown shutdown() {
        return SHUTDOWN;
    }

    private ServerShutdown() {
        super("Server Shutdown");
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}