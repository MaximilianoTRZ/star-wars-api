package dev.starwars.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.swapi.SwapiClientException;
import dev.starwars.swapi.UpstreamUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicToolErrorMessagesTest {

    private final PublicToolErrorMessages messages = new PublicToolErrorMessages();

    @Test
    @DisplayName("SWAPI failures get the same public texts as the REST detail")
    void upstreamFailures() {
        assertThat(messages.describe(new UpstreamUnavailableException(
                        "SWAPI answered 500", UpstreamUnavailableException.Reason.SERVER_ERROR, Duration.ZERO, null)))
                .isEqualTo("SWAPI did not respond correctly. Try again in a few seconds.");
        assertThat(messages.describe(
                        CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("swapi"))))
                .isEqualTo("SWAPI is failing repeatedly, so calls are paused. Try again later.");
        assertThat(messages.describe(new SwapiClientException("bad body")))
                .isEqualTo("SWAPI returned a response that could not be processed.");
    }

    @Test
    @DisplayName("an unexpected error never exposes its internal message")
    void unexpected() {
        assertThat(messages.describe(new IllegalStateException("database password is hunter2")))
                .isEqualTo("An unexpected error occurred.");
    }
}
