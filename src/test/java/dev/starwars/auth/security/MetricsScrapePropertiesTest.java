package dev.starwars.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MetricsScrapePropertiesTest {

    @Test
    @DisplayName("without a password the endpoint is open")
    void noPassword() {
        assertThat(new MetricsScrapeProperties(null, null).credentialsRequired())
                .isFalse();
        assertThat(new MetricsScrapeProperties("grafana", "").credentialsRequired())
                .isFalse();
    }

    @Test
    @DisplayName("with a password the endpoint requires credentials")
    void withPassword() {
        assertThat(new MetricsScrapeProperties("grafana", "0123456789abcdef").credentialsRequired())
                .isTrue();
    }

    @Test
    @DisplayName("a short password fails at startup")
    void shortPassword() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetricsScrapeProperties("grafana", "short"))
                .withMessageContaining("at least 16");
    }

    @Test
    @DisplayName("an unset METRICS_PASSWORD fails at startup instead of becoming the literal placeholder")
    void unresolvedPlaceholder() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetricsScrapeProperties("grafana", "${METRICS_PASSWORD}"))
                .withMessageContaining("METRICS_PASSWORD");
    }

    @Test
    @DisplayName("a password without username fails at startup")
    void missingUsername() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetricsScrapeProperties(" ", "0123456789abcdef"))
                .withMessageContaining("username");
    }
}
