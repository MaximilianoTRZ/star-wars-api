package dev.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.starwars.support.ApiClient;
import dev.starwars.support.TestTokens;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** Security against the running app: real login backed by H2, public routes and authorization. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityIT {

    private static final String TEST_SECRET = "test-secret-0123456789-0123456789-0123456789";

    @LocalServerPort
    private int port;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
    }

    @Test
    @DisplayName("the demo user created at startup can log in")
    void demoUserLogin() {
        ResponseEntity<String> response =
                api.post("/api/v1/auth/login", "{\"username\":\"demo\",\"password\":\"test-password\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(response.getBody(), "$.tokenType")).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("a wrong password returns 401")
    void wrongPassword() {
        ResponseEntity<String> response =
                api.post("/api/v1/auth/login", "{\"username\":\"demo\",\"password\":\"wrong\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(JsonPath.<String>read(response.getBody(), "$.type")).isEqualTo("/problems/invalid-credentials");
    }

    @Test
    @DisplayName("the catalog requires a token")
    void catalogRequiresToken() {
        ResponseEntity<String> response = api.get("/api/v1/people", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(JsonPath.<String>read(response.getBody(), "$.type")).isEqualTo("/problems/unauthorized");
    }

    @Test
    @DisplayName("a token without the catalog:read scope returns 403")
    void tokenWithoutScope() {
        String token = TestTokens.sign(TEST_SECRET, "star-wars-api", Instant.now(), Duration.ofMinutes(5), null);

        assertThat(api.get("/api/v1/people", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("health, info and OpenAPI docs are public")
    void publicRoutes() {
        assertThat(api.get("/actuator/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/actuator/info", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/v3/api-docs", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/swagger-ui/index.html", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a route outside the public ones and the API is rejected without a token")
    void denyByDefault() {
        assertThat(api.get("/internal/anything", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
