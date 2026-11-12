package dev.starwars.catalog.domain;

import java.util.Map;
import java.util.Objects;

/**
 * A catalog entity (person, film, starship or vehicle), independent of the SWAPI format.
 *
 * @param id         SWAPI ID
 * @param name       name, or title for films
 * @param attributes attributes with camelCase keys ({@code hairColor}, {@code episodeId}, ...).
 *                   Listings only include the name
 */
public record CatalogItem(String id, String name, Map<String, Object> attributes) {

    public CatalogItem {
        Objects.requireNonNull(id, "id");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Attribute as trimmed text, or {@code null} if absent. */
    public String attribute(String key) {
        Object value = attributes.get(key);
        return value == null ? null : value.toString().strip();
    }

    /** Integer attribute, or {@code null} if absent or not a number. */
    public Integer integerAttribute(String key) {
        Object value = attributes.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.toString().strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
