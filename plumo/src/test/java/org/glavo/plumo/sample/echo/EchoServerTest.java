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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.SocketFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class EchoServerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test(boolean unixDomainSocket) throws IOException {
        Plumo.Builder builder = Plumo.newBuilder().handler(new EchoServer());

        Path socketFile = null;
        if (unixDomainSocket) {
            socketFile = Files.createTempFile("echo-", ".socket");
            builder.bind(socketFile, true);
        }

        try (Plumo server = builder.start()) {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
            if (unixDomainSocket) {
                clientBuilder.socketFactory(new UnixDomainSocketFactory(socketFile));
            }

            OkHttpClient client = clientBuilder.build();

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
        }

        if (unixDomainSocket) {
            assertFalse(Files.exists(socketFile));
        }
    }
}
