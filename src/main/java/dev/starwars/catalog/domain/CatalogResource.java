package dev.starwars.catalog.domain;

import java.util.Locale;

/**
 * Catalog resources exposed by the API. Controllers and the service use this type instead of the SWAPI
 * one, so the web layer never depends on the {@code swapi} package.
 */
public enum CatalogResource {
    PEOPLE,
    FILMS,
    STARSHIPS,
    VEHICLES;

    /** Lowercase name used in messages, e.g. {@code people}. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
