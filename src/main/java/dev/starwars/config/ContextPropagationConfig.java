package dev.starwars.config;

import dev.starwars.auth.security.AuthenticatedUserFilter;
import dev.starwars.common.web.CorrelationIdFilter;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.integration.Slf4jThreadLocalAccessor;
import org.springframework.context.annotation.Configuration;

/**
 * Carries the request's MDC ({@code requestId} and {@code user}) to the threads where Spring for GraphQL runs
 * the queries. With virtual threads enabled, GraphQL controllers run on an executor; Spring for GraphQL already
 * propagates the security context and the request attributes, but not the MDC. Without this, the logs of a
 * GraphQL query and its errors would lose the correlation ID.
 */
@Configuration(proxyBeanMethods = false)
public class ContextPropagationConfig {

    public ContextPropagationConfig() {
        // Registering again under the same key replaces the accessor, so test contexts can call this repeatedly
        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(
                        new Slf4jThreadLocalAccessor(CorrelationIdFilter.MDC_KEY, AuthenticatedUserFilter.MDC_KEY));
    }
}
