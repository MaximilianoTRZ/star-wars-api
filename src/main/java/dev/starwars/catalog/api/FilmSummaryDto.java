package dev.starwars.catalog.api;

import dev.starwars.catalog.domain.CatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Film as shown in listings. */
public record FilmSummaryDto(
        @Schema(example = "1") String id,
        @Schema(example = "A New Hope") String title) {

    public static FilmSummaryDto from(CatalogItem item) {
        return new FilmSummaryDto(item.id(), item.name());
    }
}
