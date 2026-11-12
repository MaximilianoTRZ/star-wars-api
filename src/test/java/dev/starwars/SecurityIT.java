package dev.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.starwars.support.ApiClient;
import dev.starwars.support.Logs;
import dev.starwars.support.TestTokens;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** Security against the running app: real login backed by H2, public routes and authorization. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
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
    @DisplayName("a 401 from the security filters carries the correlation ID in header and body")
    void correlationIdOnSecurityError(CapturedOutput output) {
        ResponseEntity<String> response = api.get("/api/v1/people", null);

        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(JsonPath.<String>read(response.getBody(), "$.requestId")).isEqualTo(requestId);
        assertThat(output).containsPattern("\\[" + requestId + "] .*Request rejected as unauthenticated");
        Logs.awaitPattern(output, "\\[" + requestId + "] .*GET /api/v1/people 401");
    }

    @Test
    @DisplayName("a token without the catalog:read scope returns 403")
    void tokenWithoutScope(CapturedOutput output) {
        String token = TestTokens.sign(TEST_SECRET, "star-wars-api", Instant.now(), Duration.ofMinutes(5), null);

        assertThat(api.get("/api/v1/people", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(output).containsPattern("INFO.*Request forbidden").doesNotContain(token);
    }

    @Test
    @DisplayName("probes and OpenAPI docs are public")
    void publicRoutes() {
        assertThat(api.get("/livez", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/readyz", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/v3/api-docs", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.get("/swagger-ui/index.html", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("a route outside the public ones and the API is rejected without a token")
    void denyByDefault() {
        assertThat(api.get("/internal/anything", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("the OpenAPI document declares bearer auth and ProblemDetail errors")
    void openApiDocument() {
        String docs = api.get("/v3/api-docs", null).getBody();

        assertThat(JsonPath.<String>read(docs, "$.components.securitySchemes.bearerAuth.scheme"))
                .isEqualTo("bearer");
        assertThat(JsonPath.<Map<String, Object>>read(docs, "$.paths['/api/v1/people/{id}'].get.responses"))
                .containsKeys("200", "401", "403", "404", "503");
        assertThat(JsonPath.<Map<String, Object>>read(docs, "$.paths['/api/v1/auth/login'].post.responses"))
                .containsKeys("200", "400", "401", "429")
                .doesNotContainKey("403");
        assertThat(
                        JsonPath.<String>read(
                                docs,
                                "$.paths['/api/v1/people/{id}'].get.responses.404.content['application/problem+json'].schema.$ref"))
                .isEqualTo("#/components/schemas/ProblemDetail");
        assertThat(JsonPath.<Map<String, Object>>read(docs, "$.components.schemas.ProblemDetail.properties"))
                .containsKeys("type", "title", "status", "detail", "instance");
        assertThat(JsonPath.<String>read(docs, "$.info.version")).matches("\\d+\\.\\d+\\.\\d+.*");
    }

    @Test
    @DisplayName("the OpenAPI document shows and prefills the demo user of the running profile")
    void openApiDemoCredentials() {
        String docs = api.get("/v3/api-docs", null).getBody();

        assertThat(JsonPath.<String>read(docs, "$.info.description")).contains("`demo` / `test-password`");
        assertThat(JsonPath.<String>read(docs, "$.components.schemas.LoginRequest.properties.username.example"))
                .isEqualTo("demo");
        assertThat(JsonPath.<String>read(docs, "$.components.schemas.LoginRequest.properties.password.example"))
                .isEqualTo("test-password");
    }

    @Test
    @DisplayName("Swagger UI applies the token of a successful login to Authorize")
    void swaggerUiAutoAuthorize() {
        String initializer = api.get("/swagger-ui/swagger-initializer.js", null).getBody();

        assertThat(initializer)
                .contains("responseInterceptor")
                .contains("preauthorizeApiKey('bearerAuth', response.body.accessToken)")
                .contains("'/api/v1/auth/login'");
    }
}
