package dev.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.swapi.UpstreamUnavailableException.Reason;
import io.github.resilience4j.core.functions.Either;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwapiRetryPolicyTest {

    private final SwapiRetryPolicy policy = new SwapiRetryPolicy(Duration.ofMillis(200), 2, Duration.ofSeconds(2));

    private static UpstreamUnavailableException unavailable(Reason reason, Duration retryAfter) {
        return new UpstreamUnavailableException("down", reason, retryAfter, null);
    }

    @Test
    @DisplayName("retries server errors, connection errors and short rate limits")
    void retriesTransientErrors() {
        assertThat(policy.shouldRetry(unavailable(Reason.SERVER_ERROR, null))).isTrue();
        assertThat(policy.shouldRetry(unavailable(Reason.CONNECTION, null))).isTrue();
        assertThat(policy.shouldRetry(unavailable(Reason.RATE_LIMITED, null))).isTrue();
        assertThat(policy.shouldRetry(unavailable(Reason.RATE_LIMITED, Duration.ofSeconds(2))))
                .isTrue();
    }

    @Test
    @DisplayName("does not retry timeouts, long rate limits or other errors")
    void doesNotRetryTheRest() {
        assertThat(policy.shouldRetry(unavailable(Reason.TIMEOUT, null))).isFalse();
        assertThat(policy.shouldRetry(unavailable(Reason.RATE_LIMITED, Duration.ofSeconds(3))))
                .isFalse();
        assertThat(policy.shouldRetry(new SwapiClientException("bad"))).isFalse();
        assertThat(policy.shouldRetry(new IllegalStateException())).isFalse();
    }

    @Test
    @DisplayName("waits with exponential backoff")
    void exponentialBackoff() {
        Either<Throwable, Object> failure = Either.left(unavailable(Reason.SERVER_ERROR, null));

        assertThat(policy.apply(1, failure)).isEqualTo(200);
        assertThat(policy.apply(2, failure)).isEqualTo(400);
    }

    @Test
    @DisplayName("waits the Retry-After sent by SWAPI")
    void honorsRetryAfter() {
        Either<Throwable, Object> failure = Either.left(unavailable(Reason.RATE_LIMITED, Duration.ofSeconds(1)));

        assertThat(policy.apply(1, failure)).isEqualTo(1_000);
    }
}
