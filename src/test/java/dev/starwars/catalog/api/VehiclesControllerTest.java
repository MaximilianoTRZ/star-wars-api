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

@WebMvcTest(VehiclesController.class)
@Import(WebSliceTestConfig.class)
class VehiclesControllerTest {

    private static final CatalogItem SAND_CRAWLER = new CatalogItem(
            "4",
            "Sand Crawler",
            Map.ofEntries(
                    Map.entry("name", "Sand Crawler"),
                    Map.entry("model", "Digger Crawler"),
                    Map.entry("manufacturer", "Corellia Mining Corporation"),
                    Map.entry("vehicleClass", "wheeled"),
                    Map.entry("costInCredits", "150000"),
                    Map.entry("length", "36.8 "),
                    Map.entry("crew", "46"),
                    Map.entry("passengers", "30"),
                    Map.entry("cargoCapacity", "50000")));

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CatalogService catalogService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("lists vehicles filtered by id and name")
    void listByIdAndName() {
        given(catalogService.find(
                        CatalogResource.VEHICLES, new SearchCriteria("4", "sand"), new PageRequestParams(1, 10)))
                .willReturn(new PageResponse<>(List.of(SAND_CRAWLER), 1, 10, 1, 1));

        MvcTestResult result = mvc.get()
                .uri("/api/v1/vehicles?id=4&name=sand")
                .with(CATALOG_READER)
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"content": [{"id": "4", "name": "Sand Crawler"}]}
                """);
    }

    @Test
    @DisplayName("returns the vehicle detail with trimmed values")
    void detail() {
        given(catalogService.getById(CatalogResource.VEHICLES, "4")).willReturn(SAND_CRAWLER);

        MvcTestResult result =
                mvc.get().uri("/api/v1/vehicles/4").with(CATALOG_READER).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().isStrictlyEqualTo("""
                {"id": "4", "name": "Sand Crawler", "model": "Digger Crawler",
                 "manufacturer": "Corellia Mining Corporation", "vehicleClass": "wheeled",
                 "costInCredits": "150000", "length": "36.8", "crew": "46", "passengers": "30",
                 "cargoCapacity": "50000"}
                """);
    }
}
