package dev.starwars.catalog.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import dev.starwars.catalog.domain.CatalogItem;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import dev.starwars.catalog.domain.SearchCriteria;
import dev.starwars.catalog.service.CatalogService;
import dev.starwars.common.exception.ProblemDetailFactory;
import dev.starwars.config.ClockConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@GraphQlTest(CatalogGraphQlController.class)
@Import({ProblemDetailFactory.class, ClockConfig.class})
class CatalogGraphQlControllerTest {

    private static final CatalogItem LUKE = new CatalogItem(
            "1",
            "Luke Skywalker",
            Map.of("height", "172", "mass", "77", "hairColor", "blond", "birthYear", "19BBY", "gender", "male"));

    private static final CatalogItem HOPE = new CatalogItem(
            "1", "A New Hope", Map.of("episodeId", 4, "director", "George Lucas", "releaseDate", "1977-05-25"));

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private CatalogService catalogService;

    @Test
    @DisplayName("people uses page 1 and size 10 by default and returns the summary page")
    void peopleDefaults() {
        given(catalogService.find(eq(CatalogResource.PEOPLE), any(), any()))
                .willReturn(new PageResponse<>(List.of(LUKE), 1, 10, 82, 9));

        graphQlTester
                .document("{ people { content { id name } page size totalElements totalPages } }")
                .execute()
                .path("people.content[0].name")
                .entity(String.class)
                .isEqualTo("Luke Skywalker")
                .path("people.totalElements")
                .entity(Integer.class)
                .isEqualTo(82)
                .path("people.totalPages")
                .entity(Integer.class)
                .isEqualTo(9);

        then(catalogService).should().find(CatalogResource.PEOPLE, SearchCriteria.none(), new PageRequestParams(1, 10));
    }

    @Test
    @DisplayName("listing filters and pagination reach the service as in REST")
    void filtersAndPagination() {
        given(catalogService.find(any(), any(), any())).willReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        graphQlTester
                .document("{ starships(page: 2, size: 5, id: \"9\", name: \"death\") { content { id } } }")
                .execute()
                .path("starships.content")
                .entityList(Object.class)
                .hasSize(0);
        graphQlTester
                .document("{ films(title: \"hope\") { content { title } } }")
                .execute()
                .path("films.content")
                .entityList(Object.class)
                .hasSize(0);

        then(catalogService)
                .should()
                .find(CatalogResource.STARSHIPS, new SearchCriteria("9", "death"), new PageRequestParams(2, 5));
        then(catalogService)
                .should()
                .find(CatalogResource.FILMS, new SearchCriteria(null, "hope"), new PageRequestParams(1, 10));
    }

    @Test
    @DisplayName("person returns the detail with the same fields as the REST DTO")
    void personDetail() {
        given(catalogService.getById(CatalogResource.PEOPLE, "1")).willReturn(LUKE);

        graphQlTester
                .document("{ person(id: 1) { id name height hairColor birthYear } }")
                .execute()
                .path("person")
                .entity(Map.class)
                .satisfies(person -> assertThat(person)
                        .containsEntry("id", "1")
                        .containsEntry("name", "Luke Skywalker")
                        .containsEntry("height", "172")
                        .containsEntry("hairColor", "blond")
                        .containsEntry("birthYear", "19BBY"));
    }

    @Test
    @DisplayName("film maps title and the numeric episode id")
    void filmDetail() {
        given(catalogService.getById(CatalogResource.FILMS, "1")).willReturn(HOPE);

        graphQlTester
                .document("{ film(id: 1) { title episodeId director releaseDate } }")
                .execute()
                .path("film.title")
                .entity(String.class)
                .isEqualTo("A New Hope")
                .path("film.episodeId")
                .entity(Integer.class)
                .isEqualTo(4);
    }

    @Test
    @DisplayName("starship and vehicle details delegate to their resource")
    void starshipAndVehicle() {
        given(catalogService.getById(CatalogResource.STARSHIPS, "9"))
                .willReturn(new CatalogItem("9", "Death Star", Map.of("mglt", "10")));
        given(catalogService.getById(CatalogResource.VEHICLES, "4"))
                .willReturn(new CatalogItem("4", "Sand Crawler", Map.of("vehicleClass", "wheeled")));

        graphQlTester
                .document("{ starship(id: 9) { name mglt } vehicle(id: 4) { name vehicleClass } }")
                .execute()
                .path("starship.mglt")
                .entity(String.class)
                .isEqualTo("10")
                .path("vehicle.vehicleClass")
                .entity(String.class)
                .isEqualTo("wheeled");
    }

    @Test
    @DisplayName("vehicles listing returns the summary page")
    void vehiclesListing() {
        given(catalogService.find(eq(CatalogResource.VEHICLES), any(), any()))
                .willReturn(new PageResponse<>(List.of(new CatalogItem("4", "Sand Crawler", Map.of())), 1, 10, 39, 4));

        graphQlTester
                .document("{ vehicles { content { id name } } }")
                .execute()
                .path("vehicles.content[0].id")
                .entity(String.class)
                .isEqualTo("4");
    }
}
