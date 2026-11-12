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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** End-to-end flow per resource: login, list, filter, detail and error handling against a stubbed SWAPI. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CatalogFlowIT {

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

    private ApiClient api;
    private String token;

    record Flow(
            String resource,
            String listStub,
            String listFixture,
            int expectedTotal,
            String filterParam,
            String term,
            String searchFixture,
            List<String> searchIds,
            String id,
            String detailFixture,
            String nameField,
            String expectedName) {

        @Override
        public String toString() {
            return resource;
        }
    }

    static Stream<Arguments> flows() {
        return Stream.of(
                        new Flow(
                                "people",
                                "/api/people?page=1&limit=3",
                                "people-list-p1.json",
                                82,
                                "name",
                                "skywalker",
                                "people-search-skywalker.json",
                                List.of("1", "11", "43"),
                                "1",
                                "people-1.json",
                                "name",
                                "Luke Skywalker"),
                        new Flow(
                                "films",
                                "/api/films",
                                "films-list.json",
                                6,
                                "title",
                                "hope",
                                "films-search-hope.json",
                                List.of("1"),
                                "1",
                                "films-1.json",
                                "title",
                                "A New Hope"),
                        new Flow(
                                "starships",
                                "/api/starships?page=1&limit=3",
                                "starships-list-p1.json",
                                36,
                                "name",
                                "death",
                                "starships-search-death.json",
                                List.of("9"),
                                "9",
                                "starships-9.json",
                                "name",
                                "Death Star"),
                        new Flow(
                                "vehicles",
                                "/api/vehicles?page=1&limit=3",
                                "vehicles-list-p1.json",
                                39,
                                "name",
                                "sand",
                                "vehicles-search-sand.json",
                                List.of("4"),
                                "4",
                                "vehicles-4.json",
                                "name",
                                "Sand Crawler"))
                .map(Arguments::of);
    }

    @BeforeEach
    void login() {
        api = new ApiClient(port);
        token = api.login("demo", "test-password");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("flows")
    @DisplayName("login, list, filter by name, filter by id, detail, 404 and SWAPI down")
    void fullFlow(Flow flow) {
        String base = "/api/v1/" + flow.resource();
        swapi.stubFor(get(urlEqualTo(flow.listStub())).willReturn(okJson(Fixtures.swapi(flow.listFixture()))));
        swapi.stubFor(get(urlEqualTo("/api/" + flow.resource() + "?" + flow.filterParam() + "=" + flow.term()))
                .willReturn(okJson(Fixtures.swapi(flow.searchFixture()))));
        swapi.stubFor(get(urlEqualTo("/api/" + flow.resource() + "/" + flow.id()))
                .willReturn(okJson(Fixtures.swapi(flow.detailFixture()))));
        swapi.stubFor(get(urlEqualTo("/api/" + flow.resource() + "/9999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Fixtures.swapi("not-found.json"))));
        swapi.stubFor(get(urlEqualTo("/api/" + flow.resource() + "/500"))
                .willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> list = api.get(base + "?page=1&size=3", token);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<List<?>>read(list.getBody(), "$.content")).hasSize(3);
        assertThat(JsonPath.<Integer>read(list.getBody(), "$.totalElements")).isEqualTo(flow.expectedTotal());

        ResponseEntity<String> byName = api.get(base + "?" + flow.filterParam() + "=" + flow.term(), token);
        assertThat(byName.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<List<String>>read(byName.getBody(), "$.content[*].id"))
                .containsExactlyElementsOf(flow.searchIds());

        ResponseEntity<String> byId = api.get(base + "?id=" + flow.id(), token);
        assertThat(JsonPath.<List<String>>read(byId.getBody(), "$.content[*]." + flow.nameField()))
                .containsExactly(flow.expectedName());

        ResponseEntity<String> detail = api.get(base + "/" + flow.id(), token);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<String>read(detail.getBody(), "$." + flow.nameField()))
                .isEqualTo(flow.expectedName());

        ResponseEntity<String> missing = api.get(base + "/9999", token);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(JsonPath.<String>read(missing.getBody(), "$.type")).isEqualTo("/problems/resource-not-found");

        ResponseEntity<String> unknownInList = api.get(base + "?id=9999", token);
        assertThat(unknownInList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<Integer>read(unknownInList.getBody(), "$.totalElements"))
                .isZero();

        ResponseEntity<String> down = api.get(base + "/500", token);
        assertThat(down.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(JsonPath.<String>read(down.getBody(), "$.type")).isEqualTo("/problems/upstream-unavailable");
    }

    @Test
    @DisplayName("an out-of-range page is empty with the real totals")
    void outOfRangePage() {
        swapi.stubFor(get(urlEqualTo("/api/people?page=999&limit=10"))
                .willReturn(okJson(Fixtures.swapi("people-list-last.json"))));

        ResponseEntity<String> page = api.get("/api/v1/people?page=999", token);

        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JsonPath.<List<?>>read(page.getBody(), "$.content")).isEmpty();
        assertThat(JsonPath.<Integer>read(page.getBody(), "$.totalPages")).isEqualTo(9);
    }
}
