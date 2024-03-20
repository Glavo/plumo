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
package org.glavo.plumo.sample.echo;

import com.google.gson.JsonObject;
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

public class EchoServerTest {

    private static void test(Plumo.Builder serverBuilder, OkHttpClient.Builder clientBuilder, boolean unixDomainSocket) throws IOException {
        OkHttpClient client = clientBuilder.build();

        Plumo server = serverBuilder.start();
        try {
            Request request = new Request.Builder()
                    .url("http://localhost" + (unixDomainSocket ? "" : ":" + server.getPort()) + "/test-url")
                    .addHeader("user-agent", "echo-client/1.0")
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                JsonObject jsonObject = EchoServer.GSON.fromJson(response.body().string(), JsonObject.class);

                assertEquals("1.1", jsonObject.getAsJsonPrimitive("http-version").getAsString());
                assertEquals("GET", jsonObject.getAsJsonPrimitive("method").getAsString());
                assertEquals("/test-url", jsonObject.getAsJsonPrimitive("uri").getAsString());
                assertEquals("/test-url", jsonObject.getAsJsonPrimitive("raw-uri").getAsString());

                JsonObject headers = jsonObject.getAsJsonObject("headers");
                assertEquals("Keep-Alive", headers.getAsJsonPrimitive("connection").getAsString());
                assertEquals("echo-client/1.0", headers.getAsJsonPrimitive("user-agent").getAsString());
            }

            assertTrue(server.isRunning());
        } finally {
            server.stopAndWait();
        }

        assertFalse(server.isRunning());
    }


    @Test
    public void testOnInet() throws IOException {
        test(Plumo.newBuilder().handler(new EchoServer()), new OkHttpClient.Builder(), false);
    }

    @Test
    @EnabledIf("org.glavo.plumo.internal.util.UnixDomainSocketUtils#isAvailable")
    public void testOnUnixDomainSocket() throws IOException {
        Path socketFile = Files.createTempFile("echo-", ".socket");
        test(
                Plumo.newBuilder().handler(new EchoServer()).bind(socketFile, true),
                UnixDomainSocket.newClientBuilder(socketFile),
                true
        );
        assertFalse(Files.exists(socketFile));
    }
}
