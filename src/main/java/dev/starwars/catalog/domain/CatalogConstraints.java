package dev.starwars.catalog.domain;

/** Input rules shared by every channel that exposes the catalog (REST and GraphQL). */
public final class CatalogConstraints {

    public static final String ID_PATTERN = "\\d{1,10}";
    public static final String ID_MESSAGE = "must be a numeric SWAPI id";
    public static final int MAX_FILTER_LENGTH = 100;

    private CatalogConstraints() {}
}
