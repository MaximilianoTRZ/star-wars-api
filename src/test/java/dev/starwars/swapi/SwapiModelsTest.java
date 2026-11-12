package dev.starwars.swapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwapiModelsTest {

    @Test
    @DisplayName("films is searched by title and not paginated by SWAPI")
    void filmsDiffersFromOtherResources() {
        assertThat(ResourceType.FILMS.path()).isEqualTo("films");
        assertThat(ResourceType.FILMS.nameAttribute()).isEqualTo("title");
        assertThat(ResourceType.FILMS.paginatedUpstream()).isFalse();
        assertThat(ResourceType.PEOPLE.nameAttribute()).isEqualTo("name");
        assertThat(ResourceType.PEOPLE.paginatedUpstream()).isTrue();
    }

    @Test
    @DisplayName("an entity drops null properties and returns values as text")
    void entityPropertiesAsText() {
        var properties = new HashMap<String, Object>();
        properties.put("name", "A New Hope");
        properties.put("episode_id", 4);
        properties.put("director", null);

        var entity = new SwapiEntity("1", properties);

        assertThat(entity.property("episode_id")).isEqualTo("4");
        assertThat(entity.property("director")).isNull();
        assertThat(entity.properties()).doesNotContainKey("director");
    }

    @Test
    @DisplayName("an entity without properties has an empty map and requires a uid")
    void entityDefaults() {
        assertThat(new SwapiEntity("1", null).properties()).isEmpty();
        assertThatThrownBy(() -> new SwapiEntity(null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a page keeps a copy of its items")
    void pageCopiesItems() {
        var items = new ArrayList<>(List.of(new SwapiEntity("1", null)));

        var page = new SwapiPage(items, 1, 1);
        items.clear();

        assertThat(page.items()).hasSize(1);
    }

    @Test
    @DisplayName("an upstream error exposes its reason and the optional Retry-After")
    void upstreamUnavailableDetails() {
        var limited = new UpstreamUnavailableException(
                "429", UpstreamUnavailableException.Reason.RATE_LIMITED, Duration.ofSeconds(5), null);
        var down =
                new UpstreamUnavailableException("500", UpstreamUnavailableException.Reason.SERVER_ERROR, null, null);

        assertThat(limited.reason()).isEqualTo(UpstreamUnavailableException.Reason.RATE_LIMITED);
        assertThat(limited.retryAfter()).contains(Duration.ofSeconds(5));
        assertThat(down.retryAfter()).isEmpty();
    }

    @Test
    @DisplayName("a client exception keeps its cause")
    void clientExceptionCause() {
        var cause = new IllegalStateException("boom");

        assertThat(new SwapiClientException("bad", cause)).hasCause(cause);
        assertThat(new SwapiClientException("bad")).hasMessage("bad");
    }
}
