package dev.starwars.auth.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT settings ({@code app.security.jwt.*}). The app fails to start if the secret is missing or too short.
 *
 * @param secret         HMAC-SHA256 key that signs and validates tokens ({@code JWT_SECRET}); at least
 *                       {@value #MIN_SECRET_BYTES} bytes
 * @param issuer         {@code iss} claim issued and required
 * @param ttl            token lifetime
 * @param previousSecret optional key that only validates tokens, used while rotating the secret
 *                       ({@code JWT_PREVIOUS_SECRET}, ADR-020). Blank means not set.
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration ttl,
        String previousSecret) {

    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        requireMinLength(secret, "app.security.jwt.secret (JWT_SECRET)");
        if (previousSecret != null && previousSecret.isBlank()) {
            previousSecret = null;
        }
        requireMinLength(previousSecret, "app.security.jwt.previous-secret (JWT_PREVIOUS_SECRET)");
        if (previousSecret != null && previousSecret.equals(secret)) {
            throw new IllegalArgumentException(
                    "app.security.jwt.previous-secret (JWT_PREVIOUS_SECRET) must differ from JWT_SECRET");
        }
    }

    /** The previous secret, if a rotation is in progress. */
    public Optional<String> previous() {
        return Optional.ofNullable(previousSecret);
    }

    private static void requireMinLength(String value, String name) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(name + " must be at least " + MIN_SECRET_BYTES + " bytes");
        }
    }
}
