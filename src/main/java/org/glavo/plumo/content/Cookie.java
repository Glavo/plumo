package org.glavo.plumo.content;

import org.glavo.plumo.internal.Constants;

import java.time.Instant;

/**
 * A simple cookie representation. This is old code and is flawed in many ways.
 *
 * @author LordFokas
 */
public class Cookie {
    private final String n, v, e;

    public Cookie(String name, String value) {
        this(name, value, 30);
    }

    public Cookie(String name, String value, int numDays) {
        this.n = name;
        this.v = value;
        this.e = Constants.HTTP_TIME_FORMATTER.format(Instant.now().plusSeconds(Math.multiplyExact(numDays, 86400L)));
    }

    public Cookie(String name, String value, String expires) {
        this.n = name;
        this.v = value;
        this.e = expires;
    }

    public String getHTTPHeader() {
        return this.n + "=" + this.v + "; expires=" + this.e;
    }
}
