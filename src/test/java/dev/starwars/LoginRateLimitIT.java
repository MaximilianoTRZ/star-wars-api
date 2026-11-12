package dev.starwars;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Login rate limit on the running app, with the production limit (5 per minute) and client IPs taken from
 * {@code CF-Connecting-IP} as in {@code prod}, where Cloudflare sets it in front of Render.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.security.login-rate-limit.capacity=5", "app.client-ip.header=CF-Connecting-IP"})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LoginRateLimitIT {

    private static final String BODY = "{\"username\":\"demo\",\"password\":\"test-password\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry registry;

    private ResponseEntity<String> loginFrom(String ip) {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {})
                .build()
                .post()
                .uri("/api/v1/auth/login")
                .header("CF-Connecting-IP", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BODY)
                .retrieve()
                .toEntity(String.class);
    }

    @Test
    @DisplayName("the 6th login in a minute from the same IP gets 429 with Retry-After; another IP is not affected")
    void sixthLoginIsRejected(CapturedOutput output) {
        double blockedBefore = registry.get("auth.login.attempts")
                .tag("result", "blocked")
                .counter()
                .count();

        for (int i = 0; i < 5; i++) {
            assertThat(loginFrom("203.0.113.10").getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        ResponseEntity<String> sixth = loginFrom("203.0.113.10");

        assertThat(sixth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sixth.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(Long.parseLong(sixth.getHeaders().getFirst("Retry-After"))).isBetween(1L, 60L);
        assertThat(JsonPath.<String>read(sixth.getBody(), "$.type")).isEqualTo("/problems/too-many-requests");
        assertThat(JsonPath.<String>read(sixth.getBody(), "$.requestId")).isNotBlank();
        assertThat(loginFrom("203.0.113.20").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registry.get("auth.login.attempts")
                        .tag("result", "blocked")
                        .counter()
                        .count())
                .isEqualTo(blockedBefore + 1);
        assertThat(output)
                .containsPattern("WARN.*Login blocked by rate limit for 203\\.0\\.113\\.10, retry in \\d+s")
                .containsPattern("INFO.*POST /api/v1/auth/login 429");
    }

    @Test
    @DisplayName("a client cannot dodge the limit by sending its own X-Forwarded-For")
    void spoofedForwardedForIsIgnored() {
        for (int i = 0; i < 6; i++) {
            ResponseEntity<String> response = RestClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .defaultStatusHandler(status -> true, (request, resp) -> {})
                    .build()
                    .post()
                    .uri("/api/v1/auth/login")
                    .header("X-Forwarded-For", "198.51.100." + i)
                    .header("CF-Connecting-IP", "203.0.113.30")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BODY)
                    .retrieve()
                    .toEntity(String.class);

            assertThat(response.getStatusCode()).isEqualTo(i < 5 ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
