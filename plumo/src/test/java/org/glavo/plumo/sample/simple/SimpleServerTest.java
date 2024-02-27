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
package org.glavo.plumo.sample.simple;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.glavo.plumo.Plumo;
import org.glavo.plumo.util.UnixDomainSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleServerTest {

    private static void test(OkHttpClient client, String url) throws IOException {
        try (Response response = client.newCall(new Request.Builder()
                .url(url)
                .get()
                .build()).execute()) {
            assertEquals(200, response.code());
            assertArrayEquals(SimpleServer.TEST_DATA, response.body().bytes());
        }
    }

    private static void test(Plumo.Builder serverBuilder, OkHttpClient.Builder clientBuilder, boolean unixDomainSocket) throws IOException {
        OkHttpClient client = clientBuilder.build();

        Plumo server = serverBuilder.start();
        try {
            String urlBase = "http://localhost" + (unixDomainSocket ? "" : ":" + server.getPort());

            test(client, urlBase + "/byte-array");
            test(client, urlBase + "/byte-buffer");
            test(client, urlBase + "/byte-buffer?direct=true");

            try (Response response = client.newCall(new Request.Builder()
                    .url(urlBase + "/unknown")
                    .get()
                    .build()).execute()) {
                assertEquals(404, response.code());
                assertEquals(0, response.body().contentLength());
            }

            assertTrue(server.isRunning());
        } finally {
            server.stopAndWait();
        }

        assertFalse(server.isRunning());
    }


    @Test
    public void testOnInet() throws IOException {
        test(Plumo.newBuilder().handler(new SimpleServer()), new OkHttpClient.Builder(), false);
    }

    @Test
    @EnabledIf("org.glavo.plumo.internal.util.UnixDomainSocketUtils#isAvailable")
    public void testOnUnixDomainSocket() throws IOException {
        Path socketFile = Files.createTempFile("simple-", ".socket");
        test(
                Plumo.newBuilder().handler(new SimpleServer()).bind(socketFile, true),
                UnixDomainSocket.newClientBuilder(socketFile),
                true
        );
        assertFalse(Files.exists(socketFile));
    }
}
