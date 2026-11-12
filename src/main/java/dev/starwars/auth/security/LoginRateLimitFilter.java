package dev.starwars.auth.security;

import dev.starwars.auth.security.LoginRateLimiter.Decision;
import dev.starwars.auth.service.AuthMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies {@link LoginRateLimiter} to {@code POST /api/v1/auth/login}, keyed by client IP. Over the limit it
 * answers 429 with {@code Retry-After} before the request reaches security or the controller.
 *
 * <p>Registered by {@link SecurityConfig}. Runs right after the correlation ID filter, so the 429 carries a {@code requestId}. The IP comes from
 * {@link HttpServletRequest#getRemoteAddr()}; in {@code prod} Spring resolves it from {@code X-Forwarded-For}
 * because the app runs behind Render's proxy ({@code server.forward-headers-strategy=framework}).
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/v1/auth/login";

    private final LoginRateLimiter limiter;
    private final ProblemDetailResponseWriter writer;
    private final AuthMetrics metrics;

    LoginRateLimitFilter(LoginRateLimiter limiter, ProblemDetailResponseWriter writer, AuthMetrics metrics) {
        this.limiter = limiter;
        this.writer = writer;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Decision decision = limiter.tryConsume(request.getRemoteAddr());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        metrics.loginBlocked();
        long retryAfterSeconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        writer.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "too-many-requests",
                "Too many requests",
                "Too many login attempts. Try again in " + retryAfterSeconds + " seconds.");
    }
}
