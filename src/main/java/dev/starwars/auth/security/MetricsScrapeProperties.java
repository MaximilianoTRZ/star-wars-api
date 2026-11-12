package dev.starwars.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Credentials that protect the Prometheus endpoint ({@code app.metrics.scrape.*}).
 *
 * <p>Without a password the endpoint is open, which is only safe on a management port that is never
 * published (local and {@code docker compose}). The {@code prod} profile requires {@code METRICS_PASSWORD},
 * because there Actuator shares the public port with the API and Grafana Cloud scrapes it over the internet.
 *
 * @param username scrape username ({@code METRICS_USERNAME})
 * @param password scrape password ({@code METRICS_PASSWORD}), at least {@value #MIN_PASSWORD_LENGTH} characters
 */
@ConfigurationProperties("app.metrics.scrape")
public record MetricsScrapeProperties(String username, String password) {

    static final int MIN_PASSWORD_LENGTH = 16;

    public MetricsScrapeProperties {
        if (StringUtils.hasText(password)) {
            // An unset ${METRICS_PASSWORD} is bound as the literal placeholder, which would be a guessable password.
            if (password.contains("${")) {
                throw new IllegalArgumentException("app.metrics.scrape.password (METRICS_PASSWORD) is not set");
            }
            if (!StringUtils.hasText(username)) {
                throw new IllegalArgumentException("app.metrics.scrape.username is required when a password is set");
            }
            if (password.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalArgumentException(
                        "app.metrics.scrape.password must have at least " + MIN_PASSWORD_LENGTH + " characters");
            }
        }
    }

    /** Whether the Prometheus endpoint requires HTTP Basic credentials. */
    public boolean credentialsRequired() {
        return StringUtils.hasText(password);
    }
}
