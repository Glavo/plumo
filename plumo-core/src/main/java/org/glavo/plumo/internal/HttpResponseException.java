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

import org.glavo.plumo.HttpResponse;

import java.io.IOException;

public final class HttpResponseException extends IOException {

    private final HttpResponse response;

    public HttpResponseException(HttpResponse.Status status) {
        this.response = HttpResponse.newResponse(status);
    }

    public HttpResponseException(HttpResponse.Status status, String message) {
        super(message);
        this.response = HttpResponse.newTextResponse(status, message, "text/plain");
    }

    public HttpResponseException(HttpResponse.Status status, String message, Exception e) {
        super(message, e);
        this.response = HttpResponse.newTextResponse(status, message, "text/plain");
    }

    public HttpResponse getResponse() {
        return this.response;
    }
}
