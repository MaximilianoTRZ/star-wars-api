package dev.starwars.catalog.domain;

import java.util.Locale;
import org.slf4j.MDC;

/**
 * How a catalog query arrived. Each adapter other than REST puts its channel in the MDC for the duration of the
 * request; it is propagated to the threads that run the query, tagged on {@code catalog.queries} and written
 * to the logs. A bounded set, so it is safe as a metric tag.
 */
public enum CatalogChannel {
    REST,
    GRAPHQL;

    /** MDC key holding the channel of the current request. */
    public static final String MDC_KEY = "channel";

    /** Channel of the current request; REST when no adapter set one. */
    public static CatalogChannel current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? REST : valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** Value used in metric tags and logs. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
