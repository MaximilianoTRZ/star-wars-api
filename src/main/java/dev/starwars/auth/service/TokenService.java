package dev.starwars.auth.service;

import dev.starwars.auth.security.JwtProperties;
import dev.starwars.auth.security.SecurityConfig;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access JWTs (ADR-004).
 *
 * <p>HS256 token with claims {@code iss}, {@code sub} (username), {@code iat}, {@code exp}
 * ({@code iat} + {@code app.security.jwt.ttl}) and {@code scope = catalog:read}. The injected
 * {@link Clock} makes expiration testable.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** Issues a token for an already authenticated user. */
    public IssuedToken issue(String username) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(username)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("scope", SecurityConfig.CATALOG_READ_SCOPE)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, properties.ttl().toSeconds());
    }

    /**
     * @param value            the signed JWT
     * @param expiresInSeconds lifetime in seconds from issuance
     */
    public record IssuedToken(String value, long expiresInSeconds) {}
}
