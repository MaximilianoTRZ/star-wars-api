package dev.starwars.swapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** The three SWAPI response shapes. Internal to {@link SwapiRestClient}. */
final class SwapiResponses {

    private SwapiResponses() {}

    /** {@code GET /people?page=&limit=}: paginated listing with uid and name. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListResponse(
            @JsonProperty("total_records") Integer totalRecords,
            @JsonProperty("total_pages") Integer totalPages,
            List<ListItem> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListItem(String uid, String name) {}

    /** {@code GET /people?name=} and {@code GET /films}: unpaginated list with full details. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResponse(List<Entity> result) {}

    /** {@code GET /people/{id}}: a single entity. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetailResponse(Entity result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entity(String uid, Map<String, Object> properties) {}
}
