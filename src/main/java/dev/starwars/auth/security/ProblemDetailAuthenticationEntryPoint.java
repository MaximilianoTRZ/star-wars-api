package dev.starwars.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 401 as ProblemDetail: the token is missing, invalid, expired or from another issuer. The reason is logged at
 * {@code INFO} (it never contains the token); the response gives the client a generic message.
 */
@Component
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAuthenticationEntryPoint.class);

    private final ProblemDetailResponseWriter writer;

    ProblemDetailAuthenticationEntryPoint(ProblemDetailResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        log.atInfo()
                .setMessage("Request rejected as unauthenticated: {}")
                .addArgument(authException.getMessage())
                .addKeyValue("auth.failure", authException.getClass().getSimpleName())
                .log();
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writer.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Unauthorized",
                "Missing or invalid access token. Get one from POST /api/v1/auth/login.");
    }
}
