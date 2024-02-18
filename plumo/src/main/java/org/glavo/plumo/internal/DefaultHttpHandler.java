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
package org.glavo.plumo.internal;

import org.glavo.plumo.HttpHandler;
import org.glavo.plumo.HttpRequest;
import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.HttpSession;

public final class DefaultHttpHandler implements HttpHandler {

    private static final HttpResponse RESPONSE = HttpResponse.newTextResponse(
            "<html><header><title>Plumo Default Page</title></header><body>This is the default page for <a href=\"https://github.com/Glavo/plumo\">Plumo</a>.</body></html>",
            "text/html"
    );

    @Override
    public HttpResponse handle(HttpRequest request) throws Exception {
        return RESPONSE;
    }
}