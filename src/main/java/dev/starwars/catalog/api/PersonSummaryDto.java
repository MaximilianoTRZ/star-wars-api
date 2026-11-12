package dev.starwars.catalog.api;

import dev.starwars.catalog.domain.CatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Person as shown in listings. */
public record PersonSummaryDto(
        @Schema(example = "1") String id,
        @Schema(example = "Luke Skywalker") String name) {

    public static PersonSummaryDto from(CatalogItem item) {
        return new PersonSummaryDto(item.id(), item.name());
    }
}
