package dev.starwars.catalog.service;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Lists and searches catalog entities with uniform pagination, however SWAPI resolves them.
 *
 * <p>Strategy by filters (ADR-002):
 * <table>
 *   <caption>Filter resolution</caption>
 *   <tr><th>Filters</th><th>SWAPI call</th><th>Pagination</th></tr>
 *   <tr><td>None, paginated resource</td><td>{@code list(page, size)}</td>
 *       <td>Delegated; if {@code page > totalPages} returns empty (SWAPI would return the last page)</td></tr>
 *   <tr><td>None, films</td><td>{@code list}: every film</td><td>In memory</td></tr>
 *   <tr><td>Name</td><td>{@code search(name)}</td><td>In memory</td></tr>
 *   <tr><td>ID</td><td>{@code findById(id)}</td><td>Page with 0 or 1 element</td></tr>
 *   <tr><td>ID and name</td><td>{@code findById(id)} and the name must contain the term,
 *       case-insensitive</td><td>Page with 0 or 1 element</td></tr>
 * </table>
 *
 * <p>An unknown ID in a listing returns an empty page; only {@link #getById} throws
 * {@link ResourceNotFoundException}.
 */
@Service
public class CatalogService {

    private final SwapiClient swapiClient;

    public CatalogService(SwapiClient swapiClient) {
        this.swapiClient = swapiClient;
    }

    /** Lists a resource applying the requested filters and pagination. */
    public PageResponse<CatalogItem> find(
            CatalogResource resource, SearchCriteria criteria, PageRequestParams pageRequest) {
        ResourceType type = toSwapi(resource);
        if (criteria.hasId()) {
            List<CatalogItem> match = swapiClient
                    .findById(type, criteria.id())
                    .filter(entity -> !criteria.hasName() || nameContains(type, entity, criteria.name()))
                    .map(entity -> toItem(type, entity))
                    .stream()
                    .toList();
            return Paginator.paginate(match, pageRequest);
        }
        if (criteria.hasName()) {
            List<CatalogItem> found = swapiClient.search(type, criteria.name()).stream()
                    .map(entity -> toItem(type, entity))
                    .toList();
            return Paginator.paginate(found, pageRequest);
        }
        if (!type.paginatedUpstream()) {
            List<CatalogItem> all = swapiClient.list(type, 1, pageRequest.size()).items().stream()
                    .map(entity -> toItem(type, entity))
                    .toList();
            return Paginator.paginate(all, pageRequest);
        }
        return delegatedPage(type, pageRequest);
    }

    /**
     * Entity detail.
     *
     * @throws ResourceNotFoundException if SWAPI does not find it
     */
    public CatalogItem getById(CatalogResource resource, String id) {
        ResourceType type = toSwapi(resource);
        return swapiClient
                .findById(type, id)
                .map(entity -> toItem(type, entity))
                .orElseThrow(() -> new ResourceNotFoundException(resource, id));
    }

    private PageResponse<CatalogItem> delegatedPage(ResourceType type, PageRequestParams pageRequest) {
        SwapiPage page = swapiClient.list(type, pageRequest.page(), pageRequest.size());
        List<CatalogItem> content = pageRequest.page() > page.totalPages()
                ? List.of()
                : page.items().stream().map(entity -> toItem(type, entity)).toList();
        return new PageResponse<>(
                content, pageRequest.page(), pageRequest.size(), page.totalRecords(), page.totalPages());
    }

    /** Exhaustive on purpose: a new {@link CatalogResource} without a SWAPI resource does not compile. */
    static ResourceType toSwapi(CatalogResource resource) {
        return switch (resource) {
            case PEOPLE -> ResourceType.PEOPLE;
            case FILMS -> ResourceType.FILMS;
            case STARSHIPS -> ResourceType.STARSHIPS;
            case VEHICLES -> ResourceType.VEHICLES;
        };
    }

    private static boolean nameContains(ResourceType type, SwapiEntity entity, String term) {
        String name = entity.property(type.nameAttribute());
        return name != null && name.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private static CatalogItem toItem(ResourceType type, SwapiEntity entity) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        entity.properties().forEach((key, value) -> attributes.put(toCamelCase(key), value));
        return new CatalogItem(entity.uid(), entity.property(type.nameAttribute()), attributes);
    }

    /** {@code hair_color} → {@code hairColor}; {@code MGLT} → {@code mglt}. */
    static String toCamelCase(String key) {
        if (key.equals(key.toUpperCase(Locale.ROOT))) {
            return key.toLowerCase(Locale.ROOT);
        }
        StringBuilder result = new StringBuilder(key.length());
        boolean upperNext = false;
        for (char c : key.toCharArray()) {
            if (c == '_') {
                upperNext = !result.isEmpty();
            } else {
                result.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        return result.toString();
    }
}
