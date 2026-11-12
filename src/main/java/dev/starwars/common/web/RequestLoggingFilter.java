package dev.starwars.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Writes one access line per request when it finishes: method, route, status, duration and, if the request
 * was authenticated, the user. 5xx responses are logged as {@code WARN}, the rest as {@code INFO}.
 *
 * <p>Runs right after {@link CorrelationIdFilter}, so the line carries the {@code requestId}, and before the
 * security filters, so 401, 403 and 429 are logged too. The route is the matched pattern
 * ({@code /api/v1/people/{id}}) when there is one; the query string is never logged. Health probes and
 * Actuator are skipped because the platform polls them constantly.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** Request attribute with the authenticated user, set by the security layer. */
    public static final String USER_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".user";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/livez") || path.equals("/readyz") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            int status = response.getStatus();
            String route = route(request);
            LoggingEventBuilder line = log.atLevel(status >= 500 ? Level.WARN : Level.INFO)
                    .setMessage("{} {} {} {}ms")
                    .addArgument(request.getMethod())
                    .addArgument(route)
                    .addArgument(status)
                    .addArgument(durationMs)
                    .addKeyValue("http.method", request.getMethod())
                    .addKeyValue("http.route", route)
                    .addKeyValue("http.status", status)
                    .addKeyValue("duration_ms", durationMs);
            Object user = request.getAttribute(USER_ATTRIBUTE);
            if (user != null) {
                line = line.addKeyValue("user", user);
            }
            line.log();
        }
    }

    private static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }
}
