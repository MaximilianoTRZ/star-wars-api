package dev.starwars.auth.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 JWT encoder and decoder using the keys from {@link JwtProperties} (ADR-004).
 *
 * <p>Each key has a {@code kid} derived from the secret. The encoder signs with the current secret; the decoder
 * accepts the current one and, during a rotation, the previous one (ADR-020).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final int KEY_ID_BYTES = 8;

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return encoder(properties.secret());
    }

    /**
     * Validates signature, expiration (60 s clock skew) and issuer. The key is picked by {@code kid}; a token
     * without {@code kid} (issued before ADR-020) is tried against every key.
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        List<JWK> keys = new ArrayList<>();
        keys.add(jwk(properties.secret()));
        properties.previous().map(JwtConfig::jwk).ifPresent(keys::add);

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(
                new JWSVerificationKeySelector<>(JWSAlgorithm.HS256, new ImmutableJWKSet<>(new JWKSet(keys))));
        // Claims are validated by Spring's validators below.
        processor.setJWTClaimsSetVerifier((claims, context) -> {});

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    public static JwtEncoder encoder(String secret) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk(secret))));
    }

    /** First {@value #KEY_ID_BYTES} bytes of the secret's SHA-256, in hex. Identifies the key without exposing it. */
    public static String keyId(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, KEY_ID_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static OctetSequenceKey jwk(String secret) {
        return new OctetSequenceKey.Builder(secretKey(secret))
                .keyID(keyId(secret))
                .algorithm(JWSAlgorithm.HS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
    }
}
