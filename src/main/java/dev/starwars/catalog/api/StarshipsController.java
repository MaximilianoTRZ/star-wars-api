package dev.starwars.catalog.api;

import static dev.starwars.catalog.domain.CatalogConstraints.ID_MESSAGE;
import static dev.starwars.catalog.domain.CatalogConstraints.ID_PATTERN;
import static dev.starwars.catalog.domain.CatalogConstraints.MAX_FILTER_LENGTH;

import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import dev.starwars.catalog.domain.SearchCriteria;
import dev.starwars.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Starships endpoints. Validates input, delegates to {@link CatalogService} and maps to DTOs. */
@RestController
@RequestMapping("/api/v1/starships")
@Tag(name = "Starships", description = "Star Wars starships from SWAPI")
public class StarshipsController {

    private final CatalogService catalogService;

    public StarshipsController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    @Operation(
            summary = "List starships",
            description = "Paginated list of starships. Filter by `id` and/or `name` (case-insensitive contains). "
                    + "An unknown id returns an empty page.")
    public PageResponse<StarshipSummaryDto> list(
            @Parameter(description = "1-based page number", example = "1") @RequestParam(defaultValue = "1") @Min(1)
                    int page,
            @Parameter(description = "Page size (1-100)", example = "10")
                    @RequestParam(defaultValue = "10")
                    @Min(1)
                    @Max(PageRequestParams.MAX_SIZE)
                    int size,
            @Parameter(description = "Exact SWAPI id", example = "9")
                    @RequestParam(required = false)
                    @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE)
                    String id,
            @Parameter(description = "Text the name must contain", example = "death")
                    @RequestParam(required = false)
                    @Size(max = MAX_FILTER_LENGTH)
                    String name) {
        return catalogService
                .find(CatalogResource.STARSHIPS, new SearchCriteria(id, name), new PageRequestParams(page, size))
                .map(StarshipSummaryDto::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get starship by id")
    public StarshipDto get(
            @Parameter(description = "SWAPI id", example = "9")
                    @PathVariable
                    @Pattern(regexp = ID_PATTERN, message = ID_MESSAGE)
                    String id) {
        return StarshipDto.from(catalogService.getById(CatalogResource.STARSHIPS, id));
    }
}
