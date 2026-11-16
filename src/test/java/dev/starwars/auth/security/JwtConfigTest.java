package dev.starwars.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

/** Key rotation (ADR-020): the current secret signs, the previous one only validates. */
class JwtConfigTest {

    private static final String ISSUER = "star-wars-api";
    private static final Duration TTL = Duration.ofHours(1);
    private static final String OLD = "old-secret-0123456789-0123456789-0123456789";
    private static final String NEW = "new-secret-0123456789-0123456789-0123456789";
    private static final String OTHER = "other-secret-0123456789-0123456789-0123456789";

    private final JwtConfig config = new JwtConfig();

    @Test
    @DisplayName("an issued token carries the kid of the current secret and validates")
    void issuedTokenHasKid() {
        JwtProperties properties = new JwtProperties(NEW, ISSUER, TTL, null);
        String token = sign(config.jwtEncoder(properties), NEW, true);

        var jwt = config.jwtDecoder(properties).decode(token);

        assertThat(jwt.getHeaders()).containsEntry("kid", JwtConfig.keyId(NEW));
    }

    @Test
    @DisplayName("during a rotation, a token signed with the previous secret still validates")
    void previousSecretValidates() {
        String token = sign(JwtConfig.encoder(OLD), OLD, true);

        JwtDecoder decoder = config.jwtDecoder(new JwtProperties(NEW, ISSUER, TTL, OLD));

        assertThat(decoder.decode(token).getSubject()).isEqualTo("demo");
    }

    @Test
    @DisplayName("after the rotation, a token signed with the previous secret is rejected")
    void removedSecretIsRejected() {
        String token = sign(JwtConfig.encoder(OLD), OLD, true);

        JwtDecoder decoder = config.jwtDecoder(new JwtProperties(NEW, ISSUER, TTL, null));

        assertThatExceptionOfType(JwtException.class).isThrownBy(() -> decoder.decode(token));
    }

    @Test
    @DisplayName("a token signed with an unknown secret is rejected")
    void unknownSecretIsRejected() {
        String token = sign(JwtConfig.encoder(OTHER), OTHER, true);

        JwtDecoder decoder = config.jwtDecoder(new JwtProperties(NEW, ISSUER, TTL, OLD));

        assertThatExceptionOfType(JwtException.class).isThrownBy(() -> decoder.decode(token));
    }

    @Test
    @DisplayName("a token without kid, issued before key ids, still validates")
    void tokenWithoutKidValidates() {
        String token = sign(JwtConfig.encoder(OLD), OLD, false);

        JwtDecoder decoder = config.jwtDecoder(new JwtProperties(NEW, ISSUER, TTL, OLD));

        assertThat(decoder.decode(token).getSubject()).isEqualTo("demo");
    }

    private static String sign(JwtEncoder encoder, String secret, boolean withKid) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("demo")
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .build();
        JwsHeader.Builder header = JwsHeader.with(MacAlgorithm.HS256);
        if (withKid) {
            header.keyId(JwtConfig.keyId(secret));
        }
        return encoder.encode(JwtEncoderParameters.from(header.build(), claims)).getTokenValue();
    }
}
