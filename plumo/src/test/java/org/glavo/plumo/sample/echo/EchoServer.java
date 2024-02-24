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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;

public final class EchoServer implements HttpHandler {

    static final Gson GSON =  new GsonBuilder().setPrettyPrinting().create();

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("http-version", request.getHttpVersion());
        jsonObject.addProperty("method", request.getMethod().toString());
        jsonObject.addProperty("uri", request.getURI().toString());
        jsonObject.addProperty("raw-uri", request.getRawURI());

        JsonObject headers = new JsonObject();
        request.getHeaders().forEach((k, v) -> headers.addProperty(k.toString(), v.get(0)));
        jsonObject.add("headers", headers);

        return HttpResponse.newTextResponse(GSON.toJson(jsonObject), "application/json");
    }
}
