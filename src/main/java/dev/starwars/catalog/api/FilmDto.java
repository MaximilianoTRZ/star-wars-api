package dev.starwars.catalog.api;

import dev.starwars.catalog.domain.CatalogItem;
import io.swagger.v3.oas.annotations.media.Schema;

/** Film detail. */
public record FilmDto(
        @Schema(example = "1") String id,
        @Schema(example = "A New Hope") String title,
        @Schema(example = "4") Integer episodeId,
        @Schema(example = "George Lucas") String director,
        @Schema(example = "Gary Kurtz, Rick McCallum") String producer,
        @Schema(example = "1977-05-25") String releaseDate,
        @Schema(example = "It is a period of civil war...") String openingCrawl) {

    public static FilmDto from(CatalogItem item) {
        return new FilmDto(
                item.id(),
                item.name(),
                item.integerAttribute("episodeId"),
                item.attribute("director"),
                item.attribute("producer"),
                item.attribute("releaseDate"),
                item.attribute("openingCrawl"));
    }
}
