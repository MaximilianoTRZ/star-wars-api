package dev.starwars.catalog.api;

import dev.starwars.catalog.domain.CatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Starship detail. Values are kept as text, as SWAPI returns them (e.g. {@code "n/a"}). */
public record StarshipDto(
        @Schema(example = "9") String id,
        @Schema(example = "Death Star") String name,
        @Schema(example = "DS-1 Orbital Battle Station") String model,

        @Schema(example = "Imperial Department of Military Research, Sienar Fleet Systems")
        String manufacturer,

        @Schema(example = "Deep Space Mobile Battlestation") String starshipClass,
        @Schema(example = "1000000000000") String costInCredits,
        @Schema(example = "120000") String length,
        @Schema(example = "342,953") String crew,
        @Schema(example = "843,342") String passengers,
        @Schema(example = "1000000000000") String cargoCapacity,
        @Schema(example = "4.0") String hyperdriveRating,
        @Schema(example = "10") String mglt) {

    public static StarshipDto from(CatalogItem item) {
        return new StarshipDto(
                item.id(),
                item.name(),
                item.attribute("model"),
                item.attribute("manufacturer"),
                item.attribute("starshipClass"),
                item.attribute("costInCredits"),
                item.attribute("length"),
                item.attribute("crew"),
                item.attribute("passengers"),
                item.attribute("cargoCapacity"),
                item.attribute("hyperdriveRating"),
                item.attribute("mglt"));
    }
}
