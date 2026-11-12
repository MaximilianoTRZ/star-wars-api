package dev.starwars.config;

import dev.starwars.swapi.ResilientSwapiClient;
import dev.starwars.swapi.SwapiResilienceLogging;
import dev.starwars.swapi.SwapiRetryPolicy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import java.util.function.Predicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Completes the {@code swapi} retry instance declared in {@code application.yml} with the parts that
 * cannot be expressed as properties: which errors are retried and the {@code Retry-After} aware wait. Also
 * subscribes {@link SwapiResilienceLogging} to the retry and circuit breaker events.
 */
@Configuration(proxyBeanMethods = false)
public class SwapiResilienceConfig {

    @Bean
    RetryConfigCustomizer swapiRetryCustomizer(SwapiRetryProperties properties) {
        var policy =
                new SwapiRetryPolicy(properties.initialBackoff(), properties.multiplier(), properties.maxRetryAfter());
        Predicate<Throwable> retryable = policy::shouldRetry;
        return RetryConfigCustomizer.of(
                ResilientSwapiClient.INSTANCE,
                builder -> builder.retryOnException(retryable).intervalBiFunction(policy));
    }

    @Bean
    RegistryEventConsumer<Retry> swapiRetryLogging() {
        return SwapiResilienceLogging.retryEvents();
    }

    @Bean
    RegistryEventConsumer<CircuitBreaker> swapiCircuitBreakerLogging() {
        return SwapiResilienceLogging.circuitBreakerEvents();
    }
}
