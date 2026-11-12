package dev.starwars.catalog.graphql;

import dev.starwars.catalog.domain.CatalogChannel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Marks requests to {@code /graphql} with the {@link CatalogChannel#GRAPHQL} channel in the MDC. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class GraphQlChannelFilter extends OncePerRequestFilter {

    static final String PATH = "/graphql";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put(CatalogChannel.MDC_KEY, CatalogChannel.GRAPHQL.label());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CatalogChannel.MDC_KEY);
        }
    }
}
