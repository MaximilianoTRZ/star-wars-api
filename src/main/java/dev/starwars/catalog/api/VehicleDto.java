package dev.starwars.catalog.api;

import dev.starwars.catalog.domain.CatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Vehicle detail. Values are kept as text, as SWAPI returns them. */
public record VehicleDto(
        @Schema(example = "4") String id,
        @Schema(example = "Sand Crawler") String name,
        @Schema(example = "Digger Crawler") String model,
        @Schema(example = "Corellia Mining Corporation") String manufacturer,
        @Schema(example = "wheeled") String vehicleClass,
        @Schema(example = "150000") String costInCredits,
        @Schema(example = "36.8") String length,
        @Schema(example = "46") String crew,
        @Schema(example = "30") String passengers,
        @Schema(example = "50000") String cargoCapacity) {

    public static VehicleDto from(CatalogItem item) {
        return new VehicleDto(
                item.id(),
                item.name(),
                item.attribute("model"),
                item.attribute("manufacturer"),
                item.attribute("vehicleClass"),
                item.attribute("costInCredits"),
                item.attribute("length"),
                item.attribute("crew"),
                item.attribute("passengers"),
                item.attribute("cargoCapacity"));
    }
}
