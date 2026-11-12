package dev.starwars.auth.security;

import java.time.Duration;

/**
 * Limits login attempts per client key (the client IP).
 *
 * <p>A small interface on purpose: the in-memory implementation is meant to be replaced by one backed by a
 * shared store (Redis) when the app runs with more than one replica, without touching its callers.
 */
public interface LoginRateLimiter {

    /** Consumes one attempt for {@code key} and tells whether it is allowed. */
    Decision tryConsume(String key);

    /**
     * @param allowed    whether the attempt may proceed
     * @param retryAfter how long to wait before the next attempt is allowed; zero when allowed
     */
    record Decision(boolean allowed, Duration retryAfter) {

        static Decision allow() {
            return new Decision(true, Duration.ZERO);
        }

        static Decision reject(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }
}
