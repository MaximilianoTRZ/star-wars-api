package dev.starwars.swapi;

import dev.starwars.swapi.UpstreamUnavailableException.Reason;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.functions.Either;
import java.time.Duration;
import java.util.Optional;

/**
 * Which SWAPI failures are retried and how long to wait between attempts.
 *
 * <ul>
 *   <li>Retried: 5xx, connection errors and 429 whose {@code Retry-After} is at most {@code maxRetryAfter}.</li>
 *   <li>Not retried: read timeouts (each attempt would wait the full read timeout), 4xx and longer
 *       {@code Retry-After} values, which are passed on to the API client instead.</li>
 *   <li>Wait: the {@code Retry-After} sent by SWAPI, or exponential backoff from {@code initialBackoff}.</li>
 * </ul>
 */
public class SwapiRetryPolicy implements IntervalBiFunction<Object> {

    private final Duration initialBackoff;
    private final double multiplier;
    private final Duration maxRetryAfter;

    public SwapiRetryPolicy(Duration initialBackoff, double multiplier, Duration maxRetryAfter) {
        this.initialBackoff = initialBackoff;
        this.multiplier = multiplier;
        this.maxRetryAfter = maxRetryAfter;
    }

    public boolean shouldRetry(Throwable error) {
        if (!(error instanceof UpstreamUnavailableException upstream) || upstream.reason() == Reason.TIMEOUT) {
            return false;
        }
        return upstream.retryAfter()
                .map(wait -> wait.compareTo(maxRetryAfter) <= 0)
                .orElse(true);
    }

    /**
     * Wait before the next attempt, in milliseconds.
     *
     * @param attempt number of attempts made so far (starts at 1)
     */
    @Override
    public Long apply(Integer attempt, Either<Throwable, Object> outcome) {
        Optional<Duration> retryAfter = outcome != null
                        && outcome.isLeft()
                        && outcome.getLeft() instanceof UpstreamUnavailableException upstream
                ? upstream.retryAfter()
                : Optional.empty();
        return retryAfter
                .map(Duration::toMillis)
                .orElseGet(() -> Math.round(initialBackoff.toMillis() * Math.pow(multiplier, attempt - 1)));
    }
}
