package dev.starwars.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.support.TestTokens;
import dev.starwars.support.WebSliceTestConfig;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.ProtectedController.class)
@Import({WebSliceTestConfig.class, SecurityConfigTest.ProtectedController.class})
class SecurityConfigTest {

    private static final String SECRET = "local-dev-secret-change-me-0123456789";
    private static final String ISSUER = "star-wars-api";

    @Autowired
    private MockMvcTester mvc;

    @RestController
    static class ProtectedController {
        @GetMapping("/api/v1/protected")
        String protectedEndpoint() {
            return "ok";
        }
    }

    private static String validToken() {
        return TestTokens.sign(SECRET, ISSUER, Instant.now(), Duration.ofHours(1), "catalog:read");
    }

    private MvcTestResult getWith(String token) {
        return mvc.get()
                .uri("/api/v1/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    private static void assertUnauthorized(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/unauthorized");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(401);
        assertThat(result).bodyJson().extractingPath("$.timestamp").isNotNull();
    }

    @Test
    @DisplayName("valid token with catalog:read: 200")
    void acceptsValidToken() {
        MvcTestResult result = getWith(validToken());

        assertThat(result).hasStatusOk();
        assertThat(result).hasBodyTextEqualTo("ok");
    }

    @Test
    @DisplayName("no token: 401 ProblemDetail")
    void missingToken() {
        assertUnauthorized(mvc.get().uri("/api/v1/protected").exchange());
    }

    @Test
    @DisplayName("expired token: 401")
    void expiredToken() {
        String token = TestTokens.sign(
                SECRET, ISSUER, Instant.now().minus(Duration.ofHours(2)), Duration.ofHours(1), "catalog:read");

        assertUnauthorized(getWith(token));
    }

    @Test
    @DisplayName("token signed with another key: 401")
    void invalidSignature() {
        String token = TestTokens.sign(
                "another-key-another-key-another-key-1234", ISSUER, Instant.now(), Duration.ofHours(1), "catalog:read");

        assertUnauthorized(getWith(token));
    }

    @Test
    @DisplayName("token from another issuer: 401")
    void wrongIssuer() {
        String token = TestTokens.sign(SECRET, "another-issuer", Instant.now(), Duration.ofHours(1), "catalog:read");

        assertUnauthorized(getWith(token));
    }

    @Test
    @DisplayName("token that is not a JWT: 401")
    void garbageToken() {
        assertUnauthorized(getWith("not-a-jwt"));
    }

    @Test
    @DisplayName("valid token without catalog:read scope: 403 ProblemDetail")
    void missingScope() {
        String token = TestTokens.sign(SECRET, ISSUER, Instant.now(), Duration.ofHours(1), null);

        MvcTestResult result = getWith(token);

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/forbidden");
    }

    @Test
    @DisplayName("route outside the allowed ones is rejected even with a valid token")
    void denyByDefault() {
        MvcTestResult result = mvc.get()
                .uri("/internal/anything")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }
}
