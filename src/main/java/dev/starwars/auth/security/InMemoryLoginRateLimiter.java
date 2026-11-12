package dev.starwars.auth.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * {@link LoginRateLimiter} with one Bucket4j bucket per client, kept in memory.
 *
 * <p>Each bucket allows {@code capacity} attempts and refills them all at once every {@code period}. Buckets
 * of clients that stop trying expire after {@value #IDLE_MINUTES} minutes and the map is capped at
 * {@value #MAX_CLIENTS} entries, so it cannot grow without bound. State is not shared between replicas.
 */
@Component
public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    static final int IDLE_MINUTES = 10;
    static final int MAX_CLIENTS = 10_000;

    private final LoginRateLimitProperties properties;
    private final TimeMeter timeMeter;
    private final Cache<String, Bucket> buckets;

    public InMemoryLoginRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.timeMeter = new ClockTimeMeter(clock);
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(IDLE_MINUTES))
                .maximumSize(MAX_CLIENTS)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    @Override
    public Decision tryConsume(String key) {
        ConsumptionProbe probe = buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.allow();
        }
        return Decision.reject(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(properties.capacity())
                        .refillIntervally(properties.capacity(), properties.period()))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    /** Bucket4j time source backed by the application {@link Clock}, so tests can move time forward. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return TimeUnit.MILLISECONDS.toNanos(clock.millis());
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
