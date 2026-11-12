package dev.starwars.catalog.api;

import dev.starwars.catalog.domain.CatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Vehicle as shown in listings. */
public record VehicleSummaryDto(
        @Schema(example = "4") String id,
        @Schema(example = "Sand Crawler") String name) {

    public static VehicleSummaryDto from(CatalogItem item) {
        return new VehicleSummaryDto(item.id(), item.name());
    }
}
