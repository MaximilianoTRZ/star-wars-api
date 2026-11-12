package dev.starwars.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DomainRecordsTest {

    @Test
    @DisplayName("SearchCriteria treats blanks as absent and trims spaces")
    void searchCriteriaNormalizes() {
        var criteria = new SearchCriteria("  ", "  luke ");

        assertThat(criteria.hasId()).isFalse();
        assertThat(criteria.name()).isEqualTo("luke");
        assertThat(SearchCriteria.none().hasName()).isFalse();
    }

    @Test
    @DisplayName("PageRequestParams rejects page < 1 and size outside 1..100")
    void pageRequestValidates() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PageRequestParams(0, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageRequestParams(1, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new PageRequestParams(1, 101));
        assertThat(new PageRequestParams(3, 10).offset()).isEqualTo(20);
    }

    @Test
    @DisplayName("PageResponse.map keeps the totals")
    void pageResponseMap() {
        var page = new PageResponse<>(List.of(1, 2), 2, 2, 10, 5);

        PageResponse<String> mapped = page.map(i -> "#" + i);

        assertThat(mapped).isEqualTo(new PageResponse<>(List.of("#1", "#2"), 2, 2, 10, 5));
    }

    @Test
    @DisplayName("CatalogItem reads attributes as text or integer")
    void catalogItemAttributes() {
        var item = new CatalogItem("1", "A New Hope", Map.of("episodeId", 4, "crew", "46", "length", "n/a"));

        assertThat(item.attribute("crew")).isEqualTo("46");
        assertThat(item.integerAttribute("episodeId")).isEqualTo(4);
        assertThat(item.integerAttribute("crew")).isEqualTo(46);
        assertThat(item.integerAttribute("length")).isNull();
        assertThat(item.attribute("missing")).isNull();
    }

    @Test
    @DisplayName("ResourceNotFoundException names the resource and the ID")
    void notFoundMessage() {
        var exception = new ResourceNotFoundException(CatalogResource.STARSHIPS, "99");

        assertThat(exception).hasMessage("No starships found with id 99");
        assertThat(exception.resource()).isEqualTo(CatalogResource.STARSHIPS);
        assertThat(exception.id()).isEqualTo("99");
    }

    @Test
    void channelByPath() {
        assertThat(CatalogChannel.forPath("/graphql")).contains(CatalogChannel.GRAPHQL);
        assertThat(CatalogChannel.forPath("/mcp")).contains(CatalogChannel.MCP);
        assertThat(CatalogChannel.forPath("/api/v1/people")).isEmpty();
        assertThat(CatalogChannel.MCP.label()).isEqualTo("mcp");
    }
}
