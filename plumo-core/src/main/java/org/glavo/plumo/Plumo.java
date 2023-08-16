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

import org.glavo.plumo.internal.PlumoImpl;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.util.concurrent.ThreadFactory;

public interface Plumo {

    static Plumo create() {
        return new PlumoImpl();
    }

    default void bind(int port) {
        bind(new InetSocketAddress(port));
    }

    default void bind(String host, int port) {
        bind(new InetSocketAddress(host, port));
    }

    void bind(InetSocketAddress address);

    default void bind(Path path) {
        bind(path, false);
    }

    void bind(Path path, boolean deleteIfExists);

    void setSSLContext(SSLContext sslContext);

    void setEnabledSSLProtocols(String[] protocols);

    void setSocketTimeout(int timeout);

    void setHandler(HttpHandler handler);

    // ---

    boolean isRunning();

    SocketAddress getLocalAddress();

    void start() throws IOException;

    default void startInNewThread() throws IOException {
        startInNewThread(true);
    }

    void startInNewThread(boolean daemon) throws IOException;

    void startInNewThread(ThreadFactory threadFactory) throws IOException;

    void stop();

    void awaitTermination() throws InterruptedException;

    static void main(String[] args) throws Exception {
        Plumo plumo = Plumo.create();
        plumo.startInNewThread();
        System.out.println("Listening on " + plumo.getLocalAddress());
        plumo.awaitTermination();
    }
}
