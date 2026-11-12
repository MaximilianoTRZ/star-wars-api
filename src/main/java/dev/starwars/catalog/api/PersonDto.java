package dev.starwars.catalog.api;

import dev.starwars.catalog.domain.CatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Person detail. Values are kept as text, as SWAPI returns them (e.g. {@code "unknown"}). */
public record PersonDto(
        @Schema(example = "1") String id,
        @Schema(example = "Luke Skywalker") String name,
        @Schema(example = "172") String height,
        @Schema(example = "77") String mass,
        @Schema(example = "blond") String hairColor,
        @Schema(example = "fair") String skinColor,
        @Schema(example = "blue") String eyeColor,
        @Schema(example = "19BBY") String birthYear,
        @Schema(example = "male") String gender) {

    public static PersonDto from(CatalogItem item) {
        return new PersonDto(
                item.id(),
                item.name(),
                item.attribute("height"),
                item.attribute("mass"),
                item.attribute("hairColor"),
                item.attribute("skinColor"),
                item.attribute("eyeColor"),
                item.attribute("birthYear"),
                item.attribute("gender"));
    }
}
