package dev.starwars;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.jayway.jsonpath.JsonPath;
import dev.starwars.support.ApiClient;
import dev.starwars.support.Fixtures;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cache, retry and circuit breaker on the SWAPI client, seen through the public API.
 * No {@code @Nested} classes: the WireMock extension would restart on a new port for each one,
 * while the Spring context keeps the first base URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SwapiResilienceIT {

    @RegisterExtension
    static WireMockExtension swapi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void swapiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.swapi.base-url", () -> swapi.baseUrl() + "/api");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        circuitBreaker().reset();
        api = new ApiClient(port);
        token = api.login("demo", "test-password");
    }

    private CircuitBreaker circuitBreaker() {
        return circuitBreakers.circuitBreaker("swapi");
    }

    private ResponseEntity<String> call(String uri) {
        return api.get(uri, token);
    }

    @Test
    @DisplayName("cache: two equal requests reach SWAPI once")
    void repeatedListHitsSwapiOnce() {
        swapi.stubFor(get(urlEqualTo("/api/people?page=1&limit=3"))
                .willReturn(okJson(Fixtures.swapi("people-list-p1.json"))));

        assertThat(call("/api/v1/people?page=1&size=3").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call("/api/v1/people?page=1&size=3").getStatusCode()).isEqualTo(HttpStatus.OK);

        swapi.verify(1, getRequestedFor(urlEqualTo("/api/people?page=1&limit=3")));
    }

    @Test
    @DisplayName("cache: a search and its second page reach SWAPI once, whatever the case of the term")
    void searchPagesHitSwapiOnce() {
        swapi.stubFor(get(urlEqualTo("/api/people?name=skywalker"))
                .willReturn(okJson(Fixtures.swapi("people-search-skywalker.json"))));
        swapi.stubFor(get(urlEqualTo("/api/people?name=Skywalker"))
                .willReturn(okJson(Fixtures.swapi("people-search-skywalker.json"))));

        ResponseEntity<String> first = call("/api/v1/people?name=skywalker&page=1&size=2");
        ResponseEntity<String> second = call("/api/v1/people?name=Skywalker&page=2&size=2");

        assertThat(JsonPath.<List<String>>read(first.getBody(), "$.content[*].id"))
                .containsExactly("1", "11");
        assertThat(JsonPath.<List<String>>read(second.getBody(), "$.content[*].id"))
                .containsExactly("43");
        swapi.verify(1, getRequestedFor(urlPathMatching("/api/people")));
    }

    @Test
    @DisplayName("cache: a detail is cached, a 404 is not")
    void detailCachedButNotFoundIsNot() {
        swapi.stubFor(get(urlEqualTo("/api/people/1")).willReturn(okJson(Fixtures.swapi("people-1.json"))));
        swapi.stubFor(get(urlEqualTo("/api/people/9999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.swapi("not-found.json"))));

        call("/api/v1/people/1");
        call("/api/v1/people/1");
        assertThat(call("/api/v1/people/9999").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call("/api/v1/people/9999").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        swapi.verify(1, getRequestedFor(urlEqualTo("/api/people/1")));
        swapi.verify(2, getRequestedFor(urlEqualTo("/api/people/9999")));
    }

    @Test
    @DisplayName("cache: an error is not cached")
    void errorNotCached() {
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario("recovers")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(400))
                .willSetStateTo("up"));
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario("recovers")
                .whenScenarioStateIs("up")
                .willReturn(okJson(Fixtures.swapi("people-1.json"))));

        assertThat(call("/api/v1/people/1").getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(call("/api/v1/people/1").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("retry: 500, 500, 200 answers 200 after three requests")
    void retriesServerErrors() {
        String scenario = "flaky";
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("failed once"));
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario(scenario)
                .whenScenarioStateIs("failed once")
                .willReturn(aResponse().withStatus(502))
                .willSetStateTo("failed twice"));
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario(scenario)
                .whenScenarioStateIs("failed twice")
                .willReturn(okJson(Fixtures.swapi("people-1.json"))));

        ResponseEntity<String> response = call("/api/v1/people/1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        swapi.verify(3, getRequestedFor(urlEqualTo("/api/people/1")));
    }

    @Test
    @DisplayName("retry: a 503 followed by 200 logs the retry and the success under one requestId")
    void logsRecoveredRetry(CapturedOutput output) {
        String scenario = "fails once";
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario(scenario)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("up"));
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario(scenario)
                .whenScenarioStateIs("up")
                .willReturn(okJson(Fixtures.swapi("people-1.json"))));

        ResponseEntity<String> response = call("/api/v1/people/1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertThat(output)
                .containsPattern(
                        "\\[" + requestId + "] .*SWAPI attempt 1/3 failed \\(SERVER_ERROR\\), retrying in \\d+ms")
                .containsPattern("\\[" + requestId + "] .*SWAPI call succeeded on attempt 2")
                .containsPattern("\\[" + requestId + "] .*GET /api/v1/people/\\{id} 200 \\d+ms");
    }

    @Test
    @DisplayName("retry: gives up after three attempts with 503")
    void givesUpAfterThreeAttempts(CapturedOutput output) {
        swapi.stubFor(get(urlEqualTo("/api/people/1")).willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> response = call("/api/v1/people/1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        swapi.verify(3, getRequestedFor(urlEqualTo("/api/people/1")));
        assertThat(output)
                .containsPattern("WARN .*SWAPI call failed after 3 attempts \\(SERVER_ERROR\\)")
                .containsPattern("WARN .*GET /api/v1/people/\\{id} 503");
    }

    @Test
    @DisplayName("retry: a 429 with a short Retry-After waits that long and retries")
    void retriesShortRetryAfter() {
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario("rate limited")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "1"))
                .willSetStateTo("allowed"));
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .inScenario("rate limited")
                .whenScenarioStateIs("allowed")
                .willReturn(okJson(Fixtures.swapi("people-1.json"))));

        long start = System.nanoTime();
        ResponseEntity<String> response = call("/api/v1/people/1");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
        swapi.verify(2, getRequestedFor(urlEqualTo("/api/people/1")));
    }

    @Test
    @DisplayName("retry: a 429 with a long Retry-After is not retried and the header is passed on")
    void longRetryAfterIsPassedOn() {
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "60")));

        ResponseEntity<String> response = call("/api/v1/people/1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        swapi.verify(1, getRequestedFor(urlEqualTo("/api/people/1")));
    }

    @Test
    @DisplayName("retry: a 4xx is not retried")
    void clientErrorNotRetried() {
        swapi.stubFor(get(urlEqualTo("/api/people/1")).willReturn(aResponse().withStatus(400)));

        assertThat(call("/api/v1/people/1").getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        swapi.verify(1, getRequestedFor(urlEqualTo("/api/people/1")));
    }

    @Test
    @DisplayName("retry: a read timeout answers 503 without retrying")
    void timeoutNotRetried(CapturedOutput output) {
        swapi.stubFor(get(urlEqualTo("/api/people/1"))
                .willReturn(okJson(Fixtures.swapi("people-1.json")).withFixedDelay(4_000)));

        long start = System.nanoTime();
        ResponseEntity<String> response = call("/api/v1/people/1");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
        swapi.verify(1, getRequestedFor(urlEqualTo("/api/people/1")));
        assertThat(output).containsPattern("WARN .*SWAPI call failed and is not retried \\(TIMEOUT\\)");
    }

    @Test
    @DisplayName("circuit breaker: repeated failures open the circuit and SWAPI is no longer called")
    void opensAfterRepeatedFailures(CapturedOutput output) {
        swapi.stubFor(
                get(urlPathMatching("/api/people/.*")).willReturn(aResponse().withStatus(500)));

        for (int i = 1; i <= 5 && circuitBreaker().getState() != CircuitBreaker.State.OPEN; i++) {
            assertThat(call("/api/v1/people/" + i).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
        assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int requestsBefore = swapi.getAllServeEvents().size();

        ResponseEntity<String> response = call("/api/v1/people/42");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(JsonPath.<String>read(response.getBody(), "$.type")).isEqualTo("/problems/upstream-unavailable");
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(swapi.getAllServeEvents()).hasSize(requestsBefore);
        assertThat(output).containsPattern("WARN .*SWAPI circuit breaker changed from CLOSED to OPEN");
    }

    @Test
    @DisplayName("circuit breaker: a 4xx does not count as a failure")
    void clientErrorsDoNotOpen() {
        swapi.stubFor(
                get(urlPathMatching("/api/people/.*")).willReturn(aResponse().withStatus(400)));

        for (int i = 1; i <= 12; i++) {
            call("/api/v1/people/" + i);
        }

        assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
