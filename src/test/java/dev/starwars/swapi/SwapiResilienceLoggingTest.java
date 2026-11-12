package dev.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.swapi.UpstreamUnavailableException.Reason;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.StateTransition;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.retry.event.RetryOnIgnoredErrorEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class SwapiResilienceLoggingTest {

    @Test
    @DisplayName("an outage that is not retried is logged as WARN with its reason")
    void ignoredOutageIsWarn(CapturedOutput output) {
        var timeout = new UpstreamUnavailableException("timeout", Reason.TIMEOUT, null, null);

        SwapiResilienceLogging.onIgnoredError(new RetryOnIgnoredErrorEvent("swapi", timeout));

        assertThat(output).containsPattern("WARN .*SWAPI call failed and is not retried \\(TIMEOUT\\)");
    }

    @Test
    @DisplayName("errors that are not outages are left to the exception handler")
    void otherErrorsAreNotLogged(CapturedOutput output) {
        SwapiResilienceLogging.onIgnoredError(
                new RetryOnIgnoredErrorEvent("swapi", new SwapiClientException("SWAPI answered 400")));

        assertThat(output).doesNotContain("SWAPI call failed");
    }

    @Test
    @DisplayName("opening the circuit is WARN, closing it again is INFO")
    void circuitTransitions(CapturedOutput output) {
        SwapiResilienceLogging.onStateTransition(
                new CircuitBreakerOnStateTransitionEvent("swapi", StateTransition.CLOSED_TO_OPEN));
        SwapiResilienceLogging.onStateTransition(
                new CircuitBreakerOnStateTransitionEvent("swapi", StateTransition.HALF_OPEN_TO_CLOSED));

        assertThat(output)
                .containsPattern("WARN .*changed from CLOSED to OPEN")
                .containsPattern("INFO .*changed from HALF_OPEN to CLOSED");
    }
}
