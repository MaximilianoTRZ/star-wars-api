package dev.starwars.catalog.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.starwars.catalog.domain.CatalogItem;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import dev.starwars.catalog.domain.ResourceNotFoundException;
import dev.starwars.catalog.domain.SearchCriteria;
import dev.starwars.catalog.service.CatalogService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class CatalogMcpToolsTest {

    private static final CatalogItem LUKE =
            new CatalogItem("1", "Luke Skywalker", Map.of("height", "172", "birthYear", "19BBY", "gender", "male"));

    private CatalogService catalogService;
    private CatalogMcpTools tools;

    @BeforeEach
    void setUp() {
        catalogService = mock(CatalogService.class);
        tools = new CatalogMcpTools(
                catalogService, ex -> "public message", JsonMapper.builder().build());
    }

    @Test
    @DisplayName("search tools use page 1 and size 10 by default and return the page as JSON")
    void searchDefaults() {
        given(catalogService.find(eq(CatalogResource.PEOPLE), any(), any()))
                .willReturn(new PageResponse<>(List.of(LUKE), 1, 10, 3, 1));

        CallToolResult result = tools.searchPeople("skywalker", null, null, null);

        assertThat(result.isError()).isFalse();
        assertThat(text(result))
                .contains("\"name\":\"Luke Skywalker\"")
                .contains("\"totalElements\":3")
                .doesNotContain("height");
        then(catalogService)
                .should()
                .find(CatalogResource.PEOPLE, new SearchCriteria(null, "skywalker"), new PageRequestParams(1, 10));
    }

    @Test
    @DisplayName("filters and pagination reach the service as in REST")
    void filtersAndPagination() {
        given(catalogService.find(any(), any(), any())).willReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        tools.searchStarships("death", "9", 2, 5);
        tools.searchFilms("hope", null, null, null);
        tools.searchVehicles(null, null, null, 100);

        then(catalogService)
                .should()
                .find(CatalogResource.STARSHIPS, new SearchCriteria("9", "death"), new PageRequestParams(2, 5));
        then(catalogService)
                .should()
                .find(CatalogResource.FILMS, new SearchCriteria(null, "hope"), new PageRequestParams(1, 10));
        then(catalogService)
                .should()
                .find(CatalogResource.VEHICLES, SearchCriteria.none(), new PageRequestParams(1, 100));
    }

    @Test
    @DisplayName("get tools return every attribute with the REST field names")
    void detail() {
        given(catalogService.getById(CatalogResource.PEOPLE, "1")).willReturn(LUKE);
        given(catalogService.getById(CatalogResource.FILMS, "1"))
                .willReturn(new CatalogItem("1", "A New Hope", Map.of("episodeId", 4)));
        given(catalogService.getById(CatalogResource.STARSHIPS, "9"))
                .willReturn(new CatalogItem("9", "Death Star", Map.of("mglt", "10")));
        given(catalogService.getById(CatalogResource.VEHICLES, "4"))
                .willReturn(new CatalogItem("4", "Sand Crawler", Map.of("vehicleClass", "wheeled")));

        assertThat(text(tools.getPerson("1"))).contains("\"birthYear\":\"19BBY\"");
        assertThat(text(tools.getFilm("1"))).contains("\"title\":\"A New Hope\"", "\"episodeId\":4");
        assertThat(text(tools.getStarship("9"))).contains("\"mglt\":\"10\"");
        assertThat(text(tools.getVehicle("4"))).contains("\"vehicleClass\":\"wheeled\"");
    }

    @Test
    @DisplayName("an unknown id is a normal result with a clear message, not an error")
    void notFound() {
        given(catalogService.getById(CatalogResource.PEOPLE, "9999"))
                .willThrow(new ResourceNotFoundException(CatalogResource.PEOPLE, "9999"));

        CallToolResult result = tools.getPerson("9999");

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).startsWith("No people found with id 9999.");
    }

    @ParameterizedTest(name = "id={0}, page={1}, size={2}")
    @CsvSource(
            nullValues = "null",
            value = {
                "abc, null, null, The id must be a numeric SWAPI id",
                "null, 0, null, The page must be 1 or greater",
                "null, null, 0, The size must be between 1 and 100",
                "null, null, 101, The size must be between 1 and 100"
            })
    @DisplayName("invalid input is an error result without calling the service")
    void invalidInput(String id, Integer page, Integer size, String message) {
        CallToolResult result = tools.searchPeople(null, id, page, size);

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).startsWith(message);
        verifyNoInteractions(catalogService);
    }

    @Test
    @DisplayName("a name filter longer than 100 characters is rejected")
    void longFilter() {
        CallToolResult result = tools.searchPeople("x".repeat(101), null, null, null);

        assertThat(result.isError()).isTrue();
        verifyNoInteractions(catalogService);
    }

    @Test
    @DisplayName("any other failure is an error result with the public message, never the internal one")
    void failure() {
        given(catalogService.getById(CatalogResource.FILMS, "1"))
                .willThrow(new IllegalStateException("database password is hunter2"));

        CallToolResult result = tools.getFilm("1");

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("public message");
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }
}
