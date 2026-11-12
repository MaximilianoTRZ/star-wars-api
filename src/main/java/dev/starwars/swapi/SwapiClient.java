package dev.starwars.swapi;

import java.util.List;
import java.util.Optional;

/**
 * Access to SWAPI (swapi.tech). The only part of the app that knows the SWAPI format.
 *
 * <p>Errors common to all methods:
 * <ul>
 *   <li>{@link UpstreamUnavailableException}: SWAPI answered 429 or 5xx, or there was a timeout or I/O error.</li>
 *   <li>{@link SwapiClientException}: SWAPI answered another 4xx or a body that cannot be parsed.</li>
 * </ul>
 */
public interface SwapiClient {

    /**
     * Lists a resource.
     *
     * <p>If SWAPI paginates the resource ({@link ResourceType#paginatedUpstream()}), returns the requested
     * page with only {@code uid} and name. Otherwise (films) ignores {@code page} and {@code limit}
     * and returns every item with all its properties.
     *
     * <p>Note: if {@code page} exceeds the total pages, SWAPI returns the last page.
     * Callers must compare against {@link SwapiPage#totalPages()}.
     */
    SwapiPage list(ResourceType type, int page, int limit);

    /**
     * Searches by name ({@code name}, or {@code title} for films). SWAPI performs a case-insensitive
     * "contains" search and returns every match without pagination.
     */
    List<SwapiEntity> search(ResourceType type, String term);

    /** Entity detail, or empty if SWAPI answers 404. */
    Optional<SwapiEntity> findById(ResourceType type, String id);
}
