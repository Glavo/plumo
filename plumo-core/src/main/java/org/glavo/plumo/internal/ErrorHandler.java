package org.glavo.plumo.internal;

import org.glavo.plumo.HttpResponse;
import org.glavo.plumo.HttpSession;

import javax.net.ssl.SSLException;

public interface ErrorHandler {

    default void handleRecoverableException(HttpSession session, Throwable exception) throws Exception {
        HttpResponse resp;
        if (exception instanceof HttpResponseException) {
            resp = ((HttpResponseException) exception).getResponse();
        } else if (exception instanceof SSLException) {
            resp = HttpResponse.newTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SSL PROTOCOL FAILURE: " + exception.getMessage(), "text/plain");
        } else {
            DefaultLogger.log(DefaultLogger.Level.WARNING, "Server internal error", exception);
            resp = HttpResponse.newTextResponse(HttpResponse.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR", "text/plain");
        }

        ((HttpSessionImpl) session).send(null, (HttpResponseImpl) resp, ((HttpSessionImpl) session).outputStream, false);
    }

    default void handleUnrecoverableException(HttpSession session, Throwable exception) {
        DefaultLogger.log(DefaultLogger.Level.ERROR, "An unrecoverable exception has occurred", exception);
    }
}
