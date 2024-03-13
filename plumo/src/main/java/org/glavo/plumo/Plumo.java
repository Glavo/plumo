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
package org.glavo.plumo;

import org.glavo.plumo.internal.PlumoBuilderImpl;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public interface Plumo {

    interface Builder {
        default Builder bind(int port) {
            return bind(new InetSocketAddress(port));
        }

        default Builder bind(String host, int port) {
            return bind(new InetSocketAddress(host, port));
        }

        Builder bind(InetSocketAddress address);

        default Builder bind(Path path) {
            return bind(path, false);
        }

        Builder bind(Path path, boolean deleteIfExists);

        Builder handler(HttpHandler handler);

        Builder socketTimeout(long timeout);

        Builder sslContext(SSLContext sslContext);

        Builder enabledSSLProtocols(String[] protocols);

        Plumo build();

        default Plumo start() throws IOException {
            Plumo plumo = build();
            plumo.start();
            return plumo;
        }

        default Plumo start(boolean daemon) throws IOException {
            Plumo plumo = build();
            plumo.start(daemon);
            return plumo;
        }

        default Plumo start(ThreadFactory threadFactory) throws IOException {
            Plumo plumo = build();
            plumo.start(threadFactory);
            return plumo;
        }

        default void startAndWait() throws IOException {
            build().startAndWait();
        }
    }

    static Plumo.Builder newBuilder() {
        return new PlumoBuilderImpl();
    }

    // ---

    boolean isRunning();

    int getPort();

    SocketAddress getLocalAddress();

    String getProtocol();

    void startAndWait() throws IOException;

    default void start() throws IOException {
        start(true);
    }

    void start(boolean daemon) throws IOException;

    void start(ThreadFactory threadFactory) throws IOException;

    void stop();

    void stopAndWait();

    void awaitTermination() throws InterruptedException;

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

    static void main(String[] args) throws Exception {
        Plumo plumo = Plumo.newBuilder().build();
        plumo.start();
        System.out.println("Listening on port " + plumo.getPort());
        plumo.awaitTermination();
    }
}
