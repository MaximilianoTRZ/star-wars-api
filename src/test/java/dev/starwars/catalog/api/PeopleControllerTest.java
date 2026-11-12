package dev.starwars.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import dev.starwars.catalog.domain.CatalogItem;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import dev.starwars.catalog.domain.ResourceNotFoundException;
import dev.starwars.catalog.domain.SearchCriteria;
import dev.starwars.catalog.service.CatalogService;
import dev.starwars.support.WebSliceTestConfig;
import dev.starwars.swapi.SwapiClientException;
import dev.starwars.swapi.UpstreamUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PeopleController.class)
@Import(WebSliceTestConfig.class)
class PeopleControllerTest {

    static final RequestPostProcessor CATALOG_READER =
            jwt().authorities(new SimpleGrantedAuthority("SCOPE_catalog:read"));

    private static final CatalogItem LUKE = new CatalogItem(
            "1",
            "Luke Skywalker",
            Map.of(
                    "name",
                    "Luke Skywalker",
                    "height",
                    "172",
                    "mass",
                    "77",
                    "hairColor",
                    "blond",
                    "skinColor",
                    "fair",
                    "eyeColor",
                    "blue",
                    "birthYear",
                    "19BBY",
                    "gender",
                    "male"));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private MvcTestResult get(String uri) {
        MockMvcRequestBuilder request = mvc.get().uri(uri).with(CATALOG_READER);
        return request.exchange();
    }

    @Nested
    @DisplayName("GET /api/v1/people")
    class ListPeople {

        @Test
        @DisplayName("uses page 1 and size 10 by default and returns the page shape")
        void defaults() {
            given(catalogService.find(CatalogResource.PEOPLE, SearchCriteria.none(), new PageRequestParams(1, 10)))
                    .willReturn(new PageResponse<>(List.of(LUKE), 1, 10, 82, 9));

            MvcTestResult result = get("/api/v1/people");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"content": [{"id": "1", "name": "Luke Skywalker"}],
                     "page": 1, "size": 10, "totalElements": 82, "totalPages": 9}
                    """);
            assertThat(result).bodyJson().extractingPath("$.content[0]").asMap().containsOnlyKeys("id", "name");
        }

        @Test
        @DisplayName("passes page, size, id and name to the service")
        void bindsParameters() {
            given(catalogService.find(
                            CatalogResource.PEOPLE, new SearchCriteria("1", "luke"), new PageRequestParams(2, 5)))
                    .willReturn(new PageResponse<>(List.of(), 2, 5, 1, 1));

            assertThat(get("/api/v1/people?page=2&size=5&id=1&name=luke")).hasStatusOk();
        }

        @ParameterizedTest(name = "{0} -> 400 on {1}")
        @CsvSource({"page=0, page", "size=0, size", "size=101, size", "id=abc, id", "id=1;DROP, id"})
        @DisplayName("invalid parameters return 400 listing the field")
        void invalidParameters(String query, String field) {
            MvcTestResult result = get("/api/v1/people?" + query);

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/validation-error");
            assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo(field);
            then(catalogService).should(never()).find(any(), any(), any());
        }

        @Test
        @DisplayName("a name longer than 100 characters returns 400")
        void nameTooLong() {
            assertThat(get("/api/v1/people?name=" + "a".repeat(101))).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("a non-numeric page returns 400")
        void pageTypeMismatch() {
            MvcTestResult result = get("/api/v1/people?page=abc");

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(result).bodyJson().extractingPath("$.timestamp").isNotNull();
        }

        @Test
        @DisplayName("SWAPI unavailable returns 503 with Retry-After")
        void upstreamUnavailable() {
            given(catalogService.find(any(), any(), any()))
                    .willThrow(new UpstreamUnavailableException(
                            "down", UpstreamUnavailableException.Reason.RATE_LIMITED, Duration.ofSeconds(30), null));

            MvcTestResult result = get("/api/v1/people");

            assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(result).hasHeader(HttpHeaders.RETRY_AFTER, "30");
            assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/upstream-unavailable");
        }

        @Test
        @DisplayName("unexpected SWAPI response returns 502")
        void upstreamError() {
            given(catalogService.find(any(), any(), any())).willThrow(new SwapiClientException("bad body"));

            MvcTestResult result = get("/api/v1/people");

            assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY);
            assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/upstream-error");
        }

        @Test
        @DisplayName("an unexpected error returns 500 without internal details")
        void unexpectedError() {
            given(catalogService.find(any(), any(), any())).willThrow(new IllegalStateException("secret detail"));

            MvcTestResult result = get("/api/v1/people");

            assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/internal-error");
            assertThat(result).bodyText().doesNotContain("secret detail");
        }

        @Test
        @DisplayName("without a token returns 401")
        void unauthorized() {
            assertThat(mvc.get().uri("/api/v1/people").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("with a token without the catalog:read scope returns 403")
        void forbidden() {
            assertThat(mvc.get().uri("/api/v1/people").with(jwt()).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/people/{id}")
    class GetPerson {

        @Test
        @DisplayName("returns the person detail in camelCase")
        void detail() {
            given(catalogService.getById(CatalogResource.PEOPLE, "1")).willReturn(LUKE);

            MvcTestResult result = get("/api/v1/people/1");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().isStrictlyEqualTo("""
                    {"id": "1", "name": "Luke Skywalker", "height": "172", "mass": "77", "hairColor": "blond",
                     "skinColor": "fair", "eyeColor": "blue", "birthYear": "19BBY", "gender": "male"}
                    """);
        }

        @Test
        @DisplayName("an unknown id returns 404 ProblemDetail")
        void notFound() {
            given(catalogService.getById(CatalogResource.PEOPLE, "9999"))
                    .willThrow(new ResourceNotFoundException(CatalogResource.PEOPLE, "9999"));

            MvcTestResult result = get("/api/v1/people/9999");

            assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {"type": "/problems/resource-not-found", "title": "Resource not found", "status": 404,
                     "detail": "No people found with id 9999", "instance": "/api/v1/people/9999"}
                    """);
            assertThat(result).bodyJson().extractingPath("$.timestamp").isNotNull();
        }

        @Test
        @DisplayName("a non-numeric id returns 400")
        void invalidId() {
            assertThat(get("/api/v1/people/abc")).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }
}
