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
                .isThrownBy(() -> new JwtProperties("short", "star-wars-api", Duration.ofHours(1)))
                .withMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("accepts a 32-byte secret")
    void acceptsLongEnoughSecret() {
        var properties = new JwtProperties("x".repeat(32), "star-wars-api", Duration.ofHours(1));

        assertThat(properties.secret()).hasSize(32);
    }
}
