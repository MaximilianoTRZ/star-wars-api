package dev.starwars.config;

import dev.starwars.swapi.ResilientSwapiClient;
import dev.starwars.swapi.SwapiRetryPolicy;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import java.util.function.Predicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Completes the {@code swapi} retry instance declared in {@code application.yml} with the parts that
 * cannot be expressed as properties: which errors are retried and the {@code Retry-After} aware wait.
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
}
