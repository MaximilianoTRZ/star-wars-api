package dev.starwars.catalog.graphql;

import static dev.starwars.catalog.domain.CatalogConstraints.ID_MESSAGE;
import static dev.starwars.catalog.domain.CatalogConstraints.ID_PATTERN;
import static dev.starwars.catalog.domain.CatalogConstraints.MAX_FILTER_LENGTH;

import dev.starwars.catalog.api.FilmDto;
import dev.starwars.catalog.api.FilmSummaryDto;
import dev.starwars.catalog.api.PersonDto;
import dev.starwars.catalog.api.PersonSummaryDto;
import dev.starwars.catalog.api.StarshipDto;
import dev.starwars.catalog.api.StarshipSummaryDto;
import dev.starwars.catalog.api.VehicleDto;
import dev.starwars.catalog.api.VehicleSummaryDto;
import dev.starwars.catalog.domain.CatalogItem;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import dev.starwars.catalog.domain.SearchCriteria;
import dev.starwars.catalog.service.CatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * GraphQL queries of the catalog ({@code schema.graphqls}). Like the REST controllers, it only validates
 * input, delegates to {@link CatalogService} and maps to the same DTOs, so both channels return the same
 * shape (ADR-016). Errors are translated by {@code GraphQlExceptionResolver}.
 */
@Controller
public class CatalogGraphQlController {

    private final CatalogService catalogService;

    public CatalogGraphQlController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @QueryMapping
    public PageResponse<PersonSummaryDto> people(
            @Argument @Min(1) int page,
            @Argument @Min(1) @Max(PageRequestParams.MAX_SIZE) int size,
            @Argument @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String id,
            @Argument @Size(max = MAX_FILTER_LENGTH) String name) {
        return find(CatalogResource.PEOPLE, page, size, id, name).map(PersonSummaryDto::from);
    }

    @QueryMapping
    public PersonDto person(@Argument @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String id) {
        return PersonDto.from(catalogService.getById(CatalogResource.PEOPLE, id));
    }

    @QueryMapping
    public PageResponse<FilmSummaryDto> films(
            @Argument @Min(1) int page,
            @Argument @Min(1) @Max(PageRequestParams.MAX_SIZE) int size,
            @Argument @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String id,
            @Argument @Size(max = MAX_FILTER_LENGTH) String title) {
        return find(CatalogResource.FILMS, page, size, id, title).map(FilmSummaryDto::from);
    }

    @QueryMapping
    public FilmDto film(@Argument @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String id) {
        return FilmDto.from(catalogService.getById(CatalogResource.FILMS, id));
    }

    @QueryMapping
    public PageResponse<StarshipSummaryDto> starships(
            @Argument @Min(1) int page,
            @Argument @Min(1) @Max(PageRequestParams.MAX_SIZE) int size,
            @Argument @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String id,
            @Argument @Size(max = MAX_FILTER_LENGTH) String name) {
        return find(CatalogResource.STARSHIPS, page, size, id, name).map(StarshipSummaryDto::from);
    }

    @QueryMapping
    public StarshipDto starship(@Argument @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String id) {
        return StarshipDto.from(catalogService.getById(CatalogResource.STARSHIPS, id));
    }

    @QueryMapping
    public PageResponse<VehicleSummaryDto> vehicles(
            @Argument @Min(1) int page,
            @Argument @Min(1) @Max(PageRequestParams.MAX_SIZE) int size,
            @Argument @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String id,
            @Argument @Size(max = MAX_FILTER_LENGTH) String name) {
        return find(CatalogResource.VEHICLES, page, size, id, name).map(VehicleSummaryDto::from);
    }

    @QueryMapping
    public VehicleDto vehicle(@Argument @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE) String id) {
        return VehicleDto.from(catalogService.getById(CatalogResource.VEHICLES, id));
    }

    private PageResponse<CatalogItem> find(CatalogResource resource, int page, int size, String id, String name) {
        return catalogService.find(resource, new SearchCriteria(id, name), new PageRequestParams(page, size));
    }
}
