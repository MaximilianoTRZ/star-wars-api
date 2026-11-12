package dev.starwars.catalog.api;

/** Shared constants for the catalog endpoints. */
final class CatalogApi {

    static final String ID_PATTERN = "\\d{1,10}";
    static final String ID_MESSAGE = "must be a numeric SWAPI id";
    static final int MAX_FILTER_LENGTH = 100;

    private CatalogApi() {}
}
