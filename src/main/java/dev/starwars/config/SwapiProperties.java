package dev.starwars.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SWAPI client settings ({@code app.swapi.*}).
 *
 * @param baseUrl        base URL, e.g. {@code https://www.swapi.tech/api}
 * @param connectTimeout maximum time to open the connection
 * @param readTimeout    maximum time to wait for the response
 */
@Validated
@ConfigurationProperties("app.swapi")
public record SwapiProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {}
