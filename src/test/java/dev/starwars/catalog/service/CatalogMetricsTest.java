package dev.starwars.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.catalog.domain.CatalogChannel;
import dev.starwars.catalog.domain.CatalogResource;
import dev.starwars.catalog.domain.SearchCriteria;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;

class CatalogMetricsTest {

    @ParameterizedTest(name = "id={0}, name={1} -> {2}")
    @CsvSource(
            nullValues = "null",
            value = {"null, null, none", "1, null, id", "null, sky, name", "1, sky, id_and_name", "'  ', '  ', none"})
    void filterTag(String id, String name, String expected) {
        assertThat(CatalogMetrics.filter(new SearchCriteria(id, name))).isEqualTo(expected);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("queries are tagged with the channel in the MDC, REST by default")
    void channelTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CatalogMetrics metrics = new CatalogMetrics(registry);

        metrics.queryExecuted(CatalogResource.PEOPLE, SearchCriteria.none());
        MDC.put(CatalogChannel.MDC_KEY, CatalogChannel.GRAPHQL.label());
        metrics.queryExecuted(CatalogResource.PEOPLE, SearchCriteria.none());
        metrics.queryExecuted(CatalogResource.PEOPLE, SearchCriteria.none());

        assertThat(registry.get(CatalogMetrics.QUERIES)
                        .tag("channel", "rest")
                        .counter()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get(CatalogMetrics.QUERIES)
                        .tag("channel", "graphql")
                        .counter()
                        .count())
                .isEqualTo(2);
    }
}
