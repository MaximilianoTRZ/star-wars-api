package dev.starwars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class ProdProfileTest {

    private static SpringApplicationBuilder prodApp() {
        return new SpringApplicationBuilder(StarWarsApiApplication.class)
                .profiles("prod")
                .properties("server.port=0", "spring.main.banner-mode=off");
    }

    @Test
    @DisplayName("prod does not start without its secrets")
    void failsWithoutSecrets() {
        assumeTrue(System.getenv("JWT_SECRET") == null && System.getenv("DEMO_USER_PASSWORD") == null);

        assertThatThrownBy(() -> prodApp().run().close()).rootCause().hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("prod starts when the secrets are set")
    void startsWithSecrets() {
        try (ConfigurableApplicationContext context = prodApp()
                .run(
                        "--JWT_SECRET=prod-secret-0123456789-0123456789-0123",
                        "--DEMO_USER_PASSWORD=prod-password",
                        "--METRICS_PASSWORD=prod-metrics-password-123")) {
            assertThat(context.isRunning()).isTrue();
        }
    }
}
