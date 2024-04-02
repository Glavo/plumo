/*
 * Copyright 2024 Glavo
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
package org.glavo.plumo.internal;

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.Plumo;
import org.glavo.plumo.internal.util.UnixDomainSocketUtils;
import org.glavo.plumo.internal.util.VirtualThreadUtils;

import javax.net.ssl.SSLContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class PlumoBuilderImpl implements Plumo.Builder {

    private SocketAddress address;
    private Path unixDomainSocketPath;
    private boolean deleteUnixDomainSocketFileIfExists;
    private Executor executor;
    private boolean shutdownExecutor;
    private SSLContext sslContext;
    private String[] sslProtocols;
    private String protocol;
    private HttpHandler handler;
    private int timeout = 0;

    @Override
    public Plumo.Builder bind(InetSocketAddress address) {
        Objects.requireNonNull(address);
        this.address = address;
        this.unixDomainSocketPath = null;
        this.deleteUnixDomainSocketFileIfExists = false;
        return this;
    }

    @Override
    public Plumo.Builder bind(Path path, boolean deleteIfExists) {
        Objects.requireNonNull(path);
        UnixDomainSocketUtils.checkAvailable();

        this.address = UnixDomainSocketUtils.createUnixDomainSocketAddress(path);
        this.unixDomainSocketPath = path;
        this.deleteUnixDomainSocketFileIfExists = deleteIfExists;
        return this;
    }

    @Override
    public Plumo.Builder handler(HttpHandler handler) {
        this.handler = handler;
        return this;
    }

    @Override
    public Plumo.Builder socketTimeout(long timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("Socket timeout must not be negative");
        }
        if (timeout > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Socket timeout is too large");
        }

        this.timeout = (int) timeout;
        return this;
    }

    @Override
    public Plumo.Builder sslContext(SSLContext sslContext) {
        Objects.requireNonNull(sslContext);
        this.sslContext = sslContext;
        return this;
    }

    @Override
    public Plumo.Builder enabledSSLProtocols(String[] protocols) {
        Objects.requireNonNull(protocols);
        if (protocols.length == 0) {
            throw new IllegalArgumentException("SSL protocols must not be empty");
        }

        this.sslProtocols = protocols;
        return this;
    }

    @Override
    public Plumo build() {
        HttpHandler handler = this.handler;
        if (handler == null) {
            handler = new DefaultHttpHandler();
        }

        SocketAddress address = this.address;
        if (address == null) {
            address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        }

        Executor executor = this.executor;
        boolean shutdownExecutor = false;
        if (executor == null) {
            final AtomicLong requestCount = new AtomicLong();

            if (Constants.USE_VIRTUAL_THREAD == Boolean.TRUE || (Constants.USE_VIRTUAL_THREAD == null && VirtualThreadUtils.newThread != null)) {
                VirtualThreadUtils.checkAvailable();
                executor = command -> {
                    Thread t = VirtualThreadUtils.newVirtualThread(command);
                    t.setName("plumo-worker-" + requestCount.getAndIncrement());
                    t.start();
                };
            } else {
                shutdownExecutor = true;
                executor = Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "plumo-worker-" + requestCount.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                });
            }
        }

        return new PlumoImpl(address, unixDomainSocketPath, deleteUnixDomainSocketFileIfExists,
                executor, shutdownExecutor,
                sslContext, sslProtocols,
                timeout,
                handler);
    }
}
