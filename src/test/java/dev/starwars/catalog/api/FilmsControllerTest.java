package dev.starwars.catalog.api;

import static dev.starwars.catalog.api.PeopleControllerTest.CATALOG_READER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.starwars.catalog.domain.CatalogItem;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import dev.starwars.catalog.domain.ResourceNotFoundException;
import dev.starwars.catalog.domain.SearchCriteria;
import dev.starwars.catalog.service.CatalogService;
import dev.starwars.support.WebSliceTestConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(FilmsController.class)
@Import(WebSliceTestConfig.class)
class FilmsControllerTest {

    private static final CatalogItem A_NEW_HOPE = new CatalogItem(
            "1",
            "A New Hope",
            Map.of(
                    "title",
                    "A New Hope",
                    "episodeId",
                    4,
                    "director",
                    "George Lucas",
                    "producer",
                    "Gary Kurtz, Rick McCallum",
                    "releaseDate",
                    "1977-05-25",
                    "openingCrawl",
                    "It is a period..."));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("lists films filtered by title, exposing id and title")
    void listByTitle() {
        given(catalogService.find(
                        CatalogResource.FILMS, new SearchCriteria(null, "hope"), new PageRequestParams(1, 10)))
                .willReturn(new PageResponse<>(List.of(A_NEW_HOPE), 1, 10, 1, 1));

        MvcTestResult result =
                mvc.get().uri("/api/v1/films?title=hope").with(CATALOG_READER).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"content": [{"id": "1", "title": "A New Hope"}], "totalElements": 1}
                """);
    }

    @Test
    @DisplayName("returns the film detail with a numeric episodeId")
    void detail() {
        given(catalogService.getById(CatalogResource.FILMS, "1")).willReturn(A_NEW_HOPE);

        MvcTestResult result =
                mvc.get().uri("/api/v1/films/1").with(CATALOG_READER).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {"id": "1", "title": "A New Hope", "episodeId": 4, "director": "George Lucas",
                 "producer": "Gary Kurtz, Rick McCallum", "releaseDate": "1977-05-25",
                 "openingCrawl": "It is a period..."}
                """);
    }

    @Test
    @DisplayName("an unknown film returns 404")
    void notFound() {
        given(catalogService.getById(CatalogResource.FILMS, "99"))
                .willThrow(new ResourceNotFoundException(CatalogResource.FILMS, "99"));

        assertThat(mvc.get().uri("/api/v1/films/99").with(CATALOG_READER).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }
}
