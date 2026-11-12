package dev.starwars.catalog.api;

import dev.starwars.catalog.domain.CatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Starship as shown in listings. */
public record StarshipSummaryDto(
        @Schema(example = "9") String id,
        @Schema(example = "Death Star") String name) {

    public static StarshipSummaryDto from(CatalogItem item) {
        return new StarshipSummaryDto(item.id(), item.name());
    }
}
