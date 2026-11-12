package dev.starwars;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import dev.starwars.support.ApiClient;
import dev.starwars.support.Fixtures;
import dev.starwars.support.TestTokens;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** GraphQL over HTTP against the running app: real login, security chain and a stubbed SWAPI. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class GraphQlIT {

    private static final String TEST_SECRET = "test-secret-0123456789-0123456789-0123456789";
    private static final String PEOPLE_QUERY = "{ people(name: \"skywalker\") { content { id name } totalElements } }";

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

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        circuitBreakers.circuitBreaker("swapi").reset();
        api = new ApiClient(port);
    }

    @Test
    @DisplayName("a logged-in user lists, filters and reads the detail through /graphql")
    void queriesWithToken() {
        swapi.stubFor(get(urlEqualTo("/api/people?name=skywalker"))
                .willReturn(okJson(Fixtures.swapi("people-search-skywalker.json"))));
        swapi.stubFor(get(urlEqualTo("/api/people/1")).willReturn(okJson(Fixtures.swapi("people-1.json"))));
        String token = api.login("demo", "test-password");

        ResponseEntity<String> list = api.graphql(PEOPLE_QUERY, token);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<List<String>>read(list.getBody(), "$.data.people.content[*].id"))
                .containsExactly("1", "11", "43");
        assertThat(JsonPath.<Integer>read(list.getBody(), "$.data.people.totalElements"))
                .isEqualTo(3);

        ResponseEntity<String> detail = api.graphql("{ person(id: 1) { name hairColor birthYear } }", token);
        assertThat(JsonPath.<String>read(detail.getBody(), "$.data.person.name"))
                .isEqualTo("Luke Skywalker");
        assertThat(JsonPath.<String>read(detail.getBody(), "$.data.person.birthYear"))
                .isEqualTo("19BBY");
        assertThat(detail.getBody()).doesNotContain("\"errors\"");
    }

    @Test
    @DisplayName("errors carry the REST problem type and the request ID of the header and the logs")
    void errorsAreCorrelated(CapturedOutput output) {
        swapi.stubFor(get(urlEqualTo("/api/people/9999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.swapi("not-found.json"))));
        swapi.stubFor(get(urlEqualTo("/api/films/1")).willReturn(aResponse().withStatus(500)));
        String token = api.login("demo", "test-password");

        ResponseEntity<String> missing = api.graphql("{ person(id: 9999) { name } }", token);
        String missingId = missing.getHeaders().getFirst("X-Request-Id");
        assertThat(JsonPath.<String>read(missing.getBody(), "$.errors[0].extensions.classification"))
                .isEqualTo("NOT_FOUND");
        assertThat(JsonPath.<String>read(missing.getBody(), "$.errors[0].extensions.requestId"))
                .isNotBlank()
                .isEqualTo(missingId);

        ResponseEntity<String> down = api.graphql("{ film(id: 1) { title } }", token);
        String downId = down.getHeaders().getFirst("X-Request-Id");
        assertThat(JsonPath.<String>read(down.getBody(), "$.errors[0].extensions.type"))
                .isEqualTo("/problems/upstream-unavailable");
        assertThat(JsonPath.<String>read(down.getBody(), "$.errors[0].extensions.requestId"))
                .isEqualTo(downId);
        assertThat(output)
                .containsPattern("\\[" + downId + "] .*SWAPI attempt 1/3 failed")
                .containsPattern("\\[" + downId + "] .*POST /graphql 200");
    }

    @Test
    @DisplayName("/graphql without a token returns 401 ProblemDetail")
    void requiresToken() {
        ResponseEntity<String> response = api.graphql(PEOPLE_QUERY, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(JsonPath.<String>read(response.getBody(), "$.type")).isEqualTo("/problems/unauthorized");
    }

    @Test
    @DisplayName("/graphql with a token without the catalog:read scope returns 403")
    void requiresScope() {
        String token = TestTokens.sign(TEST_SECRET, "star-wars-api", Instant.now(), Duration.ofMinutes(5), null);

        assertThat(api.graphql(PEOPLE_QUERY, token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("GraphiQL loads without a token")
    void graphiqlIsPublic() {
        ResponseEntity<String> redirect = api.get("/graphiql", null);
        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
        String location = redirect.getHeaders().getLocation().toString();
        assertThat(location).contains("/graphiql?path=/graphql");

        ResponseEntity<String> page = api.get(location, null);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).containsIgnoringCase("graphiql");
    }
}
