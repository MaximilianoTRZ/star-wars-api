package dev.starwars.catalog.mcp;

import dev.starwars.catalog.api.FilmDto;
import dev.starwars.catalog.api.FilmSummaryDto;
import dev.starwars.catalog.api.PersonDto;
import dev.starwars.catalog.api.PersonSummaryDto;
import dev.starwars.catalog.api.StarshipDto;
import dev.starwars.catalog.api.StarshipSummaryDto;
import dev.starwars.catalog.api.VehicleDto;
import dev.starwars.catalog.api.VehicleSummaryDto;
import dev.starwars.catalog.domain.CatalogConstraints;
import dev.starwars.catalog.domain.CatalogItem;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.ResourceNotFoundException;
import dev.starwars.catalog.domain.SearchCriteria;
import dev.starwars.catalog.service.CatalogService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP tools of the catalog, one per REST operation. Like the REST and GraphQL controllers, they only validate
 * input, delegate to {@link CatalogService} and return the same DTOs as JSON (ADR-016).
 *
 * <p>Every tool builds its own {@link CallToolResult}: an unknown id is a normal result with a clear message,
 * and any failure is an {@code isError} result with a public message ({@link ToolErrorMessages}), never the
 * text of an internal exception.
 */
@Component
public class CatalogMcpTools {

    private static final Pattern ID = Pattern.compile(CatalogConstraints.ID_PATTERN);
    private static final String PAGE = "1-based page number, default 1";
    private static final String SIZE = "Page size between 1 and 100, default 10";
    private static final String ID_FILTER = "Exact SWAPI id, e.g. \"1\"";
    private static final String DETAIL_ID = "SWAPI id, e.g. \"1\"; ids come from the search tools";
    private static final String LISTING =
            " Returns a page {content, page, size, totalElements, totalPages} with id and name of each item;"
                    + " use the get tool for every attribute. An unknown id returns an empty page.";

    private final CatalogService catalogService;
    private final ToolErrorMessages errorMessages;
    private final JsonMapper jsonMapper;

    public CatalogMcpTools(CatalogService catalogService, ToolErrorMessages errorMessages, JsonMapper jsonMapper) {
        this.catalogService = catalogService;
        this.errorMessages = errorMessages;
        this.jsonMapper = jsonMapper;
    }

    @McpTool(
            name = "search_people",
            description = "Lists Star Wars characters, optionally filtered by name (case-insensitive contains)"
                    + " and/or id." + LISTING,
            annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public CallToolResult searchPeople(
            @McpToolParam(required = false, description = "Text the name must contain, e.g. \"skywalker\"") String name,
            @McpToolParam(required = false, description = ID_FILTER) String id,
            @McpToolParam(required = false, description = PAGE) Integer page,
            @McpToolParam(required = false, description = SIZE) Integer size) {
        return search(CatalogResource.PEOPLE, id, name, page, size, PersonSummaryDto::from);
    }

    @McpTool(
            name = "get_person",
            description = "Every attribute of a Star Wars character: height, mass, colors, birth year, gender.",
            annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public CallToolResult getPerson(@McpToolParam(description = DETAIL_ID) String id) {
        return get(CatalogResource.PEOPLE, id, PersonDto::from);
    }

    @McpTool(
            name = "search_films",
            description = "Lists Star Wars films, optionally filtered by title (case-insensitive contains)"
                    + " and/or id." + LISTING,
            annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public CallToolResult searchFilms(
            @McpToolParam(required = false, description = "Text the title must contain, e.g. \"hope\"") String title,
            @McpToolParam(required = false, description = ID_FILTER) String id,
            @McpToolParam(required = false, description = PAGE) Integer page,
            @McpToolParam(required = false, description = SIZE) Integer size) {
        return search(CatalogResource.FILMS, id, title, page, size, FilmSummaryDto::from);
    }

    @McpTool(
            name = "get_film",
            description = "Every attribute of a Star Wars film: episode, director, producer, release date and"
                    + " opening crawl.",
            annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public CallToolResult getFilm(@McpToolParam(description = DETAIL_ID) String id) {
        return get(CatalogResource.FILMS, id, FilmDto::from);
    }

    @McpTool(
            name = "search_starships",
            description = "Lists Star Wars starships, optionally filtered by name (case-insensitive contains)"
                    + " and/or id." + LISTING,
            annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public CallToolResult searchStarships(
            @McpToolParam(required = false, description = "Text the name must contain, e.g. \"death\"") String name,
            @McpToolParam(required = false, description = ID_FILTER) String id,
            @McpToolParam(required = false, description = PAGE) Integer page,
            @McpToolParam(required = false, description = SIZE) Integer size) {
        return search(CatalogResource.STARSHIPS, id, name, page, size, StarshipSummaryDto::from);
    }

    @McpTool(
            name = "get_starship",
            description = "Every attribute of a Star Wars starship: model, manufacturer, class, cost, crew,"
                    + " hyperdrive rating.",
            annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public CallToolResult getStarship(@McpToolParam(description = DETAIL_ID) String id) {
        return get(CatalogResource.STARSHIPS, id, StarshipDto::from);
    }

    @McpTool(
            name = "search_vehicles",
            description = "Lists Star Wars vehicles, optionally filtered by name (case-insensitive contains)"
                    + " and/or id." + LISTING,
            annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public CallToolResult searchVehicles(
            @McpToolParam(required = false, description = "Text the name must contain, e.g. \"sand\"") String name,
            @McpToolParam(required = false, description = ID_FILTER) String id,
            @McpToolParam(required = false, description = PAGE) Integer page,
            @McpToolParam(required = false, description = SIZE) Integer size) {
        return search(CatalogResource.VEHICLES, id, name, page, size, VehicleSummaryDto::from);
    }

    @McpTool(
            name = "get_vehicle",
            description = "Every attribute of a Star Wars vehicle: model, manufacturer, class, cost, crew.",
            annotations = @McpAnnotations(readOnlyHint = true, openWorldHint = true))
    public CallToolResult getVehicle(@McpToolParam(description = DETAIL_ID) String id) {
        return get(CatalogResource.VEHICLES, id, VehicleDto::from);
    }

    private CallToolResult search(
            CatalogResource resource,
            String id,
            String name,
            Integer page,
            Integer size,
            Function<CatalogItem, ?> mapper) {
        return run(() -> {
            if (id != null && !id.isBlank()) {
                requireValidId(id);
            }
            if (name != null && name.length() > CatalogConstraints.MAX_FILTER_LENGTH) {
                throw new InvalidArgumentException(
                        "The name filter must have at most " + CatalogConstraints.MAX_FILTER_LENGTH + " characters.");
            }
            PageRequestParams pageRequest = pageRequest(page, size);
            return catalogService
                    .find(resource, new SearchCriteria(id, name), pageRequest)
                    .map(mapper);
        });
    }

    private CallToolResult get(CatalogResource resource, String id, Function<CatalogItem, ?> mapper) {
        return run(() -> {
            requireValidId(id);
            return mapper.apply(catalogService.getById(resource, id));
        });
    }

    private CallToolResult run(Supplier<Object> call) {
        try {
            return CallToolResult.builder()
                    .addTextContent(jsonMapper.writeValueAsString(call.get()))
                    .isError(false)
                    .build();
        } catch (ResourceNotFoundException notFound) {
            return CallToolResult.builder()
                    .addTextContent(notFound.getMessage() + ". Use the search tools to find valid ids.")
                    .isError(false)
                    .build();
        } catch (InvalidArgumentException invalid) {
            return error(invalid.getMessage());
        } catch (RuntimeException ex) {
            return error(errorMessages.describe(ex));
        }
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    private static void requireValidId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new InvalidArgumentException("The id must be a numeric SWAPI id, e.g. \"1\".");
        }
    }

    private static PageRequestParams pageRequest(Integer page, Integer size) {
        int p = page == null ? PageRequestParams.DEFAULT_PAGE : page;
        int s = size == null ? PageRequestParams.DEFAULT_SIZE : size;
        if (p < 1) {
            throw new InvalidArgumentException("The page must be 1 or greater.");
        }
        if (s < 1 || s > PageRequestParams.MAX_SIZE) {
            throw new InvalidArgumentException("The size must be between 1 and " + PageRequestParams.MAX_SIZE + ".");
        }
        return new PageRequestParams(p, s);
    }

    /** Invalid tool input; its message is written for the client. */
    private static final class InvalidArgumentException extends RuntimeException {

        InvalidArgumentException(String message) {
            super(message);
        }
    }
}
