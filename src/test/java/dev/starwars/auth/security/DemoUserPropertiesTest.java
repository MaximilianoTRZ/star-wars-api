package dev.starwars.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DemoUserPropertiesTest {

    @Test
    @DisplayName("an unresolved placeholder prevents startup")
    void rejectsUnresolvedPlaceholder() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DemoUserProperties("demo", "${DEMO_USER_PASSWORD}"))
                .withMessageContaining("DEMO_USER_PASSWORD");
    }

    @Test
    @DisplayName("accepts a real password")
    void acceptsPassword() {
        assertThat(new DemoUserProperties("demo", "s3cret").password()).isEqualTo("s3cret");
    }
}
