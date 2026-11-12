package dev.starwars.auth.security;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Login rate limit ({@code app.security.login-rate-limit.*}).
 *
 * @param capacity attempts allowed per client within {@code period}
 * @param period   window after which the attempts are refilled
 */
@Validated
@ConfigurationProperties("app.security.login-rate-limit")
public record LoginRateLimitProperties(
        @Positive int capacity, @NotNull Duration period) {}
