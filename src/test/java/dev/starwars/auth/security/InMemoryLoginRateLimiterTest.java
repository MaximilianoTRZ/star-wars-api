package dev.starwars.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.auth.security.LoginRateLimiter.Decision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryLoginRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T10:00:00Z"));
    private final InMemoryLoginRateLimiter limiter =
            new InMemoryLoginRateLimiter(new LoginRateLimitProperties(5, Duration.ofMinutes(1)), clock);

    @Test
    @DisplayName("allows 5 attempts per minute and rejects the 6th with the time left to the refill")
    void rejectsSixthAttempt() {
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume("10.0.0.1").allowed()).isTrue();
        }
        clock.advance(Duration.ofSeconds(20));

        Decision sixth = limiter.tryConsume("10.0.0.1");

        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfter()).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    @DisplayName("refills the attempts once the period is over")
    void refillsAfterPeriod() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("10.0.0.1");
        }
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryConsume("10.0.0.1").allowed()).isTrue();
    }

    @Test
    @DisplayName("each client has its own limit")
    void clientsAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume("10.0.0.1");
        }

        assertThat(limiter.tryConsume("10.0.0.1").allowed()).isFalse();
        assertThat(limiter.tryConsume("10.0.0.2").allowed()).isTrue();
    }

    @Test
    @DisplayName("an allowed attempt has no wait")
    void allowedHasNoWait() {
        assertThat(limiter.tryConsume("10.0.0.1").retryAfter()).isZero();
    }

    /** Clock that tests move forward by hand. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
