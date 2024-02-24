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

import org.glavo.plumo.internal.*;

import javax.net.ssl.SSLException;
import java.io.Closeable;
import java.io.IOException;

@FunctionalInterface
public interface HttpHandler {
    HttpResponse handle(HttpRequest request) throws Exception;

    default HttpResponse handleRecoverableException(HttpSession session, HttpRequest request, Throwable exception) {
        HttpResponse resp;
        if (exception instanceof HttpResponseException) {
            resp = ((HttpResponseException) exception).getResponse();
        } else if (exception instanceof SSLException) {
            resp = HttpResponse.newTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SSL PROTOCOL FAILURE: " + exception.getMessage(), "text/plain");
        } else {
            DefaultLogger.log(DefaultLogger.Level.WARNING, "Server internal error", exception);
            resp = HttpResponse.newTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR", "text/plain");
        }

        return resp;
    }

    default void handleUnrecoverableException(HttpSession session, HttpRequest request, Throwable exception) {
        DefaultLogger.log(DefaultLogger.Level.ERROR, "An unrecoverable exception has occurred", exception);
    }

    default void safeClose(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            DefaultLogger.log(DefaultLogger.Level.ERROR, "Could not close", e);
        }
    }
}
