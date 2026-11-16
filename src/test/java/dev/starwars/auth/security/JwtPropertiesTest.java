package dev.starwars.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    @DisplayName("a secret shorter than 32 bytes prevents startup")
    void rejectsShortSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties("short", "star-wars-api", Duration.ofHours(1), null))
                .withMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("accepts a 32-byte secret")
    void acceptsLongEnoughSecret() {
        var properties = new JwtProperties("x".repeat(32), "star-wars-api", Duration.ofHours(1), null);

        assertThat(properties.secret()).hasSize(32);
    }

    @Test
    @DisplayName("a previous secret shorter than 32 bytes prevents startup")
    void rejectsShortPreviousSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtProperties("x".repeat(32), "star-wars-api", Duration.ofHours(1), "short"))
                .withMessageContaining("JWT_PREVIOUS_SECRET")
                .withMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("a previous secret equal to the current one prevents startup")
    void rejectsPreviousSecretEqualToCurrent() {
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new JwtProperties("x".repeat(32), "star-wars-api", Duration.ofHours(1), "x".repeat(32)))
                .withMessageContaining("must differ");
    }

    @Test
    @DisplayName("a blank previous secret means no rotation in progress")
    void treatsBlankPreviousSecretAsAbsent() {
        var properties = new JwtProperties("x".repeat(32), "star-wars-api", Duration.ofHours(1), " ");

        assertThat(properties.previous()).isEmpty();
    }
}
