package dev.starwars.auth.security;

import dev.starwars.common.web.RequestLoggingFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the user of a valid token in the MDC ({@value #MDC_KEY}), so every log line of the request names it,
 * and in a request attribute for the access line written by {@link RequestLoggingFilter}.
 *
 * <p>Added to the API security chain right after the bearer token filter. Only the {@code sub} claim is logged,
 * never the token.
 */
class AuthenticatedUserFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        String user = authentication.getName();
        request.setAttribute(RequestLoggingFilter.USER_ATTRIBUTE, user);
        MDC.put(MDC_KEY, user);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
