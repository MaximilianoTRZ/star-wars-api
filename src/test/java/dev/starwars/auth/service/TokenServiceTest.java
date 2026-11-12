package dev.starwars.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.auth.security.JwtConfig;
import dev.starwars.auth.security.JwtProperties;
import dev.starwars.auth.service.TokenService.IssuedToken;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class TokenServiceTest {

    private static final String SECRET = "test-secret-0123456789-0123456789-0123456789";
    private static final JwtProperties PROPERTIES = new JwtProperties(SECRET, "star-wars-api", Duration.ofHours(1));

    @Test
    @DisplayName("issues an HS256 JWT with issuer, subject, scope and 1 h expiration")
    void issuesTokenWithExpectedClaims() {
        Instant now = Instant.now();
        var service = new TokenService(JwtConfig.encoder(SECRET), PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));

        IssuedToken token = service.issue("demo");

        Jwt jwt = NimbusJwtDecoder.withSecretKey(
                        new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build()
                .decode(token.value());
        assertThat(token.expiresInSeconds()).isEqualTo(3600);
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("star-wars-api");
        assertThat(jwt.getSubject()).isEqualTo("demo");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("catalog:read");
        assertThat(jwt.getIssuedAt()).isEqualTo(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofHours(1)));
    }
}
