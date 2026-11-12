package dev.starwars.support;

import dev.starwars.auth.security.JwtConfig;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Hand-made tokens for cases the login endpoint never issues. */
public final class TestTokens {

    private TestTokens() {}

    public static String sign(String secret, String issuer, Instant issuedAt, Duration ttl, String scope) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("demo")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl));
        if (scope != null) {
            claims.claim("scope", scope);
        }
        return JwtConfig.encoder(secret)
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
