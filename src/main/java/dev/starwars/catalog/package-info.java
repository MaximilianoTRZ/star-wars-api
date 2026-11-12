/**
 * Catalog of SWAPI resources (people, films, starships and vehicles).
 *
 * <ul>
 *   <li>{@code api}: REST controllers and output DTOs. They never see SWAPI types.</li>
 *   <li>{@code service}: {@code CatalogService} picks how to resolve each filter combination and
 *       {@code Paginator} paginates in memory when SWAPI does not.</li>
 *   <li>{@code domain}: request and response types shared by both layers, including the {@code CatalogResource} enum.</li>
 * </ul>
 */
package dev.starwars.catalog;
