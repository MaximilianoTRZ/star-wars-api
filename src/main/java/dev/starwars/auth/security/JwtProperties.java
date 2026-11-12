package dev.starwars.auth.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings ({@code app.security.jwt.*}). The app fails to start if the secret is missing or too short.
 *
 * @param secret HMAC-SHA256 key ({@code JWT_SECRET}); at least {@value #MIN_SECRET_BYTES} bytes
 * @param issuer {@code iss} claim issued and required
 * @param ttl    token lifetime
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration ttl) {

    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes");
        }
    }
}
