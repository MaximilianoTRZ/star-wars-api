package dev.starwars.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Login metrics: attempts by result and distinct users who logged in during the last 15 minutes.
 *
 * <p>Usernames are kept only in memory to count them; they are never a tag.
 */
@Component
public class AuthMetrics {

    static final String LOGIN_ATTEMPTS = "auth.login.attempts";
    static final String ACTIVE_USERS = "auth.active.users";
    static final Duration ACTIVE_WINDOW = Duration.ofMinutes(15);

    private final Counter successes;
    private final Counter failures;
    private final Cache<String, Boolean> activeUsers;

    public AuthMetrics(MeterRegistry registry, Clock clock) {
        this.successes = loginAttempts(registry, "success");
        this.failures = loginAttempts(registry, "failure");
        this.activeUsers = Caffeine.newBuilder()
                .expireAfterWrite(ACTIVE_WINDOW)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
        Gauge.builder(ACTIVE_USERS, activeUsers, AuthMetrics::liveSize)
                .description("Distinct users who logged in during the last 15 minutes")
                .register(registry);
    }

    public void loginSucceeded(String username) {
        successes.increment();
        activeUsers.put(username, Boolean.TRUE);
    }

    public void loginFailed() {
        failures.increment();
    }

    private static Counter loginAttempts(MeterRegistry registry, String result) {
        return Counter.builder(LOGIN_ATTEMPTS)
                .description("Login attempts by result")
                .tag("result", result)
                .register(registry);
    }

    private static double liveSize(Cache<String, Boolean> cache) {
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
