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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Cache on the SWAPI client, seen through the public API.
 * No {@code @Nested} classes: the WireMock extension would restart on a new port for each one,
 * while the Spring context keeps the first base URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
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

    private ApiClient api;
    private String token;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        api = new ApiClient(port);
        token = api.login("demo", "test-password");
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
}
