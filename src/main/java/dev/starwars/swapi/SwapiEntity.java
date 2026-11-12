package dev.starwars.swapi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A SWAPI entity: its {@code uid} and its properties as returned by SWAPI.
 *
 * <p>In a paginated listing the properties only contain the name; in a search
 * or a detail they contain everything.
 *
 * @param uid        SWAPI identifier (numeric, as text)
 * @param properties snake_case properties, as returned by SWAPI
 */
public record SwapiEntity(String uid, Map<String, Object> properties) {

    public SwapiEntity {
        Objects.requireNonNull(uid, "uid");
        properties = properties == null ? Map.of() : Map.copyOf(withoutNullValues(properties));
    }

    /** Property value as text, or {@code null} if absent. */
    public String property(String key) {
        Object value = properties.get(key);
        return value == null ? null : value.toString();
    }

    private static Map<String, Object> withoutNullValues(Map<String, Object> source) {
        var copy = new HashMap<>(source);
        copy.values().removeIf(Objects::isNull);
        return copy;
    }
}
