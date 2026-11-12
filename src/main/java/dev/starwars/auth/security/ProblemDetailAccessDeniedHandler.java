package dev.starwars.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 as ProblemDetail: the token is valid but lacks the required scope. */
@Component
class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailResponseWriter writer;

    ProblemDetailAccessDeniedHandler(ProblemDetailResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
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
