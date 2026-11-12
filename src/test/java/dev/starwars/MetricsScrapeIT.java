package dev.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.support.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Actuator as it runs in {@code prod}: on the same public port as the API, with the Prometheus endpoint
 * protected by the scrape credentials that Grafana Cloud uses.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "management.server.port=",
            "app.metrics.scrape.username=grafana",
            "app.metrics.scrape.password=" + MetricsScrapeIT.SCRAPE_PASSWORD
        })
@ActiveProfiles("test")
class MetricsScrapeIT {

    static final String SCRAPE_PASSWORD = "scrape-password-0123456789";

    @LocalServerPort
    private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    @DisplayName("the Prometheus endpoint on the public port requires credentials")
    void prometheusWithoutCredentials() {
        ResponseEntity<String> response = api.get("/actuator/prometheus", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Basic");
    }

    @Test
    @DisplayName("a wrong scrape password is rejected")
    void prometheusWithWrongPassword() {
        ResponseEntity<String> response =
                api.getWithBasicAuth("/actuator/prometheus", "grafana", "wrong-password-0123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("an API user cannot scrape: the demo user is not the scrape user")
    void prometheusWithApiUser() {
        ResponseEntity<String> response = api.getWithBasicAuth("/actuator/prometheus", "demo", "test-password");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("the scrape user gets the metrics")
    void prometheusWithCredentials() {
        api.get("/livez", null);

        ResponseEntity<String> response = api.getWithBasicAuth("/actuator/prometheus", "grafana", SCRAPE_PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("application=\"star-wars-api\"")
                .contains("http_server_requests_seconds_bucket");
    }

    @Test
    @DisplayName("probes and health stay public, and the scrape credentials do not open the API")
    void otherRoutes() {
        assertThat(api.get("/livez", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/actuator/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.getWithBasicAuth("/api/v1/people", "grafana", SCRAPE_PASSWORD)
                        .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
