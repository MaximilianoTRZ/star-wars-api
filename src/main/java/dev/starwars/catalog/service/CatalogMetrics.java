package dev.starwars.catalog.service;

import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.SearchCriteria;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics for catalog queries.
 *
 * <p>Cardinality rule: tags only take values from a bounded set. Text typed by the user is never a tag;
 * the {@code entity} tag holds the name returned by SWAPI, a finite catalog of about 160 entities.
 */
@Component
public class CatalogMetrics {

    static final String QUERIES = "catalog.queries";
    static final String ENTITY_VIEWS = "catalog.entity.views";
    static final String EMPTY_SEARCHES = "catalog.searches.empty";
    static final String PAGE_REQUESTED = "catalog.page.requested";

    private final MeterRegistry registry;

    public CatalogMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Counts a listing by resource and kind of filter. */
    public void queryExecuted(CatalogResource resource, SearchCriteria criteria) {
        Counter.builder(QUERIES)
                .description("Catalog listings by resource and filter kind")
                .tag("resource", resource.label())
                .tag("filter", filter(criteria))
                .register(registry)
                .increment();
    }

    /** Records the requested page number, to see how deep users paginate. */
    public void pageRequested(CatalogResource resource, int page) {
        DistributionSummary.builder(PAGE_REQUESTED)
                .description("Requested page number")
                .tag("resource", resource.label())
                .register(registry)
                .record(page);
    }

    /** Counts a name search that found nothing. */
    public void emptySearch(CatalogResource resource) {
        Counter.builder(EMPTY_SEARCHES)
                .description("Name searches without results")
                .tag("resource", resource.label())
                .register(registry)
                .increment();
    }

    /**
     * Counts a detail view of an entity resolved by SWAPI.
     *
     * @param name entity name as returned by SWAPI, never the user's input
     */
    public void entityViewed(CatalogResource resource, String name) {
        Counter.builder(ENTITY_VIEWS)
                .description("Detail views by entity")
                .tag("resource", resource.label())
                .tag("entity", name == null ? "unknown" : name)
                .register(registry)
                .increment();
    }

    static String filter(SearchCriteria criteria) {
        if (criteria.hasId() && criteria.hasName()) {
            return "id_and_name";
        }
        if (criteria.hasId()) {
            return "id";
        }
        return criteria.hasName() ? "name" : "none";
    }
}
