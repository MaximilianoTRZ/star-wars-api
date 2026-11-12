package dev.starwars.catalog.web;

import dev.starwars.catalog.domain.CatalogChannel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the {@link CatalogChannel} of the request in the MDC: {@code /graphql} is GraphQL and {@code /mcp} is MCP.
 * Other requests carry no channel and count as REST.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class CatalogChannelFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return CatalogChannel.forPath(request.getRequestURI()).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<CatalogChannel> channel = CatalogChannel.forPath(request.getRequestURI());
        channel.ifPresent(c -> MDC.put(CatalogChannel.MDC_KEY, c.label()));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CatalogChannel.MDC_KEY);
        }
    }
}
