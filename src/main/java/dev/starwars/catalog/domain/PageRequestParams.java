package dev.starwars.catalog.domain;

/**
 * Page requested by the API client.
 *
 * @param page 1-based page number (same as SWAPI)
 * @param size page size, between 1 and {@value #MAX_SIZE}
 */
public record PageRequestParams(int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    public PageRequestParams {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    /** 0-based index of the first element of the page. */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
