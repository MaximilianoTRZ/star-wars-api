package dev.starwars.catalog.api;

import static dev.starwars.catalog.api.PeopleControllerTest.CATALOG_READER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.starwars.catalog.domain.CatalogItem;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(StarshipsController.class)
@Import(WebSliceTestConfig.class)
class StarshipsControllerTest {

    private static final CatalogItem DEATH_STAR = new CatalogItem(
            "9",
            "Death Star",
            Map.ofEntries(
                    Map.entry("name", "Death Star"),
                    Map.entry("model", "DS-1 Orbital Battle Station"),
                    Map.entry("manufacturer", "Imperial Department of Military Research"),
                    Map.entry("starshipClass", "Deep Space Mobile Battlestation"),
                    Map.entry("costInCredits", "1000000000000"),
                    Map.entry("length", "120000"),
                    Map.entry("crew", "342,953"),
                    Map.entry("passengers", "843,342"),
                    Map.entry("cargoCapacity", "1000000000000"),
                    Map.entry("hyperdriveRating", "4.0"),
                    Map.entry("mglt", "10")));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("lists starships filtered by id")
    void listById() {
        given(catalogService.find(
                        CatalogResource.STARSHIPS, new SearchCriteria("9", null), new PageRequestParams(1, 10)))
                .willReturn(new PageResponse<>(List.of(DEATH_STAR), 1, 10, 1, 1));

        MvcTestResult result =
                mvc.get().uri("/api/v1/starships?id=9").with(CATALOG_READER).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"content": [{"id": "9", "name": "Death Star"}], "totalElements": 1, "totalPages": 1}
                """);
    }

    @Test
    @DisplayName("returns the starship detail")
    void detail() {
        given(catalogService.getById(CatalogResource.STARSHIPS, "9")).willReturn(DEATH_STAR);

        MvcTestResult result =
                mvc.get().uri("/api/v1/starships/9").with(CATALOG_READER).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {"id": "9", "name": "Death Star", "model": "DS-1 Orbital Battle Station",
                 "manufacturer": "Imperial Department of Military Research",
                 "starshipClass": "Deep Space Mobile Battlestation", "costInCredits": "1000000000000",
                 "length": "120000", "crew": "342,953", "passengers": "843,342",
                 "cargoCapacity": "1000000000000", "hyperdriveRating": "4.0", "mglt": "10"}
                """);
    }
}
