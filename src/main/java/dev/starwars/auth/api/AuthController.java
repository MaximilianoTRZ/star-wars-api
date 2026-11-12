package dev.starwars.auth.api;

import dev.starwars.auth.service.AuthMetrics;
import dev.starwars.auth.service.TokenService;
import dev.starwars.auth.service.TokenService.IssuedToken;
import dev.starwars.common.web.RequestLoggingFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login: validates username and password and returns a JWT. A success is logged with the user; a failure is
 * logged with the client IP only, because the username field may hold a mistyped password.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Login and access tokens")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AuthMetrics authMetrics;

    public AuthController(
            AuthenticationManager authenticationManager, TokenService tokenService, AuthMetrics authMetrics) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.authMetrics = authMetrics;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Log in",
            description = "Returns a JWT valid for 1 hour with the catalog:read scope. "
                    + "Send it as `Authorization: Bearer <token>`.")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            authMetrics.loginFailed();
            log.atWarn()
                    .setMessage("Login failed from {}")
                    .addArgument(httpRequest.getRemoteAddr())
                    .addKeyValue("client.ip", httpRequest.getRemoteAddr())
                    .log();
            throw ex;
        }
        authMetrics.loginSucceeded(authentication.getName());
        httpRequest.setAttribute(RequestLoggingFilter.USER_ATTRIBUTE, authentication.getName());
        log.atInfo()
                .setMessage("Login succeeded for {}")
                .addArgument(authentication.getName())
                .addKeyValue("user", authentication.getName())
                .log();
        IssuedToken token = tokenService.issue(authentication.getName());
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }
}
