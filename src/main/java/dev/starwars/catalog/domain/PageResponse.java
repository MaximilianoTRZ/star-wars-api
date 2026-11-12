package dev.starwars.catalog.domain;

import java.util.List;
import java.util.function.Function;

/**
 * Uniform page returned by every listing, regardless of how SWAPI paginates underneath (ADR-002).
 *
 * @param content       page elements (empty if the page is out of range)
 * @param page          requested 1-based page number
 * @param size          requested page size
 * @param totalElements total elements matching the filters
 * @param totalPages    total pages for that {@code size}
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResponse {
        content = List.copyOf(content);
    }

    /** Same page with every element transformed. */
    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return new PageResponse<>(mapped, page, size, totalElements, totalPages);
    }
}
