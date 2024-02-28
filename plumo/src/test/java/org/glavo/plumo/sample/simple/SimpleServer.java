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

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.internal.util.ParameterParser;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Random;

public final class SimpleServer implements HttpHandler {

    static final byte[] TEST_DATA = new byte[8192 * 4];

    static {
        new Random(0).nextBytes(TEST_DATA);
    }

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        URI uri = request.getURI();
        Map<String, String> query = ParameterParser.parseQuery(uri.getQuery());
        if (request.getMethod() == HttpRequest.Method.GET) {

            switch (uri.getPath()) {
                case "/ByteArray":
                    return HttpResponse.newResponse().withBody(TEST_DATA);
                case "/ByteBuffer": {
                    ByteBuffer buffer = "true".equals(query.get("direct"))
                            ? ByteBuffer.allocateDirect(TEST_DATA.length * 2)
                            : ByteBuffer.allocate(TEST_DATA.length * 2);

                    int start = TEST_DATA.length / 2;
                    buffer.position(start).limit(start + TEST_DATA.length);
                    buffer.duplicate().put(TEST_DATA);

                    return HttpResponse.newResponse().withBody(TEST_DATA);
                }
                case "/InputStream": {
                    long contentLength;

                    if ("true".equals(query.get("unknown-length"))) {
                        contentLength = -1;
                    } else {
                        contentLength = TEST_DATA.length;
                    }

                    return HttpResponse.newResponse().withBody(new ByteArrayInputStream(TEST_DATA), contentLength);
                }
                case "/String": {

                }
                default:
                    return HttpResponse.newResponse(HttpResponse.Status.NOT_FOUND);
            }
        } else {
            return HttpResponse.newResponse(HttpResponse.Status.NOT_FOUND);
        }
    }
}
