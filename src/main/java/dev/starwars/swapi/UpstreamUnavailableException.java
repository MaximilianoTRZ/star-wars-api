package dev.starwars.swapi;

import java.time.Duration;
import java.util.Optional;

/** SWAPI is unavailable: 429, 5xx, timeout or I/O error. Mapped to 503. */
public class UpstreamUnavailableException extends RuntimeException {

    /** Why SWAPI was considered unavailable. Drives the retry policy. */
    public enum Reason {
        /** SWAPI answered 5xx. */
        SERVER_ERROR,
        /** SWAPI answered 429. */
        RATE_LIMITED,
        /** SWAPI did not answer within the read timeout. */
        TIMEOUT,
        /** The connection could not be opened or was dropped. */
        CONNECTION
    }

    private final Reason reason;
    private final transient Duration retryAfter;

    public UpstreamUnavailableException(String message, Reason reason, Duration retryAfter, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.retryAfter = retryAfter;
    }

    public Reason reason() {
        return reason;
    }

    /** Wait time suggested by SWAPI in the {@code Retry-After} header, if present. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
