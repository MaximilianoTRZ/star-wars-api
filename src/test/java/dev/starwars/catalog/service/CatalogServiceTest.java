package dev.starwars.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.starwars.catalog.domain.CatalogItem;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import dev.starwars.catalog.domain.ResourceNotFoundException;
import dev.starwars.catalog.domain.SearchCriteria;
import dev.starwars.swapi.ResourceType;
import dev.starwars.swapi.SwapiClient;
import dev.starwars.swapi.SwapiEntity;
import dev.starwars.swapi.SwapiPage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    private static final PageRequestParams FIRST_PAGE = new PageRequestParams(1, 10);

    @Mock
    private SwapiClient swapiClient;

    private SimpleMeterRegistry registry;

    private CatalogService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new CatalogService(swapiClient, new CatalogMetrics(registry));
    }

    private static SwapiEntity person(String uid, String name) {
        return new SwapiEntity(uid, Map.of("name", name, "hair_color", "blond"));
    }

    private static SwapiEntity film(String uid, String title) {
        return new SwapiEntity(uid, Map.of("title", title, "episode_id", 4));
    }

    @Nested
    @DisplayName("without filters")
    class NoFilters {

        @Test
        @DisplayName("delegates pagination to SWAPI for paginated resources")
        void delegatesPagination() {
            given(swapiClient.list(ResourceType.PEOPLE, 2, 3))
                    .willReturn(new SwapiPage(List.of(person("4", "Darth Vader")), 82, 28));

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.PEOPLE, SearchCriteria.none(), new PageRequestParams(2, 3));

            assertThat(page.content()).extracting(CatalogItem::id).containsExactly("4");
            assertThat(page.page()).isEqualTo(2);
            assertThat(page.size()).isEqualTo(3);
            assertThat(page.totalElements()).isEqualTo(82);
            assertThat(page.totalPages()).isEqualTo(28);
        }

        @Test
        @DisplayName("out-of-range page is empty even though SWAPI returns the last one")
        void outOfRangeIsEmpty() {
            given(swapiClient.list(ResourceType.PEOPLE, 999, 10))
                    .willReturn(new SwapiPage(List.of(person("82", "Sly Moore")), 82, 9));

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.PEOPLE, SearchCriteria.none(), new PageRequestParams(999, 10));

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isEqualTo(82);
            assertThat(page.totalPages()).isEqualTo(9);
        }

        @Test
        @DisplayName("films are fetched in full and paginated in memory")
        void filmsPaginatedInMemory() {
            given(swapiClient.list(eq(ResourceType.FILMS), anyInt(), anyInt()))
                    .willReturn(new SwapiPage(
                            List.of(
                                    film("1", "A New Hope"),
                                    film("2", "The Empire Strikes Back"),
                                    film("3", "Return of the Jedi")),
                            3,
                            1));

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.FILMS, SearchCriteria.none(), new PageRequestParams(2, 2));

            assertThat(page.content()).extracting(CatalogItem::name).containsExactly("Return of the Jedi");
            assertThat(page.totalElements()).isEqualTo(3);
            assertThat(page.totalPages()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("name filter")
    class NameFilter {

        @Test
        @DisplayName("searches SWAPI and paginates in memory")
        void searchesAndPaginates() {
            given(swapiClient.search(ResourceType.PEOPLE, "skywalker"))
                    .willReturn(List.of(
                            person("1", "Luke Skywalker"),
                            person("11", "Anakin Skywalker"),
                            person("43", "Shmi Skywalker")));

            PageResponse<CatalogItem> page = service.find(
                    CatalogResource.PEOPLE, new SearchCriteria(null, "skywalker"), new PageRequestParams(1, 2));

            assertThat(page.content())
                    .extracting(CatalogItem::name)
                    .containsExactly("Luke Skywalker", "Anakin Skywalker");
            assertThat(page.totalElements()).isEqualTo(3);
            assertThat(page.totalPages()).isEqualTo(2);
            then(swapiClient).should(never()).list(eq(ResourceType.PEOPLE), anyInt(), anyInt());
        }

        @Test
        @DisplayName("no matches returns an empty page")
        void noMatches() {
            given(swapiClient.search(ResourceType.PEOPLE, "zzzz")).willReturn(List.of());

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.PEOPLE, new SearchCriteria(null, "zzzz"), FIRST_PAGE);

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isZero();
            assertThat(page.totalPages()).isZero();
        }

        @Test
        @DisplayName("film item name comes from the title")
        void filmsUseTitle() {
            given(swapiClient.search(ResourceType.FILMS, "hope")).willReturn(List.of(film("1", "A New Hope")));

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.FILMS, new SearchCriteria(null, "hope"), FIRST_PAGE);

            assertThat(page.content()).extracting(CatalogItem::name).containsExactly("A New Hope");
        }
    }

    @Nested
    @DisplayName("ID filter")
    class IdFilter {

        @Test
        @DisplayName("returns a page with one element")
        void findsById() {
            given(swapiClient.findById(ResourceType.PEOPLE, "1"))
                    .willReturn(Optional.of(person("1", "Luke Skywalker")));

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.PEOPLE, new SearchCriteria("1", null), FIRST_PAGE);

            assertThat(page.content()).extracting(CatalogItem::id).containsExactly("1");
            assertThat(page.totalElements()).isEqualTo(1);
            assertThat(page.totalPages()).isEqualTo(1);
            then(swapiClient).should(never()).search(eq(ResourceType.PEOPLE), anyString());
        }

        @Test
        @DisplayName("an unknown ID returns an empty page, not an error")
        void unknownIdIsEmptyPage() {
            given(swapiClient.findById(ResourceType.PEOPLE, "9999")).willReturn(Optional.empty());

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.PEOPLE, new SearchCriteria("9999", null), FIRST_PAGE);

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isZero();
        }

        @Test
        @DisplayName("ID and matching name, case-insensitive")
        void idAndMatchingName() {
            given(swapiClient.findById(ResourceType.PEOPLE, "1"))
                    .willReturn(Optional.of(person("1", "Luke Skywalker")));

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.PEOPLE, new SearchCriteria("1", "SKYWALKER"), FIRST_PAGE);

            assertThat(page.content()).extracting(CatalogItem::id).containsExactly("1");
        }

        @Test
        @DisplayName("ID and non-matching name returns an empty page")
        void idAndNonMatchingName() {
            given(swapiClient.findById(ResourceType.PEOPLE, "1"))
                    .willReturn(Optional.of(person("1", "Luke Skywalker")));

            PageResponse<CatalogItem> page =
                    service.find(CatalogResource.PEOPLE, new SearchCriteria("1", "vader"), FIRST_PAGE);

            assertThat(page.content()).isEmpty();
            assertThat(page.totalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns the item with camelCase attributes")
        void returnsItemWithCamelCaseAttributes() {
            given(swapiClient.findById(ResourceType.PEOPLE, "1"))
                    .willReturn(Optional.of(person("1", "Luke Skywalker")));

            CatalogItem item = service.getById(CatalogResource.PEOPLE, "1");

            assertThat(item.name()).isEqualTo("Luke Skywalker");
            assertThat(item.attribute("hairColor")).isEqualTo("blond");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when SWAPI does not find it")
        void throwsWhenNotFound() {
            given(swapiClient.findById(ResourceType.FILMS, "99")).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(CatalogResource.FILMS, "99"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("films")
                    .hasMessageContaining("99");
        }
    }

    @Test
    @DisplayName("toCamelCase converts snake_case and acronyms")
    void camelCase() {
        assertThat(CatalogService.toCamelCase("hair_color")).isEqualTo("hairColor");
        assertThat(CatalogService.toCamelCase("cost_in_credits")).isEqualTo("costInCredits");
        assertThat(CatalogService.toCamelCase("MGLT")).isEqualTo("mglt");
        assertThat(CatalogService.toCamelCase("name")).isEqualTo("name");
    }

    @Test
    @DisplayName("maps every catalog resource to the SWAPI resource with the same name")
    void mapsEveryResource() {
        for (CatalogResource resource : CatalogResource.values()) {
            assertThat(CatalogService.toSwapi(resource).name()).isEqualTo(resource.name());
        }
    }

    @Nested
    @DisplayName("metrics")
    class Metrics {

        @Test
        @DisplayName("counts the query by resource and filter, and records the page")
        void countsQuery() {
            given(swapiClient.search(ResourceType.PEOPLE, "sky")).willReturn(List.of(person("1", "Luke Skywalker")));

            service.find(CatalogResource.PEOPLE, new SearchCriteria(null, "sky"), new PageRequestParams(3, 10));

            assertThat(registry.get(CatalogMetrics.QUERIES)
                            .tags("resource", "people", "filter", "name")
                            .counter()
                            .count())
                    .isEqualTo(1);
            assertThat(registry.get(CatalogMetrics.PAGE_REQUESTED)
                            .tag("resource", "people")
                            .summary()
                            .max())
                    .isEqualTo(3);
            assertThat(registry.find(CatalogMetrics.EMPTY_SEARCHES).counter()).isNull();
        }

        @Test
        @DisplayName("counts a name search without results as empty")
        void countsEmptySearch() {
            given(swapiClient.search(ResourceType.FILMS, "nothing")).willReturn(List.of());

            service.find(CatalogResource.FILMS, new SearchCriteria(null, "nothing"), FIRST_PAGE);

            assertThat(registry.get(CatalogMetrics.EMPTY_SEARCHES)
                            .tag("resource", "films")
                            .counter()
                            .count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("tags the entity view with the name from SWAPI, not the requested ID")
        void countsEntityView() {
            given(swapiClient.findById(ResourceType.PEOPLE, "1"))
                    .willReturn(Optional.of(person("1", "Luke Skywalker")));

            service.getById(CatalogResource.PEOPLE, "1");

            assertThat(registry.get(CatalogMetrics.ENTITY_VIEWS)
                            .tags("resource", "people", "entity", "Luke Skywalker")
                            .counter()
                            .count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("does not count an entity view when the entity does not exist")
        void noEntityViewOnNotFound() {
            given(swapiClient.findById(ResourceType.PEOPLE, "999")).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(CatalogResource.PEOPLE, "999"))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThat(registry.find(CatalogMetrics.ENTITY_VIEWS).counter()).isNull();
        }
    }
}
