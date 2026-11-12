package dev.starwars.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 as ProblemDetail: the token is valid but lacks the required scope. Logged at {@code INFO}. */
@Component
class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

    private final ProblemDetailResponseWriter writer;

    ProblemDetailAccessDeniedHandler(ProblemDetailResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.info("Request forbidden: the token lacks scope {}", SecurityConfig.CATALOG_READ_SCOPE);
        writer.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Forbidden",
                "The token is not allowed to access this resource (requires scope " + SecurityConfig.CATALOG_READ_SCOPE
                        + ").");
    }
}
