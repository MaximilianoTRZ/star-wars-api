package dev.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.support.ApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** Actuator on the management port: health and the Prometheus scrape endpoint, without credentials. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ManagementPortIT {

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Test
    @DisplayName("health is public on the management port")
    void health() {
        ResponseEntity<String> response = new ApiClient(managementPort).get("/actuator/health", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName(
            "the Prometheus endpoint exposes HTTP, cache, resilience, JVM and login metrics with the application tag")
    void prometheus() {
        new ApiClient(port).get("/livez", null);

        ResponseEntity<String> response = new ApiClient(managementPort).get("/actuator/prometheus", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("application=\"star-wars-api\"")
                .contains("http_server_requests_seconds_bucket")
                .contains("cache_gets_total")
                .contains("resilience4j_circuitbreaker_state")
                .contains("resilience4j_retry_calls_total")
                .contains("jvm_memory_used_bytes")
                .contains("auth_active_users")
                .contains("auth_login_attempts_total");
    }

    @Test
    @DisplayName("actuator is not served on the main port, but /livez and /readyz are")
    void mainPort() {
        ApiClient api = new ApiClient(port);

        assertThat(api.get("/livez", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/readyz", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/actuator/health", null).getStatusCode().is2xxSuccessful())
                .isFalse();
        assertThat(api.get("/actuator/prometheus", null).getStatusCode().is2xxSuccessful())
                .isFalse();
    }
}
