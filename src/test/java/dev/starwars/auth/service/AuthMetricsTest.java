package dev.starwars.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthMetricsTest {

    private SimpleMeterRegistry registry;
    private MutableClock clock;
    private AuthMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        metrics = new AuthMetrics(registry, clock);
    }

    @Test
    @DisplayName("counts login attempts by result")
    void countsAttempts() {
        metrics.loginSucceeded("demo");
        metrics.loginFailed();
        metrics.loginFailed();
        metrics.loginBlocked();

        assertThat(registry.get(AuthMetrics.LOGIN_ATTEMPTS)
                        .tag("result", "success")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get(AuthMetrics.LOGIN_ATTEMPTS)
                        .tag("result", "failure")
                        .counter()
                        .count())
                .isEqualTo(2);
        assertThat(registry.get(AuthMetrics.LOGIN_ATTEMPTS)
                        .tag("result", "blocked")
                        .counter()
                        .count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("active users counts distinct usernames and forgets them after 15 minutes")
    void activeUsers() {
        metrics.loginSucceeded("demo");
        metrics.loginSucceeded("demo");
        metrics.loginSucceeded("other");
        assertThat(activeUsersGauge()).isEqualTo(2);

        clock.advance(AuthMetrics.ACTIVE_WINDOW.plusSeconds(1));
        assertThat(activeUsersGauge()).isZero();
    }

    private double activeUsersGauge() {
        return registry.get(AuthMetrics.ACTIVE_USERS).gauge().value();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
