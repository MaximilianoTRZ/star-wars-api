package dev.starwars.common.exception;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** How long an open circuit stays open, to tell REST and GraphQL clients when to retry. */
@Component
public class CircuitRetryAfter {

    private final ObjectProvider<CircuitBreakerRegistry> circuitBreakers;

    public CircuitRetryAfter(ObjectProvider<CircuitBreakerRegistry> circuitBreakers) {
        this.circuitBreakers = circuitBreakers;
    }

    public Optional<Duration> of(CallNotPermittedException ex) {
        CircuitBreakerRegistry registry = circuitBreakers.getIfAvailable();
        if (registry == null) {
            return Optional.empty();
        }
        long waitMillis = registry.circuitBreaker(ex.getCausingCircuitBreakerName())
                .getCircuitBreakerConfig()
                .getWaitIntervalFunctionInOpenState()
                .apply(1);
        return Optional.of(Duration.ofMillis(waitMillis));
    }
}
