package dev.starwars.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.starwars.catalog.domain.SearchCriteria;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CatalogMetricsTest {

    @ParameterizedTest(name = "id={0}, name={1} -> {2}")
    @CsvSource(
            nullValues = "null",
            value = {"null, null, none", "1, null, id", "null, sky, name", "1, sky, id_and_name", "'  ', '  ', none"})
    void filterTag(String id, String name, String expected) {
        assertThat(CatalogMetrics.filter(new SearchCriteria(id, name))).isEqualTo(expected);
    }
}
