package dev.starwars.catalog.graphql;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caps how many root fields a single GraphQL query can request. Each root field ({@code people}, {@code film},
 * ...) may be a call to SWAPI, which allows 100 requests per IP; without a cap, one query with aliases could
 * fan out into dozens of calls. The query is rejected before running any field.
 *
 * <p>Depth is not limited: the schema has no recursive types, and GraphiQL's introspection query is deep.
 */
@Configuration(proxyBeanMethods = false)
public class GraphQlLimitsConfig {

    static final int MAX_ROOT_FIELDS = 5;

    @Bean
    Instrumentation maxRootFieldsInstrumentation() {
        return new MaxQueryComplexityInstrumentation(
                MAX_ROOT_FIELDS, (env, childComplexity) -> env.getParentEnvironment() == null ? 1 : 0);
    }
}
