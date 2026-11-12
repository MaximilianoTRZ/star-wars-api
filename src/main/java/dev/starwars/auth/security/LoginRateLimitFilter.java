package dev.starwars.auth.security;

import dev.starwars.auth.security.LoginRateLimiter.Decision;
import dev.starwars.auth.service.AuthMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Registered by {@link SecurityConfig}. Runs right after the correlation ID and request logging filters, so the 429 carries a {@code requestId} and gets an access line. The IP comes from
 * {@link HttpServletRequest#getRemoteAddr()}; in {@code prod} Spring resolves it from {@code X-Forwarded-For}
 * because the app runs behind Render's proxy ({@code server.forward-headers-strategy=framework}).
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/v1/auth/login";

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);

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
        String clientIp = request.getRemoteAddr();
        Decision decision = limiter.tryConsume(clientIp);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        metrics.loginBlocked();
        long retryAfterSeconds = Math.max(1, (decision.retryAfter().toMillis() + 999) / 1000);
        log.atWarn()
                .setMessage("Login blocked by rate limit for {}, retry in {}s")
                .addArgument(clientIp)
                .addArgument(retryAfterSeconds)
                .addKeyValue("client.ip", clientIp)
                .addKeyValue("retry_after_s", retryAfterSeconds)
                .log();
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
