package dev.starwars.swapi;

import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;

/**
 * Caches SWAPI responses in front of the delegate. SWAPI data is practically static, so every
 * pagination strategy (including in-memory pagination) benefits from it.
 *
 * <p>Errors are never cached (an exception skips the put), and neither is the empty result of a 404,
 * so a transient failure is not pinned. It is a separate bean because {@code @Cacheable} does not
 * apply to calls inside the same class.
 */
public class CachingSwapiClient implements SwapiClient {

    public static final String LIST_CACHE = "swapi-list";
    public static final String SEARCH_CACHE = "swapi-search";
    public static final String DETAIL_CACHE = "swapi-detail";

    private final SwapiClient delegate;

    public CachingSwapiClient(SwapiClient delegate) {
        this.delegate = delegate;
    }

    @Override
    @Cacheable(cacheNames = LIST_CACHE, key = "#type.name() + ':' + #page + ':' + #limit")
    public SwapiPage list(ResourceType type, int page, int limit) {
        return delegate.list(type, page, limit);
    }

    @Override
    @Cacheable(
            cacheNames = SEARCH_CACHE,
            key = "#type.name() + ':' + #term.strip().toLowerCase(T(java.util.Locale).ROOT)")
    public List<SwapiEntity> search(ResourceType type, String term) {
        return delegate.search(type, term);
    }

    @Override
    @Cacheable(cacheNames = DETAIL_CACHE, key = "#type.name() + ':' + #id", unless = "#result == null")
    public Optional<SwapiEntity> findById(ResourceType type, String id) {
        return delegate.findById(type, id);
    }
}
