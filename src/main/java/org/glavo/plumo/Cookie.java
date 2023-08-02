package org.glavo.plumo;

import org.glavo.plumo.internal.Constants;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

public final class Cookie {
    private final String name;
    private final String value;
    private final Map<String, Object> attributes;

    public Cookie(String name, String value) {
        this(name, value, 30);
    }

    public Cookie(String name, String value, int numDays) {
        this.name = name;
        this.value = value;
        this.attributes = Collections.singletonMap(
                "expires",
                Constants.HTTP_TIME_FORMATTER.format(Instant.now().plusSeconds(Math.multiplyExact(numDays, 86400L))));
    }

    public Cookie(String name, String value, Map<String, Object> attributes) {
        this.name = name;
        this.value = value;
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(name).append('=').append(value);

        attributes.forEach((k, v) -> {
            if ((v instanceof Boolean) || v == null) {
                Boolean b = (Boolean) v;
                if (b != Boolean.FALSE) {
                    builder.append("; ").append(k);
                }
            } else {
                builder.append("; ").append(k).append('=').append(v);
            }
        });

        return builder.toString();
    }
}
