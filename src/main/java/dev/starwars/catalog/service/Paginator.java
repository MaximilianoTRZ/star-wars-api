package dev.starwars.catalog.service;

import dev.starwars.catalog.domain.PageRequestParams;
import dev.starwars.catalog.domain.PageResponse;
import java.util.List;

/**
 * Paginates a full list in memory. Used when SWAPI does not paginate: films, searches and the ID filter.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@code totalPages = ceil(totalElements / size)}, and 0 if the list is empty.</li>
 *   <li>An out-of-range page returns empty {@code content} with the real totals, not an error.</li>
 * </ul>
 */
public final class Paginator {

    private Paginator() {}

    public static <T> PageResponse<T> paginate(List<T> all, PageRequestParams request) {
        int total = all.size();
        int totalPages = totalPages(total, request.size());
        long from = request.offset();
        List<T> content =
                from >= total ? List.of() : all.subList((int) from, (int) Math.min(from + request.size(), total));
        return new PageResponse<>(content, request.page(), request.size(), total, totalPages);
    }

    static int totalPages(long totalElements, int size) {
        return (int) ((totalElements + size - 1) / size);
    }
}
