package dev.starwars.swapi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.starwars.config.SwapiClientConfig;
import dev.starwars.config.SwapiProperties;
import dev.starwars.support.Fixtures;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class SwapiRestClientTest {

    @RegisterExtension
    static WireMockExtension swapi = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private SwapiRestClient client;

    @BeforeEach
    void setUp() {
        client = clientWithReadTimeout(Duration.ofSeconds(2));
    }

    private static SwapiRestClient clientWithReadTimeout(Duration readTimeout) {
        var properties = new SwapiProperties(URI.create(swapi.baseUrl() + "/api"), Duration.ofSeconds(1), readTimeout);
        return new SwapiRestClient(SwapiClientConfig.restClient(RestClient.builder(), properties));
    }

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("returns the SWAPI page with uid, name and totals")
        void listsPaginatedResource() {
            swapi.stubFor(get(urlEqualTo("/api/people?page=1&limit=3"))
                    .willReturn(okJson(Fixtures.swapi("people-list-p1.json"))));

            SwapiPage page = client.list(ResourceType.PEOPLE, 1, 3);

            assertThat(page.totalRecords()).isEqualTo(82);
            assertThat(page.totalPages()).isEqualTo(28);
            assertThat(page.items()).extracting(SwapiEntity::uid).containsExactly("1", "2", "3");
            assertThat(page.items().getFirst().property("name")).isEqualTo("Luke Skywalker");
        }

        @Test
        @DisplayName("films returns everything unpaginated and ignores page and limit")
        void listsFilmsWithoutPagination() {
            swapi.stubFor(get(urlEqualTo("/api/films")).willReturn(okJson(Fixtures.swapi("films-list.json"))));

            SwapiPage page = client.list(ResourceType.FILMS, 2, 3);

            assertThat(page.items()).hasSize(6);
            assertThat(page.totalRecords()).isEqualTo(6);
            assertThat(page.totalPages()).isEqualTo(1);
            assertThat(page.items().getFirst().property("title")).isEqualTo("A New Hope");
            swapi.verify(getRequestedFor(urlEqualTo("/api/films")));
        }

        @Test
        @DisplayName("fails with SwapiClientException when totals are missing")
        void failsOnUnexpectedListBody() {
            swapi.stubFor(get(urlPathEqualTo("/api/people")).willReturn(okJson("{\"results\": []}")));

            assertThatThrownBy(() -> client.list(ResourceType.PEOPLE, 1, 10)).isInstanceOf(SwapiClientException.class);
        }
    }

    @Nested
    @DisplayName("search")
    class SearchTests {

        @Test
        @DisplayName("searches by name and returns every match with details")
        void searchesByName() {
            swapi.stubFor(get(urlEqualTo("/api/people?name=skywalker"))
                    .willReturn(okJson(Fixtures.swapi("people-search-skywalker.json"))));

            List<SwapiEntity> result = client.search(ResourceType.PEOPLE, "skywalker");

            assertThat(result).extracting(SwapiEntity::uid).containsExactly("1", "11", "43");
            assertThat(result.getFirst().property("birth_year")).isEqualTo("19BBY");
        }

        @Test
        @DisplayName("searches films by title")
        void searchesFilmsByTitle() {
            swapi.stubFor(get(urlEqualTo("/api/films?title=hope"))
                    .willReturn(okJson(Fixtures.swapi("films-search-hope.json"))));

            assertThat(client.search(ResourceType.FILMS, "hope"))
                    .extracting(e -> e.property("title"))
                    .containsExactly("A New Hope");
        }

        @Test
        @DisplayName("encodes the search term in the URL")
        void encodesSearchTerm() {
            swapi.stubFor(get(urlEqualTo("/api/people?name=luke%20sky"))
                    .willReturn(okJson(Fixtures.swapi("people-search-empty.json"))));

            assertThat(client.search(ResourceType.PEOPLE, "luke sky")).isEmpty();
        }

        @Test
        @DisplayName("no matches returns an empty list")
        void emptySearch() {
            swapi.stubFor(get(urlEqualTo("/api/people?name=zzzz"))
                    .willReturn(okJson(Fixtures.swapi("people-search-empty.json"))));

            assertThat(client.search(ResourceType.PEOPLE, "zzzz")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("returns the detail with every property")
        void findsById() {
            swapi.stubFor(get(urlEqualTo("/api/starships/9")).willReturn(okJson(Fixtures.swapi("starships-9.json"))));

            Optional<SwapiEntity> result = client.findById(ResourceType.STARSHIPS, "9");

            assertThat(result).hasValueSatisfying(entity -> {
                assertThat(entity.uid()).isEqualTo("9");
                assertThat(entity.property("name")).isEqualTo("Death Star");
                assertThat(entity.property("MGLT")).isEqualTo("10");
            });
        }

        @Test
        @DisplayName("a SWAPI 404 is empty")
        void notFoundIsEmpty() {
            swapi.stubFor(get(urlEqualTo("/api/people/9999"))
                    .willReturn(aResponse()
                            .withStatus(404)
                            .withHeader("Content-Type", "application/json")
                            .withBody(Fixtures.swapi("not-found.json"))));

            assertThat(client.findById(ResourceType.PEOPLE, "9999")).isEmpty();
        }
    }

    @Nested
    @DisplayName("errors")
    class ErrorTests {

        @Test
        @DisplayName("a 500 is UpstreamUnavailableException")
        void serverErrorIsUnavailable() {
            swapi.stubFor(
                    get(urlEqualTo("/api/people/1")).willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> client.findById(ResourceType.PEOPLE, "1"))
                    .isInstanceOfSatisfying(
                            UpstreamUnavailableException.class,
                            e -> assertThat(e.reason()).isEqualTo(UpstreamUnavailableException.Reason.SERVER_ERROR));
        }

        @Test
        @DisplayName("a 429 is UpstreamUnavailableException with the SWAPI Retry-After")
        void tooManyRequestsIsUnavailableWithRetryAfter() {
            swapi.stubFor(get(urlEqualTo("/api/people?name=luke"))
                    .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "12")));

            assertThatThrownBy(() -> client.search(ResourceType.PEOPLE, "luke"))
                    .isInstanceOfSatisfying(UpstreamUnavailableException.class, e -> {
                        assertThat(e.reason()).isEqualTo(UpstreamUnavailableException.Reason.RATE_LIMITED);
                        assertThat(e.retryAfter()).contains(Duration.ofSeconds(12));
                    });
        }

        @Test
        @DisplayName("a read timeout is UpstreamUnavailableException")
        void timeoutIsUnavailable() {
            swapi.stubFor(get(urlEqualTo("/api/people/1"))
                    .willReturn(okJson(Fixtures.swapi("people-1.json")).withFixedDelay(1_000)));
            SwapiRestClient impatientClient = clientWithReadTimeout(Duration.ofMillis(200));

            assertThatThrownBy(() -> impatientClient.findById(ResourceType.PEOPLE, "1"))
                    .isInstanceOfSatisfying(
                            UpstreamUnavailableException.class,
                            e -> assertThat(e.reason()).isEqualTo(UpstreamUnavailableException.Reason.TIMEOUT));
        }

        @Test
        @DisplayName("a refused connection is UpstreamUnavailableException with reason CONNECTION")
        void refusedConnectionIsUnavailable() {
            var properties = new SwapiProperties(
                    URI.create("http://localhost:1/api"), Duration.ofSeconds(1), Duration.ofSeconds(1));
            var unreachable = new SwapiRestClient(SwapiClientConfig.restClient(RestClient.builder(), properties));

            assertThatThrownBy(() -> unreachable.findById(ResourceType.PEOPLE, "1"))
                    .isInstanceOfSatisfying(
                            UpstreamUnavailableException.class,
                            e -> assertThat(e.reason()).isEqualTo(UpstreamUnavailableException.Reason.CONNECTION));
        }

        @Test
        @DisplayName("another 4xx is SwapiClientException")
        void clientErrorIsSwapiClientException() {
            swapi.stubFor(
                    get(urlEqualTo("/api/people/1")).willReturn(aResponse().withStatus(400)));

            assertThatThrownBy(() -> client.findById(ResourceType.PEOPLE, "1"))
                    .isInstanceOf(SwapiClientException.class);
        }

        @Test
        @DisplayName("a 404 on a listing is SwapiClientException")
        void notFoundOnListIsSwapiClientException() {
            swapi.stubFor(
                    get(urlPathEqualTo("/api/people")).willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> client.list(ResourceType.PEOPLE, 1, 10)).isInstanceOf(SwapiClientException.class);
        }

        @Test
        @DisplayName("invalid JSON is SwapiClientException")
        void malformedJsonIsSwapiClientException() {
            swapi.stubFor(get(urlEqualTo("/api/people/1")).willReturn(okJson("{ this is not json")));

            assertThatThrownBy(() -> client.findById(ResourceType.PEOPLE, "1"))
                    .isInstanceOf(SwapiClientException.class);
        }

        @Test
        @DisplayName("JSON with another shape is SwapiClientException")
        void unexpectedShapeIsSwapiClientException() {
            swapi.stubFor(get(urlEqualTo("/api/people/1")).willReturn(okJson("{\"message\": \"ok\"}")));

            assertThatThrownBy(() -> client.findById(ResourceType.PEOPLE, "1"))
                    .isInstanceOf(SwapiClientException.class);
        }
    }
}
