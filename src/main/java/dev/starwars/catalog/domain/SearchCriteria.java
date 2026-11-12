package dev.starwars.catalog.domain;

/**
 * Listing filters. Blank values are treated as absent.
 *
 * @param id   exact SWAPI ID, or {@code null}
 * @param name text the name (or title, for films) must contain, or {@code null}
 */
public record SearchCriteria(String id, String name) {

    public SearchCriteria {
        id = normalize(id);
        name = normalize(name);
    }

    public static SearchCriteria none() {
        return new SearchCriteria(null, null);
    }

    public boolean hasId() {
        return id != null;
    }

    public boolean hasName() {
        return name != null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
