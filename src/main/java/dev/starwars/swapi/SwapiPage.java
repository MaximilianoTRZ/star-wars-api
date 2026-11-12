package dev.starwars.swapi;

import java.util.List;

/**
 * A page as returned by SWAPI.
 *
 * @param items        entities in the page
 * @param totalRecords total entities of the resource
 * @param totalPages   total pages according to SWAPI (1 if the resource is not paginated)
 */
public record SwapiPage(List<SwapiEntity> items, int totalRecords, int totalPages) {

    public SwapiPage {
        items = List.copyOf(items);
    }
}
