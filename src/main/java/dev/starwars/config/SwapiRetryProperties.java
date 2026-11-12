package dev.starwars.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retry settings for SWAPI calls ({@code app.swapi.retry.*}). The number of attempts lives in
 * {@code resilience4j.retry.instances.swapi.max-attempts}.
 *
 * @param initialBackoff wait before the second attempt
 * @param multiplier     backoff growth factor between attempts
 * @param maxRetryAfter  longest SWAPI {@code Retry-After} worth waiting for; longer ones are not retried
 */
@Validated
@ConfigurationProperties("app.swapi.retry")
public record SwapiRetryProperties(
        @NotNull Duration initialBackoff,
        @Positive double multiplier,
        @NotNull Duration maxRetryAfter) {}
