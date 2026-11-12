package dev.starwars.swapi;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.Optional;

/**
 * Adds retry and a circuit breaker (both named {@value #INSTANCE}) to the delegate. Retry wraps the
 * circuit breaker: every attempt counts in the circuit window, and an open circuit is not retried.
 */
public class ResilientSwapiClient implements SwapiClient {

    public static final String INSTANCE = "swapi";

    private final SwapiClient delegate;

    public ResilientSwapiClient(SwapiClient delegate) {
        this.delegate = delegate;
    }

    @Override
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    public SwapiPage list(ResourceType type, int page, int limit) {
        return delegate.list(type, page, limit);
    }

    @Override
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    public List<SwapiEntity> search(ResourceType type, String term) {
        return delegate.search(type, term);
    }

    @Override
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE)
    public Optional<SwapiEntity> findById(ResourceType type, String id) {
        return delegate.findById(type, id);
    }
}
